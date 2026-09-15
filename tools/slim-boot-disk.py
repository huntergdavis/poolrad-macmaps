#!/usr/bin/env python3
"""Rebuild a combined boot+game disk as a small, single-purpose one.

The supplied 24 MiB image carries a full working Macintosh: printer drivers,
Apple Guide, MacTCP, PC Exchange, an appearance hack, a desktop-picture
extension, puzzles. None of it is needed to boot and play one game, and some of
it actively gets in the way -- the Backdrop extension paints over the desktop
pattern, and every extension is startup time and System heap.

This keeps only what boots the Mac and runs the game, and writes the result
into a fresh, smaller volume. It builds with hfsutils rather than rebuilding
the catalog with machfs, because machfs-reconstructed images of this System
fail at startup in General Controls; see docs/LOCAL_TESTING.md.

The output still has the game in Startup Items, exactly as the source does, so
follow this with fix-startup-disk.py to move it out and leave one alias.
"""
import argparse, os, shutil, struct, subprocess, sys, tempfile, hashlib

import machfs

# Everything the Mac needs to start up, plus the game. Anything not named here
# is left out. Paths are tuples under the volume root.
KEEP_FILES = [
    ("System Folder", "System"),
    ("System Folder", "Finder"),
]
# Only the system typefaces; Palatino, Times, Courier, Helvetica, Symbol and
# New York are a megabyte of things this disk will never print with.
KEEP_FONTS = ["Chicago", "Geneva", "Monaco"]
# The few panels worth having on a single-purpose machine: screen depth, volume
# and the memory/disk-cache settings that affect the game.
KEEP_CONTROL_PANELS = ["Monitors", "Sound", "Memory", "General Controls"]
EMPTY_FOLDERS = [("System Folder", "Preferences"), ("System Folder", "Startup Items")]
GAME_PARENT = ("System Folder", "Startup Items")


def hfs(*args):
    return subprocess.check_output(list(args), stderr=subprocess.STDOUT).decode("mac_roman")


def mac_path(parts):
    return ":" + ":".join(parts)


def read_volume(path):
    raw = open(path, "rb").read()
    volume = machfs.Volume()
    volume.read(raw)
    length = raw[1024 + 36]
    volume.name = raw[1024 + 37:1024 + 37 + length].decode("mac_roman")
    return raw, volume


def plan(volume):
    """Every path to carry over, deepest folders first so parents exist."""
    files, folders = [], []
    for parts in KEEP_FILES:
        files.append(parts)
    for name in KEEP_FONTS:
        if name in volume[("System Folder", "Fonts")]:
            files.append(("System Folder", "Fonts", name))
    for name in KEEP_CONTROL_PANELS:
        if name in volume[("System Folder", "Control Panels")]:
            files.append(("System Folder", "Control Panels", name))

    def walk(parts):
        node = volume[parts]
        for name, child in node.items():
            if isinstance(child, machfs.File):
                files.append(parts + (name,))
            else:
                folders.append(parts + (name,))
                walk(parts + (name,))
    folders.append(GAME_PARENT)
    walk(GAME_PARENT)

    for parts in files:
        for depth in range(1, len(parts)):
            branch = parts[:depth]
            if branch not in folders:
                folders.append(branch)
    for parts in EMPTY_FOLDERS:
        if parts not in folders:
            folders.append(parts)
    folders.sort(key=len)
    return folders, files


def size_of(volume, files):
    total = 0
    for parts in files:
        node = volume[parts]
        total += len(node.data) + len(node.rsrc)
    return total


def build(source, destination, volume, folders, files, blocks):
    staging = tempfile.mkdtemp(prefix="slim-boot-")
    try:
        hfs("hmount", source)
        try:
            for i, parts in enumerate(files):
                out = os.path.join(staging, "%04d.bin" % i)
                hfs("hcopy", "-m", mac_path(parts), out)
        finally:
            hfs("humount")

        with open(destination, "wb") as handle:
            handle.truncate(blocks * 512)
        hfs("hformat", "-l", volume.name, destination)

        hfs("hmount", destination)
        try:
            for parts in folders:
                hfs("hmkdir", mac_path(parts))
            for i, parts in enumerate(files):
                hfs("hcopy", "-m", os.path.join(staging, "%04d.bin" % i),
                    mac_path(parts[:-1]) + ":")
            system_folder_cnid = int(hfs("hls", "-id", ":System Folder").split()[0])
        finally:
            hfs("humount")
        return system_folder_cnid
    finally:
        shutil.rmtree(staging, ignore_errors=True)


def bless(source_raw, destination, system_folder_cnid):
    """Carry the original boot blocks over and point them at the System Folder."""
    raw = bytearray(open(destination, "rb").read())
    raw[0:1024] = source_raw[0:1024]
    struct.pack_into(">I", raw, 1024 + 92, system_folder_cnid)
    open(destination, "wb").write(bytes(raw))


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("destination")
    parser.add_argument("--megabytes", type=int, default=12,
                        help="size of the new image (default 12)")
    args = parser.parse_args()

    source_raw, volume = read_volume(args.source)
    print("source %s  %.1f MiB  volume %r"
          % (args.source, len(source_raw) / 1048576, volume.name))
    folders, files = plan(volume)
    kept = size_of(volume, files)
    blocks = args.megabytes * 1048576 // 512
    print("keeping %d files in %d folders, %.2f MiB of content"
          % (len(files), len(folders), kept / 1048576))
    if kept > blocks * 512 * 0.85:
        raise SystemExit("Content does not comfortably fit a %d MiB volume" % args.megabytes)

    cnid = build(args.source, args.destination, volume, folders, files, blocks)
    bless(source_raw, args.destination, cnid)

    out_raw, rebuilt = read_volume(args.destination)
    for parts in files:
        before, after = volume[parts], rebuilt[parts]
        if (before.data, before.rsrc) != (after.data, after.rsrc):
            raise SystemExit("Fork mismatch after rebuild: %s" % mac_path(parts))
        if (before.type, before.creator) != (after.type, after.creator):
            raise SystemExit("Finder type or creator changed: %s" % mac_path(parts))
    if struct.unpack_from(">I", out_raw, 1024 + 92)[0] != cnid:
        raise SystemExit("The System Folder was not blessed")
    print("wrote %s  %.1f MiB  sha256 %s"
          % (args.destination, len(out_raw) / 1048576,
             hashlib.sha256(out_raw).hexdigest()))
    print("every kept file verified fork-for-fork; System Folder blessed as %d" % cnid)


if __name__ == "__main__":
    sys.exit(main())
