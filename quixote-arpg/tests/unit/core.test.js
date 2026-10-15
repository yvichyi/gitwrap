import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRng } from '../../src/sim/rng.js';
import { inSector, inLine, pushOutOfObstacles } from '../../src/sim/geometry.js';
import { generateItem } from '../../src/sim/items.js';
import { computeStats, currentTier, madnessEffects } from '../../src/sim/stats.js';
import { addXp, allocateAttr, createProgress, equipFromBag, learnTalent, addToBag, BAG_MAX } from '../../src/sim/progression.js';
import { DROP_WEIGHTS, RARITIES, SLOTS, UNIQUES } from '../../src/config/content.js';
import { MADNESS, PLAYER, MAX_LEVEL } from '../../src/config/balance.js';
import { XP } from '../../src/config/balance.js';

test('rng is reproducible and weighted picks follow weights', () => {
  const a = createRng(42);
  const b = createRng(42);
  for (let i = 0; i < 100; i++) assert.equal(a.next(), b.next());

  const rng = createRng(9);
  const counts = { x: 0, y: 0 };
  for (let i = 0; i < 20000; i++) counts[rng.weighted({ x: 3, y: 1 })] += 1;
  const share = counts.x / 20000;
  assert.ok(Math.abs(share - 0.75) < 0.02, `expected ~0.75, got ${share}`);
});

test('sector and line hit tests respect facing and reach', () => {
  // Facing +z (yaw 0). A 90 degree arc, reach 2.
  assert.equal(inSector(0, 0, 0, 90, 2, 0, 1.5), true);
  assert.equal(inSector(0, 0, 0, 90, 2, 0, -1.5), false, 'behind is outside');
  assert.equal(inSector(0, 0, 0, 90, 2, 1.9, 0.2), false, 'beyond the arc edge');
  assert.equal(inSector(0, 0, 0, 90, 2, 0, 2.4, 0.5), true, 'radius of the target counts');
  assert.equal(inSector(0, 0, 0, 90, 2, 0, 2.6, 0.5), false, 'but not beyond reach plus radius');

  assert.equal(inLine(0, 0, 0, 4, 1, 0, 3), true);
  assert.equal(inLine(0, 0, 0, 4, 1, 0.8, 3), false, 'outside the width');
  assert.equal(inLine(0, 0, 0, 4, 1, 0, 4.6), false, 'past the length');
});

test('circle is pushed out of static obstacles', () => {
  const e = { x: 0.5, z: 0, radius: 0.5 };
  const hit = pushOutOfObstacles(e, 0.5, [{ x: 0, z: 0, r: 1 }]);
  assert.ok(hit, 'reports the obstacle');
  assert.ok(Math.hypot(e.x, e.z) >= 1.0 - 1e-6, 'pushed to the surface');
  assert.ok(Math.abs(e.z) < 1e-9 && e.x > 0, 'pushed straight out along the approach');
});

test('generated items respect rarity affix counts and unique rules', () => {
  const rng = createRng(3);
  const counts = { common: 0, rare: 0, epic: 0, legendary: 0 };
  for (let i = 0; i < 6000; i++) {
    const item = generateItem(rng, { source: 'normal', stageIndex: 1, id: `t${i}` });
    counts[item.rarity] += 1;
    assert.equal(item.affixes.length, RARITIES[item.rarity].affixes, `affix count for ${item.rarity}`);
    const stats = new Set(item.affixes.map((a) => a.stat));
    assert.equal(stats.size, item.affixes.length, 'no duplicate affix stats');
    assert.ok(SLOTS.includes(item.slot));
    if (item.unique) {
      assert.equal(item.rarity, 'legendary', 'uniques are legendary only');
      assert.equal(UNIQUES[item.unique].slot, item.slot);
    }
    if (item.slot === 'weapon') assert.ok(item.dmg > 0, 'weapons carry damage');
  }
  const total = 6000;
  const w = DROP_WEIGHTS.normal;
  const sum = Object.values(w).reduce((s, v) => s + v, 0);
  for (const id of Object.keys(w)) {
    const expected = w[id] / sum;
    assert.ok(Math.abs(counts[id] / total - expected) < 0.02, `${id} share ${counts[id] / total} vs ${expected}`);
  }
});

