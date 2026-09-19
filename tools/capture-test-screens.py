#!/usr/bin/env python3
"""Capture a short burst of disposable-emulator screens into a new private folder."""
import argparse
from datetime import datetime
from pathlib import Path
import re
import subprocess
import time
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('serial')
p.add_argument('output', type=Path)
p.add_argument('--count', type=int, default=8)
p.add_argument('--interval', type=float, default=.25)
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial) or not 1 <= a.count <= 30 or not 0 <= a.interval <= 5:
    p.error('Expected a disposable emulator and bounded capture count/interval')
a.output.mkdir(parents=True, exist_ok=False)
for i in range(a.count):
    raw = subprocess.check_output(['/usr/lib/android-sdk/platform-tools/adb', '-s', a.serial,
                                   'exec-out', 'screencap', '-p'], timeout=15)
    if not raw.startswith(b'\x89PNG'):
        raise RuntimeError('Screenshot was not a PNG')
    (a.output / (str(i) + '.png')).write_bytes(raw)
    if i + 1 < a.count:
        time.sleep(a.interval)
print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ')
      + str(a.count) + ' test screens saved to ' + str(a.output))
