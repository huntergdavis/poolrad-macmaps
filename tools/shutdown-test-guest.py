#!/usr/bin/env python3
"""Normally quit the game and shut down the Mac on a disposable test emulator.

Requires tesseract. Finds visible labels; refuses unexpected screens. Retains
screenshots/OCR evidence and verifies the stopped UI and closed disk handles.
Never force-stops an app, kills a core, or changes a disk image.
"""
import argparse
import csv
from datetime import datetime
import io
import os
from pathlib import Path
import re
import subprocess
import time
from zoneinfo import ZoneInfo


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('serial'); p.add_argument('evidence', type=Path)
    a = p.parse_args()
    if not re.fullmatch(r'emulator-\d+', a.serial): p.error('Emulator only')
    a.evidence.mkdir(parents=True, exist_ok=False)
    adb = str(Path(os.environ.get('ANDROID_HOME', '/usr/lib/android-sdk')) / 'platform-tools/adb')
    pkg = 'com.hunterdavis.poolradmacmaps.ii'
    def run(*args, binary=False):
        return subprocess.check_output(args, timeout=30, text=not binary)
    def device(*args, binary=False): return run(adb, '-s', a.serial, *args, binary=binary)
    def log(message):
        print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ') + message, flush=True)
    number = 0
    def words(mode):
        nonlocal number
        number += 1; path = a.evidence / f'{number:03d}.png'
        path.write_bytes(device('exec-out', 'screencap', '-p', binary=True))
        output = run('tesseract', str(path), 'stdout', '--psm', str(mode), 'tsv')
        path.with_suffix('.tsv').write_text(output)
        rows = [r for r in csv.DictReader(io.StringIO(output), delimiter='\t') if r['text'].strip()]
        return rows
    def find(rows, phrase):
        wanted = phrase.casefold().split()
        for i in range(len(rows) - len(wanted) + 1):
            group = rows[i:i + len(wanted)]
            if [r['text'].casefold() for r in group] != wanted: continue
            if len({(r['block_num'], r['par_num'], r['line_num']) for r in group}) != 1: continue
            left = min(int(r['left']) for r in group)
            top = min(int(r['top']) for r in group)
            right = max(int(r['left']) + int(r['width']) for r in group)
            bottom = max(int(r['top']) + int(r['height']) for r in group)
            return ((left + right) // 2, (top + bottom) // 2)
        return None
    def wait_for(phrase, mode):
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            rows = words(mode); point = find(rows, phrase)
            if point is not None: return rows, point
            time.sleep(1)
        raise RuntimeError('Expected screen not found: ' + phrase)
    def command(action, *args):
        run('bash', str(Path(__file__).with_name('guest-command.sh')), a.serial, action, *args)
    rows = words(3)
    if find(rows, 'Restart Emulator') is None:
        log('Requesting normal game quit')
        command('quit')
        wait_for('Do you really want to quit?', 11)
        command('text', '')  # Return: normal dialog acceptance, never a guest-memory write.
        rows, special = wait_for('Special', 11)
        if not all(find(rows, word) for word in ('File', 'Edit', 'Label')):
            raise RuntimeError('Finder menu could not be verified')
        log('Opening Finder Special menu')
        device('shell', 'input', 'motionevent', 'DOWN', *map(str, special))
        release = special
        try:
            _, release = wait_for('Shut Down', 3)
            device('shell', 'input', 'motionevent', 'MOVE', *map(str, release))
        finally:
            device('shell', 'input', 'motionevent', 'UP', *map(str, release))
        log('Waiting for the stopped emulator screen')
        wait_for('Restart Emulator', 3)
    pid = device('shell', 'pidof', pkg).strip()
    if not pid.isdecimal(): raise RuntimeError('Expected one application process')
    descriptors = device('shell', 'run-as', pkg, 'ls', '-l', f'/proc/{pid}/fd')
    (a.evidence / 'closed-disk-check.txt').write_text(descriptors)
    if '/files/disks/' in descriptors or '.dsk' in descriptors:
        raise RuntimeError('A disk image is still open')
    log('Mac shut down normally; stopped screen verified; no disk image descriptor remains')


if __name__ == '__main__': main()
