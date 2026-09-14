"""Read-only checks for private classic StuffIt extractions and Gold Box DAX.

No decompressor or game data. Format provenance and limits: docs/ARCHIVE_CHECK.md.
"""
from pathlib import Path
import struct

MAX_ARCHIVE = 128 * 1024 * 1024
MAX_FORK = 16 * 1024 * 1024


def crc16(data):
    """StuffIt's reflected CRC-16/ARC, initial zero (not MacBinary's CRC)."""
    value = 0
    for byte in data:
        value ^= byte
        for _ in range(8):
            value = (value >> 1) ^ (0xa001 if value & 1 else 0)
    return value


def read_bounded(path, limit=MAX_FORK):
    path = Path(path).absolute()
    if any(p.is_symlink() for p in (path, *path.parents)):
        raise ValueError(f"Symlinks are not supported: {path}")
    if not path.is_file() or path.stat().st_size > limit:
        raise ValueError(f"Missing, non-file or oversized input: {path}")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError(f"Oversized input: {path}")
    return data


def dax_records(data):
    """Check every index/range and RLE stream without allocating decoded data."""
    if not 11 <= len(data) <= MAX_FORK:
        raise ValueError("Invalid DAX file size (empty/truncated extraction?)")
    index_size = struct.unpack_from("<H", data)[0]
    start = 2 + index_size
    if not index_size or index_size % 9 or index_size // 9 > 256 or start > len(data):
        raise ValueError("Invalid DAX index")
    ids = set()
    for at in range(2, start, 9):
        record, offset, unpacked, stored = struct.unpack_from("<BIHH", data, at)
        if record in ids:
            raise ValueError(f"Duplicate DAX record {record}")
        ids.add(record)
        source, end = start + offset, start + offset + stored
        if not stored or end > len(data):
            raise ValueError(f"Truncated DAX record {record}")
        if not unpacked:
            continue  # Uncompressed record.
        target = 0
        while source < end:
            control = data[source]
            source += 1
            literal = control < 128
            count = control + 1 if literal else 256 - control
            consumed = count if literal else 1
            if source + consumed > end:
                raise ValueError(f"Truncated DAX RLE record {record}")
            source += consumed
            target += count
            if target > unpacked:
                raise ValueError(f"DAX RLE exceeds unpacked size for record {record}")
        if target != unpacked:
            raise ValueError(f"DAX RLE unpacked size mismatch for record {record}")
    return len(ids)


def apple_double(data):
    if len(data) < 26 or struct.unpack_from(">II", data) != (0x00051607, 0x00020000):
        raise ValueError("Expected unar AppleDouble v2 sidecar, not a raw resource fork")
    count = struct.unpack_from(">H", data, 24)[0]
    end_index = 26 + 12 * count
    if end_index > len(data):
        raise ValueError("Truncated AppleDouble index")
    entries, ranges = {}, []
    for at in range(26, end_index, 12):
        kind, offset, size = struct.unpack_from(">III", data, at)
        end = offset + size
        if kind in entries or offset < end_index or end > len(data):
            raise ValueError("Invalid/duplicate AppleDouble entry")
        if size and any(offset < b and end > a for a, b in ranges):
            raise ValueError("Overlapping AppleDouble entries")
        entries[kind] = data[offset:end]
        if size:
            ranges.append((offset, end))
    return entries


