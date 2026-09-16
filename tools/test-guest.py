#!/usr/bin/env python3
"""Synthetic frames only: no screenshot, ROM, game or save bytes are embedded.

Everything here is the part of the scripted driver that can be wrong without an
emulator noticing -- region arithmetic, the ink and profile measurements the
scenarios make decisions on, and the refusal to touch anything that is not an
emulator.
"""
import importlib.util
import struct
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, TOOLS / filename)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


guest = load("guest", "guest.py")
play = load("play", "play.py")


def frame(width, height, fill=(255, 255, 255), boxes=()):
    """A screencap blob: 16-byte header then RGBA8888.

    Built a row at a time. A per-pixel loop over a 1200x1600 window made this
    file take a minute to run, which is a good way to stop running it.
    """
    background = bytes((fill[0], fill[1], fill[2], 255)) * width
    rows = [bytearray(background) for _ in range(height)]
    for (bx, by, bw, bh, value) in boxes:
        span = bytes((value[0], value[1], value[2], 255)) * max(0, min(bw, width - bx))
        for y in range(max(0, by), min(by + bh, height)):
            rows[y][bx * 4:bx * 4 + len(span)] = span
    return struct.pack("<IIII", width, height, 1, 0) + b"".join(bytes(r) for r in rows)


class ScreenTest(unittest.TestCase):
    def test_header_and_size_are_checked(self):
        with self.assertRaises(SystemExit):
            guest.Screen(b"\x00" * 8)
        with self.assertRaises(SystemExit):
            guest.Screen(struct.pack("<IIII", 4, 4, 1, 0) + b"\x00" * 10)

    def test_a_region_is_exactly_the_pixels_asked_for(self):
        screen = guest.Screen(frame(8, 4, boxes=[(2, 1, 3, 2, (0, 0, 0))]))
        self.assertEqual(8, screen.width)
        self.assertEqual(4, screen.height)
        self.assertEqual(3 * 2 * 4, len(screen.region((2, 1, 3, 2))))
        self.assertEqual(6, screen.ink((2, 1, 3, 2)))
        self.assertEqual(0, screen.ink((0, 0, 2, 4)))

    def test_a_region_running_off_the_edge_is_clipped_not_wrapped(self):
        screen = guest.Screen(frame(8, 4, boxes=[(6, 0, 2, 4, (0, 0, 0))]))
        self.assertEqual(8, screen.ink((6, 0, 99, 99)))
        self.assertEqual(0, screen.ink((99, 99, 4, 4)))

    def test_the_digest_follows_the_pixels_and_only_the_pixels(self):
        plain = guest.Screen(frame(8, 4))
        marked = guest.Screen(frame(8, 4, boxes=[(0, 0, 1, 1, (0, 0, 0))]))
        self.assertEqual(plain.digest((2, 0, 4, 4)), marked.digest((2, 0, 4, 4)))
        self.assertNotEqual(plain.digest((0, 0, 8, 4)), marked.digest((0, 0, 8, 4)))

    def test_a_screenshot_is_a_readable_png(self):
        import tempfile
        screen = guest.Screen(frame(6, 3, boxes=[(1, 1, 2, 1, (0, 0, 0))]))
        with tempfile.NamedTemporaryFile(suffix=".png") as out:
            screen.png(out.name)
            data = Path(out.name).read_bytes()
        self.assertTrue(data.startswith(b"\x89PNG\r\n\x1a\n"))
        for chunk in (b"IHDR", b"IDAT", b"IEND"):
            self.assertIn(chunk, data)


class RegionTest(unittest.TestCase):
    def setUp(self):
        self.screen = guest.Screen(frame(1200, 1600))

    def test_named_regions_split_the_window(self):
        self.assertEqual((0, 0, 1200, 1600), guest.parse_region("all", self.screen))
        pane = guest.parse_region("pane", self.screen)
        area = guest.parse_region("guest", self.screen)
        self.assertEqual(pane[1] + pane[3], area[1])
        self.assertEqual(area[1] + area[3], 1600)

    def test_the_message_region_leaves_the_animated_picture_out(self):
        # The picture window animates a campfire, so anything that includes it
        # never settles. The message region must sit below and clear of it.
        message = guest.parse_region("message", self.screen)
        picture_bottom = int(1600 * 0.55)
        self.assertGreater(message[1], picture_bottom)
        self.assertLessEqual(message[1] + message[3], 1600)
        self.assertLessEqual(message[0] + message[2], 1200)

    def test_an_explicit_box_is_taken_literally(self):
        self.assertEqual((5, 6, 7, 8), guest.parse_region("5,6,7,8", self.screen))

    def test_nonsense_is_refused_rather_than_guessed(self):
        for bad in ("5,6,7", "5,6,7,8,9", "left", "5,6,7,x", "", "messages"):
            with self.assertRaises(SystemExit, msg=bad):
                guest.parse_region(bad, self.screen)


