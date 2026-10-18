// Write the page as a claude.ai Artifact source: the head and body regions of index.html,
// without the document wrapper (the Artifact host adds its own).
//   node scripts/export-artifact.mjs <out.html>
import { readFileSync, writeFileSync } from 'node:fs';

const out = process.argv[2];
if (!out) { console.error('usage: node scripts/export-artifact.mjs <out.html>'); process.exit(2); }
const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const region = name => {
  const m = new RegExp(`<!--A:${name}-->([\\s\\S]*?)<!--/A:${name}-->`).exec(html);
  if (!m) throw new Error(`region ${name} not found`);
  return m[1].trim();
};
writeFileSync(out, region('HEAD') + '\n' + region('BODY') + '\n');
console.log(`wrote ${out}`);
