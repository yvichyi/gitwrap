// Static checks for index.html: content, embedded times, reference instants, script syntax, external loads.
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { computeBoundaries, encode, decode, START_INDEX } from './pentads-time.mjs';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const errors = [];
const fail = msg => errors.push(msg);
const block = name => {
  const m = new RegExp(`/\\*${name}:BEGIN\\*/([\\s\\S]*?)/\\*${name}:END\\*/`).exec(html);
  if (!m) throw new Error(`${name} markers missing`);
  return JSON.parse(m[1]);
};

// The commonly used list of the 72 pentads, 立春一候 first.
const CANON = `东风解冻 蛰虫始振 鱼陟负冰 獭祭鱼 候雁北 草木萌动 桃始华 仓庚鸣 鹰化为鸠 玄鸟至 雷乃发声 始电
桐始华 田鼠化为鴽 虹始见 萍始生 鸣鸠拂其羽 戴胜降于桑 蝼蝈鸣 蚯蚓出 王瓜生 苦菜秀 靡草死 麦秋至
螳螂生 鵙始鸣 反舌无声 鹿角解 蜩始鸣 半夏生 温风至 蟋蟀居宇 鹰始挚 腐草为萤 土润溽暑 大雨时行
凉风至 白露降 寒蝉鸣 鹰乃祭鸟 天地始肃 禾乃登 鸿雁来 玄鸟归 群鸟养羞 雷始收声 蛰虫坯户 水始涸
鸿雁来宾 雀入大水为蛤 菊有黄华 豺乃祭兽 草木黄落 蛰虫咸俯 水始冰 地始冻 雉入大水为蜃 虹藏不见 天气上升地气下降 闭塞而成冬
鹖鴠不鸣 虎始交 荔挺出 蚯蚓结 麋角解 水泉动 雁北乡 鹊始巢 雉始雊 鸡始乳 征鸟厉疾 水泽腹坚`.split(/\s+/);
const TERM_NAMES = '立春 雨水 惊蛰 春分 清明 谷雨 立夏 小满 芒种 夏至 小暑 大暑 立秋 处暑 白露 秋分 寒露 霜降 立冬 小雪 大雪 冬至 小寒 大寒'.split(' ');

const terms = block('TERMS'), pentads = block('PENTADS'), times = block('TIMES');
if (terms.length !== 24) fail(`expected 24 terms, found ${terms.length}`);
terms.forEach((t, j) => { if (t.n !== TERM_NAMES[j]) fail(`term ${j}: ${t.n} should be ${TERM_NAMES[j]}`); if (!t.d) fail(`term ${t.n}: missing description`); });
if (pentads.length !== 72) fail(`expected 72 pentads, found ${pentads.length}`);
pentads.forEach((p, i) => {
  if (p.n !== CANON[i]) fail(`pentad ${i + 1}: ${p.n} should be ${CANON[i]}`);
  for (const f of ['p', 'o', 'k']) if (typeof p[f] !== 'string' || p[f].length < 4) fail(`pentad ${p.n}: field ${f} is missing`);
  if (!(p.h >= 0 && p.h < 360)) fail(`pentad ${p.n}: hue ${p.h} out of range`);
  if (!(p.c >= 0 && p.c <= 0.15)) fail(`pentad ${p.n}: chroma ${p.c} out of range`);
});

// Embedded times must equal a fresh computation.
const fresh = computeBoundaries();
if (JSON.stringify(encode(fresh)) !== JSON.stringify(times)) fail('embedded pentad times are stale; run npm run build');
const bounds = decode(times);
if (times.startIndex !== START_INDEX) fail('startIndex mismatch');

// Published instants (Beijing time) for a few terms; allow one minute for rounding.
const REF = [
  ['2025-12-21 23:03', 21], ['2026-02-04 04:02', 0], ['2026-03-20 22:46', 3],
  ['2026-06-21 16:24', 9], ['2026-09-23 08:05', 15], ['2026-12-22 04:50', 21],
];
for (const [s, term] of REF) {
  const want = Date.parse(s.replace(' ', 'T') + ':00+08:00') / 60000;
  let best = null;
  bounds.forEach((m, k) => { if ((START_INDEX + k) % 72 === term * 3 && (best === null || Math.abs(m - want) < Math.abs(best - want))) best = m; });
  if (Math.abs(best - want) > 1) fail(`${TERM_NAMES[term]} ${s}: computed ${new Date(best * 60000 + 8 * 3600e3).toISOString().slice(0, 16)} (Beijing)`);
}
// Every wheel year 1900–2100 holds each pentad exactly once.
for (let y = 1900; y <= 2100; y++) {
  const from = (Date.UTC(y, 0, 3) - 8 * 3600e3) / 60000, to = (Date.UTC(y + 1, 0, 3) - 8 * 3600e3) / 60000;
  const seen = new Array(72).fill(0);
  bounds.forEach((m, k) => { if (m >= from && m < to) seen[(START_INDEX + k) % 72]++; });
  if (seen.some(n => n !== 1)) { fail(`wheel year ${y} does not hold each pentad once`); break; }
}

// Script syntax.
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
if (scripts.length !== 1) fail(`expected one inline script, found ${scripts.length}`);
for (const src of scripts) { try { new vm.Script(src, { filename: 'index.html' }); } catch (e) { fail(`script does not parse: ${e.message}`); } }

// External loads: only Google Fonts stylesheets; no external scripts.
for (const m of html.matchAll(/<(script|link|img|iframe)\b[^>]*>/g)) {
  const tag = m[0];
  if (/<script\b[^>]*\bsrc=/.test(tag)) fail(`external script: ${tag}`);
  if (/^<link\b/.test(tag) && /rel="stylesheet"/.test(tag) && !/href="https:\/\/fonts\.googleapis\.com\//.test(tag)) fail(`stylesheet outside Google Fonts: ${tag}`);
  if (/^<(img|iframe)\b/.test(tag) && /src="https?:/.test(tag)) fail(`external media: ${tag}`);
}
// The artifact regions must exist and be ordered.
const iHead = html.indexOf('<!--A:HEAD-->'), iHeadEnd = html.indexOf('<!--/A:HEAD-->'), iBody = html.indexOf('<!--A:BODY-->'), iBodyEnd = html.indexOf('<!--/A:BODY-->');
if (!(iHead >= 0 && iHead < iHeadEnd && iHeadEnd < iBody && iBody < iBodyEnd)) fail('artifact region markers are missing or out of order');
if (!/<title>[^<]+<\/title>/.test(html.slice(iHead, iHead + 8192))) fail('title must sit in the first 8KB of the artifact region');

if (errors.length) { console.error(errors.map(e => '✗ ' + e).join('\n')); process.exit(1); }
console.log(`✓ 24 terms, 72 pentads, ${bounds.length} pentad start times (1899–2101), ${REF.length} reference instants, script parses`);
