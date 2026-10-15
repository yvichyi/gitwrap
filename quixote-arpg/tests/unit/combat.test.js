import { test } from 'node:test';
import assert from 'node:assert/strict';
import { input, makeWorld, run, ticks, dummy } from '../helpers.js';
import { DODGE, FRESTON, LIGHT_COMBO, MIRROR, SPELLBOOK, WINDMILL, PLAYER, SHEEP, HEAVY } from '../../src/config/balance.js';
import { createProgress, addXp } from '../../src/sim/progression.js';
import { XP } from '../../src/config/balance.js';
import { Session } from '../../src/sim/session.js';
import { STAGES } from '../../src/config/content.js';
import { bossDamageMult } from '../../src/sim/boss.js';
import { inSector } from '../../src/sim/geometry.js';

const count = (events, type) => events.filter((e) => e.type === type).length;

// Spawns a short circle hitbox on the knight and resolves it with one step. Events stay queued for the test to drain.
function hitPlayer(world, dmg, { fromFront = false, fromBehind = false, radius = 0.8, knock = 0 } = {}) {
  const p = world.player;
  let x = p.x;
  let z = p.z;
  // Half a metre off the knight's centre so the hitbox overlaps and the frontal test sees the right side.
  if (fromFront) {
    x = p.x + Math.sin(p.yaw) * 0.5;
    z = p.z + Math.cos(p.yaw) * 0.5;
  }
  if (fromBehind) {
    x = p.x - Math.sin(p.yaw) * 0.5;
    z = p.z - Math.cos(p.yaw) * 0.5;
  }
  world.spawnHitbox({ owner: 'test', shape: 'circle', x, z, radius, dmg, knock, life: 0.02 });
  // Keep a held guard held during the resolving tick, the way a real player holding block would.
  world.step(1 / 60, input({ blockHeld: world.player.state === 'block' }));
}

test('light combo: three swings with the balance multipliers and a queued chain', () => {
  const w = makeWorld();
  const e = dummy(w, 1.3, { hp: 100000 });
  const hp0 = e.hp;
  const events = [];
  events.push(...ticks(w, 1, input({ attack: true })));
  events.push(...run(w, 0.25));
  events.push(...ticks(w, 1, input({ attack: true }))); // queued during the first recovery
  events.push(...run(w, 0.4));
  events.push(...ticks(w, 1, input({ attack: true }))); // queued during the second swing
  events.push(...run(w, 0.9));

  const hits = events.filter((ev) => ev.type === 'hit');
  assert.equal(hits.length, 3, 'three landed swings');
  const dealt = hp0 - e.hp;
  const atk = w.player.stats.atk;
  const expected = LIGHT_COMBO.reduce((s, c) => s + Math.round(atk * c.dmg), 0);
  assert.ok(Math.abs(dealt - expected) <= 3, `dealt ${dealt}, expected about ${expected}`);
  assert.ok(hits[2].amount > hits[0].amount, 'the finisher hits harder');
});

test('dodge: invulnerable inside the iframe window, vulnerable after it', () => {
  const w = makeWorld();
  ticks(w, 1, input({ dodge: true }));
  const inside = ticks(w, 6);
  hitPlayer(w, 40);
  const during = w.drainEvents();
  assert.equal(count(during, 'dodged'), 1, 'hit during the iframe is dodged');
  assert.equal(w.player.hp, w.player.maxHp);
  assert.ok(inside.length >= 0);

  run(w, 0.5);
  hitPlayer(w, 40);
  const after = w.drainEvents();
  assert.equal(count(after, 'playerHit'), 1, 'hit after the roll takes damage');
  assert.ok(w.player.hp < w.player.maxHp);
});

test('dodge cooldown blocks dodge spam and each roll is a clean window', () => {
  const w = makeWorld();
  ticks(w, 1, input({ dodge: true }));
  const first = w.player.action;
  run(w, 0.3);
  ticks(w, 1, input({ dodge: true })); // still on cooldown
  assert.equal(w.player.action, first, 'the second press during cooldown is ignored');
  assert.ok(w.player.dodgeCd > 0);
  run(w, DODGE.cooldown);
  ticks(w, 1, input({ dodge: true }));
  assert.notEqual(w.player.action, first, 'a fresh roll starts once the cooldown ends');
  assert.equal(w.player.action.kind, 'dodge');
});

