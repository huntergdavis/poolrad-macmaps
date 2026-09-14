#!/usr/bin/env python3
"""Invented archives/records only: no ROM, System, game, save or recovered bytes."""
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest

from game_content import (apple_double, audit_archive, audit_dax_tree, crc16,
                          dax_records, read_bounded, stuffit_entries)


def dax(payload=b"\x02abc", unpacked=3):
    return struct.pack("<HBIHH", 9, 7, 0, unpacked, len(payload)) + payload


def sidecar(resource):
    return (struct.pack(">II16sH", 0x00051607, 0x00020000, b"", 1)
            + struct.pack(">III", 2, 38, len(resource)) + resource)


def entry(name, data=b"", resource=b"", method=0):
    header = bytearray(112)
    header[:3] = bytes((method, method, len(name)))
    header[3:3 + len(name)] = name.encode("ascii")
    struct.pack_into(">IIIIHH", header, 84, len(resource), len(data), len(resource), len(data),
                     crc16(resource), crc16(data))
    struct.pack_into(">H", header, 110, crc16(header[:110]))
    return bytes(header) + resource + data


def sit(*entries):
    payload = b"".join(entries)
    return struct.pack(">4sHI4sBBIH", b"SIT!", 1, 22 + len(payload), b"rLau", 2, 0, 22, 0) + payload


class DaxTest(unittest.TestCase):
    def test_crc_known_vector(self):
        self.assertEqual(0xbb3d, crc16(b"123456789"))
        self.assertEqual(0, crc16(b""))

    def test_literals_repeats_and_raw(self):
        for payload, size in ((b"\x02abc", 3), (b"\x80x", 128), (b"\xffx", 1),
                              (b"\x00a\xfeb", 3), (b"raw data", 0)):
            self.assertEqual(1, dax_records(dax(payload, size)))

    def test_empty_or_bad_index(self):
        for data in (b"", b"\0" * 10, b"\0" * 20, b"\x0a\0" + b"x" * 30,
                     b"\xff\xff" + b"x" * 30):
            with self.subTest(data=data), self.assertRaisesRegex(ValueError, "DAX"):
                dax_records(data)

    def test_duplicate_ids(self):
        record = struct.pack("<BIHH", 7, 0, 3, 4)
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            dax_records(struct.pack("<H", 18) + record * 2 + b"\x02abc")

    def test_offsets_and_zero_stored_size(self):
        for offset, stored in ((0xffffffff, 4), (0, 0), (0, 99)):
            with self.assertRaisesRegex(ValueError, "Truncated"):
                dax_records(struct.pack("<HBIHH", 9, 7, offset, 3, stored) + b"\x02abc")

    def test_bad_rle(self):
        for payload, unpacked in ((b"\x02ab", 3), (b"\xff", 1), (b"\xffa", 2),
                                  (b"\xfea", 1), (b"\x00a\x00b", 1)):
            with self.subTest(payload=payload), self.assertRaisesRegex(ValueError, "RLE"):
                dax_records(dax(payload, unpacked))


class ArchiveTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.extracted = self.root / "extracted"
        self.game = self.extracted / "Game"
        self.game.mkdir(parents=True)
        self.archive = self.root / "source.sit"

    def fixture(self, data=None, resource=b"resource fork"):
        data = dax() if data is None else data
        self.archive.write_bytes(sit(entry("Game", method=0x20), entry("ITEM.DAX", data, resource),
                                     entry("", method=0x21)))
        (self.game / "ITEM.DAX").write_bytes(data)
        (self.game / "ITEM.DAX.rsrc").write_bytes(sidecar(resource))

    def audit(self):
        return audit_archive(self.archive, self.extracted)

    def test_complete_forks_and_dax(self):
        self.fixture()
        result = self.audit()
        self.assertEqual((1, 2, 1, 1), tuple(result[k] for k in
                         ("archiveFiles", "nonemptyForks", "daxFiles", "daxRecords")))
        self.assertEqual(1, audit_dax_tree(self.game)["daxRecords"])

    def test_missing_zero_size_forks_are_legitimate(self):
        self.archive.write_bytes(sit(entry("Empty")))
        self.assertEqual(1, self.audit()["archiveFiles"])
        self.assertEqual(0, self.audit()["nonemptyForks"])

    def test_missing_or_empty_expected_data_is_not_legitimate(self):
        for absent in (False, True):
            self.fixture()
            path = self.game / "ITEM.DAX"
            path.unlink() if absent else path.write_bytes(b"")
            with self.assertRaisesRegex(ValueError, "ITEM.DAX"):
                self.audit()

    def test_same_length_damage_requires_crc_check(self):
        self.fixture()
        (self.game / "ITEM.DAX").write_bytes(dax(b"\x02xyz"))
        self.assertEqual(1, audit_dax_tree(self.game)["daxFiles"])
        with self.assertRaisesRegex(ValueError, "CRC"):
            self.audit()

    def test_missing_or_corrupt_resource_fork(self):
        self.fixture()
        path = self.game / "ITEM.DAX.rsrc"
        path.unlink()
        with self.assertRaisesRegex(ValueError, "resource fork mismatch"):
            self.audit()
        path.write_bytes(sidecar(b"changed bytes"))
        with self.assertRaisesRegex(ValueError, "resource fork mismatch"):
            self.audit()

    def test_matching_crc_does_not_make_invalid_dax_valid(self):
        self.fixture(data=b"")
        with self.assertRaisesRegex(ValueError, "DAX file size"):
            self.audit()

    def test_header_crc_and_archive_length(self):
        original = sit(entry("File", b"payload"))
        changed = bytearray(original)
        changed[25] ^= 1
        for data in (changed, original[:-1], original + b"x"):
            with self.assertRaises(ValueError):
                stuffit_entries(data)

    def test_declared_payload_outside_archive(self):
        truncated = bytearray(sit(entry("File", b"payload"))[:-1])
        struct.pack_into(">I", truncated, 6, len(truncated))
        with self.assertRaisesRegex(ValueError, "truncated StuffIt fork"):
            stuffit_entries(truncated)

    def test_folder_paths_duplicates_encryption_and_format(self):
        bad = (sit(entry("..")), sit(entry("a/b")), sit(entry("a:b")),
               sit(entry("dup"), entry("dup")), sit(entry("secret", method=0x80)),
               sit(entry("", method=0x21)), sit(entry("open", method=0x20)), b"SIT5" * 50)
        for data in bad:
            with self.subTest(data=data[:30]), self.assertRaises(ValueError):
                stuffit_entries(data)

    def test_symlinks_files_parents_and_dangling(self):
        self.fixture()
        target = self.game / "ITEM.DAX"
        target.unlink()
        target.symlink_to(self.root / "nonexistent")
        with self.assertRaisesRegex(ValueError, "Symlinks"):
            self.audit()
        with self.assertRaisesRegex(ValueError, "Symlinks"):
            audit_dax_tree(self.extracted)
        alias = self.root / "alias"
        alias.symlink_to(self.extracted, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlink"):
            audit_archive(self.archive, alias)

    def test_no_game_and_oversized_file(self):
        with self.assertRaisesRegex(ValueError, "No DAX"):
            audit_dax_tree(self.game)
        self.fixture()
        with self.assertRaisesRegex(ValueError, "oversized"):
            read_bounded(self.archive, 10)

    def test_cli_failure_is_nonzero_and_read_only(self):
        self.fixture()
        (self.game / "ITEM.DAX").write_bytes(b"")
        before = self.archive.read_bytes()
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("verify-game-extraction.py")),
                                 "--archive", str(self.archive), str(self.extracted)], capture_output=True)
        self.assertEqual(1, result.returncode)
        self.assertIn(b"ITEM.DAX", result.stderr)
        self.assertEqual(before, self.archive.read_bytes())

    @unittest.skipUnless(shutil.which("node"), "Node disk-builder preflight")
    def test_disk_builder_does_not_create_output_for_damaged_dax(self):
        self.fixture()
        (self.game / "ITEM.DAX").write_bytes(b"")
        destination = self.root / "must-not-exist.dsk"
        result = subprocess.run(["node", str(Path(__file__).with_name("prepare-test-game.mjs")),
                                 str(self.game), str(destination)], capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(destination.exists())
        self.assertIn(b"DAX file size", result.stderr)


class AppleDoubleTest(unittest.TestCase):
    def test_resource_entry_and_empty_fork(self):
        self.assertEqual(b"abc", apple_double(sidecar(b"abc"))[2])
        self.assertEqual(b"", apple_double(sidecar(b""))[2])

    def test_bad_envelope_offsets_duplicates_overlap(self):
        original = sidecar(b"abc")
        bad_offset = bytearray(original)
        struct.pack_into(">I", bad_offset, 30, 0)
        for data in (b"raw resource", original[:25], original[:36], original[:-1], bad_offset):
            with self.assertRaises(ValueError):
                apple_double(data)
        header = struct.pack(">II16sH", 0x00051607, 0x00020000, b"", 2)
        for second in (2, 9):
            with self.assertRaises(ValueError):
                apple_double(header + struct.pack(">III", 2, 50, 3)
                             + struct.pack(">III", second, 51, 2) + b"abc")


if __name__ == "__main__":
    unittest.main()
