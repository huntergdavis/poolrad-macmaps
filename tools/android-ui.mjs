#!/usr/bin/env node
// Local test helper. Only operates on the explicitly selected emulator, never a physical tablet.
import { execFileSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
const [serial, action, label] = process.argv.slice(2);
if (!/^emulator-\d+$/.test(serial ?? '') || !['list', 'tap'].includes(action))
  throw new Error('Usage: node tools/android-ui.mjs emulator-5580 list|tap [exact text or description]');
const adb = (...args) => execFileSync(process.env.ADB ?? 'adb', ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 });
const decode = s => s.replace(/&#10;/g, '\n').replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
let output = '';
for (let attempt = 0; attempt < 3; attempt++) {
  output = adb('shell', 'uiautomator', 'dump', '/data/local/tmp/poolrad-ui.xml');
  if (output.includes('dumped to:')) break;
  if (attempt < 2) await delay(350);
}
if (!output.includes('dumped to:')) throw new Error('No fresh UI hierarchy. Try again after the transition.');
const xml = adb('exec-out', 'cat', '/data/local/tmp/poolrad-ui.xml');
const nodes = [...xml.matchAll(/<node\b[^>]+>/g)].map(([tag]) => Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(([, k, v]) => [k, decode(v)])));
const own = nodes.filter(n => n.package?.startsWith('com.hunterdavis.poolradmacmaps.'));
if (!own.length) throw new Error('PoolRad app is not the active UI; refusing input.');
if (action === 'list') {
  for (const n of own) if (n.text || n['content-desc']) console.log(JSON.stringify({ text: n.text, description: n['content-desc'], enabled: n.enabled, checked: n.checked, checkable: n.checkable, bounds: n.bounds }));
} else {
  const matches = own.filter(n => n.enabled === 'true' && (n.text === label || n['content-desc'] === label));
  if (matches.length !== 1) throw new Error('Expected one enabled control, found ' + matches.length + ': ' + label);
  const [x1, y1, x2, y2] = matches[0].bounds.match(/\d+/g).map(Number);
  if (x2 <= x1 || y2 <= y1) throw new Error('Control is not visible');
  adb('shell', 'input', 'tap', String(Math.floor((x1+x2)/2)), String(Math.floor((y1+y2)/2)));
  console.log('Tapped:', label);
}
