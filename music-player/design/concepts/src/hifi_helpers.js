const SEGS = { 0: 'abcdef', 1: 'bc', 2: 'abged', 3: 'abgcd', 4: 'fgbc', 5: 'afgcd', 6: 'afgedc', 7: 'abc', 8: 'abcdefg', 9: 'abcdfg', '-': 'g', ' ': '' };
function seg7(text, h, on, off) {
  const W = 12, H = 22, t = 2.4, g = .7, x0 = t / 2, x1 = W - t / 2, y0 = t / 2, ym = H / 2, y1 = H - t / 2;
  const hz = (y, a, b) => `${a},${y} ${a + t / 2},${y - t / 2} ${b - t / 2},${y - t / 2} ${b},${y} ${b - t / 2},${y + t / 2} ${a + t / 2},${y + t / 2}`;
  const vt = (x, a, b) => `${x},${a} ${x + t / 2},${a + t / 2} ${x + t / 2},${b - t / 2} ${x},${b} ${x - t / 2},${b - t / 2} ${x - t / 2},${a + t / 2}`;
  const P = { a: hz(y0, x0 + g, x1 - g), g: hz(ym, x0 + g, x1 - g), d: hz(y1, x0 + g, x1 - g), f: vt(x0, y0 + g, ym - g), b: vt(x1, y0 + g, ym - g), e: vt(x0, ym + g, y1 - g), c: vt(x1, ym + g, y1 - g) };
  let x = 0, out = '';
  for (const ch of text) {
    if (ch === '.') { out += `<rect x="${x - 2.4}" y="${H - 2.4}" width="2.4" height="2.4" fill="${on}"/>`; x += 1.5; continue; }
    if (ch === ':') { out += `<rect x="${x}" y="6" width="2.4" height="2.4" fill="${on}"/><rect x="${x}" y="14" width="2.4" height="2.4" fill="${on}"/>`; x += 5; continue; }
    const lit = SEGS[ch] ?? '';
    out += `<g transform="translate(${x},0) skewX(-7)">` + 'abcdefg'.split('').map(s => `<polygon points="${P[s]}" fill="${lit.includes(s) ? on : off}"/>`).join('') + '</g>';
    x += W + 3;
  }
  return `<svg viewBox="-1 -1 ${x} ${H + 2}" style="height:${h}px;width:${h * x / (H + 2)}px;filter:drop-shadow(0 0 3px ${on}aa);display:block">${out}</svg>`;
}
function vu(label, value) {
  const cx = 84, cy = 112, r = 76, a0 = -50, a1 = 50;
  const marks = [[-20, 0], [-10, .24], [-7, .38], [-5, .5], [-3, .63], [-2, .7], [-1, .76], [0, .82], [1, .88], [2, .94], [3, 1]];
  const pt = (f, rr) => { const a = (a0 + (a1 - a0) * f) * Math.PI / 180; return [cx + rr * Math.sin(a), cy - rr * Math.cos(a)]; };
  let s = `<defs><radialGradient id="face${label}" cx="50%" cy="85%" r="80%"><stop offset="0" stop-color="#fff6dc"/><stop offset=".6" stop-color="#f6e2b4"/><stop offset="1" stop-color="#d8bd86"/></radialGradient></defs><rect width="168" height="86" fill="url(#face${label})"/>`;
  const [rx0, ry0] = pt(.82, r), [rx1, ry1] = pt(1, r);
  s += `<path d="M${rx0} ${ry0} A ${r} ${r} 0 0 1 ${rx1} ${ry1}" stroke="#d8321c" stroke-width="4" fill="none"/>`;
  const [ax0, ay0] = pt(0, r), [ax1, ay1] = pt(.82, r);
  s += `<path d="M${ax0} ${ay0} A ${r} ${r} 0 0 1 ${ax1} ${ay1}" stroke="#1a1714" stroke-width="1" fill="none"/>`;
  for (const [v, f] of marks) {
    const [x0, y0] = pt(f, r - 1), [x1, y1] = pt(f, r + 6), [tx, ty] = pt(f, r + 13);
    s += `<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="${v > 0 ? '#d8321c' : '#1a1714'}" stroke-width="1.1"/>`;
    if ([-20, -10, -7, -5, -3, 0, 3].includes(v)) s += `<text x="${tx}" y="${ty + 2.5}" font-family="Inter" font-weight="600" font-size="7" text-anchor="middle" fill="${v > 0 ? '#d8321c' : '#1a1714'}">${v > 0 ? '+' + v : v}</text>`;
  }
  for (let i = 0; i <= 50; i++) { const f = i / 50; const [x0, y0] = pt(f, r - 1), [x1, y1] = pt(f, r + 2); s += `<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="#1a171488" stroke-width=".5"/>`; }
  // The percentage scale under the dB one.
  [[0, '0'], [.5, '50'], [.82, '100']].forEach(([f, t]) => { const [x, y] = pt(f, r - 10); s += `<text x="${x}" y="${y + 2}" font-family="Inter" font-size="5.5" text-anchor="middle" fill="#6b5e48">${t}</text>`; });
  s += `<text x="${cx}" y="62" font-family="Inter" font-weight="800" font-size="12" text-anchor="middle" fill="#1a1714" letter-spacing="1">VU</text>`;
  s += `<text x="10" y="80" font-family="Inter" font-weight="600" font-size="7" fill="#6b5e48" letter-spacing="1">${label}</text><text x="158" y="80" font-family="Inter" font-size="6" text-anchor="end" fill="#6b5e48" letter-spacing="1">LANYIN</text>`;
  const [nx, ny] = pt(value, r + 6);
  s += `<line x1="${cx + 1.5}" y1="${cy}" x2="${nx + 1.5}" y2="${ny + 2}" stroke="#00000033" stroke-width="2"/><line x1="${cx}" y1="${cy}" x2="${nx}" y2="${ny}" stroke="#141210" stroke-width="1.3"/>`;
  s += `<rect x="0" y="74" width="168" height="12" fill="#1b1916"/><circle cx="${cx}" cy="${cy - 22}" r="0"/>`;
  return `<svg viewBox="0 0 168 86" style="width:100%;height:100%;display:block">${s}</svg>`;
}
function screws(el, pts) { pts.forEach(([x, y, r]) => el.insertAdjacentHTML('beforeend', `<span class="screw" style="left:${x}px;top:${y}px;--r:${r || 35}deg"></span>`)); }
const ICON = {
  rec: '<circle cx="10" cy="10" r="5" fill="#d8321c"/>',
  rew: '<path d="M10 5 L3 10 L10 15Z M17 5 L10 10 L17 15Z" fill="#26282b"/>',
  play: '<path d="M6 4 L16 10 L6 16Z" fill="#26282b"/>',
  ff: '<path d="M3 5 L10 10 L3 15Z M10 5 L17 10 L10 15Z" fill="#26282b"/>',
  stop: '<rect x="5" y="5" width="10" height="10" fill="#26282b"/>',
  pause: '<rect x="5" y="4" width="3.5" height="12" fill="#26282b"/><rect x="11.5" y="4" width="3.5" height="12" fill="#26282b"/>',
  eject: '<path d="M10 4 L16 11 H4Z" fill="#26282b"/><rect x="4" y="13" width="12" height="2.5" fill="#26282b"/>',
};

