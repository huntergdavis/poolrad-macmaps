#!/usr/bin/env python3
"""Create a new Macintosh Pool of Radiance v1.1 save from a private RAM capture.

No external dependencies, game data, disk writes, or character adjustments.
See docs/SAVE_WRITER.md for the supported capture state and loading procedure.
"""
import argparse
import binascii
import hashlib
import json
import os
from pathlib import Path
import struct
import zlib


class Capture:
    def __init__(self, ram):
        self.ram = bytes(ram)
        if not 65536 <= len(ram) <= 16 * 1024 * 1024:
            raise ValueError('Expected a complete 24-bit Macintosh RAM capture')
        expected = b'Pool of Radiance v1.1'
        if self.read(0x910, len(expected) + 1) != bytes([len(expected)]) + expected:
            raise ValueError('Pool of Radiance v1.1 must be the current application')
        self.a5 = self.pointer(0x904)
        if self.a5 < 25572 or self.a5 & 1:
            raise ValueError('Invalid application globals')
        self.read(self.a5 - 25572, 25572 + 4688)
        self.handles = set()
        self.allocations = []

    def read(self, address, length):
        if address < 0 or length < 0 or address + length > len(self.ram):
            raise ValueError('Capture contains an out-of-bounds address')
        return self.ram[address:address + length]

    def pointer(self, address):
        return struct.unpack('>I', self.read(address, 4))[0] & 0xffffff

    def block(self, handle, length):
        if handle < 0x1000 or handle & 1 or handle in self.handles:
            raise ValueError('Invalid, shared, or cyclic handle')
        address = self.pointer(handle)
        if address < 0x1000 or address & 1:
            raise ValueError('Invalid or purged allocation')
        header = struct.unpack('>I', self.read(address - 8, 4))[0]
        physical = header & 0xffffff
        if header >> 28 != 8 or physical & 1 or physical != length + 8 + ((header >> 24) & 15):
            raise ValueError('Unexpected allocation size or kind')
        self.read(address - 8, physical)
        start, end = address - 8, address - 8 + physical
        if any(start < old_end and old_start < end for old_start, old_end in self.allocations):
            raise ValueError('Overlapping allocations')
        self.handles.add(handle)
        self.allocations.append((start, end))
        return self.read(address, length)

    def global_bytes(self, back, length=1):
        return self.read(self.a5 - back, length)

    def linked(self, handle, length, next_at, limit):
        rows = []
        while handle:
            if len(rows) == limit:
                raise ValueError('List exceeds the supported bound')
            row = self.block(handle, length)
            rows.append(row)
            handle = struct.unpack_from('>I', row, next_at)[0] & 0xffffff
        return rows


def resource_fork(characters):
    """Standard single-type resource fork: named PoRc resources, IDs 128 onward."""
    data, names, references = bytearray(), bytearray(), bytearray()
    for index, (name, payload) in enumerate(characters):
        offset = len(data)
        data += struct.pack('>I', len(payload)) + payload
        references += struct.pack('>hH', 128 + index, len(names))
        references += bytes([0]) + offset.to_bytes(3, 'big') + bytes(4)
        names += bytes([len(name)]) + name
    types = struct.pack('>H4sHH', 0, b'PoRc', len(characters) - 1, 10) + references
    map_length = 28 + len(types) + len(names)
    header = struct.pack('>IIII', 256, 256 + len(data), len(data), map_length)
    resource_map = header + bytes(8) + struct.pack('>HH', 28, 28 + len(types)) + types + names
    return header + bytes(240) + data + resource_map


def archive(name, data, resource):
    encoded = name.encode('ascii')
    packed = struct.pack('>IHH', 0x50525356, 1, len(encoded)) + encoded + b'prdtprad'
    packed += struct.pack('>I', len(data)) + data + struct.pack('>I', len(resource)) + resource
    return packed + struct.pack('>I', zlib.crc32(packed))


def macbinary(name, data, resource):
    """MacBinary II, preserving both forks and Finder type/creator via hcopy -m."""
    encoded = name.encode('ascii')
    header = bytearray(128)
    header[1] = len(encoded)
    header[2:2 + len(encoded)] = encoded
    header[65:73] = b'prdtprad'
    struct.pack_into('>II', header, 83, len(data), len(resource))
    header[122] = header[123] = 129
    struct.pack_into('>H', header, 124, binascii.crc_hqx(header[:124], 0))
    pad = lambda b: b + bytes(-len(b) % 128)
    return bytes(header) + pad(data) + pad(resource)


