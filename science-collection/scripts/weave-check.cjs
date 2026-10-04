'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),html=fs.readFileSync(path.join(root,'未完成的织物.html'),'utf8');
const source=html.slice(html.indexOf('function* solve(input)'),html.indexOf('const fresh='));
const solve=vm.runInNewContext(source+';solve');
const tiles=[0,3,6,12,9,5,10,15];
function finish(input){const it=solve(input);let r;do{r=it.next()}while(!r.done);return r.value;}
function valid(grid,w,h,pins={},allowed=tiles.map(()=>true)){
 assert.equal(grid.length,w*h);
 grid.forEach((t,i)=>{assert(allowed[t]);if(pins[i]!==undefined)assert.equal(t,pins[i]);const v=tiles[t],x=i%w,y=Math.floor(i/w);if(x===0)assert(!(v&8));if(y===0)assert(!(v&1));if(x===w-1)assert(!(v&2));if(y===h-1)assert(!(v&4));if(x<w-1)assert.equal(Boolean(v&2),Boolean(tiles[grid[i+1]]&8));if(y<h-1)assert.equal(Boolean(v&4),Boolean(tiles[grid[i+w]]&1));});
}
const base={w:12,h:12,tiles,allowed:tiles.map(()=>true),pins:{},seed:73129};
for(let seed=1;seed<=60;seed++){const input={...base,seed},a=finish(input);assert.equal(a.type,'done');valid(a.result,12,12);assert.equal(JSON.stringify(a),JSON.stringify(finish(input)));const pins=Object.fromEntries(a.result.map((v,i)=>[i,v]).filter(([i])=>i%7===0)),b=finish({...input,pins,seed:seed+70});assert.equal(b.type,'done');valid(b.result,12,12,pins);}
// Independent exhaustive 2x2 oracle, including pinned contradictions and restricted grammars.
function brute(allowed,pins){for(let a=0;a<8;a++)for(let b=0;b<8;b++)for(let c=0;c<8;c++)for(let d=0;d<8;d++){const g=[a,b,c,d];if(g.some((v,i)=>!allowed[v]||(pins[i]!==undefined&&pins[i]!==v)))continue;const [A,B,C,D]=g.map(v=>tiles[v]);if((A&9)||(B&3)||(C&12)||(D&6))continue;if(Boolean(A&2)!==Boolean(B&8)||Boolean(A&4)!==Boolean(C&1)||Boolean(B&4)!==Boolean(D&1)||Boolean(C&2)!==Boolean(D&8))continue;return true;}return false;}
for(let mask=0;mask<256;mask++){const allowed=tiles.map((_,i)=>Boolean(mask&(1<<i)));for(const pins of [{},{0:2},{0:2,1:0}]){const a=finish({w:2,h:2,tiles,allowed,pins,seed:11});assert.equal(a.type==='done',brute(allowed,pins),`mask=${mask} pins=${JSON.stringify(pins)}`);if(a.type==='done')valid(a.result,2,2,pins,allowed);}}
assert.equal(finish({...base,pins:{65:1,66:5}}).type,'impossible');
assert.equal(finish({...base,budget:0}).type,'budget');
console.log('PASS algorithm: 60 seeds, deterministic replay, pinned regeneration, 768 independent tiny-grid oracle comparisons, conflict and budget distinction.');
if(process.argv.includes('--browser'))(async()=>{
 const{chromium}=require('playwright');const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
 const context=await browser.newContext({viewport:{width:1440,height:1100},acceptDownloads:true,reducedMotion:'reduce'});const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));const url='file://'+path.join(root,'未完成的织物.html');const settled=()=>page.waitForFunction(()=>Weave.snapshot().ready);await page.goto(url);await settled();
 valid((await page.evaluate(()=>Weave.snapshot())).grid,12,12);
 fs.mkdirSync(path.join(root,'artifacts'),{recursive:true});await page.screenshot({path:path.join(root,'artifacts/weave-desktop.png'),fullPage:true});
 const initial=await page.evaluate(()=>Weave.snapshot());
 await page.locator('#weave').click();await settled();assert.notEqual((await page.evaluate(()=>Weave.snapshot())).state.seed,initial.state.seed);
 await page.locator('#undo').click();await settled();assert.deepEqual((await page.evaluate(()=>Weave.snapshot())).grid,initial.grid);
 await page.locator('#redo').click();await settled();
 await page.locator('#conflict-demo').click();await page.waitForFunction(()=>document.querySelectorAll('.fault').length===2);assert(await page.locator('#svg-export').isDisabled());assert.match(await page.locator('#status').textContent(),/接不上/);
 await page.locator('#erase').click();await page.locator('.cell').nth(66).click();await settled();const repaired=await page.evaluate(()=>Weave.snapshot());valid(repaired.grid,12,12,repaired.state.pins);assert.equal(repaired.state.pins[65],1);
 await page.locator('.cell').nth(65).focus();await page.keyboard.press('Delete');await settled();assert.equal((await page.evaluate(()=>Weave.snapshot())).state.pins[65],undefined);
 await page.keyboard.press('ArrowRight');assert.equal(await page.locator('.cell').nth(66).evaluate(e=>e===document.activeElement),true);
 await page.locator('#rules-mode').click();await page.locator('#tiles button').nth(7).click();await settled();assert(!(await page.evaluate(()=>Weave.snapshot())).grid.includes(7));
 const svg=await page.evaluate(()=>Weave.exportSVG());assert(svg.includes('width="320mm"'));assert(!svg.includes('undefined'));await page.evaluate(svg=>{const d=new DOMParser().parseFromString(svg,'image/svg+xml');if(d.querySelector('parsererror'))throw Error('invalid SVG');},svg);
 const dl=page.waitForEvent('download');await page.locator('#svg-export').click();const file=await dl;assert(file.suggestedFilename().endsWith('.svg'));
 const beforeImport=await page.evaluate(()=>Weave.snapshot());await page.locator('#file').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from('{"version":2}')});await page.waitForFunction(()=>document.getElementById('status').textContent.includes('未载入'));assert.deepEqual((await page.evaluate(()=>Weave.snapshot())).state,beforeImport.state);
 await page.locator('#file').setInputFiles({name:'saved.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(initial.state))});await settled();assert.deepEqual((await page.evaluate(()=>Weave.snapshot())).grid,initial.grid);
 await page.reload();await settled();assert.deepEqual((await page.evaluate(()=>Weave.snapshot())).grid,initial.grid);
 for(const width of [390,320]){await page.setViewportSize({width,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(root,`artifacts/weave-mobile-${width}.png`),fullPage:true});}
 // Exercise real tap creation on mobile after loading a known valid seed.
 await page.locator('#tiles button').nth(initial.grid[65]).click();await page.locator('.cell').nth(65).click();await settled();assert.equal((await page.evaluate(()=>Weave.snapshot())).state.pins[65],initial.grid[65]);
 assert.deepEqual(errors,[]);await context.close();
 const fallback=await browser.newContext();await fallback.addInitScript(()=>{window.Worker=function(){throw Error('unavailable')};Object.defineProperty(window,'localStorage',{get(){throw Error('blocked')}});});const fp=await fallback.newPage();await fp.goto(url);await fp.waitForFunction(()=>Weave.snapshot().ready);valid((await fp.evaluate(()=>Weave.snapshot())).grid,12,12);assert.match(await fp.locator('#save-note').textContent(),/未能保存/);await fallback.close();await browser.close();console.log('PASS browser: offline loading, worker/fallback, blocked storage, SVG/recipe roundtrip, undo/redo, grammar, conflict recovery, keyboard, 320/390px layouts and mobile input.');
})().catch(e=>{console.error(e);process.exit(1)});