test('boss drops never roll common', () => {
  const rng = createRng(11);
  for (let i = 0; i < 500; i++) {
    const item = generateItem(rng, { source: 'boss', stageIndex: 2, id: `b${i}` });
    assert.notEqual(item.rarity, 'common');
  }
});

test('level 1 stats match the design numbers', () => {
  const progress = createProgress();
  const s = computeStats(progress);
  assert.equal(s.maxHp, PLAYER.hpBase + 5 * PLAYER.hpPerVit + PLAYER.hpPerLevel);
  assert.ok(Math.abs(s.atk - (13 + 5 * PLAYER.atkPerStr + PLAYER.atkPerLevel)) < 1e-9);
  assert.ok(Math.abs(s.crit - (PLAYER.critBase + 5 * PLAYER.critPerAgi)) < 1e-9);
  assert.equal(s.guardMax, 100);
  assert.equal(s.maxMad, MADNESS.max);
});

test('xp curve levels up and grants points; max level clamps', () => {
  const p = createProgress();
  const gained = addXp(p, XP.curve(1));
  assert.equal(gained, 1);
  assert.equal(p.level, 2);
  assert.equal(p.attrPoints, PLAYER.attrPointsPerLevel);
  assert.equal(p.talentPoints, PLAYER.talentPointsPerLevel);

  addXp(p, 1e9);
  assert.equal(p.level, MAX_LEVEL);
  assert.equal(p.xp, 0);
});

test('attribute points allocate only with points available', () => {
  const p = createProgress();
  assert.equal(allocateAttr(p, 'str'), false);
  addXp(p, XP.curve(1));
  assert.equal(allocateAttr(p, 'str'), true);
  assert.equal(p.attrs.str, 6);
  assert.equal(allocateAttr(p, 'nope'), false);
});

test('talents unlock in order inside one school', () => {
  const p = createProgress();
  p.talentPoints = 5;
  assert.equal(learnTalent(p, 'lance-2'), false, 'skipping a tier is refused');
  assert.equal(learnTalent(p, 'lance-1'), true);
  assert.equal(p.school, 'lance');
  assert.equal(learnTalent(p, 'bulwark-1'), false, 'other schools are locked out');
  assert.equal(learnTalent(p, 'lance-2'), true);
  assert.deepEqual(p.talents, ['lance-1', 'lance-2']);
  assert.equal(p.talentPoints, 3);
});

test('equipping swaps items with the bag and bag is capped', () => {
  const p = createProgress();
  const rng = createRng(5);
  const item = generateItem(rng, { source: 'elite', stageIndex: 1, id: 'x1' });
  const armorItem = { ...item, slot: 'armor', id: 'armor-1', name: '测试胸甲', implicit: { armor: 9 }, affixes: [], unique: null, dmg: 0 };
  assert.equal(addToBag(p, armorItem), true);
  assert.equal(equipFromBag(p, 'armor-1'), true);
  assert.equal(p.equipped.armor.id, 'armor-1');
  assert.equal(p.bag.length, 0);

  for (let i = 0; i < BAG_MAX; i++) addToBag(p, { ...armorItem, id: `b${i}` });
  assert.equal(addToBag(p, { ...armorItem, id: 'overflow' }), false);
});

test('madness tiers and multipliers scale with the resource', () => {
  assert.equal(currentTier(0).id, 'sane');
  assert.equal(currentTier(30).id, 'frenzy');
  assert.equal(currentTier(69).id, 'frenzy');
  assert.equal(currentTier(70).id, 'mania');

  const low = madnessEffects(0, 100);
  const high = madnessEffects(100, 100);
  assert.equal(low.atkMult, 1);
  assert.ok(Math.abs(high.atkMult - (1 + MADNESS.atkBonus)) < 1e-9);
  assert.ok(high.damageTakenMult > low.damageTakenMult, 'more madness means more damage taken');
  assert.ok(high.staggerMult < low.staggerMult, 'more madness means less stability');
});
