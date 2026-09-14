#!/usr/bin/env python3
"""Synthetic-only package tests: contains no real ROM, System, game, or saves."""
import hashlib
import importlib.util
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("prepare-personal-package.py")
SPEC = importlib.util.spec_from_file_location("personal_package", SCRIPT)
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


def synthetic_rom(header=0x9779D2C4):
    # A checksum-valid synthetic byte pattern, not working Macintosh firmware.
    quotient, remainder = divmod(header, 65535)
    body = b"\xff\xff" * quotient + struct.pack(">H", remainder)
    return struct.pack(">I", header) + body + bytes(builder.ROM_BYTES - len(body) - 4)


class PersonalPackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="poolrad-package-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.rom = self.root / "source.rom"
        self.disk = self.root / "source.dsk"
        self.rom.write_bytes(synthetic_rom())
        self.disk.write_bytes(b"D" * 512)
        self.output = self.root / "bundle"

    def prepare(self, **kwargs):
        builder.prepare(kwargs.pop("rom", self.rom), kwargs.pop("disks", [self.disk]),
                        kwargs.pop("output", self.output), **{"acknowledge": True, **kwargs})
        return self.output

    def manifest(self):
        return self.output / "bundle.properties"

    def replace_manifest(self, old, new):
        path = self.manifest()
        path.write_bytes(path.read_bytes().replace(old, new))

    def assert_rejected(self, callable, *args, **kwargs):
        with self.assertRaises((ValueError, OSError)):
            callable(*args, **kwargs)

    def test_prepare_preserves_sources_and_disk_boot_order(self):
        second = self.root / "game.dsk"
        second.write_bytes(b"G" * 1024)
        originals = {path: (path.read_bytes(), path.stat().st_mtime_ns)
                     for path in (self.rom, self.disk, second)}
        self.prepare(disks=[self.disk, second])
        raw, snapshots = builder.verify(self.output)
        entries = builder.parse_manifest(raw)
        self.assertEqual("2", entries["disk.count"])
        self.assertEqual("1", entries["format"])
        self.assertEqual("macII", entries["machine"])
        self.assertEqual(b"D" * 512, (self.output / "disk1.dsk").read_bytes())
        self.assertEqual(b"G" * 1024, (self.output / "disk2.dsk").read_bytes())
        self.assertEqual({"bundle.properties", "MacII.ROM", "disk1.dsk", "disk2.dsk"}, set(snapshots))
        for source, original in originals.items():
            self.assertEqual(original, (source.read_bytes(), source.stat().st_mtime_ns))
        # Copies are independent; changing a prepared disk cannot change the input.
        (self.output / "disk1.dsk").write_bytes(b"X" * 512)
        self.assertEqual(b"D" * 512, self.disk.read_bytes())

    def test_both_rom_headers_and_entire_checksum_are_required(self):
        for header in builder.ROM_HEADERS:
            builder.validate_rom(synthetic_rom(header))
        for invalid in (b"", synthetic_rom()[:-2], b"\x00" * builder.ROM_BYTES,
                        synthetic_rom()[:-2] + b"\0\1"):
            self.assert_rejected(builder.validate_rom, invalid)
        # The last case has a matching earlier prefix but a nonzero trailing word.

    def test_explicit_private_acknowledgement_is_required(self):
        self.assert_rejected(builder.prepare, self.rom, [self.disk], self.output)
        self.assertFalse(self.output.exists())

    def test_bad_rom_is_rejected_before_output_creation(self):
        self.rom.write_bytes(synthetic_rom()[:-2] + b"\0\1")
        self.assert_rejected(self.prepare)
        self.assertFalse(self.output.exists())

    def test_disk_count_sector_alignment_and_individual_limit(self):
        for disks in ([], [self.disk] * 9):
            self.assert_rejected(self.prepare, disks=disks)
        for data in (b"", b"x", b"x" * 513):
            self.disk.write_bytes(data)
            self.assert_rejected(self.prepare)
        with self.disk.open("wb") as stream:
            stream.truncate(builder.DISK_MAX + 512)
        self.assert_rejected(self.prepare)
        self.assertFalse(self.output.exists())

    def test_manifest_accepts_total_disk_limit_plus_additional_rom(self):
        self.prepare(disks=[self.disk, self.disk])
        raw = self.manifest().read_bytes().replace(b".bytes=512", b".bytes=134217728")
        self.assertEqual("2", builder.parse_manifest(raw)["disk.count"])
        third = (b"disk.3.file=disk3.dsk\ndisk.3.bytes=512\ndisk.3.sha256="
                 + b"0" * 64 + b"\n")
        self.assert_rejected(builder.parse_manifest, raw.replace(b"disk.count=2", b"disk.count=3") + third)

    def test_existing_output_file_directory_and_symlink_are_never_replaced(self):
        self.output.write_bytes(b"keep me")
        self.assert_rejected(self.prepare)
        self.assertEqual(b"keep me", self.output.read_bytes())
        self.output.unlink()
        self.output.mkdir()
        self.assert_rejected(self.prepare)
        self.assertEqual([], list(self.output.iterdir()))
        self.output.rmdir()
        try:
            self.output.symlink_to(self.root / "missing")
        except (OSError, NotImplementedError):
            return
        self.assert_rejected(self.prepare)
        self.assertTrue(self.output.is_symlink())

    def test_verify_is_read_only_and_stage_preserves_manifest_bytes(self):
        self.prepare()
        self.manifest().write_bytes(self.manifest().read_bytes().replace(b"\n", b"\r\n"))
        original = builder.verify(self.output)
        staged = self.root / "generated" / "personalAssets"
        builder.stage(self.output, staged)
        self.assertEqual({"personal"}, {p.name for p in staged.iterdir()})
        self.assertEqual(original, builder.verify(self.output))
        self.assertEqual(original[0], builder.verify(staged / "personal")[0])
        for name in original[1]:
            self.assertEqual((self.output / name).read_bytes(), (staged / "personal" / name).read_bytes())
        self.assert_rejected(builder.stage, self.output, staged)
        self.assert_rejected(builder.stage, self.output, self.output / "nested")
        self.assertFalse((self.output / "nested").exists())

    def test_verify_rejects_missing_files_extra_files_and_subdirectories(self):
        self.prepare()
        for name in ("bundle.properties", "MacII.ROM", "disk1.dsk"):
            source, held = self.output / name, self.root / "held"
            source.rename(held)
            self.assert_rejected(builder.verify, self.output)
            held.rename(source)
        extra = self.output / "extra"
        extra.write_bytes(b"private notes must not be silently bundled")
        self.assert_rejected(builder.verify, self.output)
        extra.unlink()
        extra.mkdir()
        self.assert_rejected(builder.verify, self.output)

    def test_source_and_bundle_symlinks_are_rejected(self):
        link = self.root / "link.rom"
        try:
            link.symlink_to(self.rom)
        except (OSError, NotImplementedError):
            self.skipTest("Symlinks unsupported")
        self.assert_rejected(self.prepare, rom=link)
        self.prepare()
        alias = self.root / "alias"
        alias.symlink_to(self.output, target_is_directory=True)
        self.assert_rejected(builder.verify, alias)
        (self.output / "disk1.dsk").unlink()
        (self.output / "disk1.dsk").symlink_to(self.disk)
        self.assert_rejected(builder.verify, self.output)

    def test_strict_manifest_rejects_duplicates_unknown_keys_and_unsafe_values(self):
        self.prepare()
        raw = self.manifest().read_bytes()
        for bad in (raw + b"format=1\n", raw + b"surprise=yes\n", raw + b"# comment\n",
                    raw.replace(b"macII", b"macPlus"), raw.replace(b"disk1.dsk", b"../disk1.dsk"),
                    raw.replace(b"disk.count=1", b"disk.count=01"), raw.replace(b"disk.count=1", b"disk.count=9"),
                    raw.replace(b"disk.1.bytes=512", b"disk.1.bytes=0"), raw.replace(b"format=1", b"format=2"),
                    raw.replace(b"rom.sha256=", b"rom.sha256=Z"), raw.replace(b"format=1", b"format=\xff"),
                    raw.replace(b"\n", b"\v"), raw + b" " * builder.MANIFEST_MAX, b""):
            with self.subTest(prefix=bad[:45]):
                self.assert_rejected(builder.parse_manifest, bad)

    def test_changed_and_truncated_payloads_fail_hash_validation(self):
        self.prepare()
        payload = self.output / "disk1.dsk"
        for bad in (b"D" * 511, b"E" * 512, b"D" * 1024):
            payload.write_bytes(bad)
            self.assert_rejected(builder.verify, self.output)
        payload.write_bytes(b"D" * 512)
        rom = self.output / "MacII.ROM"
        bad = synthetic_rom()[:-2] + b"\0\1"
        rom.write_bytes(bad)
        # Even a matching replacement SHA cannot disguise an invalid ROM checksum.
        raw = self.manifest().read_bytes()
        original = hashlib.sha256(synthetic_rom()).hexdigest().encode()
        self.manifest().write_bytes(raw.replace(original, hashlib.sha256(bad).hexdigest().encode()))
        self.assert_rejected(builder.verify, self.output)

    def test_changed_source_during_prepare_aborts_without_publication(self):
        original_copy = builder._copy
        def change_after_copy(source, destination, expected):
            original_copy(source, destination, expected)
            if source == self.disk:
                self.disk.write_bytes(b"new user data".ljust(512, b"!"))
        with patch.object(builder, "_copy", side_effect=change_after_copy):
            self.assert_rejected(self.prepare)
        self.assertFalse(self.output.exists())
        self.assertTrue(self.disk.read_bytes().startswith(b"new user data"))
        self.assertEqual([], list(self.root.glob(".poolrad-package-*")))

    def test_stage_revalidates_input_and_does_not_publish_changed_payload(self):
        self.prepare()
        original_copy = builder._copy
        def change_after_copy(source, destination, expected):
            original_copy(source, destination, expected)
            if source.name == "disk1.dsk":
                source.write_bytes(b"X" * 512)
        staged = self.root / "staged"
        with patch.object(builder, "_copy", side_effect=change_after_copy):
            self.assert_rejected(builder.stage, self.output, staged)
        self.assertFalse(staged.exists())

    def test_publish_cannot_replace_output_created_after_validation(self):
        original_publish = builder._publish
        def raced(staging, output, nested):
            output.mkdir()
            (output / "belongs-to-user").write_bytes(b"keep")
            original_publish(staging, output, nested)
        with patch.object(builder, "_publish", side_effect=raced):
            self.assert_rejected(self.prepare)
        self.assertEqual(b"keep", (self.output / "belongs-to-user").read_bytes())

    def test_cli_prepare_verify_stage_and_private_warning(self):
        def run(*args):
            return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)],
                                  capture_output=True, text=True, timeout=10)
        result = run("prepare", "--rom", self.rom, "--disk", self.disk, "--output", self.output)
        self.assertEqual(2, result.returncode)
        self.assertIn("acknowledge", result.stderr)
        result = run("prepare", "--rom", self.rom, "--disk", self.disk, "--output", self.output,
                     "--acknowledge-private-assets")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("do not publish", result.stdout)
        self.assertEqual(0, run("verify", self.output).returncode)
        result = run("stage", self.output, self.root / "staged")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("NEVER", run("--help").stdout)


if __name__ == "__main__":
    unittest.main()