def stuffit_entries(data):
    """Read classic SIT!/rLau metadata, validating each 112-byte header CRC.

    The top-level reserved CRC is not enforced (the supplied v2 file uses zero).
    Unknown/encrypted formats are rejected, never guessed or extracted here.
    """
    if (not 22 <= len(data) <= MAX_ARCHIVE or data[:4] != b"SIT!"
            or data[10:14] != b"rLau" or data[14] not in (1, 2)
            or struct.unpack_from(">I", data, 16)[0] != 22):
        raise ValueError("Expected a classic StuffIt v1/v2 archive with a 22-byte header")
    if struct.unpack_from(">I", data, 6)[0] != len(data):
        raise ValueError("StuffIt archive length mismatch")
    at, stack, seen, entries, headers = 22, [], set(), [], 0
    while at < len(data):
        headers += 1
        if headers > 4096 or at + 112 > len(data):
            raise ValueError("Truncated/excessive StuffIt headers")
        header = data[at:at + 112]
        if crc16(header[:110]) != struct.unpack_from(">H", header, 110)[0]:
            raise ValueError(f"StuffIt header CRC mismatch at {at}")
        at += 112
        resource_method, data_method, name_length = header[:3]
        if resource_method == data_method == 0x21:
            if not stack:
                raise ValueError("Unbalanced StuffIt end-folder")
            stack.pop()
            continue
        if not 1 <= name_length <= 31:
            raise ValueError("Unsupported StuffIt filename length")
        name = header[3:3 + name_length].decode("mac_roman")
        if name in (".", "..") or any(c in name for c in "/\\:\0"):
            raise ValueError("Unsafe StuffIt filename")
        path = tuple(stack + [name])
        if path in seen:
            raise ValueError("Duplicate StuffIt path: " + "/".join(path))
        seen.add(path)
        if resource_method == data_method == 0x20:
            stack.append(name)
            if len(stack) > 64:
                raise ValueError("Excessive StuffIt nesting")
            continue
        if resource_method > 15 or data_method > 15:
            raise ValueError("Unsupported/encrypted StuffIt entry")
        resource_size, data_size, resource_stored, data_stored = struct.unpack_from(">IIII", header, 84)
        if max(resource_size, data_size) > MAX_FORK or at + resource_stored + data_stored > len(data):
            raise ValueError("Oversized/truncated StuffIt fork")
        resource_crc, data_crc = struct.unpack_from(">HH", header, 100)
        entries.append((path, data_size, data_crc, resource_size, resource_crc))
        at += resource_stored + data_stored
    if stack or not entries:
        raise ValueError("Unclosed/empty StuffIt archive")
    return entries


def audit_archive(archive, root):
    """Root is the extraction parent containing the archive's top-level folder."""
    root = Path(root).absolute()
    if not root.is_dir() or any(p.is_symlink() for p in (root, *root.parents)):
        raise ValueError("Expected a real extraction directory, not a symlink")
    entries = stuffit_entries(read_bounded(archive, MAX_ARCHIVE))
    files, forks, dax_files, records, errors = 0, 0, 0, 0, []
    for parts, data_size, data_crc, resource_size, resource_crc in entries:
        path = Path(root).joinpath(*parts)
        try:
            # Zero-size forks may be absent; a dangling symlink is never absence.
            data = read_bounded(path) if path.exists() or path.is_symlink() or data_size else b""
            sidecar = path.with_name(path.name + ".rsrc")
            envelope = apple_double(read_bounded(sidecar)) if sidecar.exists() or sidecar.is_symlink() else {}
            resource = envelope.get(2, b"")
            for name, content, size, crc in (("data", data, data_size, data_crc),
                                            ("resource", resource, resource_size, resource_crc)):
                if len(content) != size or crc16(content) != crc:
                    raise ValueError(f"{name} fork mismatch: expected {size} bytes / CRC {crc:04x}, "
                                     f"got {len(content)} bytes / CRC {crc16(content):04x}")
                forks += int(size > 0)
            if path.suffix.upper() == ".DAX":
                records += dax_records(data)
                dax_files += 1
            files += 1
        except (OSError, ValueError) as error:
            errors.append("/".join(parts) + ": " + str(error))
    if errors:
        raise ValueError("Archive audit failed:\n" + "\n".join(errors))
    return {"archiveFiles": files, "nonemptyForks": forks, "daxFiles": dax_files,
            "daxRecords": records, "scope": "fork lengths/CRC and DAX structure; not a playthrough"}


def audit_dax_tree(root):
    root = Path(root).absolute()
    if not root.is_dir() or any(p.is_symlink() for p in (root, *root.parents)):
        raise ValueError("Expected a real extraction directory, not a symlink")
    files, records = 0, 0
    pending = [root]
    while pending:
        for path in pending.pop().iterdir():
            if path.is_symlink():
                raise ValueError(f"Symlinks are not supported: {path}")
            if path.is_dir():
                pending.append(path)
            elif path.suffix.upper() == ".DAX":
                try:
                    records += dax_records(read_bounded(path))
                except ValueError as error:
                    raise ValueError(f"{path}: {error}") from error
                files += 1
    if not files:
        raise ValueError("No DAX files found; this is not an extracted game folder")
    return {"daxFiles": files, "daxRecords": records,
            "scope": "DAX structure only; use --archive to verify original fork CRCs"}
