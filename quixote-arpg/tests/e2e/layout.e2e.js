// End-to-end layout and input checks in a real browser.
// Run the game (`npm start`), then `npm run test:e2e`. Set GAME_URL to point at another host,
// and CHROME_PATH to use a specific Chromium binary.
/* global getComputedStyle */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const URL_BASE = process.env.GAME_URL ?? 'http://localhost:8080/index.html';
const OUT = fileURLToPath(new URL('./out/', import.meta.url));
mkdirSync(OUT, { recursive: true });

// Landscape phones from 16:9 to 20:9, with notch-style insets where the device has them.
const LANDSCAPE = [
  { name: '16:9 (1280x720)', width: 1280, height: 720, insets: 0 },
  { name: '19.5:9 (844x390)', width: 844, height: 390, insets: 44 },
  { name: '20:9 (915x412)', width: 915, height: 412, insets: 0 },
  { name: '19.5:9 Android (2340x1080 @ 3x)', width: 780, height: 360, insets: 0 },
];
const PORTRAIT = { name: 'portrait phone (390x844)', width: 390, height: 844 };

const failures = [];
const expect = (cond, msg) => {
  if (!cond) failures.push(msg);
};

const launchOpts = {
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-sandbox'],
  ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
};

function rectOf(page, selector) {
  return page.$$eval(selector, (els) => els.map((el) => {
    const r = el.getBoundingClientRect();
    return { id: el.dataset.id ?? el.id ?? el.className, x: r.left, y: r.top, w: r.width, h: r.height, r: r.right, b: r.bottom };
  }));
}

