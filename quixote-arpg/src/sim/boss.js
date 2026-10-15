import { DEG, dirOf, dist, pushOutOfObstacles, yawOf } from './geometry.js';
import { FRESTON } from '../config/balance.js';
import { clearTelegraph, makeEnemy, moveBy, setTelegraph, turnTo } from './enemies.js';

// Attack rotation per phase. Phases start at the thresholds in FRESTON.phases.
const CYCLES = [
  ['verse', 'summon', 'blink'],
  ['ram', 'ring', 'blink', 'summon'],
  ['spiral', 'ring', 'clone', 'blink', 'ram', 'verse'],
];

const PREFERRED_RANGE = 7;

export function createBoss(world, id, x, z, hpMult) {
  const boss = makeEnemy('boss', id, x, z, { hpMult });
  Object.assign(boss, {
    phase: 0,
    transformT: 0,
    cycleIndex: 0,
    burst: null,
    busy: false,
    stones: [],
  });
  for (let i = 0; i < FRESTON.stoneCount; i++) {
    const orbit = (i / FRESTON.stoneCount) * Math.PI * 2;
    const stone = world.spawnEnemy('stone', x, z, { parent: boss.id, orbit, hpMult: 1 });
    stone.hp = FRESTON.stoneHp;
    stone.maxHp = FRESTON.stoneHp;
    boss.stones.push(stone.id);
  }
  return boss;
}

export function aliveStones(world, boss) {
  return world.enemies.filter((e) => e.kind === 'stone' && e.parent === boss.id && !e.dead);
}

export function bossPhaseFor(ratio) {
  let phase = 0;
  for (let i = 1; i < FRESTON.phases.length; i++) {
    if (ratio <= FRESTON.phases[i].at) phase = i;
  }
  return phase;
}

export function updateBoss(boss, dt, world) {
  const p = world.player;
  const stones = aliveStones(world, boss);
  const ratio = boss.hp / boss.maxHp;

  for (const s of stones) {
    s.orbit += dt * 0.9;
    s.x = boss.x + Math.cos(s.orbit) * FRESTON.stoneOrbit;
    s.z = boss.z + Math.sin(s.orbit) * FRESTON.stoneOrbit;
    s.flash = Math.max(0, s.flash - dt);
  }

  const nextPhase = bossPhaseFor(ratio);
  if (nextPhase > boss.phase) {
    boss.phase = nextPhase;
    boss.transformT = FRESTON.transformTime;
    boss.cycleIndex = 0;
    boss.cd = 1.0;
    boss.busy = false;
    boss.burst = null;
    clearTelegraph(boss);
    boss.state = 'transform';
    world.emit('bossPhase', { phase: nextPhase, name: FRESTON.phases[nextPhase].name, x: boss.x, z: boss.z });
  }

  if (boss.transformT > 0) {
    boss.transformT = Math.max(0, boss.transformT - dt);
    if (boss.transformT === 0) boss.state = 'idle';
    return;
  }

  const aim = yawOf(p.x - boss.x, p.z - boss.z);
  turnTo(boss, aim, 2.4, dt);

  if (boss.burst) {
    updateBurst(boss, dt, world);
  }

  if (!boss.busy && !boss.telegraph) {
    const d = dist(boss.x, boss.z, p.x, p.z);
    const dir = dirOf(aim);
    if (d > PREFERRED_RANGE + 2) moveBy(boss, dir.x, dir.z, dt * FRESTON.speed, world);
    else if (d < PREFERRED_RANGE - 2) moveBy(boss, -dir.x, -dir.z, dt * FRESTON.speed, world);
    boss.cd = Math.max(0, boss.cd - dt);
    if (boss.cd <= 0) {
      const cycle = CYCLES[boss.phase];
      const name = cycle[boss.cycleIndex % cycle.length];
      boss.cycleIndex += 1;
      boss.cd = FRESTON.phases[boss.phase].cooldown;
      startAttack(boss, name, world);
    }
  }
}

