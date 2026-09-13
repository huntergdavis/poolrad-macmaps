// Local test utility: preserve unar's AppleDouble forks through MacBinary II.
// Format references: docs/RESEARCH.md; no game data is embedded here.
import { readFileSync, readdirSync, existsSync, mkdirSync, mkdtempSync,
  writeFileSync, openSync, ftruncateSync, closeSync } from 'node:fs';
import { resolve, join, basename } from 'node:path';
import { execFileSync } from 'node:child_process';

const [sourceArg, diskArg] = process.argv.slice(2);
if (!sourceArg || !diskArg) throw Error('Usage: node tools/prepare-test-game.mjs EXTRACTED_FOLDER NEW_DISK.dsk');
const source = resolve(sourceArg), disk = resolve(diskArg);
if (existsSync(disk)) throw Error('Refusing to overwrite an existing disk: ' + disk);
const run = (command, args) => execFileSync(command, args, { stdio: 'inherit' });
const scratch = resolve('scratch');
mkdirSync(scratch, { recursive: true });
const staging = mkdtempSync(join(scratch, 'macbinary-'));

function packFile(name, data, sidecar) {
  if (!/^[\x20-\x7e]{1,31}$/.test(name) || name.includes(':')) throw Error('Unsupported HFS name: ' + name);
  let resource = Buffer.alloc(0), finder = Buffer.alloc(32);
  if (sidecar) {
    if (sidecar.length < 26 || sidecar.readUInt32BE(0) !== 0x00051607) throw Error('Not AppleDouble: ' + name);
    const count = sidecar.readUInt16BE(24);
    if (26 + count * 12 > sidecar.length) throw Error('Truncated AppleDouble index');
    for (let i = 0; i < count; i++) {
      const at = 26 + i * 12, id = sidecar.readUInt32BE(at);
      const offset = sidecar.readUInt32BE(at + 4), length = sidecar.readUInt32BE(at + 8);
      if (offset + length > sidecar.length) throw Error('Truncated AppleDouble entry');
      const entry = sidecar.subarray(offset, offset + length);
      if (id === 2) resource = entry;
      if (id === 9 && length >= 32) finder = entry;
    }
  }
  const header = Buffer.alloc(128);
  header[1] = name.length;
  header.write(name, 2, 'ascii');
  finder.copy(header, 65, 0, 8); // File type and creator.
  header[73] = finder[8];
  finder.copy(header, 75, 10, 16); // Finder position and folder.
  header.writeUInt32BE(data.length, 83);
  header.writeUInt32BE(resource.length, 87);
  header[101] = finder[9];
  header[122] = 129;
  header[123] = 129;
  let crc = 0;
  for (const byte of header.subarray(0, 124)) {
    crc ^= byte << 8;
    for (let bit = 0; bit < 8; bit++) crc = ((crc << 1) ^ ((crc & 0x8000) ? 0x1021 : 0)) & 0xffff;
  }
  header.writeUInt16BE(crc, 124);
  const pad = b => Buffer.alloc((128 - b.length % 128) % 128);
  return Buffer.concat([header, data, pad(data), resource, pad(resource)]);
}

// Validate and stage every file before creating the disk.
const files = [], directories = [];
function stage(folder, hfsPath) {
  directories.push(hfsPath);
  const entries = readdirSync(folder, { withFileTypes: true });
  if (entries.some(e => e.isSymbolicLink())) throw Error('Symlinks are not supported');
  const names = new Set(entries.filter(e => e.isFile() && !e.name.startsWith('.'))
    .map(e => e.name.replace(/\.rsrc$/, '')));
  for (const name of names) {
    const dataPath = join(folder, name), sidePath = dataPath + '.rsrc';
    const binary = packFile(name, existsSync(dataPath) ? readFileSync(dataPath) : Buffer.alloc(0),
      existsSync(sidePath) ? readFileSync(sidePath) : null);
    const staged = join(staging, files.length + '.bin');
    writeFileSync(staged, binary, { flag: 'wx' });
    files.push([staged, hfsPath + ':' + name]);
  }
  for (const entry of entries.filter(e => e.isDirectory())) stage(join(folder, entry.name), hfsPath + ':' + entry.name);
}
stage(source, ':' + basename(source));
const fd = openSync(disk, 'wx');
try { ftruncateSync(fd, 64 * 1024 * 1024); } finally { closeSync(fd); }
run('hformat', ['-l', 'PoolRad Game', disk]);
try {
  for (const directory of directories) run('hmkdir', [directory]);
  for (const [file, target] of files) run('hcopy', ['-m', file, target]);
} finally { run('humount', []); }
console.log('Prepared ' + files.length + ' fork-preserving files in ' + disk);
console.log('Temporary MacBinary copies remain private in ' + staging);
