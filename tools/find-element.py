#!/usr/bin/env python3
"""Print the centre of the on-screen element whose text or description contains a phrase.

    tools/find-element.py ui.xml "Load a saved game"    -> "312 745"

Reads a `uiautomator dump`. Exits 1 with nothing printed when no element matches,
so a caller cannot mistake "not there" for a coordinate.
"""
import re
import sys


def main(path, phrase):
    wanted = phrase.lower()
    xml = open(path, errors="replace").read()
    # An exact match beats a substring one. Asking for "LOAD" on a dialog whose
    # title is "Load SampleParty?" must press the button, not tap the title --
    # which is exactly what happened once, and the sequence under test never
    # started while every trace said it had.
    exact, loose = None, None
    for node in re.finditer(r"<node [^>]*>", xml):
        attrs = dict(re.findall(r'(\w[\w-]*)="([^"]*)"', node.group(0)))
        text = attrs.get("text", "").strip().lower()
        desc = attrs.get("content-desc", "").strip().lower()
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        centre = ((x1 + x2) // 2, (y1 + y2) // 2)
        if wanted in (text, desc):
            exact = centre
            break
        if loose is None and (wanted in text or wanted in desc):
            loose = centre
    hit = exact or loose
    if hit is None:
        return 1
    print(*hit)
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
