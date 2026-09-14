#!/usr/bin/env python3
"""Small synthetic HFS tests. No System, ROM, game or save bytes are embedded."""
import importlib.util
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest

import machfs
from macresources import Resource, make_file, parse_file

SPEC = importlib.util.spec_from_file_location("personal_boot", Path(__file__).with_name("prepare-personal-boot.py"))
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


def file(kind, data=b"", resource=b""):
    value = machfs.File()
    value.type, value.creator = kind, b"TEST"
    value.data, value.rsrc = data, resource
    value.crdate, value.mddate, value.bkdate = 123, 456, 789
    return value


def volumes():
    boot = machfs.Volume()
    boot.name, boot.crdate = "Test Boot", 3000000000
    boot["System Folder"] = machfs.Folder()
    boot["System Folder", "Startup Items"] = machfs.Folder()
    blocks = bytearray(1024)
    blocks[:2] = b"LK"
    blocks[90:97] = b"\x06Finder"
    version = b"\x07\x55\x80\0\0\0\x057.5.5\0"
    boot[builder.SYSTEM] = file(b"zsys", b"system-data", make_file([
        Resource(b"boot", 1, data=blocks), Resource(b"vers", 1, data=version)]))
    boot[builder.FINDER] = file(b"FNDR", b"finder-data", b"finder-resource")
    boot["Manual"] = file(b"TEXT", b"user manual", b"custom fork")
    alias = file(b"TEXT")
    alias.flags, alias.aliastarget = 0x8000, boot["Manual"]
    boot["Read Manual"] = alias
    game = machfs.Volume()
    game.name, game.crdate = "Test Game", 3000000001
    game["Pool Of Radiance"] = machfs.Folder()
    game[builder.GAME] = file(b"APPL", b"application data", b"application resource")
    game["Pool Of Radiance", "PoolRadSave"] = machfs.Folder()
    game["Pool Of Radiance", "PoolRadSave", "My Party"] = file(b"prdt", b"saved progress", b"saved characters")
    return boot, game


def raw(volume):
    # hfsutils requires enough free B-tree nodes for a split. machfs's default
    # single-node clump creates a packed fixture its writer cannot extend.
    return volume.write(size=4 * 1024 * 1024, align=4096)


