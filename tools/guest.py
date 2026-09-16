#!/usr/bin/env python3
"""Drive the emulated Macintosh from the command line.

Reaching a battle by hand has repeatedly failed: a synthetic `input tap` does
not open a Mac menu and two taps are too far apart to be a double click. The
primitive that does work is `input motionevent`, which gives a real mouse down,
move and up, so this tool builds proper clicks, double clicks and menu pulls out
of it -- and sends keys, which reach the guest because the companion map is
deliberately not focusable.

The other half is waiting. A fixed sleep is a guess that is either slow or
flaky, so everything here waits on the screen itself: `settle` blocks until a
region stops changing, `await-change` until it starts. Screens come from
`adb exec-out screencap`, whose raw form is a 16-byte header and RGBA8888
pixels, so this needs no image library.

Emulator only. It drives the game the way a player does; it never writes guest
memory, and nothing here belongs anywhere near a physical tablet.
"""

import argparse
import hashlib
import re
import struct
import subprocess
import sys
import time
import zlib

SERIAL = re.compile(r"^emulator-\d+$")
HEADER = 16


def adb(serial, *args, binary=False, attempts=3):
    """One adb call, retried.

    A scripted run takes hundreds of screencaps from an emulator that is also
    emulating a Macintosh, and one of them occasionally takes longer than any
    reasonable timeout. That is a slow frame, not a failure, so retry before
    giving up on the whole run.
    """
    for attempt in range(attempts):
        try:
            result = subprocess.run(["adb", "-s", serial, *args],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
        except subprocess.TimeoutExpired:
            if attempt == attempts - 1:
                raise SystemExit("adb %s timed out %d times" % (" ".join(args), attempts))
            time.sleep(2)
            continue
        if result.returncode == 0:
            return result.stdout if binary else result.stdout.decode()
        if attempt == attempts - 1:
            raise SystemExit("adb %s failed: %s" % (" ".join(args), result.stderr.decode().strip()))
        time.sleep(1)
    raise SystemExit("unreachable")


class Screen:
    """One frame, as raw pixels."""

    def __init__(self, blob):
        if len(blob) < HEADER:
            raise SystemExit("Short screencap")
        self.width, self.height, _format = struct.unpack("<III", blob[:12])
        self.pixels = blob[HEADER:]
        if len(self.pixels) != self.width * self.height * 4:
            raise SystemExit("Unexpected screencap size %d for %dx%d"
                             % (len(self.pixels), self.width, self.height))

    def region(self, box):
        x, y, w, h = box
        stride = self.width * 4
        rows = []
        for row in range(y, min(y + h, self.height)):
            start = row * stride + x * 4
            rows.append(self.pixels[start:start + min(w, self.width - x) * 4])
        return b"".join(rows)

    def digest(self, box):
        return hashlib.sha256(self.region(box)).hexdigest()[:16]

    def ink(self, box, threshold=128):
        """Dark pixels in a region. How a script asks "is that button there?"."""
        data = self.region(box)
        return sum(1 for i in range(0, len(data), 4) if data[i] < threshold)

    def png(self, path):
        """Minimal RGBA PNG, so a screenshot needs no image library either."""
        raw = bytearray()
        stride = self.width * 4
        for row in range(self.height):
            raw.append(0)
            raw += self.pixels[row * stride:(row + 1) * stride]

        def chunk(kind, payload):
            body = kind + payload
            return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body))

        head = struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0)
        with open(path, "wb") as out:
            out.write(b"\x89PNG\r\n\x1a\n")
            out.write(chunk(b"IHDR", head))
            out.write(chunk(b"IDAT", zlib.compress(bytes(raw), 6)))
            out.write(chunk(b"IEND", b""))


def grab(serial):
    return Screen(adb(serial, "exec-out", "screencap", binary=True))


def parse_region(text, screen):
    """A box, either x,y,w,h or one of the named areas."""
    if text in (None, "all"):
        return (0, 0, screen.width, screen.height)
    if text == "guest":
        # The guest occupies the lower part of the window under every companion
        # layout; this is deliberately generous rather than clever.
        top = int(screen.height * 0.38)
        return (0, top, screen.width, screen.height - top)
    if text == "message":
        # The game's Message window and its button row, and nothing else. The
        # picture window beside it animates -- a campfire, a torch -- so a
        # region that includes it never settles, ever.
        return (10, int(screen.height * 0.725), 1020, int(screen.height * 0.23))
    if text == "pane":
        return (0, 0, screen.width, int(screen.height * 0.38))
    parts = text.split(",")
    if len(parts) != 4 or not all(p.strip().lstrip("-").isdigit() for p in parts):
        raise SystemExit("Region must be x,y,w,h or all/guest/pane/message")
    return tuple(int(p) for p in parts)


PACKAGE = "com.hunterdavis.poolradmacmaps."


def foreground(serial):
    """The package that currently owns the window, or "" if that is unclear."""
    for line in adb(serial, "shell", "dumpsys window").splitlines():
        if "mCurrentFocus" in line:
            match = re.search(r"\s([A-Za-z0-9_.]+)/", line)
            return match.group(1) if match else ""
    return ""


def require_foreground(serial):
    """Refuse to type into whatever else happens to be on screen.

    The app can crash or be swapped out mid-run, and everything after that
    lands somewhere else. It did once: the guest died, the rest of a scripted
    walk went into the launcher, and the emulator ended up on a web search for
    the letter "a". `android-ui.mjs` has always refused input on this ground and
    so does this.
    """
    front = foreground(serial)
    if not front.startswith(PACKAGE):
        raise SystemExit("PoolRad is not the foreground app (%s); refusing input"
                         % (front or "nothing focused"))


def shell(serial, script):
    """Plain adb shell, for anything that is not input."""
    return adb(serial, "shell", script)