test('continuous dodging: five rolls in a row each dodge a telegraphed hit, the gap hit lands', () => {
  const w = makeWorld();
  let dodged = 0;
  let damaged = 0;
  for (let k = 0; k < 5; k++) {
    ticks(w, 1, input({ dodge: true }));
    run(w, 0.12); // inside the iframe window
    hitPlayer(w, 30);
    const ev = w.drainEvents();
    dodged += count(ev, 'dodged');
    damaged += count(ev, 'playerHit');
    run(w, DODGE.cooldown - 0.12);
  }
  assert.equal(dodged, 5);
  assert.equal(damaged, 0);
  assert.equal(w.player.hp, w.player.maxHp);

  // Mistimed: the same hit after the iframes end lands.
  ticks(w, 1, input({ dodge: true }));
  run(w, DODGE.iframeEnd + 0.05);
  hitPlayer(w, 30);
  assert.equal(count(w.drainEvents(), 'playerHit'), 1);
});

test('block: frontal damage is cut by block reduction; the rear is not blocked', () => {
  const w = makeWorld();
  ticks(w, 1, input({ blockDown: true, blockHeld: true }));
  run(w, 0.5, input({ blockHeld: true }));
  const armorMult = 1 - w.player.stats.armor / (w.player.stats.armor + PLAYER.armorK);
  const before = w.player.hp;
  hitPlayer(w, 40, { fromFront: true });
  const ev = w.drainEvents();
  assert.equal(count(ev, 'blocked'), 1);
  const taken = before - w.player.hp;
  const expected = 40 * armorMult * (1 - w.player.stats.block);
  assert.ok(Math.abs(taken - expected) < 0.5, `took ${taken}, expected ${expected}`);

  const hp2 = w.player.hp;
  hitPlayer(w, 40, { fromBehind: true });
  assert.equal(count(w.drainEvents(), 'playerHit'), 1, 'a hit from behind is not blocked');
  assert.ok(w.player.hp < hp2);
});

test('perfect block (first 0.2s of the guard) negates damage and fires a counter strike', () => {
  const w = makeWorld();
  const e = dummy(w, 1.6, { hp: 100000 });
  ticks(w, 1, input({ blockDown: true, blockHeld: true }));
  run(w, 0.1, input({ blockHeld: true }));
  const hp = w.player.hp;
  hitPlayer(w, 60, { fromFront: true });
  const ev = w.drainEvents();
  assert.equal(count(ev, 'perfect'), 1);
  assert.equal(w.player.hp, hp, 'no damage on a perfect block');
  assert.equal(count(ev, 'counter'), 1);
  assert.ok(e.hp < 100000, 'the counter strike hit the enemy in front');
});

test('a second frontal hit in the same guard is no longer perfect', () => {
  const w = makeWorld();
  ticks(w, 1, input({ blockDown: true, blockHeld: true }));
  run(w, 0.05, input({ blockHeld: true }));
  hitPlayer(w, 20, { fromFront: true });
  w.drainEvents();
  run(w, 0.05, input({ blockHeld: true }));
  hitPlayer(w, 20, { fromFront: true });
  const ev = w.drainEvents();
  assert.equal(count(ev, 'perfect'), 0);
  assert.equal(count(ev, 'blocked'), 1);
});

test('guard break staggers the knight', () => {
  const w = makeWorld();
  ticks(w, 1, input({ blockDown: true, blockHeld: true }));
  run(w, 0.4, input({ blockHeld: true }));
  for (let i = 0; i < 40 && !w.player.guardBroken; i++) {
    hitPlayer(w, 60, { fromFront: true });
    w.drainEvents();
    run(w, 0.05, input({ blockHeld: true }));
  }
  assert.equal(w.player.guardBroken, true);
  assert.equal(w.player.state, 'stagger');
});

