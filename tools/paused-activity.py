#!/usr/bin/env python3
"""Read process/thread CPU counters on an emulator; run after backgrounding the app."""
import subprocess, time, json, sys, re
adb='/usr/lib/android-sdk/platform-tools/adb'
pkg='com.hunterdavis.poolradmacmaps.ii'
if len(sys.argv) not in (2,3) or not re.fullmatch(r'emulator-[0-9]+',sys.argv[1]):
    raise SystemExit('usage: paused-activity.py emulator-NNNN [seconds]')
serial=sys.argv[1]
seconds=float(sys.argv[2]) if len(sys.argv)>2 else 10
if not 1 <= seconds <= 60:
    raise SystemExit('measurement must be 1–60 seconds')
def shell(command):
    return subprocess.check_output([adb,'-s',serial,'shell',command],text=True).strip()
pid=shell('pidof '+pkg)
assert pid.isdigit()
def read():
    script=f'cat /proc/{pid}/stat; for f in /proc/{pid}/task/*/stat; do cat "$f"; done'
    output=shell("run-as "+pkg+" sh -c '"+script+"'")
    result={}
    for i,line in enumerate(output.splitlines()):
        a=line.index('('); b=line.rindex(')')
        fields=line[b+2:].split()
        key='process' if i==0 else line[:a].strip()
        result[key]={'name':line[a+1:b],'state':fields[0], 'ticks':int(fields[11])+int(fields[12])}
    for key,val in result.items():
        if val['name']=='EmulationThread':
            val['runtimeNs']=int(shell(f'run-as {pkg} cat /proc/{pid}/task/{key}/schedstat').split()[0])
            status=shell(f'run-as {pkg} cat /proc/{pid}/task/{key}/status')
            val['switches']=sum(int(line.split(':')[1]) for line in status.splitlines()
                                if line.startswith(('voluntary_ctxt_switches:', 'nonvoluntary_ctxt_switches:')))
    return result
before=read(); start=time.monotonic(); time.sleep(seconds); after=read()
print('elapsedSeconds=',round(time.monotonic()-start,2))
for key,val in after.items():
    if key not in before: continue
    delta=val['ticks']-before[key]['ticks']
    if delta or key=='process' or val['name']=='EmulationThread':
        if key != 'process':
            val['wchan']=shell(f'run-as {pkg} cat /proc/{pid}/task/{key}/wchan')
        print(key,json.dumps(val),'deltaTicks=',delta)
        if 'runtimeNs' in val:
            print('deltaRuntimeNs=',val['runtimeNs']-before[key]['runtimeNs'],
                  'deltaContextSwitches=',val['switches']-before[key]['switches'])