class PersonalBootTest(unittest.TestCase):
    def build(self, boot=None, game=None, **kwargs):
        default_boot, default_game = volumes()
        return builder.prepare(raw(boot if boot is not None else default_boot),
                               raw(game if game is not None else default_game),
                               size_mib=32, **kwargs)

    def test_real_hfs_roundtrip_preserves_both_forks_and_original_boot_blocks(self):
        boot, game = volumes()
        boot_raw, game_raw = raw(boot), raw(game)
        boot_hash, game_hash = builder.digest(boot_raw), builder.digest(game_raw)
        built, report = builder.prepare(boot_raw, game_raw, 32)
        parsed = builder.load_volume(built)
        self.assertEqual(boot_raw[:1024], built[:1024])
        self.assertEqual("7.5.5", report["systemVersion"])
        self.assertEqual(boot.name, parsed.name)
        self.assertEqual(boot.crdate, parsed.crdate)
        self.assertEqual(boot_raw[1116:1128], built[1116:1128])
        for path in (builder.SYSTEM, builder.FINDER, ("Manual",)):
            self.assertEqual(bytes(boot[path].data), bytes(parsed[path].data))
            self.assertEqual(bytes(boot[path].rsrc), bytes(parsed[path].rsrc))
        for path, entry in builder.inventory(game).items():
            if entry["kind"] == "file":
                self.assertEqual(bytes(game[path].data), bytes(parsed[path].data))
                self.assertEqual(bytes(game[path].rsrc), bytes(parsed[path].rsrc))
        self.assertEqual(boot_hash, builder.digest(boot_raw))
        self.assertEqual(game_hash, builder.digest(game_raw))
        self.assertEqual(boot_hash, report["bootSourceSha256"])
        self.assertEqual(game_hash, report["gameSourceSha256"])

    def test_startup_and_existing_aliases_resolve_to_same_named_targets(self):
        built, report = self.build()
        parsed = builder.load_volume(built)
        self.assertIs(parsed[builder.GAME], parsed[builder.STARTUP].aliastarget)
        self.assertIs(parsed["Manual"], parsed["Read Manual"].aliastarget)
        self.assertTrue(parsed[builder.STARTUP].flags & 0x8000)
        self.assertEqual([["Read Manual"]], report["preservedAliases"])
        boot, _ = volumes()
        self.assertEqual(builder.load_volume(raw(boot))["Read Manual"].rsrc,
                         parsed["Read Manual"].rsrc)
        self.assertEqual("not performed by the builder", report["coldBootAcceptance"])

    def test_recovery_disk_has_no_startup_alias_but_retains_game_and_saves(self):
        built, report = self.build(startup=False)
        parsed = builder.load_volume(built)
        self.assertEqual(0, len(parsed[builder.STARTUP[:-1]]))
        self.assertEqual(b"saved progress", parsed["Pool Of Radiance", "PoolRadSave", "My Party"].data)
        self.assertFalse(report["startup"])

    def test_rejects_invalid_image_size_magic_and_original_startup_override(self):
        boot, game = volumes()
        a, b = raw(boot), raw(game)
        for broken in (b"", a[:1024], a[:1024] + b"XX" + a[1026:]):
            with self.assertRaises(ValueError):
                builder.prepare(broken, b)
        for size in (0, 31, 64, 513):
            with self.assertRaises(ValueError):
                builder.prepare(a, b, size)
        changed = bytearray(a)
        struct.pack_into(">I", changed, 1120, 99)
        with self.assertRaisesRegex(ValueError, "overrides"):
            builder.prepare(bytes(changed), b)

    def test_refuses_wrong_system_version_or_missing_application_resource_fork(self):
        boot, game = volumes()
        resources = list(parse_file(boot[builder.SYSTEM].rsrc))
        next(r for r in resources if r.type == b"vers").data[0] = 6
        boot[builder.SYSTEM].rsrc = make_file(resources)
        with self.assertRaisesRegex(ValueError, "System 7"):
            self.build(boot=boot)
        game[builder.GAME].rsrc = b""
        with self.assertRaisesRegex(ValueError, "fork-preserved"):
            self.build(game=game)

    def test_path_collision_and_existing_startup_are_not_overwritten(self):
        boot, game = volumes()
        game["Manual"] = file(b"TEXT", b"different manual")
        with self.assertRaisesRegex(ValueError, "collision"):
            self.build(game=game)
        boot[builder.STARTUP] = file(b"TEXT", b"existing startup item")
        with self.assertRaisesRegex(ValueError, "existing items"):
            self.build(boot=boot)

    def test_extra_alias_content_is_included_in_exact_preservation_check(self):
        boot, _ = volumes()
        parsed = builder.load_volume(raw(boot))
        existing = parsed["Read Manual"]
        existing.rsrc = make_file(list(parse_file(existing.rsrc)) + [Resource(b"ICN#", 1, data=b"custom icon")])
        expected = builder.inventory(parsed)
        self.assertEqual(builder.digest(existing.rsrc), expected[("Read Manual",)]["resourceSha256"])
        existing.rsrc = b"lost custom icon"
        with self.assertRaisesRegex(ValueError, "changed"):
            builder.compare_inventory(expected, builder.inventory(parsed))

    def test_preservation_checker_detects_lost_file_fork_and_retargeted_alias(self):
        boot, _ = volumes()
        parsed = builder.load_volume(raw(boot))
        expected = builder.inventory(parsed)
        parsed["Manual"].data += b"corruption"
        with self.assertRaisesRegex(ValueError, "changed"):
            builder.compare_inventory(expected, builder.inventory(parsed))
        parsed = builder.load_volume(raw(boot))
        parsed["Read Manual"].aliastarget = parsed[builder.FINDER]
        with self.assertRaisesRegex(ValueError, "changed"):
            builder.compare_inventory(expected, builder.inventory(parsed))

    def test_growth_preserves_entire_original_allocation_area_and_volume_identity(self):
        boot, _ = volumes()
        before = raw(boot)
        after = builder.grow_hfs(before)
        count, size = struct.unpack_from(">HI", before, 1042)
        old_start = struct.unpack_from(">H", before, 1052)[0] * 512
        new_start = struct.unpack_from(">H", after, 1052)[0] * 512
        self.assertEqual(before[old_start:old_start + count * size],
                         after[new_start:new_start + count * size])
        self.assertEqual(before[:1024], after[:1024])
        self.assertEqual(after[1024:1536], after[-1024:-512])
        self.assertEqual(builder.inventory(builder.load_volume(before)),
                         builder.inventory(builder.load_volume(after)))
        new_count = struct.unpack_from(">H", after, 1042)[0]
        free_before = struct.unpack_from(">H", before, 1058)[0]
        free_after = struct.unpack_from(">H", after, 1058)[0]
        self.assertEqual(new_count - count, free_after - free_before)

    def test_growth_rejects_dirty_truncated_and_inconsistent_bitmap(self):
        boot, _ = volumes()
        source = raw(boot)
        dirty = bytearray(source)
        struct.pack_into(">H", dirty, 1034, 0)
        with self.assertRaisesRegex(ValueError, "cleanly"):
            builder.grow_hfs(bytes(dirty))
        bad_count = bytearray(source)
        free = struct.unpack_from(">H", source, 1058)[0]
        struct.pack_into(">H", bad_count, 1058, free - 1)
        with self.assertRaisesRegex(ValueError, "free block"):
            builder.grow_hfs(bytes(bad_count))
        with self.assertRaisesRegex(ValueError, "Truncated"):
            builder.grow_hfs(source[:-4096])
        for size in (len(source), len(source) - 512, 512 * 1024 * 1024, 33):
            with self.assertRaises(ValueError):
                builder.grow_hfs(source, size)

    def test_growth_rejects_overlapping_bitmap_and_allocation_data(self):
        boot, _ = volumes()
        source = bytearray(raw(boot))
        struct.pack_into(">H", source, 1052, 3)
        with self.assertRaisesRegex(ValueError, "overlaps"):
            builder.grow_hfs(bytes(source))

    def test_cli_refuses_existing_output_and_dangling_symlink(self):
        with tempfile.TemporaryDirectory(prefix="poolrad-boot-test-") as temp:
            output = Path(temp) / "existing.dsk"
            output.write_bytes(b"important existing save disk")
            command = [sys.executable, str(Path(__file__).with_name("prepare-personal-boot.py")),
                       "nonexistent-boot", "nonexistent-game", str(output)]
            result = subprocess.run(command, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn(b"Refusing to overwrite", result.stderr)
            self.assertEqual(b"important existing save disk", output.read_bytes())
            link = Path(temp) / "link.dsk"
            link.symlink_to(Path(temp) / "absent")
            command[-1] = str(link)
            result = subprocess.run(command, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertTrue(link.is_symlink())

    @unittest.skipUnless(all(shutil.which(x) for x in ("hmount", "hls", "hcopy", "humount")), "hfsutils required")
    def test_cli_independent_hfsutils_forks_and_blessing_checks(self):
        boot, game = volumes()
        with tempfile.TemporaryDirectory(prefix="poolrad-boot-test-") as temp:
            folder = Path(temp)
            a, b, output = folder / "boot.dsk", folder / "game.dsk", folder / "combined.dsk"
            a.write_bytes(raw(boot)); b.write_bytes(raw(game))
            before = (builder.digest(a.read_bytes()), builder.digest(b.read_bytes()))
            subprocess.run([sys.executable, str(Path(__file__).with_name("prepare-personal-boot.py")),
                            str(a), str(b), str(output), "--size-mib", "32"], check=True, capture_output=True)
            self.assertEqual(before, (builder.digest(a.read_bytes()), builder.digest(b.read_bytes())))
            self.assertTrue(Path(str(output) + ".manifest.json").is_file())
            parsed = builder.load_volume(output.read_bytes())
            self.assertIs(parsed[builder.GAME], parsed[builder.STARTUP].aliastarget)


if __name__ == "__main__":
    unittest.main()
