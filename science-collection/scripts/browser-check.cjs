'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const report = { pages: [], regressions: {} };
const server = http.createServer((req, res) => {
  try {
    const relative = decodeURIComponent(new URL(req.url, 'http://localhost').pathname).replace(/^\/+/, '') || 'index.html';
    const file = path.resolve(root, relative);
    if (!file.startsWith(root + path.sep) || !fs.statSync(file).isFile()) throw Error('Not found');
    res.setHeader('Content-Type', file.endsWith('.html') ? 'text/html; charset=utf-8' : 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  } catch { res.writeHead(404); res.end('Not found'); }
});

async function run() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
    headless: true,
    args: ['--no-sandbox']
  });
  try {
    for (const file of fs.readdirSync(root).filter(f => f.endsWith('.html')).sort()) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
      const errors = [], external = [], failedRequests = [];
      await context.route('**/*', route => {
        const url = route.request().url();
        if (/^https?:/.test(url) && !url.startsWith(base)) { external.push(url); return route.abort(); }
        return route.continue();
      });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      page.on('requestfailed', request => failedRequests.push(request.url()));
      const response = await page.goto(base + encodeURIComponent(file), { waitUntil: 'load' });
      assert.equal(response.status(), 200, file);
      const views = [];
      for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        await page.waitForTimeout(120);
        const tabs = page.locator('.tabs button');
        const count = await tabs.count();
        for (let i = 0; i < count; i++) { await tabs.nth(i).click(); await page.waitForTimeout(20); }
        const state = await page.evaluate(() => ({
          width: innerWidth, scrollWidth: document.documentElement.scrollWidth,
          bootError: window.__bootError || null, renderError: window.__renderError || null,
          ready: window.__ready === undefined ? null : window.__ready
        }));
        assert.equal(state.bootError, null, `${file}: boot error`);
        assert.equal(state.renderError, null, `${file}: render error`);
        assert.notEqual(state.ready, false, `${file}: startup incomplete`);
        views.push({ ...state, tabButtonsChecked: count });
      }
      assert.deepEqual(errors, [], `${file}: runtime errors`);
      assert.deepEqual(external, [], `${file}: external runtime requests`);
      assert.deepEqual(failedRequests, [], `${file}: failed runtime requests`);
      report.pages.push({ file, views, runtimeErrors: errors.length, externalRequests: external.length });
      console.log(`PASS load/tabs: ${file}`);
      await context.close();
    }

    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await page.addInitScript(() => {
      window.__canvasClears = 0;
      const clear = CanvasRenderingContext2D.prototype.clearRect;
      CanvasRenderingContext2D.prototype.clearRect = function (...args) {
        window.__canvasClears++;
        return clear.apply(this, args);
      };
    });
    await page.goto(base + encodeURIComponent('拥堵之波.html'));
    const tick = async () => Number((await page.locator('#run-status').textContent()).match(/第 (\d+) 秒/)?.[1] || 0);
    await page.locator('#play-button').click();
    await page.evaluate(() => { window.__canvasClears = 0; });
    const start = await tick();
    await page.waitForTimeout(2000);
    const traffic = await page.evaluate(() => ({
      clears: window.__canvasClears,
      tick: Number(document.getElementById('run-status').textContent.match(/第 (\d+) 秒/)[1])
    }));
    assert.ok(traffic.tick - start >= 8, 'Traffic model must advance');
    assert.ok(traffic.clears > 0 && traffic.clears <= (traffic.tick - start) * 3 + 3, 'Redraw only changed traffic states');
    await page.locator('#play-button').click();
    const paused = await tick();
    await page.waitForTimeout(300);
    assert.equal(await tick(), paused, 'Traffic pause');
    await page.locator('#step-button').click();
    assert.equal(await tick(), paused + 1, 'Traffic single step');
    await page.locator('#play-button').click();
    await page.waitForTimeout(300);
    assert.ok(await tick() > paused + 1, 'Traffic resume');
    await page.locator('#play-button').click();
    report.regressions.traffic = { sampleMs: 2000, modelSteps: traffic.tick - start, canvasClears: traffic.clears, pauseStepResume: 'passed' };

    await page.goto(base + encodeURIComponent('共名.html'));
    await page.locator('#btn-pause').click();
    const generation = await page.evaluate(() => window.__probe.state.st.gen);
    await page.locator('#btn-step').focus();
    await page.keyboard.press('Space');
    const language = await page.evaluate(() => ({ generation: window.__probe.state.st.gen, running: window.__probe.state.running }));
    assert.equal(language.generation, generation + 1, 'Native Space should click the focused step button');
    assert.equal(language.running, false, 'Single step should stay paused');
    await page.locator('#r-b').focus();
    await page.keyboard.press('Space');
    assert.equal(await page.evaluate(() => window.__probe.state.running), false, 'Range input should not toggle playback');
    await page.evaluate(() => document.activeElement.blur());
    await page.keyboard.press('Space');
    assert.equal(await page.evaluate(() => window.__probe.state.running), true, 'Global shortcut still works');
    report.regressions.language = { before: generation, after: language.generation, nativeButton: 'passed', inputFocus: 'passed', globalShortcut: 'passed' };

    await page.goto(base + encodeURIComponent('未选之路.html'));
    const banditWidths = [];
    for (const width of [320, 360, 390, 430]) {
      await page.setViewportSize({ width, height: 844 });
      await page.waitForTimeout(120);
      const scroll = await page.evaluate(() => document.documentElement.scrollWidth);
      assert.equal(scroll, width, 'Bandit title must fit the viewport');
      banditWidths.push({ width, scrollWidth: scroll });
    }
    await page.screenshot({ path: path.join(root, 'artifacts/bandit-mobile.png') });
    report.regressions.bandit = { titleWidths: banditWidths };
    await page.setViewportSize({ width: 390, height: 844 });

    await page.goto(base + encodeURIComponent('此刻.html'));
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390, 'Closed sidebar must not widen the document');
    await page.keyboard.press('l');
    await page.waitForTimeout(300);
    const side = await page.locator('#side').boundingBox();
    assert.ok(side.x >= 0 && side.x + side.width <= 391, 'Open sidebar stays in viewport');
    await page.locator('#sourcesBtn').click();
    assert.equal(await page.locator('#drawer').evaluate(e => e.classList.contains('show')), true, 'Source drawer opens');
    await page.locator('#drawerClose').click();
    await page.keyboard.press('l');
    await page.waitForTimeout(300);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390, 'Sidebar closes without overflow');
    await page.screenshot({ path: path.join(root, 'artifacts/clock-mobile.png'), fullPage: true });
    report.regressions.clock = { viewport: 390, scrollWidth: 390, openCloseAndSources: 'passed' };

    await page.goto(base + 'index.html');
    assert.equal(await page.locator('.card:visible').count(), 42, 'Gallery includes all works offline');
    await page.locator('#query').fill('wasserstein');
    assert.equal(await page.locator('.card:visible').count(), 1, 'Search model names');
    await page.locator('#query').fill('没有这样的关键词');
    assert.equal(await page.locator('.card:visible').count(), 0, 'Empty search result');
    assert.equal(await page.locator('#empty').isVisible(), true);
    await page.getByRole('button', { name: '清除筛选' }).click();
    await page.locator('#category').selectOption('地球与交通');
    assert.equal(await page.locator('.card:visible').count(), 5, 'Category filter');
    await page.getByRole('button', { name: '清除筛选' }).click();
    assert.equal(await page.locator('.card:visible').count(), 42, 'Reset filters');
    await page.setViewportSize({ width: 320, height: 700 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 320, 'Gallery fits 320px viewport');
    await page.screenshot({ path: path.join(root, 'artifacts/gallery-mobile.png'), fullPage: true });
    const noJS = await browser.newContext({ javaScriptEnabled: false });
    const staticPage = await noJS.newPage();
    await staticPage.goto(base + 'index.html');
    assert.equal(await staticPage.locator('.card a').count(), 42, 'All links remain without JavaScript');
    await noJS.close();
    report.regressions.gallery = { localServer: 'passed', directFile: 'not_tested_browser_policy', search: 'passed', category: 'passed', reset: 'passed', mobile320: 'passed', withoutJavaScript: 'passed' };
    await page.close();
    report.result = 'passed';
    console.log('PASS regressions:', JSON.stringify(report.regressions));
  } finally { await browser.close(); }
}

fs.mkdirSync(path.join(root, 'artifacts'), { recursive: true });
run().catch(error => { report.result = 'failed'; report.error = error.stack; console.error(error); process.exitCode = 1; }).finally(() => {
  fs.writeFileSync(path.join(root, 'artifacts/browser-report.json'), JSON.stringify(report, null, 2) + '\n');
  server.close();
});
