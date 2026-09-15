#!/usr/bin/env python3
"""Repair a combined boot+game HFS disk whose game sits inside Startup Items.

System 7 opens *every* item in System Folder:Startup Items at startup. A disk
built by dropping the whole game folder in there therefore opens the journals,
the rule books and the data folders at boot instead of just launching the game,
and the Mac's own text editor then refuses the oversized TEXT files.

This moves those items into a folder at the volume root and leaves a single
alias behind, which is the layout tools/prepare-personal-boot.py produces. It
optionally rewrites the desktop pattern at the same time, because doing it here
costs a rebuild rather than a guest shutdown.

Reads the source read-only and always writes a new file; never modifies input.
"""
import argparse, binascii, datetime, hashlib, os, struct, subprocess, sys, tempfile

import machfs
from macresources import Resource, make_file
from mac_alias import Alias, VolumeInfo, TargetInfo

STARTUP_PARENT = ("System Folder", "Startup Items")
SYSTEM = ("System Folder", "System")
ALIAS_NAME = "Pool of Radiance"
APP_TYPE, APP_CREATOR = b"APPL", b"prad"
# Recovered from the withdrawn DesktopDisk: eight rows of an 8x8 bit pattern.
PATTERNS = {
    "white": bytes(8),
    "mist": bytes([0x88, 0, 0x22, 0, 0x88, 0, 0x22, 0]),
    "stone": bytes([0xff, 0x80, 0x80, 0x80, 0xff, 0x08, 0x08, 0x08]),
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def load_volume(raw):
    if len(raw) < 4096 or len(raw) % 512 or raw[1024:1026] != b"BD":
        raise ValueError("Expected a complete raw HFS image")
    volume = machfs.Volume()
    volume.read(raw)
    length = raw[1024 + 36]
    if not 1 <= length <= 27:
        raise ValueError("Invalid HFS volume name")
    volume.name = raw[1024 + 37:1024 + 37 + length].decode("mac_roman")
    return volume


def find_application(folder):
    found = [name for name, obj in folder.items()
             if isinstance(obj, machfs.File) and obj.type == APP_TYPE and obj.creator == APP_CREATOR]
    if len(found) != 1:
        raise ValueError("Expected exactly one %s/%s application in Startup Items, found %d"
                         % (APP_TYPE.decode(), APP_CREATOR.decode(), len(found)))
    return found[0]


def colour_pattern(current, bits):
    """Reproduces the withdrawn DesktopDisk.colorPattern byte-for-byte."""
    if len(current) != 182:
        raise ValueError("Unsupported System desktop pixel-pattern size")
    result = bytearray(current)
    result[20:28] = bits
    for y in range(8):
        for x in range(0, 8, 2):
            row = bits[y]
            result[78 + y * 4 + x // 2] = ((row >> (7 - x)) & 1) * 16 + ((row >> (6 - x)) & 1)
    result[120:126] = b"\xff" * 6   # Palette 0: white.
    result[128:134] = b"\0" * 6     # Palette 1: black.
    return bytes(result)


def resource_offsets(fork):
    """Byte ranges of each resource's payload inside a raw resource fork."""
    if len(fork) < 16:
        raise ValueError("System resource fork is too small")
    data_off, map_off, data_len, map_len = struct.unpack_from(">IIII", fork, 0)
    if data_off + data_len > len(fork) or map_off + map_len > len(fork):
        raise ValueError("System resource fork header is out of range")
    type_list_off = map_off + struct.unpack_from(">H", fork, map_off + 24)[0]
    count = struct.unpack_from(">H", fork, type_list_off)[0] + 1
    found = {}
    for i in range(count):
        at = type_list_off + 2 + i * 8
        kind, entries, ref_off = struct.unpack_from(">4sHH", fork, at)
        for j in range(entries + 1):
            ref = type_list_off + ref_off + j * 12
            rid, _, attrs_and_off = struct.unpack_from(">hHI", fork, ref)
            payload = data_off + (attrs_and_off & 0x00ffffff)
            size = struct.unpack_from(">I", fork, payload)[0]
            found[(kind, rid)] = (payload + 4, size)
    return found


def apply_pattern(volume, style):
    system = volume[SYSTEM]
    fork = bytearray(system.rsrc)
    offsets = resource_offsets(fork)
    bits = PATTERNS[style]
    for key, expected in ((b"PAT ", 8), (b"ppat", 182)):
        if (key, 16) not in offsets:
            raise ValueError("System is missing %s resource 16" % key.decode())
        start, size = offsets[(key, 16)]
        if size != expected:
            raise ValueError("Unsupported %s resource size %d" % (key.decode(), size))
    pat_at, _ = offsets[(b"PAT ", 16)]
    ppat_at, _ = offsets[(b"ppat", 16)]
    before = bytes(fork)
    fork[pat_at:pat_at + 8] = bits
    fork[ppat_at:ppat_at + 182] = colour_pattern(bytes(before[ppat_at:ppat_at + 182]), bits)
    changed = sum(1 for a, b in zip(before, fork) if a != b)
    if len(fork) != len(system.rsrc):
        raise ValueError("Desktop patch changed the resource fork length")
    system.rsrc = bytes(fork)
    return changed


def build_alias(volume, raw_after_move, app_path, folder_name):
    """CNIDs only exist once written, so query the staged image with hfsutils."""
    with tempfile.NamedTemporaryFile(suffix=".dsk", delete=False) as staged:
        staged.write(raw_after_move)
        staged_name = staged.name
    try:
        def hfs(*args):
            return subprocess.check_output(args, stderr=subprocess.STDOUT)
        hfs(b"hmount", staged_name.encode())
        try:
            folder_cnid = int(hfs(b"hls", b"-id", (":" + folder_name).encode("mac_roman")).split()[0])
            app_cnid = int(hfs(b"hls", b"-id",
                               (":" + folder_name + ":" + app_path).encode("mac_roman")).split()[0])
        finally:
            hfs(b"humount")
    finally:
        os.unlink(staged_name)

    epoch = datetime.datetime(1904, 1, 1, tzinfo=datetime.timezone.utc)
    application = volume[(folder_name, app_path)]
    alias = Alias(version=2,
        volume=VolumeInfo(volume.name, epoch + datetime.timedelta(seconds=volume.crdate),
                          b"BD", 5, 0, b"\0\0"),
        target=TargetInfo(0, app_path, folder_cnid, app_cnid,
                          epoch + datetime.timedelta(seconds=application.crdate),
                          application.creator, application.type, folder_name=folder_name,
                          cnid_path=[folder_cnid],
                          carbon_path=(volume.name + ":" + folder_name + ":" + app_path).encode("mac_roman")))
    encoded = alias.to_bytes()
    decoded = Alias.from_bytes(encoded)
    if decoded.target.cnid != app_cnid or decoded.target.folder_cnid != folder_cnid:
        raise ValueError("Startup alias catalog identity failed verification")
    node = machfs.File()
    node.type, node.creator = b"adrp", application.creator
    node.flags = 0x8000  # Finder alias bit.
    node.rsrc = bytes(make_file([Resource(b"alis", 0, data=encoded)]))
    node.crdate = node.mddate = application.crdate
    return node


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("destination")
    parser.add_argument("--folder", default="Pool Of Radiance",
                        help="root folder the game moves into")
    parser.add_argument("--desktop", choices=sorted(PATTERNS), default=None,
                        help="also rewrite the desktop pattern")
    args = parser.parse_args()

    if os.path.exists(args.destination):
        raise SystemExit("Refusing to overwrite an existing destination: " + args.destination)
    with open(args.source, "rb") as handle:
        raw = handle.read()
    print("source %s  %d bytes  sha256 %s" % (args.source, len(raw), digest(raw)))

    volume = load_volume(raw)
    startup = volume[STARTUP_PARENT]
    if not isinstance(startup, machfs.Folder):
        raise SystemExit("System Folder:Startup Items is not a folder")
    names = sorted(startup.keys())
    if not names:
        raise SystemExit("Startup Items is already empty; nothing to repair")
    app = find_application(startup)
    print("moving %d Startup Items entries into :%s  (application %r)"
          % (len(names), args.folder, app))
    if args.folder in volume:
        raise SystemExit("Volume already has a root :%s" % args.folder)

    moved = machfs.Folder()
    for name in names:
        moved[name] = startup.pop(name)
    volume[args.folder] = moved
    if len(startup):
        raise SystemExit("Startup Items did not empty")

    staged = volume.write(len(raw), align=512, desktopdb=False, bootable=True)
    alias = build_alias(volume, staged, app, args.folder)
    volume[STARTUP_PARENT + (ALIAS_NAME,)] = alias

    if args.desktop:
        changed = apply_pattern(volume, args.desktop)
        print("desktop pattern -> %s (%d System resource bytes changed)" % (args.desktop, changed))

    out = volume.write(len(raw), align=512, desktopdb=False, bootable=True)
    if len(out) != len(raw):
        raise SystemExit("Rebuilt image changed size")
    with open(args.destination, "wb") as handle:
        handle.write(out)
    print("wrote %s  %d bytes  sha256 %s" % (args.destination, len(out), digest(out)))
    print("Startup Items now holds exactly one alias: %r" % ALIAS_NAME)


if __name__ == "__main__":
    main()
