#!/usr/bin/env python3
"""Update a disposable emulator app after a verified normal guest shutdown.

Usage: update-test-app.py emulator-NNNN UNIVERSAL_APK SAVE_NAME NEW_EVIDENCE_DIR
Keeps shutdown/load screenshots. Never force-stops, kills, resets, or patches RAM.
Requires a running game that the normal shutdown helper can quit.
"""
import argparse
from datetime import datetime
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
from zoneinfo import ZoneInfo


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial')
    parser.add_argument('apk', type=Path)
    parser.add_argument('save')
    parser.add_argument('evidence', type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Disposable Android emulator only')
    if not re.fullmatch(r'[A-Za-z0-9]{1,31}', args.save):
        parser.error('Save name must be ASCII alphanumeric')
    apk = args.apk.resolve()
    if not apk.is_file() or apk.suffix != '.apk':
        parser.error('Expected an existing built APK')
    sdk = Path(os.environ.get('ANDROID_HOME', '/usr/lib/android-sdk'))
    adb = str(sdk / 'platform-tools/adb')
    helpers = Path(__file__).resolve().parent
    args.evidence.mkdir(parents=True, exist_ok=False)
    package = 'com.hunterdavis.poolradmacmaps.ii'

    def log(message):
        print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ')
              + message, flush=True)

    def run(*command, timeout=300):
        subprocess.run(command, check=True, timeout=timeout)

    run('bash', str(helpers / 'check-apk.sh'), str(apk))
    with apk.open('rb') as stream:
        checksum = hashlib.file_digest(stream, 'sha256').hexdigest()
    (args.evidence / 'apk-sha256.txt').write_text(checksum + '  ' + apk.name + '\n')
    log('Shutting down the disposable guest normally')
    run(sys.executable, str(helpers / 'shutdown-test-guest.py'), args.serial,
        str(args.evidence / 'shutdown'))
    log('Installing the checked APK after the disk descriptors closed')
    run(adb, '-s', args.serial, 'install', '-r', str(apk), timeout=90)
    log('Starting the app and waiting for the original game')
    run(adb, '-s', args.serial, 'shell', 'monkey', '-p', package,
        '-c', 'android.intent.category.LAUNCHER', '1', timeout=30)
    run(sys.executable, str(helpers / 'load-test-save.py'), args.serial, args.save,
        str(args.evidence / 'load'))
    log('Update/load workflow completed; retained screenshots need feature-specific verification')


if __name__ == '__main__':
    main()