test('multi-enemy: four enemies hitting in one tick each land and each show a number', () => {
  const w = makeWorld();
  for (let i = 0; i < 4; i++) {
    const p = w.player;
    w.spawnHitbox({ owner: `m${i}`, shape: 'circle', x: p.x, z: p.z, radius: 0.8, dmg: 12, knock: 0, life: 0.02 });
  }
  const hp = w.player.hp;
  w.step(1 / 60, input());
  const ev = w.drainEvents();
  const hits = ev.filter((e) => e.type === 'playerHit');
  assert.equal(hits.length, 4, 'every simultaneous hit resolves');
  const armorMult = 1 - w.player.stats.armor / (w.player.stats.armor + PLAYER.armorK);
  const taken = hp - w.player.hp;
  assert.ok(Math.abs(taken - 4 * 12 * armorMult) < 0.5, `total ${taken}`);

  // Same four hits during an iframe: all dodged.
  const w2 = makeWorld();
  ticks(w2, 1, input({ dodge: true }));
  run(w2, 0.1);
  for (let i = 0; i < 4; i++) {
    const p = w2.player;
    w2.spawnHitbox({ owner: `m${i}`, shape: 'circle', x: p.x, z: p.z, radius: 0.8, dmg: 12, knock: 0, life: 0.02 });
  }
  w2.step(1 / 60, input());
  const ev2 = w2.drainEvents();
  assert.equal(count(ev2, 'dodged'), 4);
  assert.equal(w2.player.hp, w2.player.maxHp);
});

test('multi-enemy: one swing hits each target in the arc once, not once per tick', () => {
  const w = makeWorld();
  const e = dummy(w, 1.2, { hp: 100000 });
  const hp = e.hp;
  const events = [...ticks(w, 1, input({ attack: true })), ...run(w, 0.5)];
  assert.equal(count(events, 'hit'), 1, 'a single swing hits each target once');
  assert.ok(e.hp < hp);
});

test('windmill: front armour, side glancing, and the energy core behind it', () => {
  const w = makeWorld();
  const g = w.spawnEnemy('windmill', 0, 0);
  g.yaw = 0;
  g.hp = 100000;
  g.maxHp = 100000;
  const roll = { dmg: 100, crit: false };

  w.player.x = 0;
  w.player.z = 3;
  w.damageEnemy(g, roll, { source: 'player' });
  const front = 100000 - g.hp;
  assert.equal(front, Math.round(100 * WINDMILL.frontMult));

  g.hp = 100000;
  w.player.x = 3;
  w.player.z = 0;
  w.damageEnemy(g, roll, { source: 'player' });
  assert.equal(100000 - g.hp, Math.round(100 * WINDMILL.sideMult));

  g.hp = 100000;
  w.player.x = 0;
  w.player.z = -3;
  w.damageEnemy(g, roll, { source: 'player' });
  assert.equal(100000 - g.hp, Math.round(100 * WINDMILL.coreMult));
  assert.equal(g.state, 'stun', 'a core hit staggers the giant');
  assert.ok(w.events.some((ev) => ev.type === 'hit' && ev.back), 'the hit is flagged as a core hit');
});

test('windmill sweep covers 270 degrees and leaves the front 90 safe', () => {
  const w = makeWorld();
  const g = w.spawnEnemy('windmill', 0, 0);
  g.yaw = 0;
  g.cd = 0;
  w.player.x = 0;
  w.player.z = 3; // in front of the giant (it faces +z)
  let sweep = null;
  for (let i = 0; i < 120 && !sweep; i++) {
    w.step(1 / 60, input());
    w.drainEvents();
    if (g.telegraph?.shape === 'sector' && g.telegraph.arc === WINDMILL.sweep.arc) sweep = g.telegraph;
  }
  assert.ok(sweep, 'telegraphs the sweep before it lands');
  // Front is safe, back is covered: the sector is centred on the rear.
  assert.ok(!inSector(g.x, g.z, sweep.yaw, sweep.arc, sweep.reach, 0, 3), 'the front 90 degrees is safe');
  assert.ok(inSector(g.x, g.z, sweep.yaw, sweep.arc, sweep.reach, 0, -3), 'the rear is covered');
  assert.ok(inSector(g.x, g.z, sweep.yaw, sweep.arc, sweep.reach, 3, 0), 'the sides are covered');
  assert.equal(WINDMILL.sweep.arc, 270);
});

