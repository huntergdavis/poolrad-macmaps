#!/usr/bin/env python3
"""Capture current emulator screenshots and enforce README image freshness."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / 'docs/images/readme-screenshots.json'
PACKAGE = 'com.hunterdavis.poolradmacmaps.ii'


def run(*args):
    return subprocess.check_output(args, cwd=ROOT)


def version(value):
    if not re.fullmatch(r'\d+\.\d+\.\d+', value):
        raise ValueError(f'Invalid release version: {value}')
    return tuple(map(int, value.split('.')))


def images(text):
    return set(re.findall(r'<img\b[^>]*\bsrc=["\x27]([^"\x27]+)', text)) | set(
        re.findall(r'!\[[^\]]*\]\(([^\s)]+)(?:\s+[^)]*)?\)', text))


def check(target):
    version(target)
    tags = run('git', 'tag', '--list', 'v*').decode().splitlines()
    releases = {t[1:] for t in tags if re.fullmatch(r'v\d+\.\d+\.\d+', t)}
    releases.add(target)
    ordered = sorted(releases, key=version)
    metadata = json.loads(MANIFEST.read_text())
    linked = images((ROOT / 'README.md').read_text())
    errors = []
    for name in sorted(linked):
        record = metadata.get(name)
        path = ROOT / name
        if not record or not path.is_file():
            errors.append(f'{name}: capture metadata or file missing')
            continue
        captured = record['release']
        if captured not in releases or version(captured) > version(target):
            errors.append(f'{name}: invalid capture release {captured}')
            continue
        age = ordered.index(target) - ordered.index(captured)
        if age > 3:
            errors.append(f'{name}: {age} releases old; recapture or remove from README')
        if hashlib.sha256(path.read_bytes()).hexdigest() != record['sha256']:
            errors.append(f'{name}: image changed since capture was recorded')
    if not linked:
        # A README that deliberately carries no screenshots has nothing to go
        # stale; say so rather than blocking the release.
        print('README: no screenshots linked; nothing to check for staleness')
        return
    if errors:
        raise ValueError('\n'.join(errors))
    print(f'README: {len(linked)} screenshots verified, each at most 3 releases old for {target}')


def capture(serial, name, adb):
    if not re.fullmatch(r'emulator-\d+', serial):
        raise ValueError('Capture only from a disposable Android emulator')
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]*', name):
        raise ValueError('Use a lowercase hyphenated image name without an extension')
    info = run(adb, '-s', serial, 'shell', 'dumpsys', 'package', PACKAGE).decode()
    match = re.search(r'versionName=(\d+\.\d+\.\d+)\s', info)
    if not match:
        raise ValueError('Cannot read the installed app version')
    release = match.group(1)
    run('git', 'rev-parse', '--verify', f'refs/tags/v{release}')
    resumed = run(adb, '-s', serial, 'shell', 'dumpsys', 'activity', 'activities').decode()
    if not any(PACKAGE in line and ('mResumedActivity' in line or 'topResumedActivity' in line)
               for line in resumed.splitlines()):
        raise ValueError('PoolRad must be the foreground activity')
    png = run(adb, '-s', serial, 'exec-out', 'screencap', '-p')
    if not png.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('Device did not return a PNG')
    relative = f'docs/images/{name}.png'
    (ROOT / relative).write_bytes(png)
    metadata = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
    metadata[relative] = dict(release=release, sha256=hashlib.sha256(png).hexdigest(),
                             captured_at=datetime.now(timezone.utc).isoformat())
    MANIFEST.write_text(json.dumps(metadata, indent=2, sort_keys=True) + '\n')
    print(f'Captured {relative} from installed v{release}; visually inspect before publishing')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    checking = commands.add_parser('check')
    checking.add_argument('release', help='current or next release version')
    capturing = commands.add_parser('capture')
    capturing.add_argument('serial')
    capturing.add_argument('name')
    capturing.add_argument('--adb', default='/usr/lib/android-sdk/platform-tools/adb')
    args = parser.parse_args()
    try:
        if args.command == 'check':
            check(args.release)
        else:
            capture(args.serial, args.name, args.adb)
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))


if __name__ == '__main__':
    main()
