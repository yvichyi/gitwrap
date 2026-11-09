const { chromium } = require('playwright');
const dir = process.argv[2];
const ids = process.argv.slice(3);
(async () => {
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: 1360, height: 1000 }, deviceScaleFactor: 1 });
  for (const id of ids) {
    await pg.goto('file://' + dir + '/' + id + '.html');
    await pg.evaluate(() => document.fonts.ready);
    await pg.waitForTimeout(400);
    await pg.locator('section.board').screenshot({ path: `${dir}/r2_${id}.png` });
  }
  await b.close();
})();