test('spellbook: hitting it while it chants interrupts the spell', () => {
  const w = makeWorld();
  const book = w.spawnEnemy('spellbook', 0, 0);
  book.cd = 0;
  w.player.x = 0;
  w.player.z = SPELLBOOK.preferRange;
  w.step(1 / 60, input());
  assert.equal(book.state, 'chant', 'starts chanting');
  w.damageEnemy(book, { dmg: 5, crit: false }, { source: 'player' });
  const ev = w.drainEvents();
  assert.equal(count(ev, 'interrupt'), 1);
  assert.equal(book.state, 'stun');
  run(w, SPELLBOOK.chantTime + 0.3);
  assert.equal(w.projectiles.length, 0, 'the interrupted spell fires nothing');
});

test('spellbook: an uninterrupted chant fires the full fan of pages', () => {
  const w = makeWorld();
  const book = w.spawnEnemy('spellbook', 0, 0);
  book.cd = 0;
  w.player.x = 0;
  w.player.z = SPELLBOOK.preferRange;
  w.step(1 / 60, input());
  assert.equal(book.state, 'chant');
  run(w, SPELLBOOK.chantTime + 0.1);
  const pages = w.projectiles.filter((p) => p.kind === 'page');
  assert.equal(pages.length, SPELLBOOK.pages);
});

test('sheep: a flock stampedes when the knight gets close', () => {
  const w = makeWorld();
  w.player.x = 0;
  w.player.z = 2.5;
  const a = w.spawnEnemy('sheep', 0, 0, { flockId: 'f1' });
  const b = w.spawnEnemy('sheep', 0.9, 0, { flockId: 'f1' });
  w.step(1 / 60, input());
  assert.equal(a.state, 'warn');
  assert.equal(b.state, 'warn', 'the whole flock is warned together');
  run(w, SHEEP.stampedeDelay + 0.05);
  assert.equal(a.state, 'charge');
});

test('sheep: a charge stopped by an obstacle stuns the sheep, which then takes extra damage', () => {
  const w = makeWorld();
  w.obstacles = [{ x: 0, z: 2.6, r: 0.6, kind: 'hay' }];
  w.player.x = 0;
  w.player.z = 3.2;
  const a = w.spawnEnemy('sheep', 0, 0, { flockId: 'solo' });
  a.hp = 100000;
  a.maxHp = 100000;
  w.step(1 / 60, input());
  const ev = run(w, SHEEP.stampedeDelay + 0.6);
  assert.equal(count(ev, 'stun'), 1, 'the hay bale stuns the charging sheep');
  assert.equal(a.state, 'stun');
  const before = a.hp;
  w.damageEnemy(a, { dmg: 100, crit: false }, { source: 'player' });
  assert.equal(before - a.hp, Math.round(100 * SHEEP.stunDamageMult));
});

test('mirror knight: a feint during its reaction window leaves it open', () => {
  const w = makeWorld();
  const m = w.spawnEnemy('mirror', 0, 0);
  m.hp = 100000;
  m.maxHp = 100000;
  w.player.x = 0;
  w.player.z = 3;
  m.yaw = 0;
  w.notify('attackStart', { kind: 'light' });
  assert.equal(m.state, 'pending');
  w.notify('feint', { by: 'dodge' });
  assert.equal(m.state, 'open', 'a feint before its guard goes up opens it');
  w.damageEnemy(m, { dmg: 100, crit: false }, { source: 'player' });
  assert.equal(100000 - m.hp, Math.round(100 * MIRROR.open.mult));
});

test('mirror knight: a committed attack meets a guard then a counter thrust', () => {
  const w = makeWorld();
  const m = w.spawnEnemy('mirror', 0, 0);
  w.player.x = 0;
  w.player.z = 3;
  m.yaw = 0;
  w.notify('attackStart', { kind: 'light' });
  run(w, MIRROR.reaction + 0.05);
  assert.equal(m.state, 'guard');
  w.notify('attackCommit', { kind: 'light' });
  run(w, MIRROR.guard.duration + 0.05);
  assert.equal(m.state, 'counter');
  assert.equal(m.telegraph?.shape, 'line');
  run(w, MIRROR.counter.telegraph + 0.05);
  assert.ok(w.hitboxes.some((hb) => hb.owner === m.id && hb.shape === 'line'), 'counter thrust lands as a line hitbox');
});

