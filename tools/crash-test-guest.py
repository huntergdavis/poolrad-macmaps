#!/usr/bin/env python3
"""Kill only PoolRad's process on a disposable emulator for an explicit crash audit.

Requires an open private disk1.dsk and records the exact process/descriptor
evidence first. Copies the disk after process death; never restarts or repairs it.
This is a crash-test tool, not an update or ordinary shutdown helper.
"""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
from zoneinfo import ZoneInfo


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('serial')
    p.add_argument('evidence', type=Path, help='New private evidence directory')
    a = p.parse_args()
    if not re.fullmatch(r'emulator-\d+', a.serial):
        p.error('Disposable emulator only')
    a.evidence.mkdir(parents=True, exist_ok=False)
    adb = '/usr/lib/android-sdk/platform-tools/adb'
    pkg = 'com.hunterdavis.poolradmacmaps.ii'

    def run(*args, check=True):
        return subprocess.run([adb, '-s', a.serial, *args], check=check,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    def current_pid():
        found = run('shell', 'pidof', pkg, check=False)
        if found.returncode not in (0, 1) or found.stderr.strip():
            raise RuntimeError('Could not verify whether PoolRad is running')
        value = found.stdout.decode().strip()
        if found.returncode == 1 and value:
            raise RuntimeError('Unexpected process-poll result')
        return value
    def log(message):
        print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ')
              + message, flush=True)

    pid = current_pid()
    if not pid.isdigit():
        raise RuntimeError('Expected exactly one live PoolRad process')
    command = run('shell', 'run-as', pkg, 'cat', '/proc/' + pid + '/cmdline').stdout.split(b'\0')[0]
    if command.decode() != pkg:
        raise RuntimeError('The PID does not belong to PoolRad')
    descriptors = run('shell', 'run-as', pkg, 'ls', '-l', '/proc/' + pid + '/fd').stdout.decode()
    if '/files/disks/disk1.dsk' not in descriptors:
        raise RuntimeError('No mounted private disk1.dsk: this would not exercise a guest crash')
    (a.evidence / 'open-descriptors.txt').write_text(descriptors)
    screen = run('exec-out', 'screencap', '-p').stdout
    if not screen.startswith(b'\x89PNG'):
        raise RuntimeError('Could not capture the pre-crash screen')
    (a.evidence / 'before.png').write_bytes(screen)
    if current_pid() != pid:
        raise RuntimeError('The process changed before the crash request')
    log('Force-stopping the verified disposable PoolRad process ' + pid)
    run('shell', 'am', 'force-stop', pkg)
    for _ in range(40):
        if not current_pid():
            break
        time.sleep(.25)
    else:
        raise RuntimeError('PoolRad is still running; no disk copy taken')
    raw = run('exec-out', 'run-as', pkg, 'cat', 'files/disks/disk1.dsk').stdout
    if current_pid():
        raise RuntimeError('PoolRad restarted while copying; discard this observation')
    if len(raw) < 4096 or raw[1024:1026] != b'BD':
        raise RuntimeError('Expected a complete raw classic HFS disk')
    (a.evidence / 'disk.dsk').write_bytes(raw)
    record = {
        'at': datetime.now(ZoneInfo('America/Los_Angeles')).isoformat(),
        'serial': a.serial, 'killed_pid': int(pid), 'action': 'Android am force-stop',
        'stopped_before_and_after_copy': True,
        'disk_bytes': len(raw), 'disk_sha256': hashlib.sha256(raw).hexdigest(),
    }
    (a.evidence / 'crash.json').write_text(json.dumps(record, indent=2) + '\n')
    log('Process death verified; copied the stopped disk to ' + str(a.evidence / 'disk.dsk'))


if __name__ == '__main__':
    main()
