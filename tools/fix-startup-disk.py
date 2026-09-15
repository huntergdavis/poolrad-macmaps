#!/usr/bin/env python3
"""Move a game out of System Folder:Startup Items and leave one alias behind.

System 7 opens every item in Startup Items at login, so a combined boot+game
disk that keeps the whole game folder there opens all of it. This moves the
game to a root folder and leaves a single alias, so startup launches the game
and nothing else. It can also set the desktop pattern at the same time.

**Everything is done in place.** An earlier version of this tool rebuilt the
whole HFS volume with machfs and the result failed at startup with the Mac's
own "Not enough memory is available while using General Controls" -- the same
failure docs/PERSONAL_BOOT.md had already recorded for machfs-reconstructed
images, on a disk whose System file was byte-identical either way. Moving the
catalog entries with hfsutils instead changes roughly two kilobytes of a
twenty-five megabyte image and boots cleanly. Do not reintroduce a full-volume
writer here; see docs/LOCAL_TESTING.md for the three-way boot comparison.
"""
import argparse, binascii, datetime, hashlib, os, shutil, struct, subprocess, sys

import machfs
from macresources import Resource, make_file
from mac_alias import Alias, VolumeInfo, TargetInfo

STARTUP_PARENT = ":System Folder:Startup Items:"
ALIAS_NAME = "Pool of Radiance"
APP_TYPE, APP_CREATOR = b"APPL", b"prad"
# Eight rows of an 8x8 bit pattern, one bit per pixel, 1 is black.
# "bricks" is the standard offset course: a mortar row every four rows with the
# vertical joints staggered by half a brick. "stone" is the name an earlier
# version gave those identical bits and is kept so old commands still work.
BRICKS = bytes([0xff, 0x80, 0x80, 0x80, 0xff, 0x08, 0x08, 0x08])
PATTERNS = {
    "white": bytes(8),
    "mist": bytes([0x88, 0, 0x22, 0, 0x88, 0, 0x22, 0]),
    "bricks": BRICKS,
    "stone": BRICKS,
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def hfs(*args):
    return subprocess.check_output([a if isinstance(a, str) else a for a in args],
                                   stderr=subprocess.STDOUT).decode("mac_roman")


def read_volume(path):
    raw = open(path, "rb").read()
    if len(raw) < 4096 or len(raw) % 512 or raw[1024:1026] != b"BD":
        raise ValueError("Expected a complete raw HFS image")
    volume = machfs.Volume()
    volume.read(raw)
    length = raw[1024 + 36]
    if not 1 <= length <= 27:
        raise ValueError("Invalid HFS volume name")
    volume.name = raw[1024 + 37:1024 + 37 + length].decode("mac_roman")
    return raw, volume


def startup_entries(volume):
    folder = volume[("System Folder", "Startup Items")]
    names = list(folder.keys())
    apps = [n for n, o in folder.items()
            if isinstance(o, machfs.File) and o.type == APP_TYPE and o.creator == APP_CREATOR]
    if len(apps) != 1:
        raise ValueError("Expected exactly one %s/%s application in Startup Items, found %d"
                         % (APP_TYPE.decode(), APP_CREATOR.decode(), len(apps)))
    return names, apps[0]


def move_in_place(image, names, folder_name):
    """hfsutils edits the catalog; the rest of the volume is left untouched."""
    hfs("hmount", image)
    try:
        hfs("hmkdir", ":" + folder_name)
        for name in names:
            hfs("hrename", STARTUP_PARENT + name, ":" + folder_name + ":")
        remaining = [line for line in hfs("hls", "-1", STARTUP_PARENT).splitlines() if line.strip()]
        if remaining:
            raise ValueError("Startup Items still holds: %s" % ", ".join(remaining))
    finally:
        hfs("humount")


def catalog_ids(image, folder_name, app_name):
    hfs("hmount", image)
    try:
        folder_cnid = int(hfs("hls", "-id", ":" + folder_name).split()[0])
        app_cnid = int(hfs("hls", "-id", ":" + folder_name + ":" + app_name).split()[0])
    finally:
        hfs("humount")
    return folder_cnid, app_cnid


def alias_resource(volume, folder_name, app_name, folder_cnid, app_cnid):
    epoch = datetime.datetime(1904, 1, 1, tzinfo=datetime.timezone.utc)
    application = volume[(folder_name, app_name)]
    alias = Alias(version=2,
        volume=VolumeInfo(volume.name, epoch + datetime.timedelta(seconds=volume.crdate),
                          b"BD", 5, 0, b"\0\0"),
        target=TargetInfo(0, app_name, folder_cnid, app_cnid,
                          epoch + datetime.timedelta(seconds=application.crdate),
                          application.creator, application.type, folder_name=folder_name,
                          cnid_path=[folder_cnid],
                          carbon_path=(volume.name + ":" + folder_name + ":" + app_name).encode("mac_roman")))
    encoded = alias.to_bytes()
    decoded = Alias.from_bytes(encoded)
    if decoded.target.cnid != app_cnid or decoded.target.folder_cnid != folder_cnid:
        raise ValueError("Startup alias catalog identity failed verification")
    return bytes(make_file([Resource(b"alis", 0, data=encoded)])), application


def macbinary(name, type_, creator, flags, data, rsrc, crdate, mddate):
    """MacBinary II, so hcopy carries both forks and the Finder alias bit."""
    encoded = name.encode("mac_roman")
    if not 1 <= len(encoded) <= 63:
        raise ValueError("Unsupported alias name length")
    header = bytearray(128)
    header[1] = len(encoded)
    header[2:2 + len(encoded)] = encoded
    header[65:69] = type_
    header[69:73] = creator
    header[73] = (flags >> 8) & 0xff
    header[101] = flags & 0xff
    struct.pack_into(">II", header, 83, len(data), len(rsrc))
    struct.pack_into(">II", header, 91, crdate, mddate)
    header[122] = header[123] = 129
    struct.pack_into(">H", header, 124, binascii.crc_hqx(bytes(header[:124]), 0))
    pad = lambda blob: blob + bytes(-len(blob) % 128)
    return bytes(header) + pad(data) + pad(rsrc)


def write_alias(image, blob):
    path = os.path.join(os.path.dirname(os.path.abspath(image)), "_alias.bin")
    open(path, "wb").write(blob)
    try:
        hfs("hmount", image)
        try:
            hfs("hcopy", "-m", path, STARTUP_PARENT)
        finally:
            hfs("humount")
    finally:
        os.unlink(path)


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


def colour_pattern(current, bits):
    """The 'ppat' pixel image and its two-entry palette, left the same size."""
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


def locate(raw, fork, start, size, label):
    """Find a resource payload's absolute offset by its surrounding bytes."""
    margin = 48
    lo, hi = max(0, start - margin), min(len(fork), start + size + margin)
    window = bytes(fork[lo:hi])
    first = raw.find(window)
    if first < 0 or raw.find(window, first + 1) >= 0:
        raise ValueError("Could not uniquely locate %s inside the image" % label)
    return first + (start - lo)


def patch_pattern(image, style):
    """Rewrite only the desktop-pattern bytes where they already sit."""
    raw, volume = read_volume(image)
    fork = volume[("System Folder", "System")].rsrc
    offsets = resource_offsets(fork)
    bits = PATTERNS[style]
    for key, expected in ((b"PAT ", 8), (b"ppat", 182)):
        if (key, 16) not in offsets:
            raise ValueError("System is missing %s resource 16" % key.decode())
        if offsets[(key, 16)][1] != expected:
            raise ValueError("Unsupported %s resource size" % key.decode())
    pat_at, _ = offsets[(b"PAT ", 16)]
    ppat_at, _ = offsets[(b"ppat", 16)]
    pat_abs = locate(raw, fork, pat_at, 8, "PAT  16")
    ppat_abs = locate(raw, fork, ppat_at, 182, "ppat 16")
    patched = bytearray(raw)
    patched[pat_abs:pat_abs + 8] = bits
    patched[ppat_abs:ppat_abs + 182] = colour_pattern(bytes(fork[ppat_at:ppat_at + 182]), bits)
    changed = sum(1 for a, b in zip(raw, patched) if a != b)
    open(image, "wb").write(bytes(patched))
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("destination")
    parser.add_argument("--folder", default="Pool Of Radiance",
                        help="root folder to hold the moved game")
    parser.add_argument("--desktop", choices=sorted(PATTERNS), default=None,
                        help="also set the desktop pattern")
    args = parser.parse_args()

    raw, volume = read_volume(args.source)
    print("source %s  %d bytes  sha256 %s" % (args.source, len(raw), digest(raw)))
    names, app_name = startup_entries(volume)
    print("moving %d Startup Items entries into :%s  (application %r)"
          % (len(names), args.folder, app_name))

    shutil.copyfile(args.source, args.destination)
    move_in_place(args.destination, names, args.folder)
    folder_cnid, app_cnid = catalog_ids(args.destination, args.folder, app_name)
    _, moved = read_volume(args.destination)
    blob, application = alias_resource(moved, args.folder, app_name, folder_cnid, app_cnid)
    write_alias(args.destination, macbinary(ALIAS_NAME, b"adrp", application.creator,
                                            0x8000, b"", blob,
                                            application.crdate, application.crdate))
    if args.desktop:
        changed = patch_pattern(args.destination, args.desktop)
        print("desktop pattern -> %s (%d image bytes changed)" % (args.desktop, changed))

    after_raw, after = read_volume(args.destination)
    before_system = volume[("System Folder", "System")]
    after_system = after[("System Folder", "System")]
    if before_system.data != after_system.data:
        raise ValueError("The System data fork changed; it must not")
    if not args.desktop and before_system.rsrc != after_system.rsrc:
        raise ValueError("The System resource fork changed without a pattern request")
    remaining = after[("System Folder", "Startup Items")]
    if list(remaining.keys()) != [ALIAS_NAME]:
        raise ValueError("Startup Items holds %r" % list(remaining.keys()))
    alias_node = remaining[ALIAS_NAME]
    if alias_node.type != b"adrp" or not alias_node.flags & 0x8000:
        raise ValueError("The startup alias is not marked as an alias")
    for name in names:
        original, carried = volume[("System Folder", "Startup Items", name)], after[(args.folder, name)]
        if isinstance(original, machfs.File):
            if (original.data, original.rsrc, original.type, original.creator) != \
               (carried.data, carried.rsrc, carried.type, carried.creator):
                raise ValueError("Moved file %r changed" % name)
    differing = sum(1 for a, b in zip(raw, after_raw) if a != b)
    print("wrote %s  %d bytes  sha256 %s" % (args.destination, len(after_raw), digest(after_raw)))
    print("%d of %d image bytes differ from the source" % (differing, len(raw)))
    print("Startup Items now holds exactly one alias: %r" % ALIAS_NAME)


if __name__ == "__main__":
    sys.exit(main())
