import { FRESTON, MIRROR, SHEEP, SPELLBOOK, WINDMILL } from '../config/balance.js';
import { angleDiff, DEG, dirOf, dist, pushOutOfObstacles, wrapAngle, yawOf, clampToArena } from './geometry.js';

// Each monster type: create() sets its fields, update() runs its AI, onDamaged() and onNotify() react to events.
// A telegraph is { shape, x, z, yaw, arc, reach, length, width, radius, total, left, onEnd }; onEnd spawns the real effect.

const STONE = { name: '魔力石', hp: FRESTON.stoneHp, radius: 0.45, xp: 0, madKill: 0, dropChance: 0 };

export const DEFS = {
  sheep: SHEEP,
  spellbook: SPELLBOOK,
  windmill: WINDMILL,
  mirror: MIRROR,
  boss: FRESTON,
  stone: STONE,
};

export function makeEnemy(type, id, x, z, opts = {}) {
  const def = DEFS[type];
  const hpMult = opts.hpMult ?? 1;
  const e = {
    id,
    type,
    kind: type === 'stone' ? 'stone' : 'monster',
    name: def.name,
    x,
    z,
    yaw: opts.yaw ?? 0,
    radius: def.radius ?? 0.6,
    hp: Math.round(def.hp * hpMult),
    maxHp: Math.round(def.hp * hpMult),
    elite: Boolean(opts.elite ?? def.elite),
    summoned: Boolean(opts.summoned),
    state: 'idle',
    t: 0,
    cd: opts.cooldown ?? 0.6,
    telegraph: null,
    contact: null,
    knock: { x: 0, z: 0 },
    stunT: 0,
    flash: 0,
    dead: false,
    xp: def.xp ?? 0,
    madKill: def.madKill ?? 0,
    dropChance: def.dropChance ?? 0,
    flockId: opts.flockId ?? null,
    parent: opts.parent ?? null,
    orbit: opts.orbit ?? 0,
    hitByPlayerAt: -1,
  };
  if (type === 'sheep') Object.assign(e, { state: 'graze', wanderT: 0, dir: { x: 0, z: 1 }, fleeT: 0, calmT: 0 });
  if (type === 'spellbook') Object.assign(e, { strafeSign: opts.strafeSign ?? 1 });
  if (type === 'windmill') Object.assign(e, { gcd: 2.5, phaseTwo: false, recoverT: 0 });
  if (type === 'mirror') Object.assign(e, { committed: false, dashDir: { x: 0, z: 0 }, dashT: 0, cd: 0.4 });
  return e;
}

export function setTelegraph(e, t, onEnd) {
  e.telegraph = { ...t, left: t.total, onEnd };
}

export function clearTelegraph(e) {
  e.telegraph = null;
}

// Turns e toward a target yaw at a max rate. Returns the remaining angular error.
export function turnTo(e, target, rate, dt) {
  const d = wrapAngle(target - e.yaw);
  const step = rate * dt;
  e.yaw = wrapAngle(e.yaw + Math.max(-step, Math.min(step, d)));
  return Math.abs(d);
}

export function moveBy(e, dx, dz, dt, world) {
  e.x += dx * dt;
  e.z += dz * dt;
  pushOutOfObstacles(e, e.radius, world.obstacles);
  clampToArena(e, world.half, e.radius);
}

// Telegraph timers and knockback decay. Called for every monster each tick.
export function tickCommon(e, dt, world) {
  e.flash = Math.max(0, e.flash - dt);
  if (e.telegraph) {
    e.telegraph.left -= dt;
    if (e.telegraph.left <= 0) {
      const { onEnd } = e.telegraph;
      e.telegraph = null;
      onEnd?.(e, world);
    }
  }
  if (e.stunT > 0) e.stunT = Math.max(0, e.stunT - dt);
  if (e.knock.x || e.knock.z) {
    e.x += e.knock.x * dt;
    e.z += e.knock.z * dt;
    const decay = Math.exp(-10 * dt);
    e.knock.x *= decay;
    e.knock.z *= decay;
    if (Math.abs(e.knock.x) < 1e-3) e.knock.x = 0;
    if (Math.abs(e.knock.z) < 1e-3) e.knock.z = 0;
    pushOutOfObstacles(e, e.radius, world.obstacles);
    clampToArena(e, world.half, e.radius);
  }
}

