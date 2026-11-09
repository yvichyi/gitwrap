const { chromium } = require('playwright');
const dir = process.argv[2];
(async () => {
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: 1200, height: 900 }, deviceScaleFactor: 1 });
  await pg.goto('file://' + dir + '/land.html');
  await pg.evaluate(() => document.fonts.ready);
  await pg.waitForTimeout(500);
  for (const id of ['neon', 'device', 'game', 'acid', 'dusk']) await pg.locator('#l_' + id).screenshot({ path: `${dir}/land_${id}.png` });
  await b.close();
})();
