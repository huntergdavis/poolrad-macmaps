#!/usr/bin/env python3
"""Load a visible named snapshot through the app and retain its native result.
Disposable emulator only. Fails on ambiguous/missing controls or process changes.
Does not restart the app or edit RAM/disks. The name must be visible in the browser.
"""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('serial')
p.add_argument('name')
p.add_argument('evidence', type=Path)
p.add_argument('--expect', choices=('true', 'false'), required=True)
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial): p.error('Disposable emulator only')
a.evidence.mkdir(parents=True, exist_ok=False)
adb = '/usr/lib/android-sdk/platform-tools/adb'
pkg = 'com.hunterdavis.poolradmacmaps.ii'
env = dict(os.environ, PATH=str(Path(adb).parent) + os.pathsep + os.environ.get('PATH', ''))
ui = Path(__file__).with_name('android-ui.mjs')
def run(*args, binary=False):
    return subprocess.check_output(args, env=env, text=not binary, timeout=35)
def log(message):
    print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ') + message, flush=True)
def tap(label): run('node', str(ui), a.serial, 'tap', label)
def capture(name):
    data = run(adb, '-s', a.serial, 'exec-out', 'screencap', '-p', binary=True)
    if not data.startswith(b'\x89PNG'): raise RuntimeError('Invalid screenshot')
    (a.evidence / (name + '.png')).write_bytes(data)
pid = run(adb, '-s', a.serial, 'shell', 'pidof', pkg).strip()
if not pid.isdigit(): raise RuntimeError('Expected one live app process')
def records():
    text = run(adb, '-s', a.serial, 'logcat', '-d', '--pid=' + pid, '-v', 'time', '-s', 'PoolRad.SaveState')
    return [line for line in text.splitlines() if 'Restore completed:' in line]
before = records()
capture('before')
tap('POOLRAD'); tap('Load…')
rows = [json.loads(line) for line in run('node', str(ui), a.serial, 'list').splitlines()]
matches = [row['text'] for row in rows if row['text'].split('\n')[0] == a.name]
if len(matches) != 1: raise RuntimeError('Expected exactly one visible snapshot named ' + a.name)
tap(matches[0]); capture('confirmation'); tap('LOAD')
deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    if run(adb, '-s', a.serial, 'shell', 'pidof', pkg).strip() != pid:
        raise RuntimeError('The app process changed during the load')
    after = records()
    if after != before:
        result = after[-1]
        (a.evidence / 'result.txt').write_text(result + '\n')
        capture('after')
        if not result.endswith('Restore completed: ' + a.expect):
            raise RuntimeError('Unexpected native result: ' + result)
        log('PASS: ' + a.name + ' native restore result ' + a.expect)
        break
    time.sleep(.5)
else:
    raise RuntimeError('No new native completion observed; guest left running')