// ---- sheep ---------------------------------------------------------------

// A calm, grazing flock member stampedes. Once a flock has run, it stays calm for SHEEP.calmTime.
function triggerFlock(e, world) {
  const p = world.player;
  const members = world.enemies.filter((m) => m.type === 'sheep' && m.flockId === e.flockId && m.state === 'graze' && m.calmT <= 0);
  for (const m of members) {
    const dir = dirOf(yawOf(p.x - m.x, p.z - m.z));
    m.state = 'warn';
    m.dir = dir;
    setTelegraph(m, { shape: 'line', x: m.x, z: m.z, yaw: yawOf(dir.x, dir.z), length: 8, width: 1.6, total: SHEEP.stampedeDelay, tone: 'warn' }, (sheep) => {
      sheep.state = 'charge';
      sheep.t = 0;
      sheep.contact = { damage: SHEEP.damage, radius: sheep.radius + 0.35, used: false, knock: 2.6 };
    });
  }
}

const sheepBehavior = {
  update(e, dt, world) {
    const p = world.player;
    const toPlayer = dist(e.x, e.z, p.x, p.z);
    e.calmT = Math.max(0, e.calmT - dt);

    switch (e.state) {
      case 'graze': {
        e.wanderT -= dt;
        if (e.wanderT <= 0) {
          const a = world.rng.range(0, Math.PI * 2);
          e.dir = dirOf(a);
          e.wanderT = world.rng.range(2, 4);
        }
        moveBy(e, e.dir.x, e.dir.z, dt * SHEEP.speed * 0.25, world);
        if (toPlayer <= SHEEP.fleeRange && e.calmT <= 0) triggerFlock(e, world);
        break;
      }
      case 'warn':
        break;
      case 'charge': {
        e.t += dt;
        const before = { x: e.x, z: e.z };
        e.x += e.dir.x * SHEEP.stampedeSpeed * dt;
        e.z += e.dir.z * SHEEP.stampedeSpeed * dt;
        const hitObstacle = pushOutOfObstacles(e, e.radius, world.obstacles);
        const blockedWall = Math.abs(e.x) >= world.half - e.radius - 1e-3 || Math.abs(e.z) >= world.half - e.radius - 1e-3;
        const moved = Math.hypot(e.x - before.x, e.z - before.z);
        if (hitObstacle || (blockedWall && moved < SHEEP.stampedeSpeed * dt * 0.5)) {
          e.state = 'stun';
          e.stunT = SHEEP.stunTime;
          e.contact = null;
          world.emit('stun', { id: e.id, x: e.x, z: e.z });
        } else if (e.t >= SHEEP.stampedeTime) {
          e.state = 'flee';
          e.fleeT = SHEEP.fleeTime;
          e.calmT = SHEEP.calmTime;
          e.contact = null;
        }
        clampToArena(e, world.half, e.radius);
        break;
      }
      case 'flee': {
        e.fleeT -= dt;
        const away = dirOf(yawOf(e.x - p.x, e.z - p.z));
        moveBy(e, away.x, away.z, dt * SHEEP.speed, world);
        if (e.fleeT <= 0) e.state = 'graze';
        break;
      }
      case 'stun':
        if (e.stunT <= 0) {
          e.state = 'flee';
          e.fleeT = 1.5;
        }
        break;
      default:
        break;
    }
  },
  onDamaged(e, info, world) {
    if (e.state === 'graze') triggerFlock(e, world);
  },
};

// ---- spellbook -------------------------------------------------------------

function firePages(e, world, pages, fanDeg, speed, dmg, life, radius, center) {
  const base = center;
  for (let i = 0; i < pages; i++) {
    const frac = pages === 1 ? 0 : i / (pages - 1) - 0.5;
    const a = base + frac * fanDeg * DEG;
    world.spawnProjectile({
      x: e.x,
      z: e.z,
      vx: Math.sin(a) * speed,
      vz: Math.cos(a) * speed,
      dmg,
      life,
      radius,
      owner: 'enemy',
      kind: 'page',
    });
  }
}

