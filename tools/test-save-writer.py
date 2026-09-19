#!/usr/bin/env python3
"""Synthetic save-writer tests; no game records or extracted assets."""
import binascii
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

spec = importlib.util.spec_from_file_location('writer', Path(__file__).with_name('write-game-save.py'))
writer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(writer)


class SaveWriterTest(unittest.TestCase):
    def setUp(self):
        self.ram = bytearray(0x40000)
        self.a5 = 0x30000
        self.next_handle, self.next_data = 0x1100, 0x5000
        self.put32(0x904, self.a5)
        application = b'Pool of Radiance v1.1'
        self.ram[0x910:0x911 + len(application)] = bytes([len(application)]) + application
        for back, value in ((0x5e90, 4), (0x5e89, 1), (0x5ed3, 2)):
            self.ram[self.a5 - back] = value
        self.worlds = []
        for i, (back, size) in enumerate(((0x5eb2, 2048), (0x5eae, 2048), (0x5eaa, 1024), (0x5ea6, 7680))):
            handle, address = self.allocate(bytes([i + 1]) * size)
            self.put32(self.a5 - back, handle)
            self.worlds.append(address)
        effect = bytes([31, 9, 8, 7, 6, 5]) + bytes(4)
        self.effect_handle, self.effect = self.allocate(effect)
        item = bytearray(66); item[:4] = b'Item'
        self.item_handle, self.item = self.allocate(item)
        row = bytearray(302); row[:4] = b'Hero'; row[0x32] = 12; row[0x12b] = 9
        struct.pack_into('>I', row, 0xd4, self.item_handle)
        struct.pack_into('>I', row, 0x82, self.effect_handle)
        self.hero_handle, self.hero = self.allocate(row)
        self.put32(self.a5 - 0x519e, self.hero_handle)

    def put32(self, address, value):
        struct.pack_into('>I', self.ram, address, value)

    def allocate(self, contents):
        handle, data = self.next_handle, self.next_data
        self.put32(handle, data); self.put32(data - 8, 0x80000000 | (len(contents) + 8))
        self.ram[data:data + len(contents)] = contents
        self.next_handle += 4; self.next_data += (len(contents) + 31) & ~15
        return handle, data

    def export(self):
        return writer.serialize(self.ram, 'Generated')

    def test_forks_preserve_records_and_counts(self):
        original = bytes(self.ram)
        data, fork, report = self.export()
        self.assertEqual(original, self.ram)
        self.assertEqual(len(data), 12826)
        self.assertEqual(data[-17], 1)
        self.assertEqual(data[-16:], self.ram[self.hero:self.hero + 16])
        start, map_at, data_len, map_len = struct.unpack_from('>IIII', fork)
        self.assertEqual(fork[map_at:map_at + 16], fork[:16])
        self.assertEqual(map_at + map_len, len(fork))
        self.assertEqual(map_at, start + data_len)
        types = map_at + struct.unpack_from('>H', fork, map_at + 24)[0]
        self.assertEqual(fork[types:types + 10], struct.pack('>H4sHH', 0, b'PoRc', 0, 10))
        ref = types + 10
        self.assertEqual(struct.unpack_from('>h', fork, ref)[0], 128)
        names = map_at + struct.unpack_from('>H', fork, map_at + 26)[0]
        self.assertEqual(fork[names:names + 5], b'\x04Hero')
        length = struct.unpack_from('>I', fork, start)[0]
        expected = self.ram[self.hero:self.hero + 302] + b'\0\1'
        expected += self.ram[self.item:self.item + 66] + b'\0\1' + self.ram[self.effect:self.effect + 10]
        self.assertEqual(fork[start + 4:start + 4 + length], expected)
        self.assertEqual(report['characters'][0]['effects'], 1)

    def test_preprocessing_matches_game_without_source_writes(self):
        for i in range(1, 4):
            struct.pack_into('>HH', self.ram, self.a5 - 0x3ae8 + i * 4, i * 123, i * 234)
        self.ram[self.a5 - 0x5ea2] = 81
        self.ram[self.a5 - 0x5e8d] = 255
        self.ram[self.a5 - 0x5e8e] = 7
        self.ram[self.a5 - 0x513c] = 93
        original = bytes(self.ram)
        data, _, _ = self.export()
        for i in range(1, 4):
            self.assertEqual(struct.unpack_from('>H', data, 1 + (0x1f9 + i) * 2)[0], i * 123)
            self.assertEqual(struct.unpack_from('>H', data, 1 + (0x1fc + i) * 2)[0], i * 234)
        self.assertEqual(struct.unpack_from('>H', data, 1 + 0x1f8)[0], 81)
        self.assertEqual(struct.unpack_from('>H', data, 1 + 0x1fe)[0], 261)
        self.assertEqual(struct.unpack_from('>H', data, 2049 + 0x624)[0], 93)
        self.assertEqual(original, self.ram)

    def test_rejects_invalid_states(self):
        for back, bad in ((0x5e90, 5), (0x30e1, 1), (0x5e8b, 1), (0x1921, 1), (0x60a4, 1)):
            with self.subTest(back=back):
                before = self.ram[self.a5 - back]
                self.ram[self.a5 - back] = bad
                with self.assertRaises(ValueError): self.export()
                self.ram[self.a5 - back] = before

    def test_rejects_cycles_and_bad_allocations(self):
        original = bytes(self.ram)
        for address, value in ((self.hero + 0x110, self.hero_handle),
                               (self.item + 0x2a, self.item_handle),
                               (self.effect + 6, self.effect_handle),
                               (self.item_handle, 0), (self.hero_handle, len(self.ram) + 16),
                               (self.item - 8, 0x8000004c)):
            with self.subTest(address=address):
                self.ram[:] = original; self.put32(address, value)
                with self.assertRaises(ValueError): self.export()

    def test_containers_have_correct_checksums_and_forks(self):
        data, resource, _ = self.export()
        archive = writer.archive('Generated', data, resource)
        self.assertEqual(zlib.crc32(archive[:-4]), struct.unpack('>I', archive[-4:])[0])
        binary = writer.macbinary('Generated', data, resource)
        self.assertEqual(binary[65:73], b'prdtprad')
        self.assertEqual(binascii.crc_hqx(binary[:124], 0), struct.unpack_from('>H', binary, 124)[0])
        self.assertEqual(binary[128:128 + len(data)], data)
        resource_at = 128 + ((len(data) + 127) // 128) * 128
        self.assertEqual(binary[resource_at:resource_at + len(resource)], resource)

    def test_existing_output_is_untouched(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / 'source.ram'; source.write_bytes(self.ram)
            output = Path(root) / 'output'
            writer.write_new(source, output, 'Generated')
            first = {f.name: f.read_bytes() for f in output.iterdir()}
            with self.assertRaises(FileExistsError): writer.write_new(source, output, 'Generated')
            self.assertEqual(first, {f.name: f.read_bytes() for f in output.iterdir()})
            self.assertEqual(source.read_bytes(), self.ram)


if __name__ == '__main__': unittest.main()