function startAttack(boss, name, world) {
  const p = world.player;
  boss.busy = true;
  switch (name) {
    case 'verse': {
      const aim = yawOf(p.x - boss.x, p.z - boss.z);
      const v = FRESTON.verse;
      setTelegraph(boss, { shape: 'sector', x: boss.x, z: boss.z, yaw: aim, arc: v.fanDeg, reach: 14, total: v.telegraph, tone: 'danger' }, (b, w) => {
        fanPages(b, w, v.pages, v.fanDeg, aim, v.pageSpeed, v.dmg, v.life);
        finish(b);
      });
      break;
    }
    case 'ring': {
      const r = FRESTON.ring;
      const aim = yawOf(p.x - boss.x, p.z - boss.z);
      setTelegraph(boss, { shape: 'ring', x: boss.x, z: boss.z, yaw: aim, radius: 14, gapDeg: r.gapDeg, total: r.telegraph, tone: 'danger' }, (b, w) => {
        for (let i = 0; i < r.pages; i++) {
          const a = (i / r.pages) * Math.PI * 2;
          if (Math.abs(wrapDelta(a, aim)) < (r.gapDeg * DEG) / 2) continue;
          w.spawnProjectile({ x: b.x, z: b.z, vx: Math.sin(a) * r.pageSpeed, vz: Math.cos(a) * r.pageSpeed, dmg: r.dmg, life: r.life, radius: 0.3, owner: 'enemy', kind: 'page' });
        }
        finish(b);
      });
      break;
    }
    case 'spiral': {
      const s = FRESTON.spiral;
      setTelegraph(boss, { shape: 'ring', x: boss.x, z: boss.z, yaw: 0, radius: 6, gapDeg: 0, total: s.telegraph, tone: 'warn' }, (b) => {
        b.burst = { kind: 'spiral', round: 0, timer: 0 };
      });
      // The burst keeps firing after the telegraph; updateBurst clears busy when the last round is out.
      break;
    }
    case 'ram': {
      const r = FRESTON.ram;
      const aim = yawOf(p.x - boss.x, p.z - boss.z);
      setTelegraph(boss, { shape: 'line', x: boss.x, z: boss.z, yaw: aim, length: r.length, width: r.width, total: r.telegraph, tone: 'danger' }, (b, w) => {
        for (const off of [-12, 0, 12]) {
          const a = aim + off * DEG;
          const sheep = w.spawnEnemy('sheep', b.x, b.z, { summoned: true, life: 6 });
          sheep.state = 'charge';
          sheep.t = 0;
          sheep.dir = dirOf(a);
          sheep.contact = { damage: 22, radius: sheep.radius + 0.35, used: false, knock: 2.6 };
        }
        finish(b);
      });
      break;
    }
    case 'blink': {
      const b0 = FRESTON.blink;
      const spot = pickBlinkSpot(world, p);
      setTelegraph(boss, { shape: 'circle', x: spot.x, z: spot.z, radius: b0.slamRadius, total: b0.telegraph, tone: 'warn' }, (b, w) => {
        b.x = spot.x;
        b.z = spot.z;
        pushOutOfObstacles(b, b.radius, w.obstacles);
        w.spawnHitbox({ owner: b.id, shape: 'circle', x: b.x, z: b.z, radius: b0.slamRadius, dmg: b0.dmg, knock: 2.0, life: 0.15 });
        finish(b);
      });
      break;
    }
    case 'summon': {
      const spot = pickPointNear(world, boss, 4.5);
      setTelegraph(boss, { shape: 'circle', x: spot.x, z: spot.z, radius: 1.2, total: 1.0, tone: 'warn' }, (b, w) => {
        const count = w.enemies.filter((e) => e.type === 'spellbook' && e.summoned && !e.dead).length;
        if (count < FRESTON.summonCap) {
          w.spawnEnemy('spellbook', spot.x, spot.z, { summoned: true, life: 25, strafeSign: w.rng.chance(0.5) ? -1 : 1 });
        }
        finish(b);
      });
      break;
    }
    case 'clone': {
      setTelegraph(boss, { shape: 'circle', x: boss.x, z: boss.z, radius: 1.5, total: 1.2, tone: 'warn' }, (b, w) => {
        w.spawnEnemy('mirror', b.x, b.z, { summoned: true, life: 40, hpMult: 0.5 });
        finish(b);
      });
      break;
    }
    default:
      finish(boss);
  }
}

function finish(boss) {
  boss.busy = false;
}

function updateBurst(boss, dt, world) {
  const burst = boss.burst;
  const s = FRESTON.spiral;
  burst.timer -= dt;
  if (burst.timer > 0) return;
  burst.timer = s.interval;
  const offset = burst.round * 0.5 * (Math.PI / s.perRound);
  for (let i = 0; i < s.perRound; i++) {
    const a = offset + (i / s.perRound) * Math.PI * 2;
    world.spawnProjectile({ x: boss.x, z: boss.z, vx: Math.sin(a) * s.pageSpeed, vz: Math.cos(a) * s.pageSpeed, dmg: s.dmg, life: s.life, radius: 0.3, owner: 'enemy', kind: 'page' });
  }
  burst.round += 1;
  if (burst.round >= s.rounds) {
    boss.burst = null;
    boss.busy = false;
  }
}

function fanPages(boss, world, pages, fanDeg, aim, speed, dmg, life) {
  for (let i = 0; i < pages; i++) {
    const frac = pages === 1 ? 0 : i / (pages - 1) - 0.5;
    const a = aim + frac * fanDeg * DEG;
    world.spawnProjectile({ x: boss.x, z: boss.z, vx: Math.sin(a) * speed, vz: Math.cos(a) * speed, dmg, life, radius: 0.3, owner: 'enemy', kind: 'page' });
  }
}

function wrapDelta(a, b) {
  let d = a - b;
  while (d > Math.PI) d -= Math.PI * 2;
  while (d < -Math.PI) d += Math.PI * 2;
  return d;
}

function pickBlinkSpot(world, p) {
  const lim = world.half - 3;
  for (let i = 0; i < 20; i++) {
    const x = world.rng.range(-lim, lim);
    const z = world.rng.range(-lim, lim);
    if (dist(x, z, p.x, p.z) >= 8) return { x, z };
  }
  return { x: 0, z: -lim };
}

function pickPointNear(world, from, radius) {
  const a = world.rng.range(0, Math.PI * 2);
  const lim = world.half - 2;
  return {
    x: Math.max(-lim, Math.min(lim, from.x + Math.sin(a) * radius)),
    z: Math.max(-lim, Math.min(lim, from.z + Math.cos(a) * radius)),
  };
}

// Damage multiplier for the boss: transform immunity, and the stone shield while any stone is alive.
export function bossDamageMult(world, boss) {
  if (boss.transformT > 0) return 0;
  return aliveStones(world, boss).length > 0 ? FRESTON.shieldMult : 1;
}
