#!/usr/bin/env python3
"""Set a companion Options checkbox through visible controls on a test emulator.
Start on the companion Info tab; stops after bounded scrolling or ambiguity.
"""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
from zoneinfo import ZoneInfo

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('serial')
p.add_argument('label')
p.add_argument('value',choices=['true','false'])
a=p.parse_args()
if not re.fullmatch(r'emulator-\d+',a.serial):p.error('Disposable emulator only')
adb='/usr/lib/android-sdk/platform-tools/adb'
env=dict(os.environ,PATH=str(Path(adb).parent)+os.pathsep+os.environ.get('PATH',''))
base=['node',str(Path(__file__).with_name('android-ui.mjs')),a.serial]
def rows():return [json.loads(x) for x in subprocess.check_output(base+['list'],env=env,text=True,timeout=40).splitlines()]
def tap(label):subprocess.run(base+['tap',label],env=env,check=True,timeout=40)
tap('Options')
for attempt in range(10):
    found=[r for r in rows() if r['text']==a.label and r['checkable']=='true']
    if found:
        if len(found)!=1:raise RuntimeError('Ambiguous checkbox')
        before=found[0]['checked']
        if before!=a.value:tap(a.label)
        final=next(r for r in rows() if r['text']==a.label)
        if final['checked']!=a.value:raise RuntimeError('Checkbox did not reach requested state')
        print(datetime.now(ZoneInfo('America/Los_Angeles')).strftime('[%Y-%m-%d %H:%M:%S %Z] ')
              +a.label+': '+before+' -> '+a.value,flush=True)
        tap('CLOSE')
        break
    subprocess.run([adb,'-s',a.serial,'shell','input','swipe','600','505','600','260','450'],check=True,timeout=15)
else:raise RuntimeError('Checkbox not found within bounded Options scrolling')
