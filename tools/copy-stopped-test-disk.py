#!/usr/bin/env python3
"""Copy disk1 from a stopped disposable emulator, retaining before/after proof.

Requires the app's Restart Emulator screen and no mounted disk descriptors.
Never stops a process, repairs a disk, or overwrites an existing output.
"""
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('serial')
p.add_argument('output', type=Path, help='new private .dsk path')
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial):
    p.error('Disposable emulator only')
proof = Path(str(a.output) + '.json')
if a.output.exists() or proof.exists():
    p.error('Use new output and evidence paths')
adb = Path('/usr/lib/android-sdk/platform-tools/adb')
pkg = 'com.hunterdavis.poolradmacmaps.ii'
env = dict(os.environ, PATH=str(adb.parent) + ':' + os.environ.get('PATH', ''))

def run(*args):
    return subprocess.check_output([str(adb), '-s', a.serial, *args], timeout=30)

def stopped():
    ui = subprocess.check_output(
        ['node', str(Path(__file__).with_name('android-ui.mjs')), a.serial, 'list'],
        text=True, env=env, timeout=45)
    rows = [json.loads(line) for line in ui.splitlines() if line.startswith('{')]
    if not any(row.get('text') == 'Restart Emulator' for row in rows):
        raise RuntimeError('The stopped emulator screen is not visible')
    pid = run('shell', 'pidof', pkg).decode().strip()
    if not re.fullmatch(r'[0-9]+', pid):
        raise RuntimeError('Expected one live app process')
    fds = run('shell', 'run-as', pkg, 'ls', '-l', '/proc/' + pid + '/fd').decode()
    if '/files/disks/' in fds or '.dsk' in fds:
        raise RuntimeError('An emulator disk descriptor is still open')
    return {'pid': pid, 'ui': rows, 'fds': fds}

before = stopped()
raw = run('exec-out', 'run-as', pkg, 'cat', 'files/disks/disk1.dsk')
after = stopped()
if before['pid'] != after['pid'] or raw[1024:1026] != b'BD':
    raise RuntimeError('Process changed or disk is not raw classic HFS')
a.output.parent.mkdir(parents=True, exist_ok=True)
with a.output.open('xb') as out:
    out.write(raw)
now = datetime.now(ZoneInfo('America/Los_Angeles'))
with proof.open('x') as out:
    json.dump({'captured_at': now.isoformat(), 'serial': a.serial,
               'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
               'before': before, 'after': after}, out, indent=2)
    out.write('\n')
print(now.strftime('[%Y-%m-%d %H:%M:%S %Z] ') + 'Copied closed disk: ' + str(a.output))
