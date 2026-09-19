#!/usr/bin/env python3
"""Cold-launch PoolRad only from its stopped guest screen with no open disks."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('serial')
p.add_argument('evidence', type=Path)
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial): p.error('Disposable emulator only')
a.evidence.mkdir(parents=True, exist_ok=False)
adb = '/usr/lib/android-sdk/platform-tools/adb'
pkg = 'com.hunterdavis.poolradmacmaps.ii'
base = [adb, '-s', a.serial]
env = dict(os.environ, PATH=str(Path(adb).parent) + os.pathsep + os.environ.get('PATH', ''))
def run(*args):
    return subprocess.check_output(base + list(args), text=True, timeout=40)
ui = subprocess.check_output(['node', str(Path(__file__).with_name('android-ui.mjs')), a.serial, 'list'],
                             env=env, text=True, timeout=40)
rows = [json.loads(line) for line in ui.splitlines()]
if not any(row.get('text') == 'Restart Emulator' for row in rows):
    raise RuntimeError('Expected the stopped guest screen')
pid = run('shell', 'pidof', pkg).strip()
if not pid.isdigit(): raise RuntimeError('Expected one app process')
fds = run('shell', 'run-as', pkg, 'ls', '-l', '/proc/' + pid + '/fd')
if '/files/disks/' in fds or '.dsk' in fds:
    raise RuntimeError('A disk descriptor remains open')
if run('shell', 'pidof', pkg).strip() != pid:
    raise RuntimeError('The app process changed')
(a.evidence / 'closed-descriptors.txt').write_text(fds)
(a.evidence / 'stopped-ui.json').write_text(json.dumps(rows, indent=2))
run('shell', 'am', 'force-stop', pkg)
stopped = subprocess.run(base + ['shell', 'pidof', pkg], capture_output=True)
if stopped.returncode != 1 or stopped.stdout.strip() or stopped.stderr.strip():
    raise RuntimeError('Could not verify Android process death')
result = run('shell', 'am', 'start', '-W', '-n', pkg + '/name.osher.gil.minivmac.MiniVMac')
(a.evidence / 'launch.txt').write_text(result)
print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ')
      + 'Cold launch after verified guest shutdown; previous PID ' + pid, flush=True)
print(result)
