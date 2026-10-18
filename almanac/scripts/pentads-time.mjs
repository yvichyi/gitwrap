// Compute the instants when the Sun's apparent ecliptic longitude crosses
// every multiple of 5° (the start of each of the 72 pentads), 1899-12 → 2101-01.
import * as A from 'astronomy-engine';

export const START_LON = 270;            // 1899 冬至, so 1900-01-01 is covered
export const START_INDEX = 63;           // pentad index of 冬至一候 when 立春一候 is 0
export const DIFF_OFFSET = 6900;         // minutes; diffs are stored as (diff - offset) in base36
const FIRST_SEARCH = new Date('1899-12-15T00:00:00Z');
const END = Date.parse('2101-01-12T00:00:00Z');

export function computeBoundaries() {
  const out = [];
  let lon = START_LON;
  let from = A.MakeTime(FIRST_SEARCH);
  for (;;) {
    const t = A.SearchSunLongitude(lon, from, 12);
    if (!t) throw new Error(`no crossing for ${lon}° after ${from.date.toISOString()}`);
    out.push(Math.round(t.date.getTime() / 60000));
    if (t.date.getTime() > END) break;
    lon = (lon + 5) % 360;
    from = t.AddDays(3);
  }
  return out;
}

export function encode(minutes) {
  let diffs = '';
  for (let i = 1; i < minutes.length; i++) {
    const d = minutes[i] - minutes[i - 1] - DIFF_OFFSET;
    if (d < 0 || d >= 36 * 36) throw new Error(`diff out of range at ${i}: ${d}`);
    diffs += d.toString(36).padStart(2, '0');
  }
  return { start: minutes[0], startIndex: START_INDEX, offset: DIFF_OFFSET, diffs };
}

export function decode({ start, offset, diffs }) {
  const out = [start];
  for (let i = 0; i < diffs.length; i += 2) out.push(out[out.length - 1] + offset + parseInt(diffs.slice(i, i + 2), 36));
  return out;
}