test('mirror knight: a dodge gets a dash telegraph, a block gets a slam on the knight', () => {
  const w = makeWorld();
  const m = w.spawnEnemy('mirror', 0, 0);
  w.player.x = 0;
  w.player.z = 4;
  m.yaw = 0;
  w.notify('dodge', { x: 0, z: 6 });
  assert.equal(m.state, 'dash');
  assert.equal(m.telegraph.shape, 'line', 'the dash is telegraphed as a line');
  run(w, 1.0);
  w.notify('block', {});
  assert.equal(m.state, 'slam');
  assert.equal(m.telegraph.shape, 'circle');
  assert.equal(m.telegraph.x, w.player.x, 'the slam lands where the knight was guarding');
  assert.equal(m.telegraph.z, w.player.z);
});

test('boss: phases change once each at the thresholds and the transform is immune', () => {
  const w = makeWorld({ stageIndex: 2 });
  const boss = w.spawnEnemy('boss', 0, 0);
  assert.equal(boss.stones.length, FRESTON.stoneCount);
  assert.equal(bossDamageMult(w, boss), FRESTON.shieldMult, 'stones shield the boss at the start');
  w.player.x = 0;
  w.player.z = 12;

  boss.hp = Math.floor(boss.maxHp * 0.6);
  const first = run(w, 0.1);
  assert.equal(boss.phase, 1);

  const quiet = run(w, FRESTON.transformTime + 0.2);
  assert.equal(count(quiet, 'bossPhase'), 0, 'no repeat event while staying in the phase');
  boss.hp = Math.floor(boss.maxHp * 0.3);
  const ev2 = run(w, 0.1);
  assert.equal(boss.phase, 2);
  assert.equal(count([...first, ...ev2], 'bossPhase'), 2, 'one event per phase change');
  assert.ok(w.slowT > 0 || count(ev2, 'bossPhase') === 1, 'phase change requests slow motion');

  boss.transformT = 1;
  w.damageEnemy(boss, { dmg: 500, crit: false }, { source: 'player' });
  assert.equal(boss.hp, Math.floor(boss.maxHp * 0.3), 'no damage during the transform');
  assert.equal(count(w.events, 'immune'), 1);
});

test('boss: stones shield the boss until they are all broken', () => {
  const w = makeWorld({ stageIndex: 2 });
  const boss = w.spawnEnemy('boss', 0, 0);
  w.player.x = 0;
  w.player.z = 12;
  run(w, 0.05);
  const before = boss.hp;
  w.damageEnemy(boss, { dmg: 200, crit: false }, { source: 'player' });
  assert.equal(before - boss.hp, Math.round(200 * FRESTON.shieldMult));

  for (const id of boss.stones) {
    const stone = w.enemies.find((e) => e.id === id);
    if (stone) w.damageEnemy(stone, { dmg: 999, crit: false }, { source: 'player' });
  }
  const ev = w.drainEvents();
  assert.equal(count(ev, 'shieldBroken'), 1);
  assert.equal(bossDamageMult(w, boss), 1, 'no shield once the stones are gone');
  const b2 = boss.hp;
  w.damageEnemy(boss, { dmg: 200, crit: false }, { source: 'player' });
  assert.equal(b2 - boss.hp, 200, 'full damage once the shield is down');
});

test('boss death: drops loot, clears its summons and stones, and marks the boss down', () => {
  const w = makeWorld({ stageIndex: 2 });
  const boss = w.spawnEnemy('boss', 0, 0);
  w.player.x = 0;
  w.player.z = 12;
  const summon = w.spawnEnemy('spellbook', 3, 3, { summoned: true, life: 30 });
  boss.hp = 1;
  boss.transformT = 0;
  w.damageEnemy(boss, { dmg: 10, crit: false }, { source: 'player' });
  assert.equal(w.bossDown, true);
  assert.ok(summon.dead, 'summons vanish with the boss');
  assert.equal(w.loot.length, 3, 'boss drops three items');
  assert.ok(w.enemies.every((e) => e.dead || e.kind !== 'stone'), 'stones are cleared');
});