const spellbookBehavior = {
  update(e, dt, world) {
    const p = world.player;
    const d = dist(e.x, e.z, p.x, p.z);
    const aim = yawOf(p.x - e.x, p.z - e.z);
    e.cd = Math.max(0, e.cd - dt);
    if (e.state === 'stun') {
      if (e.stunT <= 0) e.state = 'idle';
      else turnTo(e, aim, 4, dt);
      return;
    }
    if (e.state === 'chant') {
      turnTo(e, aim, 2, dt);
      return;
    }
    turnTo(e, aim, 3, dt);
    const speed = SPELLBOOK.speed;
    if (d > SPELLBOOK.preferRange + 1) {
      const dir = dirOf(aim);
      moveBy(e, dir.x, dir.z, dt * speed, world);
    } else if (d < SPELLBOOK.preferRange - 1) {
      const dir = dirOf(aim + Math.PI);
      moveBy(e, dir.x, dir.z, dt * speed, world);
    } else {
      const side = dirOf(aim + (Math.PI / 2) * e.strafeSign);
      moveBy(e, side.x, side.z, dt * speed * 0.5, world);
    }
    if (e.cd <= 0 && d <= SPELLBOOK.aggroRange) {
      e.state = 'chant';
      const facing = aim;
      setTelegraph(
        e,
        { shape: 'sector', x: e.x, z: e.z, yaw: facing, arc: SPELLBOOK.fanDeg, reach: 14, total: SPELLBOOK.chantTime, tone: 'warn' },
        (book, w) => {
          firePages(book, w, SPELLBOOK.pages, SPELLBOOK.fanDeg, SPELLBOOK.pageSpeed, SPELLBOOK.pageDamage, SPELLBOOK.pageLife, SPELLBOOK.pageRadius, facing);
          book.state = 'idle';
          book.cd = SPELLBOOK.cooldown;
        },
      );
    }
  },
  onDamaged(e, info, world) {
    if (e.state === 'chant' && e.telegraph) {
      clearTelegraph(e);
      e.state = 'stun';
      e.stunT = SPELLBOOK.interruptStun;
      e.cd = SPELLBOOK.cooldown;
      world.emit('interrupt', { id: e.id, x: e.x, z: e.z });
    }
  },
};

// ---- windmill giant --------------------------------------------------------

const windmillBehavior = {
  update(e, dt, world) {
    const p = world.player;
    const d = dist(e.x, e.z, p.x, p.z);
    e.phaseTwo = e.hp <= e.maxHp * WINDMILL.phase2At;
    const speedMult = e.phaseTwo ? WINDMILL.phase2.speedMult : 1;
    const cdMult = e.phaseTwo ? WINDMILL.phase2.cooldownMult : 1;
    e.cd = Math.max(0, e.cd - dt);
    e.gcd = Math.max(0, e.gcd - dt);

    if (e.state === 'stun') {
      if (e.stunT <= 0) e.state = 'idle';
      return;
    }
    if (e.state === 'telegraph') return;
    if (e.state === 'recover') {
      e.recoverT -= dt;
      if (e.recoverT <= 0) e.state = 'idle';
      return;
    }
    if (d > WINDMILL.aggroRange) return;

    const aim = yawOf(p.x - e.x, p.z - e.z);
    turnTo(e, aim, WINDMILL.turnRate * DEG, dt);
    const misalign = angleDiff(aim, e.yaw);

    if (e.cd <= 0 && d <= WINDMILL.sweep.radius + p.radius + 0.6) {
      startSweep(e, world, cdMult, e.phaseTwo);
      return;
    }
    if (e.gcd <= 0 && d <= WINDMILL.gust.reach && misalign < 35 * DEG) {
      startGust(e, world);
      return;
    }
    if (d > 3.0) {
      const dir = dirOf(aim);
      moveBy(e, dir.x, dir.z, dt * WINDMILL.speed * speedMult, world);
    }
  },
  onDamaged() {},
};