def send(serial, script):
    """Input, which only ever goes to the app."""
    require_foreground(serial)
    return adb(serial, "shell", script)


def click(serial, x, y, times=1):
    """A real mouse down and up, which a Mac needs and `input tap` fakes badly."""
    press = "input motionevent DOWN %d %d; input motionevent UP %d %d" % (x, y, x, y)
    send(serial, "; ".join([press] * times))


def drag(serial, x1, y1, x2, y2, steps=6):
    """Down, move, up -- how a Mac menu is pulled down."""
    parts = ["input motionevent DOWN %d %d" % (x1, y1)]
    for step in range(1, steps + 1):
        parts.append("input motionevent MOVE %d %d"
                     % (x1 + (x2 - x1) * step // steps, y1 + (y2 - y1) * step // steps))
    parts.append("input motionevent UP %d %d" % (x2, y2))
    send(serial, "; ".join(parts))


def settle(serial, box_text, quiet_ms, timeout_ms, poll_ms):
    """Block until a region has looked the same for `quiet_ms`."""
    deadline = time.monotonic() + timeout_ms / 1000
    last, since = None, time.monotonic()
    while time.monotonic() < deadline:
        screen = grab(serial)
        current = screen.digest(parse_region(box_text, screen))
        now = time.monotonic()
        if current != last:
            last, since = current, now
        elif (now - since) * 1000 >= quiet_ms:
            return last
        time.sleep(poll_ms / 1000)
    return None


def await_change(serial, box_text, before, timeout_ms, poll_ms):
    screen = await_change_screen(serial, box_text, before, timeout_ms, poll_ms)
    return None if screen is None else screen.digest(parse_region(box_text, screen))


def await_change_screen(serial, box_text, before, timeout_ms, poll_ms):
    """As await_change, but hands back the frame that differed.

    A screencap is the expensive part of every scripted step, so a caller that
    wants to look at the rest of that frame should not have to take another one.
    """
    deadline = time.monotonic() + timeout_ms / 1000
    while True:
        screen = grab(serial)
        if screen.digest(parse_region(box_text, screen)) != before:
            return screen
        if time.monotonic() >= deadline:
            return None
        time.sleep(poll_ms / 1000)


def main(argv=None):
    # Shared options are on every subcommand as well as the top level, so
    # `guest.py --serial X settle --region guest` and the other order both work.
    # SUPPRESS, not a default: a subparser that also carries these options would
    # otherwise overwrite whatever the top level already parsed with its own None.
    common = argparse.ArgumentParser(add_help=False)
    keep = argparse.SUPPRESS
    common.add_argument("--serial", default=keep, help="emulator-NNNN only")
    common.add_argument("--region", default=keep, help="x,y,w,h or all/guest/pane/message")
    common.add_argument("--quiet", type=int, default=keep, help="ms a region must hold still")
    common.add_argument("--timeout", type=int, default=keep, help="ms before giving up")
    common.add_argument("--poll", type=int, default=keep, help="ms between screen samples")

    parser = argparse.ArgumentParser(parents=[common], description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    def add(name, *arguments):
        child = sub.add_parser(name, parents=[common])
        for spec in arguments:
            child.add_argument(*spec[0], **spec[1])
        return child

    add("shot", ((("path",), {})))
    add("digest")
    add("ink")
    add("settle")
    add("await-change", ((("before",), {})))
    add("key", ((("names",), {"nargs": "+"})))
    add("type", ((("text",), {})))
    # press/move/release are separate so a menu can be photographed while it is
    # still open: the mouse stays down between two invocations of this tool.
    for name in ("click", "dclick", "press", "move", "release"):
        add(name, ((("x",), {"type": int})), ((("y",), {"type": int})))
    add("drag", *[(((axis,), {"type": int})) for axis in ("x1", "y1", "x2", "y2")])

    args = parser.parse_args(argv)
    for name, fallback in (("serial", None), ("region", None),
                           ("quiet", 900), ("timeout", 60000), ("poll", 250)):
        if not hasattr(args, name):
            setattr(args, name, fallback)
    if not args.serial:
        raise SystemExit("--serial emulator-NNNN is required")
    if not SERIAL.match(args.serial):
        raise SystemExit("Refusing a non-emulator target: %s" % args.serial)

    if args.command == "shot":
        grab(args.serial).png(args.path)
        print("wrote", args.path)
    elif args.command == "digest":
        screen = grab(args.serial)
        print(screen.digest(parse_region(args.region, screen)))
    elif args.command == "ink":
        screen = grab(args.serial)
        print(screen.ink(parse_region(args.region, screen)))
    elif args.command == "settle":
        found = settle(args.serial, args.region, args.quiet, args.timeout, args.poll)
        if found is None:
            raise SystemExit("Still changing after %dms" % args.timeout)
        print(found)
    elif args.command == "await-change":
        found = await_change(args.serial, args.region, args.before, args.timeout, args.poll)
        if found is None:
            raise SystemExit("Unchanged after %dms" % args.timeout)
        print(found)
    elif args.command == "key":
        send(args.serial, "; ".join("input keyevent %s" % k for k in args.names))
    elif args.command == "type":
        send(args.serial, "input text %s" % subprocess.list2cmdline([args.text]))
    elif args.command == "click":
        click(args.serial, args.x, args.y)
    elif args.command == "dclick":
        click(args.serial, args.x, args.y, times=2)
    elif args.command in ("press", "move", "release"):
        motion = {"press": "DOWN", "move": "MOVE", "release": "UP"}[args.command]
        send(args.serial, "input motionevent %s %d %d" % (motion, args.x, args.y))
    elif args.command == "drag":
        drag(args.serial, args.x1, args.y1, args.x2, args.y2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