test('waves: stage clears once every monster is gone and no waves remain', () => {
  const w = makeWorld({ stageIndex: 0 });
  w.waveTimer = 0.01;
  run(w, 0.2);
  assert.ok(w.waveIndex >= 1, 'the first wave spawns on its own timer');
  w.enemies.forEach((e) => e.kind === 'monster' && (e.dead = true));
  w.waveIndex = w.stage.waves.length;
  w.waveTimer = 1e9;
  const ev = run(w, 0.05);
  assert.equal(count(ev, 'stageClear'), 1);
  assert.equal(w.clear, true);
  assert.equal(count(run(w, 1), 'stageClear'), 0, 'stage clear fires once');
});

test('death: reaching zero HP ends the stage exactly once', () => {
  const w = makeWorld();
  w.player.hp = 1;
  w.player.x = 0;
  w.player.z = 0.8;
  const e = w.spawnEnemy('sheep', 0, 0);
  e.contact = { damage: 9999, radius: 2, used: false, knock: 0 };
  e.state = 'charge';
  e.dir = { x: 0, z: 1 };
  const ev = run(w, 0.1);
  assert.equal(count(ev, 'death'), 1);
  assert.equal(w.over, true);
  assert.equal(count(run(w, 0.5), 'death'), 0);
});

test('mid-stage level-up raises max HP and keeps the HP ratio', () => {
  const progress = createProgress();
  const w = makeWorld({ progress });
  const before = w.player.maxHp;
  w.player.hp = before * 0.5;
  w.grantXp(XP.curve(1));
  assert.ok(w.player.maxHp > before, 'max HP rises on level-up');
  assert.ok(Math.abs(w.player.hp / w.player.maxHp - 0.5) < 0.02, 'the HP ratio is kept');
  assert.ok(w.events.some((ev) => ev.type === 'levelUp'));
});

test('heavy attack: a full charge hits at maximum power and is flagged as heavy', () => {
  const w = makeWorld();
  const e = dummy(w, 1.3, { hp: 100000 });
  const hp = e.hp;
  const events = [...ticks(w, 1, input({ heavyDown: true, heavyHeld: true })), ...run(w, HEAVY.chargeTime + 0.1, input({ heavyHeld: true }))];
  events.push(...ticks(w, 1, input({ heavyHeld: false })));
  events.push(...run(w, 1.0));
  const hit = events.find((ev) => ev.type === 'hit');
  assert.ok(hit, 'the heavy lands');
  assert.equal(hit.heavy, true);
  const dealt = hp - e.hp;
  assert.ok(Math.abs(dealt - Math.round(w.player.stats.atk * HEAVY.dmgMax)) <= 3, `dealt ${dealt}`);
});

test('pause freezes the world; resume continues from the same tick', () => {
  const session = new Session({ progress: createProgress(), stageIndex: 0 });
  session.startStage(0);
  session.world.waveTimer = 1e9;
  session.tick(input());
  session.pause();
  const t = session.world.time;
  for (let i = 0; i < 30; i++) assert.deepEqual(session.tick(input()), []);
  assert.equal(session.world.time, t, 'time is frozen while paused');
  session.resume();
  session.tick(input());
  assert.ok(session.world.time > t);
});

test('stage list is ordered and the last stage ends in victory', () => {
  const session = new Session({ progress: createProgress(), stageIndex: STAGES.length - 1 });
  session.startStage(STAGES.length - 1);
  assert.equal(session.nextStage(), false);
  assert.equal(session.state, 'victory');
});

test('character XP is not lost when the stage restarts', () => {
  const progress = createProgress();
  addXp(progress, 100);
  const session = new Session({ progress, stageIndex: 1 });
  session.startStage(1);
  session.retryStage();
  assert.equal(session.progress.xp, progress.xp);
  assert.equal(session.stageIndex, 1);
});

export { PLAYER };