function startSweep(e, world, cdMult, phaseTwo) {
  const sweep = WINDMILL.sweep;
  e.state = 'telegraph';
  const back = e.yaw + Math.PI;
  setTelegraph(
    e,
    { shape: 'sector', x: e.x, z: e.z, yaw: back, arc: sweep.arc, reach: sweep.radius, total: phaseTwo ? WINDMILL.phase2.sweepTelegraph : sweep.telegraph, tone: 'danger' },
    (g, w) => {
      w.spawnHitbox({
        owner: g.id,
        shape: 'sector',
        x: g.x,
        z: g.z,
        yaw: back,
        arc: sweep.arc,
        reach: sweep.radius,
        dmg: sweep.dmg,
        knock: sweep.knock,
        life: sweep.activeTime,
      });
      g.state = 'recover';
      g.recoverT = 0.5;
      g.cd = sweep.cooldown * cdMult;
    },
  );
}

function startGust(e, world) {
  const gust = WINDMILL.gust;
  e.state = 'telegraph';
  const facing = e.yaw;
  setTelegraph(
    e,
    { shape: 'sector', x: e.x, z: e.z, yaw: facing, arc: gust.arc, reach: gust.reach, total: gust.telegraph, tone: 'danger' },
    (g, w) => {
      w.spawnHitbox({
        owner: g.id,
        shape: 'sector',
        x: g.x,
        z: g.z,
        yaw: facing,
        arc: gust.arc,
        reach: gust.reach,
        dmg: gust.dmg,
        knock: gust.knock,
        life: gust.activeTime,
      });
      g.state = 'recover';
      g.recoverT = 0.5;
      g.gcd = gust.cooldown;
    },
  );
}

// ---- mirror knight -----------------------------------------------------------

function startDash(e, world, target) {
  const d = dist(e.x, e.z, target.x, target.z);
  const dir = dirOf(yawOf(target.x - e.x, target.z - e.z));
  e.state = 'dash';
  setTelegraph(
    e,
    { shape: 'line', x: e.x, z: e.z, yaw: yawOf(dir.x, dir.z), length: Math.min(d + 1, MIRROR.dashSlash.distance + 2), width: 1.2, total: MIRROR.dashSlash.telegraph, tone: 'danger' },
    (m) => {
      m.state = 'dashing';
      m.dashDir = dir;
      m.dashT = MIRROR.dashSlash.distance / MIRROR.dashSlash.speed;
      m.contact = { damage: MIRROR.dashSlash.dmg, radius: m.radius + 0.4, used: false, knock: 2.0 };
    },
  );
}

function startSlam(e, world, target) {
  e.state = 'slam';
  const pos = { x: target.x, z: target.z };
  setTelegraph(
    e,
    { shape: 'circle', x: pos.x, z: pos.z, radius: MIRROR.slam.radius, total: MIRROR.slam.telegraph, tone: 'danger' },
    (m, w) => {
      w.spawnHitbox({ owner: m.id, shape: 'circle', x: pos.x, z: pos.z, radius: MIRROR.slam.radius, dmg: MIRROR.slam.dmg, knock: 1.5, life: 0.15 });
      m.state = 'cool';
      m.cd = MIRROR.cooldown;
    },
  );
}

