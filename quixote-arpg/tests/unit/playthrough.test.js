import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createProgress, allocateAttr, learnTalent, equipFromBag } from '../../src/sim/progression.js';
import { Session } from '../../src/sim/session.js';
import { STAGES, SCHOOLS } from '../../src/config/content.js';
import { TICK } from '../../src/config/balance.js';
import { botInput } from '../sim/bot.js';

// A scripted player that spends points on vitality and strength, takes the lance branch, and equips upgrades.
// The campaign is deterministic for a given seed, so these runs guard the core loop: enter, fight, drop, level, next.
function play({ seed, stageIndex = 0, maxSeconds = 300, untilStageEnd = false }) {
  const progress = createProgress();
  const session = new Session({ progress, stageIndex, seed });
  session.startStage(stageIndex);
  const events = [];
  let t = 0;
  while (t < maxSeconds && session.state !== 'dead' && session.state !== 'victory') {
    if (session.state === 'clear') {
      if (untilStageEnd) break;
      session.nextStage();
      continue;
    }
    if (session.state !== 'stage') break;
    spend(progress);
    session.world.player.refresh(progress);
    events.push(...session.tick(botInput(session.world)));
    t += TICK;
  }
  return { session, progress, events, seconds: t };
}

function spend(progress) {
  let k = 0;
  while (progress.attrPoints > 0) allocateAttr(progress, ['vit', 'str', 'vit', 'agi'][k++ % 4]);
  for (const t of SCHOOLS.lance.talents) {
    if (progress.talentPoints > 0) learnTalent(progress, t.id);
  }
  for (const it of [...progress.bag]) {
    const cur = progress.equipped[it.slot];
    if (!cur || (it.dmg || 0) > (cur.dmg || 0)) equipFromBag(progress, it.id);
  }
}

test('stage 1 is a complete loop: waves spawn, monsters drop, the knight levels, the stage clears', () => {
  const run = play({ seed: 1001, untilStageEnd: true });
  const types = new Set(run.events.map((e) => e.type));
  for (const t of ['wave', 'kill', 'xp', 'levelUp', 'drop', 'hit', 'stageClear']) {
    assert.ok(types.has(t), `expected a "${t}" event during stage 1`);
  }
  assert.equal(run.session.state, 'clear', 'the stage ends in a clear, not a death');
  assert.ok(run.progress.level >= 3, 'the knight gained levels');
  assert.equal(run.session.world.waveIndex, STAGES[0].waves.length, 'every wave was spawned');
});

test('the scripted knight can finish the whole campaign with a built character', () => {
  const run = play({ seed: 3001, maxSeconds: 900 });
  assert.equal(run.session.state, 'victory', `ended in "${run.session.state}" after ${run.seconds.toFixed(0)}s`);
  assert.ok(run.events.some((e) => e.type === 'bossPhase'), 'the boss changed phase on the way');
  assert.ok(run.events.some((e) => e.type === 'bossDown'), 'the boss went down');
});

test('a fresh run is reproducible for the same seed', () => {
  const a = play({ seed: 77, untilStageEnd: true });
  const b = play({ seed: 77, untilStageEnd: true });
  assert.equal(a.seconds, b.seconds);
  assert.equal(a.events.length, b.events.length);
  assert.equal(a.session.world.stats.kills, b.session.world.stats.kills);
});
