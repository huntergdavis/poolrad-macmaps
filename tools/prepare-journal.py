#!/usr/bin/env python3
"""Convert the user's supported Mac journal documents into a private .prjr book.

Requires macresources (personal-boot venv) and ImageMagick. No network access.
Original data/resources are read-only; output must not exist. See JOURNAL.md.
"""
import argparse
import hashlib
import io
from pathlib import Path
import re
import struct
import subprocess
import tempfile

from macresources import parse_file
from game_content import apple_double, read_bounded

PROCLAMATIONS = (59, 64, 78, 101, 109, 110, 114, 120, 126, 129, 134, 154, 156, 170, 190, 201, 204, 214)
PROFILE = {
    "Journal Entry 01-33": ("76ac1678120cc680d435c11595046606fe8a6ef669e287efda5cfb03930678fe",
                            "9d260f14f267c177b62c622d8e20e61004b68b32a9f5bce887a2ea5c77f5f3ab"),
    "Journal Entry 34-END": ("330be1b9585f7d66dd7f54afcbacf2e942097c4863896e32084716ad2b67800e",
                             "c52b6ab1f71cc1176bca3ec081235783f2e00cba793105b29e271b96b5d4d334"),
}


def roman(value):
    total = previous = 0
    for c in reversed(value):
        n = dict(I=1, V=5, X=10, L=50, C=100, D=500, M=1000)[c]
        total += -n if n < previous else n
        previous = max(n, previous)
    return total


def parse_documents(documents):
    """Join entry 33 across files; image IDs follow the CEM8 NBSP placeholders."""
    result, current = {}, None
    for text, pictures in documents:
        picture_id = 1000
        for line in text.replace("\r", "\n").splitlines():
            if line.startswith(("-- Page ", "+")):
                continue
            if line.strip() in ("JOURNAL ENTRIES", "TAVERN TALES"):
                current = None
                continue
            start = re.match(r"Journal Entry (\d+):", line, re.I)
            graphic = re.search(r"GRAPHIC: (?:JOURNAL )?ENTRY (\d+)(?:\]|\")", line, re.I)
            proclamation = re.match(r"Proclamation ([IVXLCDM]+)$", line)
            tale = re.match(r"Tale (\d+):\s*(.*)", line)
            key = None
            if start or graphic:
                key = (0, int((start or graphic).group(1)))
            elif "GRAPHIC: A MASSIVE ATLAS" in line:
                key = (0, 37)
            elif proclamation:
                key = (1, roman(proclamation.group(1)))
            elif tale:
                key = (2, int(tale.group(1)))
            if key:
                if key in result:
                    raise ValueError("Duplicate journal heading: " + str(key))
                current = key
                result[key] = []
                if tale:
                    result[key].append((0, tale.group(2)))
                continue_if_heading = bool(start or proclamation or tale)
                if continue_if_heading:
                    continue
            if "\xa0" in line:
                if current is None or line.strip() or picture_id not in pictures:
                    raise ValueError("Unmapped journal picture placeholder")
                result[current].append((1, pictures[picture_id]))
                picture_id += 1
            elif current is not None:
                # The PCX part-3 label repeats the Phlan picture already above it.
                # Render readable captions, not legacy document markup.
                if "<<" in line:
                    if "JOURNAL ENTRY 37 PART 3" in line:
                        continue
                    caption = re.search(r'GRAPHIC:\s*([^\]>"\n]+)', line, re.I)
                    if not caption:
                        raise ValueError("Unrecognized illustration marker")
                    line = caption.group(1).strip().capitalize()
                result[current].append((0, line))
        if picture_id - 1000 != len(pictures):
            raise ValueError("Unused/missing original illustrations")
    expected = {(0, n) for n in range(1, 59)} | {(1, n) for n in PROCLAMATIONS} | {(2, n) for n in range(1, 24)}
    if set(result) != expected:
        raise ValueError("Journal entry set mismatch: " + str(expected ^ set(result)))
    # Merge adjacent lines without reordering paragraphs or illustrations.
    for key, blocks in result.items():
        merged = []
        for kind, value in blocks:
            if kind == 0 and merged and merged[-1][0] == 0:
                merged[-1] = (0, merged[-1][1] + "\n" + value)
            else:
                merged.append((kind, value))
        result[key] = [(k, v.strip() if k == 0 else v) for k, v in merged if k or v.strip()]
    return result


def encode(entries):
    out = io.BytesIO()
    out.write(b"PRJR\x01")
    def blob(value):
        out.write(struct.pack(">I", len(value)))
        out.write(value)
    blob(b"User-supplied Macintosh CEM8 journal; SSI Pool of Radiance")
    out.write(struct.pack(">H", len(entries)))
    for (kind, number), blocks in sorted(entries.items()):
        out.write(struct.pack(">BHH", kind, number, len(blocks)))
        for block_kind, value in blocks:
            out.write(bytes([block_kind]))
            blob(value.encode("utf-8") if block_kind == 0 else value)
    return out.getvalue()


def build(source):
    documents = []
    with tempfile.TemporaryDirectory(prefix="poolrad-journal-") as temp:
        for name, expected in PROFILE.items():
            data = read_bounded(source / name)
            resource = apple_double(read_bounded(source / (name + ".rsrc")))[2]
            if tuple(hashlib.sha256(b).hexdigest() for b in (data, resource)) != expected:
                raise ValueError("Unsupported/changed journal document: " + name)
            pictures = {}
            for r in parse_file(resource):
                if r.type != b"PICT":
                    continue
                path = Path(temp) / "picture.pict"
                path.write_bytes(bytes(512) + bytes(r.data))
                png = subprocess.check_output(["magick", str(path), "-strip", "png:-"], timeout=15)
                pictures[r.id] = png
            documents.append((data.decode("mac_roman"), pictures))
    return encode(parse_documents(documents))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Fork-preserving extracted Pool Of Radiance folder")
    parser.add_argument("output", type=Path, help="NEW private .prjr file; do not publish")
    args = parser.parse_args()
    if args.output.exists() or args.output.is_symlink():
        parser.error("Refusing to overwrite an existing journal package")
    data = build(args.source)
    with args.output.open("xb") as out:
        out.write(data)
    print(f"Prepared 99 entries and 14 original illustrations: {args.output} ({len(data)} bytes)")
    print("Private user-supplied reference: import through Info > Journal; never publish this file.")


if __name__ == "__main__":
    main()