def serialize(ram, name):
    if not 1 <= len(name) <= 31 or any(not (' ' <= c <= '~') or c in ':/' for c in name):
        raise ValueError('Use a new 1–31 character ASCII Macintosh filename without colon or slash')
    capture = Capture(ram)
    # Deliberately limited to verified, idle local exploration. No combat,
    # camp, wilderness, loading, relocation, or in-flight movement exports.
    required = {0x5e90: b'\x04', 0x5e89: b'\x01', 0x5ed4: b'\x00\x02',
                0x30e1: b'\x00', 0x5e8b: b'\x00', 0x1921: b'\x00', 0x60a4: b'\x00'}
    for back, value in required.items():
        if capture.global_bytes(back, len(value)) != value:
            raise ValueError('Capture must show idle local exploration, outside combat/loading')
    blocks = [bytearray(capture.block(capture.pointer(capture.a5 - back), length))
              for back, length in ((0x5eb2, 2048), (0x5eae, 2048), (0x5eaa, 1024), (0x5ea6, 7680))]
    # Reproduce CODE2 +0x37b6..0x386a on private copies, never the guest.
    for i in range(1, 4):
        values = capture.read(capture.a5 - 0x3ae8 + i * 4, 4)
        blocks[0][(0x1f9 + i) * 2:(0x1f9 + i) * 2 + 2] = values[:2]
        blocks[0][(0x1fc + i) * 2:(0x1fc + i) * 2 + 2] = values[2:]
    struct.pack_into('>H', blocks[0], 0x1f8, capture.global_bytes(0x5ea2)[0])
    direction = ((capture.global_bytes(0x5e8d)[0] * 2) & 255) + capture.global_bytes(0x5e8e)[0]
    struct.pack_into('>H', blocks[0], 0x1fe, direction)
    struct.pack_into('>H', blocks[1], 0x624, capture.global_bytes(0x513c)[0])
    rows = capture.linked(capture.pointer(capture.a5 - 0x519e), 302, 0x110, 8)
    if not rows:
        raise ValueError('No party is loaded')
    characters, report, seen_names, slots = [], [], set(), set()
    for row in rows:
        raw_name = row[:16].split(b'\0')[0]
        if not raw_name or len(raw_name) > 15 or any(b < 32 or b == 127 for b in raw_name):
            raise ValueError('Invalid character name')
        text = raw_name.decode('mac_roman')
        if text.casefold() in seen_names or row[0xc9] >= 8 or row[0xc9] in slots or row[0x2f] > 17:
            raise ValueError('Duplicate name/slot, non-party record, or unknown class')
        seen_names.add(text.casefold()); slots.add(row[0xc9])
        items = capture.linked(struct.unpack_from('>I', row, 0xd4)[0] & 0xffffff, 66, 0x2a, 256)
        effects = capture.linked(struct.unpack_from('>I', row, 0x82)[0] & 0xffffff, 10, 6, 64)
        payload = row + struct.pack('>H', len(items)) + b''.join(items)
        payload += struct.pack('>H', len(effects)) + b''.join(effects)
        characters.append((raw_name, payload))
        report.append({'name': text, 'class': row[0x2f], 'hp': row[0x12b], 'maxHp': row[0x32],
                       'items': len(items), 'effects': len(effects), 'resourceBytes': len(payload)})
    data = capture.global_bytes(0x513c) + b''.join(blocks) + capture.global_bytes(0x3aee, 6)
    data += capture.global_bytes(0x5e89) + capture.global_bytes(0x5e90) + bytes([len(rows)])
    data += b''.join(row[:16] for row in rows)
    resource = resource_fork(characters)
    metadata = {'format': 'Macintosh Pool of Radiance v1.1', 'name': name,
                'captureSha256': hashlib.sha256(ram).hexdigest(), 'characters': report,
                'dataBytes': len(data), 'resourceBytes': len(resource), 'adjustments': []}
    return data, resource, metadata


def write_new(capture_path, destination, name):
    if capture_path.stat().st_size > 16 * 1024 * 1024:
        raise ValueError('Capture is too large')
    data, resource, report = serialize(capture_path.read_bytes(), name)
    outputs = {'save.data': data, 'save.rsrc': resource,
               'save.bin': macbinary(name, data, resource), 'save.prsv': archive(name, data, resource)}
    report['sha256'] = {key: hashlib.sha256(value).hexdigest() for key, value in outputs.items()}
    outputs['manifest.json'] = (json.dumps(report, indent=2) + '\n').encode()
    destination.mkdir()  # Existing directories are refused, even if empty.
    try:
        for filename, contents in outputs.items():
            with (destination / filename).open('xb') as stream:
                stream.write(contents); stream.flush(); os.fsync(stream.fileno())
    except Exception:
        for filename in outputs:
            (destination / filename).unlink(missing_ok=True)
        destination.rmdir()
        raise
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('destination', type=Path, help='new output directory')
    parser.add_argument('--name', required=True, help='new Macintosh save filename')
    args = parser.parse_args()
    try:
        print(json.dumps(write_new(args.capture, args.destination, args.name), indent=2))
    except (ValueError, OSError) as error:
        parser.exit(1, f'Refused: {error}\n')


if __name__ == '__main__':
    main()
