#!/usr/bin/env python3
"""Build a private System 7 + Pool of Radiance disk; never edit input images.

Requires tools/personal-boot-requirements.txt and hfsutils. Catalogs are never
reconstructed: grow allocation storage, then append with hfsutils. Alias
encoding uses mac_alias (MIT). See docs/PERSONAL_BOOT.md.
"""
import argparse
import binascii
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

import machfs
from macresources import Resource, make_file, parse_file
from mac_alias import Alias, VolumeInfo, TargetInfo

SYSTEM = ("System Folder", "System")
FINDER = ("System Folder", "Finder")
GAME = ("Pool Of Radiance", "Pool of Radiance v1.1")
STARTUP = ("System Folder", "Startup Items", "Pool of Radiance")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def load_volume(raw):
    if len(raw) < 4096 or len(raw) % 512 or raw[1024:1026] != b"BD":
        raise ValueError("Expected a complete raw HFS image, not a partitioned disk or archive")
    volume = machfs.Volume()
    volume.read(raw)
    length = raw[1024 + 36]
    if not 1 <= length <= 27:
        raise ValueError("Invalid HFS volume name")
    # machfs.read restores dates but does not restore the volume label.
    volume.name = raw[1024 + 37:1024 + 37 + length].decode("mac_roman")
    return volume


def inventory(volume):
    objects = list(volume.iter_paths())
    names = {id(obj): path for path, obj in objects}
    result = {}
    for path, obj in objects:
        entry = {"kind": "file" if isinstance(obj, machfs.File) else "folder",
                 "created": obj.crdate, "modified": obj.mddate, "backup": obj.bkdate}
        if isinstance(obj, machfs.File):
            entry.update(dataBytes=len(obj.data), resourceBytes=len(obj.rsrc),
                         dataSha256=digest(obj.data), resourceSha256=digest(obj.rsrc),
                         type=obj.type.hex(), creator=obj.creator.hex(), flags=obj.flags)
            if obj.aliastarget is not None:
                target = names.get(id(obj.aliastarget))
                if target is None:
                    raise ValueError("Unsupported alias target: " + ":".join(path))
                entry["aliasTarget"] = list(target)
        result[path] = entry
    return result


def merge_folders(target, source, path=()):
    for name, obj in source.items():
        if name not in target:
            target[name] = obj
        elif isinstance(obj, machfs.Folder) and isinstance(target[name], machfs.Folder):
            merge_folders(target[name], obj, path + (name,))
        else:
            raise ValueError("Boot/game path collision; no file was overwritten: " + ":".join(path + (name,)))


def system_version(system):
    version = next((r for r in parse_file(system.rsrc) if r.type == b"vers" and r.id == 1), None)
    if version is None or len(version.data) < 7 or version.data[0] != 7:
        raise ValueError("This builder supports a verified System 7 startup folder, not other Mac versions")
    length = version.data[6]
    if 7 + length > len(version.data):
        raise ValueError("Truncated System version resource")
    return bytes(version.data[7:7 + length]).decode("mac_roman")


def compare_inventory(expected, actual):
    if set(expected) != set(actual):
        raise ValueError("File/folder inventory changed unexpectedly")
    for path, before in expected.items():
        after = actual[path]
        # hfsutils legitimately updates a parent directory's modification date
        # while appending children. All original file bytes/metadata stay exact.
        keys = before.keys() if before["kind"] == "file" else ("kind", "created", "backup")
        if any(before.get(key) != after.get(key) for key in keys):
            raise ValueError("Original file/folder changed: " + ":".join(path))


