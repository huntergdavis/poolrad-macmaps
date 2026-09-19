#!/usr/bin/env python3
"""Inspect a stopped/private raw HFS image without repair.

Requires an Apple-derived fsck_hfs / fsck.hfsplus executable. Always passes -fn
and checks the image checksum again afterwards. A baseline separates existing
checker findings from newly observed diagnostics; equality is not a proof that
all possible corruption is absent. Never copy a live mounted image for this check.
"""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
from zoneinfo import ZoneInfo


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('output', type=Path, help='New JSON evidence file')
    parser.add_argument('--checker', type=Path, required=True)
    parser.add_argument('--baseline', type=Path)
    args = parser.parse_args()
    if args.output.exists() or Path(str(args.output) + '.log').exists():
        parser.error('Use a new output path')
    if not args.image.is_file() or not args.checker.is_file():
        parser.error('Expected an existing image and filesystem checker')
    with args.image.open('rb') as stream:
        stream.seek(1024)
        mdb = stream.read(512)
    if mdb[:2] != b'BD':
        parser.error('Expected a raw classic HFS image')
    before = digest(args.image)
    run = subprocess.run([str(args.checker.resolve()), '-fn', str(args.image.resolve())],
                         text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         timeout=90)
    after = digest(args.image)
    if before != after:
        raise RuntimeError('The supposedly read-only check changed the image')
    # Preserve every substantive diagnostic, including numeric record details.
    # Paths and routine checker phases are retained in the complete companion log.
    diagnostics = Counter()
    for line in run.stdout.splitlines():
        value = line.strip()
        if not value or value.startswith('Executing fsck_hfs'):
            continue
        if value.startswith('** ' + str(args.image.resolve())):
            continue
        if value.startswith('The volume name is '):
            continue
        diagnostics[value] += 1
    result = {
        'captured_at': datetime.now(ZoneInfo('America/Los_Angeles')).isoformat(),
        'image': str(args.image),
        'sha256': before,
        'bytes': args.image.stat().st_size,
        'unchanged_by_checker': True,
        'checker_exit': run.returncode,
        'checker_sha256': digest(args.checker),
        'clean_unmount_flag': bool(struct.unpack_from('>H', mdb, 10)[0] & 0x0100),
        'diagnostics': dict(sorted(diagnostics.items())),
    }
    if args.baseline:
        baseline = json.loads(args.baseline.read_text())
        previous = Counter(baseline['diagnostics'])
        result['baseline'] = str(args.baseline)
        result['added_diagnostics'] = dict(sorted((diagnostics - previous).items()))
        result['removed_diagnostics'] = dict(sorted((previous - diagnostics).items()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    Path(str(args.output) + '.log').write_text(run.stdout)
    timestamp = datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z]')
    print(f'{timestamp} HFS checker exit={run.returncode}; '
          f'clean-unmount={result["clean_unmount_flag"]}; image unchanged by check')
    if args.baseline:
        print(f'{timestamp} Added diagnostics: {result["added_diagnostics"]}')
    print(f'{timestamp} Evidence: {args.output}')


if __name__ == '__main__':
    main()
