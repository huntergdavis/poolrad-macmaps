#!/usr/bin/env python3
"""Trim an Android screencap to the app itself.

The status bar at the top and the navigation bar at the bottom say nothing
about the app and make every screenshot in the README a different shape.
Cropping them off by hand, once per shot, is how they end up inconsistent.

    tools/crop-shot.py in.png out.png [--top 40] [--bottom 60]
"""
import sys
from PIL import Image

def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    source, target = argv[1], argv[2]
    top, bottom = 40, 60
    for i, arg in enumerate(argv):
        if arg == "--top": top = int(argv[i + 1])
        if arg == "--bottom": bottom = int(argv[i + 1])
    image = Image.open(source)
    width, height = image.size
    image.crop((0, top, width, height - bottom)).save(target, optimize=True)
    print(f"{target} {Image.open(target).size[0]}x{Image.open(target).size[1]}")

if __name__ == "__main__":
    main(sys.argv)
