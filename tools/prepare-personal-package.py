#!/usr/bin/env python3
"""Build private APK input assets from files you already own. Never upload them."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import stat
import struct
import tempfile

ROM_BYTES = 262144
ROM_HEADERS = {0x97851DB6, 0x9779D2C4}
DISK_MAX = 128 * 1024 * 1024
DISKS_TOTAL_MAX = 256 * 1024 * 1024  # ROM is additional, matching the installer.
MANIFEST_MAX = 8192


def _identity(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def _inspect(path, limit, capture=False, destination=None):
    """Bounded reads; reject symlinks and a source changed during this read."""
    path = Path(path)
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or not 0 < before.st_size <= limit:
        raise ValueError(f"Not a regular, nonempty file within size limit: {path.name}")
    digest, parts, length = hashlib.sha256(), [], 0
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(fd, "rb") as source:
        if _identity(before) != _identity(os.fstat(source.fileno())):
            raise ValueError(f"Source changed before reading: {path.name}")
        while block := source.read(1024 * 1024):
            length += len(block)
            if length > limit:
                raise ValueError(f"Source grew beyond size limit: {path.name}")
            digest.update(block)
            if capture:
                parts.append(block)
            if destination is not None:
                destination.write(block)
        if (_identity(before) != _identity(os.fstat(source.fileno()))
                or _identity(before) != _identity(path.lstat()) or length != before.st_size):
            raise ValueError(f"Source changed while reading: {path.name}")
    return (length, digest.hexdigest(), _identity(before)), b"".join(parts)


def validate_rom(data):
    # Reuses RomManager.java's Mac II signatures and unsigned BE16 checksum.
    # Require the complete body, not an accidentally matching earlier prefix.
    if len(data) != ROM_BYTES:
        raise ValueError("Mac II ROM must contain exactly 262144 bytes")
    signature = struct.unpack_from(">I", data)[0]
    if signature not in ROM_HEADERS:
        raise ValueError("Unsupported Mac II ROM checksum header")
    if sum(word[0] for word in struct.iter_unpack(">H", data[4:])) != signature:
        raise ValueError("Mac II ROM checksum does not match its complete body")


def _decimal(value):
    if not re.fullmatch(r"[1-9][0-9]*", value) or len(value) > 10:
        raise ValueError("Expected a bounded positive canonical decimal")
    return int(value)


def parse_manifest(raw):
    if not 0 < len(raw) <= MANIFEST_MAX:
        raise ValueError("Manifest is empty or exceeds 8192 bytes")
    try:
        text = raw.decode("ascii").replace("\r\n", "\n")
    except UnicodeDecodeError as error:
        raise ValueError("Manifest must be ASCII") from error
    if any(ord(char) < 32 and char != "\n" or ord(char) > 126 for char in text):
        raise ValueError("Manifest contains unsupported control characters")
    entries = {}
    for line in text.removesuffix("\n").split("\n"):
        if line.count("=") != 1:
            raise ValueError("Manifest requires one exact key=value pair per line")
        key, value = line.split("=")
        if key in entries:
            raise ValueError("Duplicate manifest key")
        entries[key] = value
    count = _decimal(entries.get("disk.count", ""))
    if not 1 <= count <= 8:
        raise ValueError("Package must contain 1 through 8 disks")
    fixed = {"format": "1", "machine": "macII", "rom.file": "MacII.ROM",
             "rom.bytes": str(ROM_BYTES), "disk.count": str(count)}
    expected = set(fixed) | {"rom.sha256"}
    total = 0
    for index in range(1, count + 1):
        prefix = f"disk.{index}."
        fixed[prefix + "file"] = f"disk{index}.dsk"
        expected.update(prefix + part for part in ("file", "bytes", "sha256"))
        size = _decimal(entries.get(prefix + "bytes", ""))
        if size > DISK_MAX or size % 512:
            raise ValueError("Each raw disk must be sector-aligned and at most 128 MiB")
        total += size
    if total > DISKS_TOTAL_MAX:
        raise ValueError("Combined disk payload exceeds 256 MiB")
    if set(entries) != expected or any(entries[key] != value for key, value in fixed.items()):
        raise ValueError("Manifest keys, format, machine, or filenames are not exact")
    for key, value in entries.items():
        if key.endswith(".sha256") and not re.fullmatch(r"[0-9a-f]{64}", value):
            raise ValueError("Expected a lowercase SHA-256 digest")
    return entries


def _names(directory):
    if not stat.S_ISDIR(directory.lstat().st_mode):
        raise ValueError("Package must be a real directory, not a symlink")
    with os.scandir(directory) as files:
        names = set()
        for entry in files:
            names.add(entry.name)
            if len(names) > 10:
                raise ValueError("Unexpected extra package files")
    return names


def verify(directory):
    """Validate without modifying anything; return exact manifest and snapshots."""
    directory = Path(directory)
    names = _names(directory)
    manifest_snapshot, raw = _inspect(directory / "bundle.properties", MANIFEST_MAX, True)
    entries = parse_manifest(raw)
    expected = {"bundle.properties", "MacII.ROM"}
    expected.update(f"disk{i}.dsk" for i in range(1, int(entries["disk.count"]) + 1))
    if names != expected:
        raise ValueError("Missing or unexpected package files")
    snapshots = {"bundle.properties": manifest_snapshot}
    for prefix in ["rom"] + [f"disk.{i}" for i in range(1, int(entries["disk.count"]) + 1)]:
        name = entries[prefix + ".file"]
        snapshot, data = _inspect(directory / name, ROM_BYTES if prefix == "rom" else DISK_MAX,
                                  prefix == "rom")
        if snapshot[:2] != (int(entries[prefix + ".bytes"]), entries[prefix + ".sha256"]):
            raise ValueError(f"Payload size or SHA-256 mismatch: {name}")
        if prefix == "rom":
            validate_rom(data)
        snapshots[name] = snapshot
    if _names(directory) != names:
        raise ValueError("Package contents changed while verifying")
    return raw, snapshots


def _copy(source, destination, expected):
    with destination.open("xb") as output:
        actual, _ = _inspect(source, expected[0], destination=output)
        output.flush()
        os.fsync(output.fileno())
    if actual != expected:
        raise ValueError(f"Source changed since validation: {Path(source).name}")


def _fresh_output(output):
    output = Path(output).absolute()
    if os.path.lexists(output):
        raise ValueError("Output already exists; use a new private destination")
    return output


def _publish(staging, output, nested):
    # Exclusive mkdir, not directory replacement. The manifest is installed last;
    # a partially interrupted publication therefore cannot verify as a package.
    created, directories = [], []
    try:
        output.mkdir(mode=0o700)
        directories.append(output)
        payload = output / "personal" if nested else output
        if nested:
            payload.mkdir(mode=0o700)
            directories.append(payload)
        names = sorted(path.name for path in staging.iterdir() if path.name != "bundle.properties")
        for name in names + ["bundle.properties"]:
            target = payload / name
            os.link(staging / name, target)  # Only our private copies; never source files.
            created.append((target, _identity(target.lstat())))
    except BaseException:
        for path, identity in reversed(created):
            try:
                if _identity(path.lstat()) == identity:
                    path.unlink()
            except OSError:
                pass
        for path in reversed(directories):
            try:
                path.rmdir()  # Never recursively remove an unknown file.
            except OSError:
                pass
        raise


def prepare(rom, disks, output, acknowledge=False):
    if not acknowledge:
        raise ValueError("Pass --acknowledge-private-assets; never publish this bundle or its APK")
    if not 1 <= len(disks) <= 8:
        raise ValueError("Provide 1 through 8 disks in boot order")
    output = _fresh_output(output)
    sources = {"MacII.ROM": Path(rom)}
    sources.update((f"disk{i}.dsk", Path(path)) for i, path in enumerate(disks, 1))
    snapshots = {}
    entries = {"format": "1", "machine": "macII", "rom.file": "MacII.ROM",
               "rom.bytes": str(ROM_BYTES)}
    for index, (name, path) in enumerate(sources.items()):
        snapshot, data = _inspect(path, ROM_BYTES if index == 0 else DISK_MAX, index == 0)
        if index == 0:
            validate_rom(data)
        snapshots[name] = snapshot
        prefix = "rom" if index == 0 else f"disk.{index}"
        entries.update({prefix + ".file": name, prefix + ".bytes": str(snapshot[0]),
                        prefix + ".sha256": snapshot[1]})
    entries["disk.count"] = str(len(disks))
    raw = "".join(f"{key}={value}\n" for key, value in entries.items()).encode("ascii")
    parse_manifest(raw)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".poolrad-package-", dir=output.parent) as temporary:
        staging = Path(temporary)
        for name, path in sources.items():
            _copy(path, staging / name, snapshots[name])
        (staging / "bundle.properties").write_bytes(raw)
        verify(staging)
        for name, path in sources.items():
            if _inspect(path, snapshots[name][0])[0] != snapshots[name]:
                raise ValueError("Source changed before publication")
        _publish(staging, output, False)


def stage(directory, output):
    directory, output = Path(directory), _fresh_output(output)
    if output.resolve().is_relative_to(directory.resolve()):
        raise ValueError("Stage output must not be inside the input package")
    raw, snapshots = verify(directory)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".poolrad-package-", dir=output.parent) as temporary:
        staging = Path(temporary)
        for name, snapshot in snapshots.items():
            _copy(directory / name, staging / name, snapshot)
        if verify(staging)[0] != raw or verify(directory) != (raw, snapshots):
            raise ValueError("Package changed during staging")
        _publish(staging, output, True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=
        "Local files only; no downloads. Private bundles/APKs must NEVER be uploaded publicly.")
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("prepare", help="Copy your own ROM and raw disks into a private bundle")
    build.add_argument("--rom", required=True, type=Path)
    build.add_argument("--disk", required=True, action="append", type=Path)
    build.add_argument("--output", required=True, type=Path)
    build.add_argument("--acknowledge-private-assets", action="store_true")
    check = commands.add_parser("verify", help="Read-only strict bundle verification")
    check.add_argument("directory", type=Path)
    install = commands.add_parser("stage", help="Verify/copy into NEW_OUTPUT/personal for a private APK")
    install.add_argument("directory", type=Path)
    install.add_argument("output", type=Path)
    arguments = parser.parse_args()
    try:
        if arguments.command == "prepare":
            prepare(arguments.rom, arguments.disk, arguments.output, arguments.acknowledge_private_assets)
        elif arguments.command == "verify":
            verify(arguments.directory)
        else:
            stage(arguments.directory, arguments.output)
    except (OSError, ValueError) as error:
        parser.exit(2, f"Error: {error}\n")
    print(f"{arguments.command}: OK — private assets only; do not publish this bundle or APK.")


if __name__ == "__main__":
    main()
