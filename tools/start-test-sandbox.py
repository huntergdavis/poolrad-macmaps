#!/usr/bin/env python3
"""Start a fresh, disposable Android emulator and seed copies of private inputs.

The destination must not exist. Never replaces a running app or mounted disk.
Private inputs and emulator data remain outside Git. Requires an existing AVD.
"""
import argparse
import os
from pathlib import Path
import subprocess
import struct
import xml.etree.ElementTree as ET
import time


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--avd', required=True)
    p.add_argument('--port', type=int, required=True)
    p.add_argument('--directory', type=Path, required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--rom', type=Path, required=True)
    p.add_argument('--disk', type=Path, required=True)
    a=p.parse_args()
    sdk=Path(os.environ.get('ANDROID_HOME','/usr/lib/android-sdk'))
    adb=str(sdk/'platform-tools/adb'); serial=f'emulator-{a.port}'
    if a.port < 5554 or a.port > 5682 or a.port % 2:
        p.error('Use an even emulator port between 5554 and 5682')
    for f in (a.apk,a.rom,a.disk):
        if not f.is_file(): p.error(f'Missing input: {f}')
    # Match the app's Mac II ROM identities before seeding first-run preferences.
    rom = a.rom.read_bytes()
    known = {0x97851DB6: 'Macintosh II (800k v1)', 0x9779D2C4: 'Macintosh II (800k v2)'}
    if len(rom) != 0x40000:
        p.error('Expected a complete Macintosh II ROM')
    checksum = int.from_bytes(rom[:4], 'big')
    if checksum not in known or sum(struct.unpack('>131070H', rom[4:])) != checksum:
        p.error('Macintosh II ROM checksum is not valid')
    if serial in subprocess.check_output([adb,'devices'],text=True):
        p.error('Emulator port is already in use')
    directory=a.directory.resolve(); directory.mkdir(parents=True,exist_ok=False)
    with (directory/'emulator.log').open('wb') as log:
        proc=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',a.avd,'-port',str(a.port),
            '-datadir',str(directory),'-no-window','-no-audio','-no-snapshot',
            '-no-boot-anim','-gpu','swiftshader','-memory','2048'],
            stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    (directory/'sandbox.pid').write_text(str(proc.pid)+'\n')
    print(f'Started {serial}; log: {directory}/emulator.log',flush=True)
    def run(*args):
        return subprocess.check_output([adb,'-s',serial,*args],text=True,timeout=90).strip()
    for _ in range(120):
        if proc.poll() is not None: raise SystemExit('Emulator exited; inspect emulator.log')
        try:
            if run('shell','getprop','sys.boot_completed') == '1': break
        except subprocess.CalledProcessError: pass
        time.sleep(2)
    else: raise SystemExit('Android boot timed out; sandbox preserved for inspection')
    print(run('install',str(a.apk.resolve())),flush=True)
    pkg='com.hunterdavis.poolradmacmaps.ii'
    run('shell','wm','size','1200x1600'); run('shell','wm','density','200')
    run('push',str(a.rom.resolve()),'/data/local/tmp/poolrad-test.rom')
    run('push',str(a.disk.resolve()),'/data/local/tmp/poolrad-test.dsk')
    run('shell','run-as',pkg,'mkdir','-p','files/rom','files/disks')
    run('shell','run-as',pkg,'cp','/data/local/tmp/poolrad-test.rom','files/rom/MacII.ROM')
    run('shell','run-as',pkg,'cp','/data/local/tmp/poolrad-test.dsk','files/disks/disk1.dsk')
    run('shell','rm','/data/local/tmp/poolrad-test.rom','/data/local/tmp/poolrad-test.dsk')
    # WelcomeFragment requires selection metadata, even when the ROM file exists.
    # Seed only this new disposable app before its first launch.
    settings = ET.Element('map')
    ET.SubElement(settings, 'string', name='pref_rom').text = known[checksum]
    ET.SubElement(settings, 'string', name='pref_rom_file').text = 'MacII.ROM'
    ET.SubElement(settings, 'long', name='pref_rom_checksum', value=str(checksum))
    preferences = directory / 'sandbox-preferences.xml'
    ET.ElementTree(settings).write(preferences, encoding='utf-8', xml_declaration=True)
    run('push', str(preferences), '/data/local/tmp/poolrad-test-preferences.xml')
    run('shell', 'run-as', pkg, 'mkdir', '-p', 'shared_prefs')
    run('shell', 'run-as', pkg, 'cp', '/data/local/tmp/poolrad-test-preferences.xml',
        'shared_prefs/' + pkg + '_preferences.xml')
    run('shell', 'rm', '/data/local/tmp/poolrad-test-preferences.xml')
    run('shell', 'am', 'start', '-W', '-n', pkg + '/name.osher.gil.minivmac.MiniVMac')
    print(f'Ready: {serial}. Guest disk is a disposable copy. Shut down the Mac normally before updates.',flush=True)


if __name__=='__main__': main()
