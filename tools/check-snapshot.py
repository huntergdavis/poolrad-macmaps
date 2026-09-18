#!/usr/bin/env python3
"""Refuse a RAM snapshot that came back empty.

A snapshot taken while the emulator core is restarting is the right size and
almost entirely zeros, and it looks exactly like a real one until something
tries to read it -- which is a confusing half hour. The Macintosh keeps the
running application's globals pointer in low memory at 0x904, so an
implausible value there, or a memory image with almost nothing in it, means
try again once the game has settled.
"""
import struct
import sys

def main(path):
    data = open(path, "rb").read()
    if len(data) < 0x908:
        sys.exit("Snapshot is too small to be a Macintosh memory image")
    a5 = struct.unpack_from(">I", data, 0x904)[0] & 0x00FFFFFF
    live = sum(1 for i in range(0, len(data), 4096) if data[i:i + 4096].strip(b"\x00"))
    if a5 in (0, 0xFFFFFF) or a5 > len(data) or live < 100:
        sys.exit(f"Snapshot looks empty: A5={a5:#x}, {live} pages with anything in them. "
                 "The core was probably restarting; try again once the game is settled.")
    print(f"  A5={a5:#x}, {live} pages in use")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else sys.exit(__doc__))
