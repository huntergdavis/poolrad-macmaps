#!/usr/bin/env node
// Verify the distributable APK, not an emulator cache or an intermediate build.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const apk = resolve(process.argv[2] ?? `${root}android/minivmac/build/outputs/apk/macII/debug/minivmac-macII-universal-debug.apk`);
const entries = execFileSync('unzip', ['-Z1', apk], { encoding: 'utf8' }).trim().split('\n');
const expected = ['esp', 'det'].flatMap(ring => Array.from({ length: 36 }, (_, i) => `${ring}${String(i + 1).padStart(2, '0')}.gif`));
assert.deepEqual(entries.filter(name => name.startsWith('assets/codewheel/')).sort(), expected.map(name => `assets/codewheel/${name}`).sort());
let total = 0;
for (const name of expected) {
    const bundled = execFileSync('unzip', ['-p', apk, `assets/codewheel/${name}`]);
    const original = readFileSync(`${root}android/minivmac/src/main/assets/codewheel/${name}`);
    assert.deepEqual(bundled, original, `${name}: bundled artwork differs`);
    assert.match(bundled.toString('ascii', 0, 6), /^GIF8[79]a$/);
    assert.ok(bundled.length >= 10 && bundled.length <= 32768);
    assert.ok(bundled.readUInt16LE(6) > 0 && bundled.readUInt16LE(6) <= 256);
    assert.ok(bundled.readUInt16LE(8) > 0 && bundled.readUInt16LE(8) <= 256);
    total += bundled.length;
}
assert.ok(!entries.some(name => /\.(rom|dsk|dax|ram|sit|probe|prjr)$/i.test(name)), 'Private game inputs and journal books must not be bundled');
assert.ok(!entries.some(name => name.startsWith('assets/personal/')), 'Personal-package payload/metadata must not enter public APKs');
console.log(`PASS: all 72 rune GIFs are bundled unchanged (${total} bytes); no ROMs, disks, or game archives.`);