def grow_hfs(raw, size=32 * 1024 * 1024):
    """Grow a clean bare HFS copy without changing allocation size or any CNID.

    HFS extents are relative to the allocation-area start. Reserve the maximum
    16-block bitmap, shift that entire area intact, then expose free blocks.
    Deliberately reject >65535 allocation blocks, partition maps and dirty HFS.
    """
    if len(raw) < 4096 or len(raw) % 512 or raw[1024:1026] != b"BD":
        raise ValueError("A complete raw HFS image is required")
    mdb = bytearray(raw[1024:1536])
    old_count = struct.unpack_from(">H", mdb, 18)[0]
    alloc_size = struct.unpack_from(">I", mdb, 20)[0]
    old_start = struct.unpack_from(">H", mdb, 28)[0]
    bitmap_start = struct.unpack_from(">H", mdb, 14)[0]
    old_free = struct.unpack_from(">H", mdb, 34)[0]
    attributes = struct.unpack_from(">H", mdb, 10)[0]
    if not attributes & 0x100:
        raise ValueError("Source HFS was not cleanly unmounted; use a quiescent original copy")
    if attributes & 0x8080:
        raise ValueError("Locked HFS volumes are unsupported")
    if size <= len(raw) or size % 512 or not alloc_size or alloc_size % 512:
        raise ValueError("Invalid HFS growth size/allocation size")
    if bitmap_start < 3 or not old_count or old_free > old_count:
        raise ValueError("Invalid HFS bitmap metadata")
    if (old_count + 7) // 8 > (old_start - bitmap_start) * 512:
        raise ValueError("Volume bitmap overlaps allocation storage")
    if old_start * 512 + old_count * alloc_size > len(raw) - 1024:
        raise ValueError("Truncated HFS allocation area")
    new_start = max(old_start, bitmap_start + 16)
    new_count = (size - new_start * 512 - 1024) // alloc_size
    if not old_count < new_count <= 65535:
        raise ValueError("Cannot grow without changing allocation size; no catalog reconstruction is attempted")
    bitmap = bytearray(8192)
    original_bitmap = raw[bitmap_start * 512:bitmap_start * 512 + (old_count + 7) // 8]
    bitmap[:len(original_bitmap)] = original_bitmap
    used = sum(bool(bitmap[i // 8] & (0x80 >> (i % 8))) for i in range(old_count))
    if used != old_count - old_free:
        raise ValueError("HFS free block count does not match its bitmap")
    for i in range(old_count, 65536):
        bitmap[i // 8] &= ~(0x80 >> (i % 8))
    struct.pack_into(">H", mdb, 18, new_count)
    struct.pack_into(">H", mdb, 28, new_start)
    struct.pack_into(">H", mdb, 34, old_free + new_count - old_count)
    out = bytearray(size)
    out[:bitmap_start * 512] = raw[:bitmap_start * 512]
    out[bitmap_start * 512:bitmap_start * 512 + 8192] = bitmap
    area = raw[old_start * 512:old_start * 512 + old_count * alloc_size]
    out[new_start * 512:new_start * 512 + len(area)] = area
    out[1024:1536] = mdb
    out[-1024:-512] = mdb
    if out[:1024] != raw[:1024] or out[new_start * 512:new_start * 512 + len(area)] != area:
        raise ValueError("Allocation/boot byte preservation failed")
    return bytes(out)


def hfs_run(*args):
    try:
        return subprocess.check_output(args, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as error:
        raise ValueError("HFS operation failed: " + error.output.decode("utf-8", errors="replace").strip()) from error


def hfs_path(path):
    return (":" + ":".join(path)).encode("mac_roman")


def catalog_id(path):
    return int(hfs_run(b"hls", b"-id", hfs_path(path)).split()[0])


def alias_macbinary(volume, application, folder_cnid, app_cnid):
    """Use maintained v2 Alias encoder; MacBinary II wrapper reuses repo format."""
    epoch = datetime.datetime(1904, 1, 1, tzinfo=datetime.timezone.utc)
    alias = Alias(version=2,
        volume=VolumeInfo(volume.name, epoch + datetime.timedelta(seconds=volume.crdate),
                          b"BD", 5, 0, b"\0\0"),
        target=TargetInfo(0, GAME[-1], folder_cnid, app_cnid,
                          epoch + datetime.timedelta(seconds=application.crdate),
                          application.creator, application.type, folder_name=GAME[0],
                          cnid_path=[folder_cnid],
                          carbon_path=(volume.name + ":" + ":".join(GAME)).encode("mac_roman")))
    encoded = alias.to_bytes()
    decoded = Alias.from_bytes(encoded)
    if decoded.target.cnid != app_cnid or decoded.target.folder_cnid != folder_cnid:
        raise ValueError("Startup alias catalog identity failed verification")
    resource = make_file([Resource(b"alis", 0, data=encoded)])
    name = STARTUP[-1].encode("mac_roman")
    header = bytearray(128)
    header[1] = len(name)
    header[2:2 + len(name)] = name
    header[65:69], header[69:73] = b"adrp", application.creator
    header[73] = 0x80  # Finder alias bit; hfsutils imports this from MacBinary.
    struct.pack_into(">I", header, 87, len(resource))
    struct.pack_into(">II", header, 91, application.crdate, application.crdate)
    header[122] = header[123] = 129
    struct.pack_into(">H", header, 124, binascii.crc_hqx(header[:124], 0))
    return bytes(header) + bytes(resource) + bytes((-len(resource)) % 128)


def prepare(boot_raw, game_raw, size_mib=32, startup=True):
    if size_mib != 32:
        raise ValueError("This personal builder supports a 32 MiB output only")
    boot, game = load_volume(boot_raw), load_volume(game_raw)
    if boot_raw[:2] != b"LK" or struct.unpack_from(">I", boot_raw, 1116)[0] == 0:
        raise ValueError("Input boot disk has no Macintosh boot blocks/blessed System Folder")
    for path, expected_type in ((SYSTEM, b"ZSYS"), (FINDER, b"FNDR")):
        if path not in dict(boot.iter_paths()) or boot[path].type.upper() != expected_type:
            raise ValueError("Missing original System/Finder file: " + ":".join(path))
    version = system_version(boot[SYSTEM])
    if GAME not in dict(game.iter_paths()) or game[GAME].type != b"APPL" or not game[GAME].rsrc:
        raise ValueError("Missing fork-preserved Pool of Radiance v1.1 application")
    # Preserve boot blocks byte-for-byte; require their existing normal Finder
    # startup instead of silently inheriting a different startup application.
    for offset, name in ((10, b"System"), (26, b"Finder"), (90, b"Finder")):
        if boot_raw[offset + 1:offset + 1 + boot_raw[offset]] != name:
            raise ValueError("Boot blocks use a nonstandard startup; retain the original recovery disk")
    if any(struct.unpack_from(">II", boot_raw, 1120)):
        raise ValueError("Boot disk already overrides its startup application")
    try:
        startup_folder = boot[STARTUP[:-1]]
    except KeyError:
        raise ValueError("Original System 7 Startup Items folder is missing") from None
    if not isinstance(startup_folder, machfs.Folder) or len(startup_folder):
        raise ValueError("Startup Items must be an empty original folder; existing items are not removed")

    boot_before, game_before = inventory(boot), inventory(game)
    collision_check = load_volume(boot_raw)
    merge_folders(collision_check, game)
    grown = grow_hfs(boot_raw, size_mib * 1024 * 1024)
    # No alias or catalog rewrite: even pre-existing broken/external aliases
    # retain their exact bytes and original volume creation identity.
    compare_inventory(boot_before, inventory(load_volume(grown)))
    with tempfile.TemporaryDirectory(prefix="poolrad-append-") as temp:
        folder = Path(temp)
        source_copy, destination = folder / "game-copy.dsk", folder / "combined.dsk"
        source_copy.write_bytes(game_raw)
        destination.write_bytes(grown)
        exports = []
        hfs_run("hmount", str(source_copy))
        try:
            for path, entry in game_before.items():
                if entry["kind"] == "file":
                    staged = folder / (str(len(exports)) + ".bin")
                    staged.write_bytes(hfs_run(b"hcopy", b"-m", hfs_path(path), b"-"))
                    exports.append((path, staged))
        finally:
            hfs_run("humount")
        hfs_run("hmount", str(destination))
        try:
            original_ids = {path: catalog_id(path) for path in boot_before}
            if original_ids[SYSTEM[:-1]] != struct.unpack_from(">I", boot_raw, 1116)[0]:
                raise ValueError("Original blessing does not identify System Folder")
            for path, entry in sorted(game_before.items(), key=lambda item: len(item[0])):
                if entry["kind"] == "folder" and path not in boot_before:
                    hfs_run(b"hmkdir", hfs_path(path))
            for path, staged in exports:
                hfs_run(b"hcopy", b"-m", os.fsencode(staged), hfs_path(path))
            if startup:
                alias_file = folder / "startup.bin"
                alias_file.write_bytes(alias_macbinary(boot, game[GAME], catalog_id(GAME[:-1]), catalog_id(GAME)))
                hfs_run(b"hcopy", b"-m", os.fsencode(alias_file), hfs_path(STARTUP))
            for path, before_id in original_ids.items():
                if catalog_id(path) != before_id:
                    raise ValueError("An original catalog ID changed: " + ":".join(path))
        finally:
            hfs_run("humount")
        raw = destination.read_bytes()
    rebuilt = load_volume(raw)
    actual = inventory(rebuilt)
    if startup:
        added = actual.pop(STARTUP, None)
        if added is None or added.get("aliasTarget") != list(GAME):
            raise ValueError("Generated Startup Items alias does not resolve to the original game")
    compare_inventory(boot_before, {p: actual[p] for p in boot_before})
    if set(actual) != set(boot_before) | set(game_before):
        raise ValueError("Unexpected combined file/folder inventory")
    for path, entry in game_before.items():
        if entry["kind"] == "file":
            # MacBinary carries both forks, type, creator, Finder flags and
            # creation/modification dates; HFS backup dates are not portable.
            keys = ("dataBytes", "resourceBytes", "dataSha256", "resourceSha256",
                    "type", "creator", "flags", "created", "modified")
            if any(entry[k] != actual[path][k] for k in keys):
                raise ValueError("Game/save file changed during MacBinary copy: " + ":".join(path))
    if (raw[:1024] != boot_raw[:1024] or raw[1116:1128] != boot_raw[1116:1128]
            or rebuilt.name != boot.name or rebuilt.crdate != boot.crdate):
        raise ValueError("Boot block/blessing verification failed")

    warnings = []
    empty_item = ("Pool Of Radiance", "PoolRad2", "ITEM2.DAX")
    if empty_item in game_before and game_before[empty_item]["dataBytes"] == 0:
        warnings.append("Source PoolRad2/ITEM2.DAX is empty; preserved unchanged, not repaired")
    report = {
        "format": "poolrad-personal-boot-v1", "systemVersion": version,
        "startup": startup, "startupAlias": list(STARTUP) if startup else None,
        "gameApplication": list(GAME), "volumeName": boot.name,
        "bootSourceSha256": digest(boot_raw), "gameSourceSha256": digest(game_raw),
        "outputSha256": digest(raw), "outputBytes": len(raw),
        "originalBootBlocksSha256": digest(boot_raw[:1024]),
        "bootFiles": sum(e["kind"] == "file" for e in boot_before.values()),
        "gameFiles": sum(e["kind"] == "file" for e in game_before.values()),
        "outputContentFiles": sum(e["kind"] == "file" for e in actual.values()) + int(startup),
        "preservedAliases": [list(p) for p, e in boot_before.items() if "aliasTarget" in e],
        "preservedCatalogIds": {":".join(p): cnid for p, cnid in original_ids.items()},
        "metadataNote": "Original boot catalog IDs, volume identity and all original forks retained; game copied through MacBinary; parent folder modification dates and game backup dates may change",
        "warnings": warnings,
        "verifiedContent": {":".join(p): e for p, e in actual.items()},
        "coldBootAcceptance": "not performed by the builder",
    }
    return raw, report


def hfs_check(image, expected_blessing, file_inventory=None):
    """Independent hfsutils check, always on a newly created disposable image."""
    def run(*args):
        return subprocess.check_output(args, stderr=subprocess.STDOUT)
    run("hmount", str(image))
    try:
        found = int(run("hls", "-id", ":System Folder").split()[0])
        if found != expected_blessing:
            raise ValueError("Blessed catalog ID does not identify the original System Folder")
        if file_inventory:
            for path, entry in file_inventory.items():
                if entry["kind"] != "file":
                    continue
                blob = run(b"hcopy", b"-m", (":" + ":".join(path)).encode("mac_roman"), b"-")
                if len(blob) < 128:
                    raise ValueError("Independent fork export is truncated")
                data_size, resource_size = struct.unpack_from(">II", blob, 83)
                resource_start = 128 + ((data_size + 127) // 128) * 128
                if (digest(blob[128:128 + data_size]) != entry["dataSha256"]
                        or digest(blob[resource_start:resource_start + resource_size]) != entry["resourceSha256"]):
                    raise ValueError("Independent file-fork check failed: " + ":".join(path))
    finally:
        run("humount")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("boot", type=Path)
    parser.add_argument("game", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--size-mib", type=int, default=32)
    parser.add_argument("--no-startup", action="store_true", help="Build a normal Finder recovery disk without the game startup alias")
    args = parser.parse_args()
    output = args.output.absolute()
    manifest = Path(str(output) + ".manifest.json")
    if output.exists() or output.is_symlink() or manifest.exists() or manifest.is_symlink():
        raise ValueError("Refusing to overwrite an existing disk or manifest")
    for tool in ("hmount", "hls", "hcopy", "hmkdir", "humount"):
        if not shutil.which(tool):
            raise ValueError("Install hfsutils first: missing " + tool)
    for source in (args.boot, args.game):
        if not source.is_file() or source.stat().st_size > 512 * 1024 * 1024:
            raise ValueError("Expected a local source image no larger than 512 MiB: " + str(source))
    boot_raw, game_raw = args.boot.read_bytes(), args.game.read_bytes()
    load_volume(boot_raw)
    load_volume(game_raw)
    with tempfile.TemporaryDirectory(prefix="poolrad-boot-", dir=output.parent) as temp:
        staging = Path(temp)
        # hfsutils can update mounted metadata, so never pass an original path.
        boot_copy = staging / "source-boot-copy.dsk"
        boot_copy.write_bytes(boot_raw)
        hfs_check(boot_copy, struct.unpack_from(">I", boot_raw, 1116)[0])
        raw, report = prepare(boot_raw, game_raw, args.size_mib, not args.no_startup)
        disk = staging / "verified.dsk"
        disk.write_bytes(raw)
        exact_files = {tuple(path.split(":")): entry for path, entry in report["verifiedContent"].items()}
        hfs_check(disk, struct.unpack_from(">I", raw, 1116)[0], exact_files)
        # hfsutils may alter its mount bookkeeping. Restore the already checked
        # generated bytes before publishing the image and its content hash.
        disk.write_bytes(raw)
        if digest(args.boot.read_bytes()) != digest(boot_raw) or digest(args.game.read_bytes()) != digest(game_raw):
            raise ValueError("An input changed during the build; stop the emulator and use quiescent copies")
        report["independentHfsutilsChecks"] = "System Folder blessing and every original boot/game/save/alias data/resource fork passed"
        report["originalInputsUnchanged"] = True
        report_file = staging / "manifest.json"
        report_file.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        # Hard links publish complete files without replacing even a path that
        # appeared after preflight. Temporary artifacts are on the same volume.
        os.link(report_file, manifest)
        os.link(disk, output)
    print("Prepared private disk:", output)
    print("System", report["systemVersion"], "| game files", report["gameFiles"], "| startup", report["startup"])
    print("Original input hashes unchanged. Manifest:", manifest)
    print("For automatic mounting, import a copy named disk1.dsk; retain old disks as exported backups outside the active list.")
    print("Cold-boot acceptance still required; no ROM or game data belongs in git or the APK.")
    for warning in report["warnings"]:
        print("Warning:", warning)


if __name__ == "__main__":
    main()
