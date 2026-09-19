#!/usr/bin/env python3
"""Verify a PRQS4 snapshot against the one-disk crash-test-guest.py evidence.

Read-only: refuses missing process-death evidence, changed evidence bytes,
multiple drives, and any disk fingerprint mismatch. Does not start the guest.
"""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import struct
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('snapshot', type=Path)
p.add_argument('audit', type=Path)
a = p.parse_args()
record = json.loads((a.audit / 'crash.json').read_text())
if record.get('stopped_before_and_after_copy') is not True:
    p.error('The audit does not prove process death around disk copying')
with a.snapshot.open('rb') as stream:
    header = stream.read(49)
if len(header) != 49 or header[:7] != b'PRQS4\n\x01' or header[7:9] != b'\x00\x01':
    p.error('Expected current PRQS4 with exactly one writable drive in slot zero')
disk = (a.audit / 'disk.dsk').read_bytes()
digest = hashlib.sha256(disk).digest()
if len(disk) != record.get('disk_bytes') or digest.hex() != record.get('disk_sha256'):
    p.error('The disk no longer matches its stopped-process audit')
if len(disk) != struct.unpack_from('>Q', header, 9)[0] or digest != header[17:49]:
    p.error('The snapshot disk fingerprint differs from the stopped disk')
print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ')
      + 'PASS: snapshot exactly matches the verified stopped disk; SHA-256 ' + digest.hex())
