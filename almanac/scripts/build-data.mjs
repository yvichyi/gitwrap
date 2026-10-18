// Recompute the pentad start times and write them into index.html.
//   node scripts/build-data.mjs            rewrite the embedded data
//   node scripts/build-data.mjs --check    exit 1 if the embedded data is stale
import { readFileSync, writeFileSync } from 'node:fs';
import { computeBoundaries, encode } from './pentads-time.mjs';

const FILE = new URL('../index.html', import.meta.url);
const MARK = /\/\*TIMES:BEGIN\*\/[\s\S]*?\/\*TIMES:END\*\//;

const html = readFileSync(FILE, 'utf8');
if (!MARK.test(html)) throw new Error('TIMES markers not found in index.html');
const block = `/*TIMES:BEGIN*/${JSON.stringify(encode(computeBoundaries()))}/*TIMES:END*/`;
const next = html.replace(MARK, block);

if (process.argv.includes('--check')) {
  if (next !== html) { console.error('index.html: embedded pentad times are stale; run npm run build'); process.exit(1); }
  console.log('index.html: embedded pentad times are current');
} else {
  writeFileSync(FILE, next);
  console.log(`index.html: wrote ${block.length} bytes of pentad times`);
}