const mirrorBehavior = {
  update(e, dt, world) {
    const p = world.player;
    const d = dist(e.x, e.z, p.x, p.z);
    const aim = yawOf(p.x - e.x, p.z - e.z);
    e.cd = Math.max(0, e.cd - dt);
    e.t = Math.max(0, e.t - dt);

    switch (e.state) {
      case 'idle':
      case 'cool': {
        turnTo(e, aim, 4, dt);
        if (e.state === 'cool' && e.cd <= 0) e.state = 'idle';
        if (d > MIRROR.holdRange) {
          const dir = dirOf(aim);
          moveBy(e, dir.x, dir.z, dt * MIRROR.speed, world);
        }
        break;
      }
      case 'pending':
        turnTo(e, aim, 6, dt);
        if (e.t <= 0) {
          e.state = 'guard';
          e.t = MIRROR.guard.duration;
          e.committed = false;
        }
        break;
      case 'guard':
        turnTo(e, aim, 6, dt);
        if (e.t <= 0) {
          if (e.committed) {
            startCounter(e, world, p);
          } else {
            e.state = 'cool';
            e.cd = MIRROR.cooldown;
          }
        }
        break;
      case 'open':
        if (e.t <= 0) {
          e.state = 'cool';
          e.cd = MIRROR.cooldown;
        }
        break;
      case 'dashing': {
        e.dashT -= dt;
        moveBy(e, e.dashDir.x, e.dashDir.z, dt * MIRROR.dashSlash.speed, world);
        if (e.dashT <= 0) {
          e.state = 'cool';
          e.cd = MIRROR.cooldown;
          e.contact = null;
        }
        break;
      }
      default:
        break;
    }
  },
  onDamaged() {},
  onNotify(e, type, data, world) {
    const idle = e.state === 'idle' || e.state === 'cool';
    if (type === 'attackStart' && idle) {
      e.state = 'pending';
      e.t = MIRROR.reaction;
      e.committed = false;
    } else if (type === 'attackCommit' && (e.state === 'pending' || e.state === 'guard')) {
      e.committed = true;
    } else if (type === 'feint' && e.state === 'pending') {
      e.state = 'open';
      e.t = MIRROR.open.duration;
      world.emit('open', { id: e.id, x: e.x, z: e.z });
    } else if (type === 'dodge' && idle && dist(e.x, e.z, world.player.x, world.player.z) < 12) {
      startDash(e, world, data);
    } else if (type === 'block' && idle) {
      startSlam(e, world, world.player);
    }
  },
};

function startCounter(e, world, p) {
  e.state = 'counter';
  const aim = yawOf(p.x - e.x, p.z - e.z);
  e.yaw = aim;
  setTelegraph(
    e,
    { shape: 'line', x: e.x, z: e.z, yaw: aim, length: MIRROR.counter.reach, width: MIRROR.counter.width, total: MIRROR.counter.telegraph, tone: 'danger' },
    (m, w) => {
      w.spawnHitbox({
        owner: m.id,
        shape: 'line',
        x: m.x,
        z: m.z,
        yaw: aim,
        length: MIRROR.counter.reach,
        width: MIRROR.counter.width,
        dmg: MIRROR.counter.dmg,
        knock: 1.8,
        life: MIRROR.counter.activeTime,
      });
      m.state = 'cool';
      m.cd = MIRROR.cooldown;
    },
  );
}

export const BEHAVIORS = {
  sheep: sheepBehavior,
  spellbook: spellbookBehavior,
  windmill: windmillBehavior,
  mirror: mirrorBehavior,
};

// Damage multiplier from the attack direction, per monster rules. `info` = { fromX, fromZ, stage }.
export function damageMultiplier(e, info, world) {
  let mult = 1;
  switch (e.type) {
    case 'sheep':
      if (e.state === 'stun') mult *= SHEEP.stunDamageMult;
      break;
    case 'spellbook':
      if (e.state === 'stun') mult *= SPELLBOOK.interruptMult;
      break;
    case 'windmill': {
      const toAttacker = yawOf(info.fromX - e.x, info.fromZ - e.z);
      const rel = angleDiff(toAttacker, e.yaw);
      if (rel < 60 * DEG) mult *= WINDMILL.frontMult;
      else if (rel < 120 * DEG) mult *= WINDMILL.sideMult;
      else mult *= WINDMILL.coreMult;
      break;
    }
    case 'mirror': {
      if (e.state === 'open') mult *= MIRROR.open.mult;
      else if (e.state === 'guard' || e.state === 'counter') {
        const toAttacker = yawOf(info.fromX - e.x, info.fromZ - e.z);
        if (angleDiff(toAttacker, e.yaw) <= (MIRROR.guard.arc * DEG) / 2) mult *= MIRROR.guard.frontMult;
      }
      break;
    }
    default:
      break;
  }
  return mult;
}

