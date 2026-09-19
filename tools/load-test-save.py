#!/usr/bin/env python3
"""Load a named save through the original game's file picker after a cold boot.

Emulator only. Requires tesseract and a booted game with no party loaded.
Uses timed guest keyboard input, preserves screenshots, and stops on mismatch.
Does not restart the guest, edit RAM, write disk files, or overwrite a save.
"""
import argparse
from datetime import datetime
import os
from pathlib import Path
import re
import subprocess
import time
from zoneinfo import ZoneInfo


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('serial')
    p.add_argument('name', help='existing ASCII alphanumeric save name, e.g. ExportProof')
    p.add_argument('evidence', type=Path, help='new evidence directory')
    a = p.parse_args()
    if not re.fullmatch(r'emulator-\d+', a.serial): p.error('Emulator only')
    if not re.fullmatch(r'[A-Za-z0-9]{1,31}', a.name): p.error('Use an alphanumeric save name')
    a.evidence.mkdir(parents=True, exist_ok=False)
    adb = str(Path(os.environ.get('ANDROID_HOME', '/usr/lib/android-sdk')) / 'platform-tools/adb')
    helper = Path(__file__).with_name('guest-command.sh')
    number = 0

    def run(*args, binary=False):
        return subprocess.check_output(args, text=not binary, timeout=35)

    def log(message):
        print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ') + message, flush=True)

    def screen():
        nonlocal number
        number += 1
        image = a.evidence / f'{number:03d}.png'
        image.write_bytes(run(adb, '-s', a.serial, 'exec-out', 'screencap', '-p', binary=True))
        text = "\n".join(run('tesseract', str(image), 'stdout', '--psm', str(mode)) for mode in (3, 11))
        image.with_suffix('.txt').write_text(text)
        return set(re.findall(r'[a-z0-9]+', text.casefold()))

    def wait_for(required, label):
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            words = screen()
            if set(required) <= words: return words
            time.sleep(1)
        raise RuntimeError('Expected screen not found: ' + label)

    words = screen()
    if not {'file', 'edit', 'character', 'windows'} <= words or 'information' in words:
        raise RuntimeError('Expected the original game before a party is loaded')
    if {'cancel', 'desktop', 'eject'} <= words:
        log('Using the already-open game file picker')
    else:
        log('Opening original-game Load')
        run('bash', str(helper), a.serial, 'load')
        words = wait_for(['cancel', 'desktop', 'eject'], 'game file picker')
    if 'poolradsave' not in words:
        raise RuntimeError('Expected PoolRadSave in the visible game folder')
    # At the game root PoolRadSave is a folder row; inside it, the save is visible.
    if a.name.casefold() not in words:
        log('Opening PoolRadSave')
        run('bash', str(helper), a.serial, 'text', 'PoolRadSave')
        words = wait_for(['poolradsave', a.name.casefold(), 'cancel', 'desktop'], 'named save in PoolRadSave')
    log('Loading verified visible save: ' + a.name)
    run('bash', str(helper), a.serial, 'text', a.name)
    wait_for(['information', 'message', 'encamp'], 'loaded party windows')
    log('Original game shows a loaded party; inspect retained screenshots to verify party and location')


if __name__ == '__main__':
    main()