function cassette(w, h, prog, label) {
  const lr = 8 + 18 * (1 - prog), rr = 8 + 18 * prog;
  const hub = (cx, cy, r) => {
    let s = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#4a2b1a"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#tapeSheen)"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="7" fill="#e9eaec" stroke="#9a9da1" stroke-width=".6"/><circle cx="${cx}" cy="${cy}" r="3.4" fill="#2a2b2d"/>`;
    for (let k = 0; k < 6; k++) { const a = k * Math.PI / 3; s += `<rect x="${cx - .8}" y="${cy - 3.4}" width="1.6" height="1.8" fill="#e9eaec" transform="rotate(${k * 60} ${cx} ${cy})"/>`; }
    return s;
  };
  return `<svg viewBox="0 0 200 128" style="width:${w}px;height:${h}px;display:block">
    <defs><radialGradient id="tapeSheen"><stop offset=".3" stop-color="#7a4a2a" stop-opacity="0"/><stop offset=".9" stop-color="#9a6a42" stop-opacity=".5"/><stop offset="1" stop-color="#2a170d" stop-opacity=".6"/></radialGradient>
      <linearGradient id="shell" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#3a3c40"/><stop offset="1" stop-color="#232427"/></linearGradient></defs>
    <rect x="2" y="2" width="196" height="124" rx="7" fill="url(#shell)" stroke="#0e0e10"/>
    <rect x="12" y="10" width="176" height="78" rx="4" fill="#efe6d2"/>
    <rect x="12" y="10" width="176" height="18" rx="4" fill="#c84a2c"/><rect x="12" y="22" width="176" height="6" fill="#c84a2c"/>
    <text x="18" y="23" font-family="Inter" font-weight="800" font-size="9" fill="#efe6d2" letter-spacing="1">A</text>
    <text x="182" y="23" font-family="Inter" font-weight="600" font-size="6" fill="#efe6d2" text-anchor="end" letter-spacing="1">CrO2 · 60 MIN</text>
    ${label}
    <rect x="46" y="44" width="108" height="40" rx="6" fill="#15161a" stroke="#0a0a0b"/>
    <rect x="48" y="46" width="104" height="36" rx="5" fill="#2a2c30" opacity=".6"/>
    ${hub(72, 64, lr)}${hub(128, 64, rr)}
    <rect x="48" y="46" width="104" height="14" rx="4" fill="#fff" opacity=".07"/>
    <path d="M40 126 L52 104 H148 L160 126" fill="#1b1c1f" stroke="#0e0e10"/>
    <circle cx="66" cy="114" r="4" fill="#0e0e10"/><circle cx="134" cy="114" r="4" fill="#0e0e10"/><rect x="92" y="108" width="16" height="8" rx="1" fill="#0e0e10"/>
    ${[[8, 8], [192, 8], [8, 120], [192, 120], [100, 98]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="2.6" fill="#8a8d91"/><line x1="${x - 1.6}" y1="${y}" x2="${x + 1.6}" y2="${y}" stroke="#3a3c40" stroke-width=".7"/>`).join('')}
  </svg>`;
}