async function checkLandscape(browser, vp) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height }, hasTouch: true, isMobile: true, deviceScaleFactor: 1 });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });

  await page.goto(URL_BASE, { waitUntil: 'load' });
  await page.waitForFunction(() => document.querySelector('[data-act="start"]'));
  await page.screenshot({ path: `${OUT}menu-${vp.width}x${vp.height}.png` });

  // Menu buttons stay inside the viewport and fit without horizontal scroll.
  const scroll = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(scroll <= 1, `${vp.name}: horizontal scroll on menu (${scroll}px)`);

  await page.click('[data-act="start"]');
  await page.waitForFunction(() => globalThis.__quixote?.state === 'stage');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${OUT}stage-${vp.width}x${vp.height}.png` });

  // Touch buttons: visible, inside the screen with a margin, and not overlapping each other.
  const btns = await rectOf(page, '.tbtn');
  expect(btns.length === 8, `${vp.name}: expected 8 touch buttons, got ${btns.length}`);
  for (const b of btns) {
    expect(b.x >= 0 && b.y >= 0 && b.r <= vp.width && b.b <= vp.height, `${vp.name}: button ${b.id} off-screen ${JSON.stringify(b)}`);
    expect(b.w >= 40, `${vp.name}: button ${b.id} smaller than 40px (${b.w})`);
  }
  // Buttons are round, so compare circles (centre distance against the sum of radii), not their bounding boxes.
  for (let i = 0; i < btns.length; i++) {
    for (let j = i + 1; j < btns.length; j++) {
      const a = btns[i];
      const c = btns[j];
      const d = Math.hypot(a.x + a.w / 2 - (c.x + c.w / 2), a.y + a.h / 2 - (c.y + c.h / 2));
      expect(d >= a.w / 2 + c.w / 2 + 2, `${vp.name}: buttons ${a.id} and ${c.id} overlap (gap ${(d - a.w / 2 - c.w / 2).toFixed(1)}px)`);
    }
  }

  // HUD bars and touch buttons do not sit on top of each other.
  const hudBars = await rectOf(page, '.hud-left .bar, .hud-right .chip');
  for (const h of hudBars) {
    for (const b of btns) {
      const overlap = h.x < b.r && b.x < h.r && h.y < b.b && b.y < h.b;
      expect(!overlap, `${vp.name}: HUD ${h.id} overlaps touch button ${b.id}`);
    }
  }

  // Keyboard movement moves the knight.
  const before = await page.evaluate(() => ({ x: globalThis.__quixote.world.player.x, z: globalThis.__quixote.world.player.z }));
  await page.keyboard.down('KeyA');
  await page.waitForTimeout(600);
  await page.keyboard.up('KeyA');
  const after = await page.evaluate(() => ({ x: globalThis.__quixote.world.player.x, z: globalThis.__quixote.world.player.z }));
  expect(Math.abs(after.x - before.x) > 0.5 || Math.abs(after.z - before.z) > 0.5, `${vp.name}: keyboard movement did nothing`);

  // Joystick: a pointer down on the left half, dragged right, moves the knight in +x.
  const j0 = await page.evaluate(() => ({ x: globalThis.__quixote.world.player.x }));
  await page.evaluate(() => {
    const root = document.getElementById('touch');
    const fire = (type, x, y) => root.dispatchEvent(new PointerEvent(type, { clientX: x, clientY: y, pointerId: 7, bubbles: true, pointerType: 'touch', isPrimary: true }));
    fire('pointerdown', window.innerWidth * 0.2, window.innerHeight * 0.5);
    for (let i = 1; i <= 10; i++) fire('pointermove', window.innerWidth * 0.2 + i * 12, window.innerHeight * 0.5);
  });
  await page.waitForTimeout(500);
  await page.evaluate(() => {
    const root = document.getElementById('touch');
    root.dispatchEvent(new PointerEvent('pointerup', { clientX: 0, clientY: 0, pointerId: 7, bubbles: true, pointerType: 'touch', isPrimary: true }));
  });
  const j1 = await page.evaluate(() => ({ x: globalThis.__quixote.world.player.x }));
  expect(j1.x !== j0.x, `${vp.name}: joystick drag did nothing`);

  // Attack button: a press lands a swing that produces hit or miss feedback.
  // A real touch (CDP) so the browser produces genuine pointer events and pointer capture.
  const attackBox = await (await page.$('.tbtn[data-id="attack"]')).boundingBox();
  const cdp = await context.newCDPSession(page);
  const ax = attackBox.x + attackBox.width / 2;
  const ay = attackBox.y + attackBox.height / 2;
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: ax, y: ay, id: 1 }] });
  await page.waitForTimeout(250);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  const swung = await page.evaluate(() => globalThis.__quixote.world.player.action?.kind ?? globalThis.__quixote.world.player.state);
  expect(swung === 'light' || swung === 'free', `${vp.name}: attack press produced ${swung}`);

  // Background: a hidden tab pauses the battle; coming back shows the pause menu, not a running fight.
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await page.waitForTimeout(300);
  const paused = await page.evaluate(() => globalThis.__quixote.state);
  expect(paused === 'paused', `${vp.name}: hidden tab did not pause (state ${paused})`);
  const pausedTime = await page.evaluate(() => globalThis.__quixote.world.time);
  await page.waitForTimeout(600);
  const pausedTime2 = await page.evaluate(() => globalThis.__quixote.world.time);
  expect(Math.abs(pausedTime2 - pausedTime) < 1e-6, `${vp.name}: sim kept running while paused`);
  await page.screenshot({ path: `${OUT}pause-${vp.width}x${vp.height}.png` });

  // Layout still fits with the pause sheet on top.
  const overflowing = await page.evaluate(() => {
    const sheet = document.querySelector('#screen .sheet');
    if (!sheet) return 'no sheet';
    const r = sheet.getBoundingClientRect();
    return r.right > window.innerWidth + 1 || r.left < -1 ? 'sheet wider than screen' : null;
  });
  expect(!overflowing, `${vp.name}: pause sheet ${overflowing}`);

  expect(errors.length === 0, `${vp.name}: console errors: ${errors.slice(0, 3).join(' | ')}`);
  await context.close();
}

async function checkPortrait(browser) {
  const context = await browser.newContext({ viewport: { width: PORTRAIT.width, height: PORTRAIT.height }, hasTouch: true, isMobile: true });
  const page = await context.newPage();
  await page.goto(URL_BASE, { waitUntil: 'load' });
  await page.waitForTimeout(400);
  const visible = await page.evaluate(() => getComputedStyle(document.getElementById('rotate')).display !== 'none');
  expect(visible, `${PORTRAIT.name}: rotate prompt not shown`);
  await page.screenshot({ path: `${OUT}portrait.png` });
  await context.close();
}

async function checkResume(browser) {
  const context = await browser.newContext({ viewport: { width: 844, height: 390 }, hasTouch: true, isMobile: true });
  const page = await context.newPage();
  await page.goto(URL_BASE, { waitUntil: 'load' });
  await page.waitForFunction(() => document.querySelector('[data-act="start"]'));
  await page.click('[data-act="start"]');
  await page.waitForFunction(() => globalThis.__quixote?.state === 'stage');
  await page.evaluate(() => globalThis.__quixote.session.pause());
  await page.reload({ waitUntil: 'load' });
  await page.waitForFunction(() => document.querySelector('[data-act="start"]'));
  const label = await page.textContent('[data-act="start"]');
  expect(label.includes('继续冒险'), `save/resume: menu shows "${label}" after reload`);
  await context.close();
}

const browser = await chromium.launch(launchOpts);
try {
  for (const vp of LANDSCAPE) {
    await checkLandscape(browser, vp);
    console.log(`ok  ${vp.name}`);
  }
  await checkPortrait(browser);
  console.log(`ok  ${PORTRAIT.name}`);
  await checkResume(browser);
  console.log('ok  save and resume');
} finally {
  await browser.close();
}

if (failures.length) {
  console.error(`\n${failures.length} layout/input check(s) failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log('\nall layout and input checks passed; screenshots in tests/e2e/out/');