class ButtonRowTest(unittest.TestCase):
    """How the scenarios read the game's state: by counting its buttons."""

    def row(self, count, pointer=None):
        """A Message window offering `count` evenly spaced buttons."""
        x, y, width, height = play.BUTTON_ROW
        span = width // (count * 2) if count else 0
        boxes = []
        for i in range(count):
            left = x + span + i * 2 * span
            boxes.append((left, y, span, height, (0, 0, 0)))
        if pointer:
            boxes.append(pointer + ((0, 0, 0),))
        # The Message window's own frame, which runs the full width above and
        # below the buttons and would merge them all into one run if the band
        # were not measured carefully.
        boxes.append((x - 10, y - 4, width + 20, 2, (0, 0, 0)))
        boxes.append((x - 10, y + height + 4, width + 20, 3, (0, 0, 0)))
        return guest.Screen(frame(1200, 1600, boxes=boxes))

    def test_each_prompt_is_named_by_its_button_count(self):
        for count, name in ((1, "continue"), (2, "question"),
                            (4, "encounter"), (6, "explore"), (0, "busy")):
            self.assertEqual(name, play.state(self.row(count)), "%d buttons" % count)

    def test_an_unexpected_count_is_not_guessed_at(self):
        # Three buttons is a half-painted exploration row -- Area, Cast, View
        # and nothing yet -- caught mid-redraw. It is not a state the game has,
        # and must never be read as one.
        self.assertEqual("unknown", play.state(self.row(3)))
        self.assertEqual("unknown", play.state(self.row(5)))
        self.assertNotIn("unknown", play.KNOWN)
        self.assertNotIn("busy", play.KNOWN)

    def test_the_window_frame_does_not_merge_the_buttons(self):
        # The frame runs the full width; counting over it once made every
        # prompt look like a single button.
        self.assertEqual(6, len(play.button_spans(self.row(6))))

    def test_the_mouse_pointer_is_not_a_button(self):
        # The pointer parks in this row after every click and sits low in it.
        x, y, _width, height = play.BUTTON_ROW
        pointer = (x + 30, y + height - 14, 12, 16)
        self.assertEqual("question", play.state(self.row(2, pointer=pointer)))
        self.assertEqual("explore", play.state(self.row(6, pointer=pointer)))

    def test_spans_are_left_to_right_and_inside_the_row(self):
        spans = play.button_spans(self.row(4))
        self.assertEqual(4, len(spans))
        self.assertEqual(sorted(spans), spans)
        x, _y, width, _height = play.BUTTON_ROW
        for left, right in spans:
            self.assertLess(left, right)
            self.assertGreaterEqual(left, x)
            self.assertLessEqual(right, x + width)


class ForegroundTest(unittest.TestCase):
    """Input must never land in whatever else happens to be on screen."""

    def dumpsys(self, line):
        original = guest.adb
        guest.adb = lambda serial, *args, **kw: line
        self.addCleanup(lambda: setattr(guest, "adb", original))

    def test_the_focused_package_is_read_out_of_dumpsys(self):
        self.dumpsys("  mCurrentFocus=Window{73bc82 u0 "
                     "com.hunterdavis.poolradmacmaps.ii/name.osher.gil.minivmac.MiniVMac}")
        self.assertEqual("com.hunterdavis.poolradmacmaps.ii", guest.foreground("emulator-1"))

    def test_nothing_focused_reads_as_nothing_rather_than_as_the_app(self):
        for line in ("  mCurrentFocus=null", "", "  mFocusedApp=null"):
            self.dumpsys(line)
            self.assertEqual("", guest.foreground("emulator-1"))

    def test_input_is_refused_when_another_app_is_in_front(self):
        # This is not hypothetical: the guest died mid-run, the rest of a
        # scripted walk went into the launcher, and the emulator ended up on a
        # web search for the letter "a".
        for other in ("org.chromium.webview_shell", "com.android.launcher3", ""):
            self.dumpsys("  mCurrentFocus=Window{1 u0 %s/x}" % other if other else "")
            with self.assertRaises(SystemExit) as refused:
                guest.require_foreground("emulator-1")
            self.assertIn("refusing input", str(refused.exception))

    def test_input_is_allowed_for_either_flavour_of_the_app(self):
        for package in ("com.hunterdavis.poolradmacmaps.ii",
                        "com.hunterdavis.poolradmacmaps.plus"):
            self.dumpsys("  mCurrentFocus=Window{1 u0 %s/x}" % package)
            guest.require_foreground("emulator-1")


class SafetyTest(unittest.TestCase):
    def test_only_an_emulator_serial_is_accepted(self):
        for good in ("emulator-5554", "emulator-5584"):
            self.assertTrue(guest.SERIAL.match(good))
        # Real hardware, and the shapes a typo might take.
        for bad in ("R5CT12345", "emulator-", "emulator-abc", "127.0.0.1:5555",
                    "emulator-5584x", "xemulator-5584", ""):
            self.assertFalse(guest.SERIAL.match(bad), bad)

    def test_the_tools_refuse_a_non_emulator_target(self):
        for module, args in ((guest, ["--serial", "R5CT12345", "digest"]),
                             (play, ["--serial", "R5CT12345", "tour"])):
            with self.assertRaises(SystemExit) as refused:
                module.main(args)
            self.assertIn("Refusing", str(refused.exception))


if __name__ == "__main__":
    unittest.main()
