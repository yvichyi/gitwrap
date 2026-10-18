// Load index.html in Chromium at fixed instants, exercise the page, and write screenshots.
//   CHROME_PATH=/path/to/chrome node scripts/browser-check.mjs
import { chromium } from 'playwright';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const URL_ = new URL('../index.html', import.meta.url).href;
const SHOTS = fileURLToPath(new URL('../docs/screenshots/', import.meta.url));
const launch = { executablePath: process.env.CHROME_PATH || undefined };
if (process.env.HTTPS_PROXY) launch.proxy = { server: process.env.HTTPS_PROXY };   // for Google Fonts

const failures = [];
const expect = (ok, msg) => { if (!ok) failures.push(msg); };
const browser = await chromium.launch(launch);

async function open({ at, width = 1280, height = 900, scheme = 'light', ctx }) {
  const context = ctx || await browser.newContext({ viewport: { width, height }, colorScheme: scheme, deviceScaleFactor: 2, locale: 'zh-CN' });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error' && !/fonts\.g/.test(m.text())) errors.push(m.text()); });
  await page.clock.setFixedTime(new Date(at));
  await page.goto(URL_);
  await page.evaluate(() => document.fonts.ready);
  return { page, context, errors };
}
const text = (page, sel) => page.locator(sel).first().innerText();
const noOverflow = page => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);

// 1. Desktop, light, morning of 2026-10-18: still 寒露二候.
{
  const { page, context, errors } = await open({ at: '2026-10-18T10:20:00+08:00' });
  expect(await text(page, '#today-name') === '雀入大水为蛤', 'desktop: today should be 雀入大水为蛤');
  expect((await text(page, '#countdown')).includes('菊有黄华'), 'desktop: countdown should name 菊有黄华');
  expect((await text(page, '#table-title')) === '2026 年节气表', 'desktop: table title');
  expect(await page.locator('#terms tbody tr').count() === 24, 'desktop: 24 table rows');
  expect((await text(page, '#terms tr.now')).includes('寒露'), 'desktop: 寒露 row marked as now');
  expect(await noOverflow(page), 'desktop: horizontal overflow');
  await page.screenshot({ path: SHOTS + 'desktop-light.png', fullPage: true });

  await page.locator('#wheel .seg[data-i="0"]').click();
  expect(await text(page, '#detail h3') === '东风解冻', 'wheel: clicking segment 0 selects 东风解冻');
  expect((await text(page, '#detail')).includes('2026年2月4日 04:02'), 'wheel: 立春 2026 starts 02-04 04:02');
  await page.locator('#year-next').click();
  expect(await text(page, '#year-label') === '2027', 'year: next goes to 2027');
  expect(await text(page, '#table-title') === '2027 年节气表', 'year: table follows the wheel');
  await page.locator('#year-now').click();
  expect(await text(page, '#detail h3') === '雀入大水为蛤', 'year: back to today');
  expect(errors.length === 0, 'desktop errors: ' + errors.join(' | '));
  await context.close();
}

// 2. Desktop, dark.
{
  const { page, context, errors } = await open({ at: '2026-10-18T10:20:00+08:00', scheme: 'dark' });
  await page.screenshot({ path: SHOTS + 'desktop-dark.png', fullPage: false });
  expect(errors.length === 0, 'dark errors: ' + errors.join(' | '));
  await context.close();
}

// 3. Phone, evening of 2026-10-18: 菊有黄华 has begun. Then the notebook, the card, and a year later.
{
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'light', deviceScaleFactor: 2, locale: 'zh-CN', isMobile: true, hasTouch: true });
  let { page, errors } = await open({ at: '2026-10-18T18:00:00+08:00', ctx });
  expect(await text(page, '#today-name') === '菊有黄华', 'phone: today should be 菊有黄华');
  expect(await noOverflow(page), 'phone: horizontal overflow');
  await page.screenshot({ path: SHOTS + 'phone-light.png', fullPage: false });

  await page.locator('#go-note').click();
  expect(await page.inputValue('#note-date') === '2026-10-18', 'note: date defaults to today');
  expect((await text(page, '#note-hint')).includes('菊有黄华'), 'note: hint names the pentad');
  await page.fill('#note-text', '楼下的桂花落了一地，菊花摊摆出来了');
  await page.locator('#note-add').click();
  await page.waitForSelector('#note-list li:not(.example)');
  expect(await page.locator('#note-list li:not(.example)').count() === 1, 'note: one note listed');
  expect(await page.locator('#wheel .dot').count() === 1, 'note: wheel shows one dot');
  await page.locator('#notebook').screenshot({ path: SHOTS + 'phone-notebook.png' });

  await page.locator('#make-card').click();
  await page.waitForSelector('#card-dialog[open]');
  const src = await page.getAttribute('#card-img', 'src');
  expect(src && src.startsWith('data:image/png'), 'card: PNG generated');
  if (src) writeFileSync(SHOTS + 'card.png', Buffer.from(src.split(',')[1], 'base64'));
  await page.locator('#card-close').click();
  expect(errors.length === 0, 'phone errors: ' + errors.join(' | '));
  await page.close();

  // Same browser profile a year later, in the same pentad: the page recalls last year's note.
  ({ page, errors } = await open({ at: '2027-10-21T09:00:00+08:00', ctx }));
  expect(await text(page, '#today-name') === '菊有黄华', '2027: today should be 菊有黄华');
  expect(await page.locator('#echo').isVisible(), '2027: last year\'s note is recalled');
  expect((await text(page, '#echo')).includes('桂花'), '2027: echo shows the note text');
  expect(await noOverflow(page), '2027: horizontal overflow');
  await page.locator('#plate').screenshot({ path: SHOTS + 'phone-echo.png' });
  expect(errors.length === 0, '2027 errors: ' + errors.join(' | '));
  await ctx.close();
}

await browser.close();
if (failures.length) { console.error(failures.map(f => '✗ ' + f).join('\n')); process.exit(1); }
console.log('✓ browser checks passed; screenshots in docs/screenshots/');
