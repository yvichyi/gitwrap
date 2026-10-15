import { FEEL, MADNESS, PLAYER, WINDMILL } from '../config/balance.js';
import { BEHAVIORS, DEFS, clearTelegraph, damageMultiplier, makeEnemy, tickCommon } from './enemies.js';
import { bossDamageMult, createBoss, updateBoss } from './boss.js';
import { Companion } from './companion.js';
import { Player } from './player.js';
import { addToBag, addXp } from './progression.js';
import { generateItem } from './items.js';
import { createRng } from './rng.js';
import { circleHit, dist, inLine, inSector, yawOf } from './geometry.js';

const WAVE_GAP = 1.6; // seconds between a cleared wave and the next spawn
const INTRO_TIME = 1.0;
const OBSTACLE_THEME = {
  plain: [
    { kind: 'hay', r: 0.9, n: 9 },
    { kind: 'post', r: 0.35, n: 12 },
    { kind: 'tower', r: 1.6, n: 2 },
  ],
  hill: [
    { kind: 'rock', r: 1.2, n: 7 },
    { kind: 'hay', r: 0.9, n: 6 },
    { kind: 'tower', r: 1.6, n: 2 },
  ],
  castle: [
    { kind: 'pillar', r: 0.9, n: 9 },
    { kind: 'rock', r: 1.2, n: 4 },
  ],
};

// One fixed-step battle. Owns entities, spawning, combat resolution, loot, and stage flow.
export class World {
  constructor({ stage, stageIndex, progress, seed = 1 }) {
    this.stage = stage;
    this.stageIndex = stageIndex;
    this.progress = progress;
    this.rng = createRng(seed + stageIndex * 7919);
    this.half = stage.size / 2;
    this.obstacles = buildObstacles(this.rng, stage, this.half);
    this.player = new Player(progress, this.rng);
    this.player.x = 0;
    this.player.z = this.half - 6;
    this.player.yaw = Math.PI;
    this.companion = new Companion();
    this.companion.x = this.player.x;
    this.companion.z = this.player.z + 2;
    this.enemies = [];
    this.hitboxes = [];
    this.projectiles = [];
    this.loot = [];
    this.events = [];
    this.time = 0;
    this.hitStop = 0;
    this.slowT = 0;
    this.nextId = 1;
    this.waveIndex = 0;
    this.waveTimer = INTRO_TIME;
    this.clear = false;
    this.over = false;
    this.bossDown = false;
    this.stats = { kills: 0, eliteKills: 0, damageDealt: 0, damageTaken: 0, xp: 0, items: 0, dodges: 0, perfectBlocks: 0, interrupts: 0 };
  }

  // ---- events & notifications ----------------------------------------------

  emit(type, data = {}) {
    this.events.push({ type, ...data });
  }

  drainEvents() {
    const out = this.events;
    this.events = [];
    return out;
  }

  notify(type, data) {
    for (const e of this.enemies) {
      if (!e.dead) BEHAVIORS[e.type]?.onNotify?.(e, type, data, this);
    }
  }

  // ---- queries used by actors ----------------------------------------------

  nearestEnemy(x, z, range) {
    let best = null;
    let bestD = range;
    for (const e of this.enemies) {
      if (e.dead) continue;
      const d = dist(x, z, e.x, e.z) - e.radius;
      if (d <= bestD) {
        bestD = d;
        best = e;
      }
    }
    return best;
  }

  // ---- spawning --------------------------------------------------------------

  spawnEnemy(type, x, z, opts = {}) {
    const id = this.nextId++;
    if (type === 'boss') {
      const boss = createBoss(this, id, x, z, this.stage.hpMult);
      this.enemies.push(boss);
      return boss;
    }
    const hpMult = opts.hpMult ?? this.stage.hpMult;
    const e = makeEnemy(type, id, x, z, { ...opts, hpMult, strafeSign: this.rng.chance(0.5) ? -1 : 1 });
    if (type === 'sheep') e.wanderT = this.rng.range(0, 2);
    e.life = opts.life ?? Infinity;
    e.yaw = yawOf(this.player.x - x, this.player.z - z);
    this.enemies.push(e);
    return e;
  }

  spawnHitbox(hb) {
    this.hitboxes.push({ ...hb, hit: false });
  }

  spawnProjectile(p) {
    this.projectiles.push({ ...p, age: 0 });
  }

  // Ring spawn point around the knight, kept clear of obstacles and the arena walls.
  spawnPoint() {
    const p = this.player;
    for (let i = 0; i < 25; i++) {
      const a = this.rng.range(0, Math.PI * 2);
      const r = this.rng.range(10, 15);
      const x = p.x + Math.sin(a) * r;
      const z = p.z + Math.cos(a) * r;
      const lim = this.half - 2;
      if (Math.abs(x) > lim || Math.abs(z) > lim) continue;
      if (this.obstacles.some((o) => dist(x, z, o.x, o.z) < o.r + 1.2)) continue;
      return { x, z };
    }
    return { x: 0, z: -this.half + 3 };
  }

  spawnWave(index) {
    const wave = this.stage.waves[index];
    let group = 0;
    for (const [type, count] of wave) {
      const flock = type === 'sheep' ? `flock-${index}-${group++}` : null;
      if (type === 'sheep') {
        const center = this.spawnPoint();
        for (let i = 0; i < count; i++) {
          const ox = (i % 3) * 1.1 - 1.1;
          const oz = Math.floor(i / 3) * 1.1;
          const at = { x: clampTo(center.x + ox, this.half), z: clampTo(center.z + oz, this.half) };
          this.spawnEnemy('sheep', at.x, at.z, { flockId: flock });
          this.emit('spawn', { x: at.x, z: at.z, elite: false });
        }
        continue;
      }
      for (let i = 0; i < count; i++) {
        const at = this.spawnPoint();
        this.spawnEnemy(type, at.x, at.z);
        this.emit('spawn', { x: at.x, z: at.z, elite: Boolean(DEFS[type]?.elite) });
      }
    }
    this.emit('wave', { index, total: this.stage.waves.length });
  }

  // ---- damage ----------------------------------------------------------------

  // roll = { dmg, crit } from the actor; opts = { source, knock, stagger, seen }.
  damageEnemy(e, roll, opts = {}) {
    if (e.dead) return 0;
    const info = { fromX: this.player.x, fromZ: this.player.z };
    let mult = damageMultiplier(e, info, this);
    if (e.type === 'boss') mult *= bossDamageMult(this, e);
    if (mult === 0) {
      this.emit('immune', { x: e.x, z: e.z });
      return 0;
    }
    const dmg = Math.max(1, Math.round(roll.dmg * mult));
    e.hp -= dmg;
    e.flash = 0.12;
    this.stats.damageDealt += dmg;
    const back = e.type === 'windmill' && mult >= WINDMILL.coreMult - 1e-6;
    this.emit('hit', {
      x: e.x,
      z: e.z,
      amount: dmg,
      crit: Boolean(roll.crit),
      weak: mult < 0.6,
      back,
      kind: opts.source,
      heavy: Boolean(opts.stagger),
    });
    if (opts.knock) {
      const dx = e.x - info.fromX;
      const dz = e.z - info.fromZ;
      const d = Math.hypot(dx, dz) || 1;
      e.knock.x += (dx / d) * opts.knock * 10;
      e.knock.z += (dz / d) * opts.knock * 10;
    }
    if (back && e.type === 'windmill') {
      clearTelegraph(e);
      e.state = 'stun';
      e.stunT = WINDMILL.coreStagger;
      this.emit('core', { x: e.x, z: e.z });
    }

    BEHAVIORS[e.type]?.onDamaged?.(e, info, this);
    if (e.hp <= 0) this.killEnemy(e);
    return dmg;
  }

  killEnemy(e) {
    if (e.dead) return;
    e.dead = true;
    e.hp = 0;

    if (e.kind === 'stone') {
      this.emit('stoneBroken', { x: e.x, z: e.z });
      const boss = this.enemies.find((b) => b.type === 'boss' && b.id === e.parent);
      if (boss && !this.enemies.some((s) => s.kind === 'stone' && s.parent === boss.id && !s.dead)) {
        this.emit('shieldBroken', { x: boss.x, z: boss.z });
      }
      return;
    }

    this.emit('kill', { x: e.x, z: e.z, name: e.name, elite: e.elite, boss: e.type === 'boss', monster: e.type });
    if (e.summoned) return;

    this.stats.kills += 1;
    if (e.elite) this.stats.eliteKills += 1;
    this.player.addMadness(e.elite ? MADNESS.gain.eliteKill : MADNESS.gain.kill);
    this.grantXp(e.xp);

    if (e.type === 'boss') {
      this.bossDown = true;
      for (const other of this.enemies) {
        if (other.kind === 'stone' && other.parent === e.id) other.dead = true;
        if (other.summoned) other.dead = true;
      }
      this.emit('bossDown', { x: e.x, z: e.z });
      for (let i = 0; i < 3; i++) this.dropLoot(e, 'boss');
    } else if (this.rng.chance(e.dropChance)) {
      this.dropLoot(e, e.elite ? 'elite' : 'normal');
    }
  }

  grantXp(amount) {
    const before = this.progress.level;
    addXp(this.progress, amount);
    this.stats.xp += amount;
    this.emit('xp', { amount });
    if (this.progress.level > before) {
      this.player.refresh(this.progress, { keepHp: true });
      this.emit('levelUp', { level: this.progress.level });
    }
  }

  dropLoot(from, source) {
    const item = generateItem(this.rng, { source, stageIndex: this.stageIndex, id: `i${this.nextId++}` });
    const a = this.rng.range(0, Math.PI * 2);
    const r = this.rng.range(0.4, 1.4);
    this.loot.push({ id: item.id, x: from.x + Math.sin(a) * r, z: from.z + Math.cos(a) * r, item });
    this.emit('drop', { item, x: from.x, z: from.z });
  }

  // Shield bash deflects enemy pages in its arc. Reflected pages hurt the enemy that fired them.
  reflectProjectiles(x, z, yaw, arc, reach) {
    for (const p of this.projectiles) {
      if (p.owner !== 'enemy') continue;
      if (inSector(x, z, yaw, arc, reach, p.x, p.z, p.radius)) {
        p.owner = 'player';
        p.vx = -p.vx;
        p.vz = -p.vz;
        p.dmg *= 1.5;
        p.life += 1;
        this.emit('reflect', { x: p.x, z: p.z });
      }
    }
  }

  // Perfect-block counter: the strike after a perfect guard.
  playerCounter(player, mult) {
    const hits = this.strikeSector(player.x, player.z, player.yaw, 120, 2.4, () => player.hitRoll(mult), {
      source: 'player',
      knock: 1.4,
      stagger: 0.6,
    });
    this.stats.perfectBlocks += 1;
    this.emit('counter', { x: player.x, z: player.z, hits });
    if (hits > 0) player.onConnect(0, hits);
  }

  // ---- player strikes ----------------------------------------------------------

  strikeSector(ox, oz, yaw, arc, reach, roll, opts = {}) {
    let hits = 0;
    for (const e of this.enemies) {
      if (e.dead) continue;
      if (inSector(ox, oz, yaw, arc, reach, e.x, e.z, e.radius)) {
        this.damageEnemy(e, roll(), opts);
        hits += 1;
      }
    }
    return hits;
  }

  strikeLine(ox, oz, yaw, length, width, roll, opts = {}) {
    const candidates = this.enemies
      .filter((e) => !e.dead && inLine(ox, oz, yaw, length, width, e.x, e.z, e.radius))
      .sort((a, b) => dist(ox, oz, a.x, a.z) - dist(ox, oz, b.x, b.z));
    const max = opts.maxTargets ?? Infinity;
    let hits = 0;
    for (const e of candidates) {
      if (hits >= max) break;
      this.damageEnemy(e, roll(), opts);
      hits += 1;
    }
    return hits;
  }

  strikeCircle(x, z, r, roll, opts = {}) {
    let hits = 0;
    for (const e of this.enemies) {
      if (e.dead) continue;
      if (dist(x, z, e.x, e.z) > r + e.radius) continue;
      if (opts.seen) {
        if (opts.seen.has(e.id)) continue;
        opts.seen.add(e.id);
      }
      this.damageEnemy(e, roll(), opts);
      hits += 1;
    }
    return hits;
  }

  // ---- enemy hits on the player -----------------------------------------------

  applyPlayerHit(hit) {
    const p = this.player;
    if (p.dead) return;
    const res = p.takeHit(hit, this);
    this.stats.damageTaken += res.dmg ?? 0;
    switch (res.type) {
      case 'dodged':
        this.stats.dodges += 1;
        this.emit('dodged', { x: p.x, z: p.z });
        break;
      case 'perfect':
        this.hitStop = Math.max(this.hitStop, FEEL.hitStopLight);
        this.emit('perfect', { x: p.x, z: p.z });
        break;
      case 'blocked':
      case 'guardBroken':
        this.emit('blocked', { x: p.x, z: p.z, amount: Math.round(res.dmg), broken: res.type === 'guardBroken' });
        break;
      case 'hit':
      case 'staggered':
      case 'dead':
        this.hitStop = Math.max(this.hitStop, FEEL.hitStopPlayerHurt);
        this.emit('playerHit', { x: p.x, z: p.z, amount: Math.round(res.dmg), staggered: res.type === 'staggered' });
        break;
      default:
        break;
    }
    if (p.dead && !this.over) {
      this.over = true;
      this.emit('death', { level: this.progress.level });
    }
  }

  // ---- loop --------------------------------------------------------------------

  // Advances one fixed tick. `input` is the latched input for this tick.
  step(dt, input) {
    this.time += dt;
    if (this.over || this.clear) return;
    if (this.hitStop > 0) {
      this.hitStop = Math.max(0, this.hitStop - dt);
      return;
    }
    this.slowT = Math.max(0, this.slowT - dt);
    const s = dt * (this.slowT > 0 ? FEEL.slowMoScale : 1);

    this.player.step(s, input, this);
    this.companion.step(s, this);

    for (const e of this.enemies) {
      if (e.dead || e.kind === 'stone') continue;
      if (Number.isFinite(e.life)) {
        e.life -= s;
        if (e.life <= 0) {
          e.dead = true;
          continue;
        }
      }
      if (e.type === 'boss') {
        const phase = e.phase;
        updateBoss(e, s, this);
        if (e.phase !== phase) this.requestSlowMo();
      } else {
        tickCommon(e, s, this);
        BEHAVIORS[e.type]?.update(e, s, this);
      }
    }

    this.resolveContacts();
    this.resolveHitboxes(s);
    this.resolveProjectiles(s);
    this.resolveLoot();
    this.updateWaves(dt);
    this.compact();
  }

  resolveContacts() {
    const p = this.player;
    for (const e of this.enemies) {
      if (e.dead || !e.contact || e.contact.used) continue;
      if (circleHit(e.x, e.z, e.contact.radius, p.x, p.z, p.radius)) {
        e.contact.used = true;
        this.applyPlayerHit({ damage: e.contact.damage, fromX: e.x, fromZ: e.z, knock: e.contact.knock, stagger: true });
      }
    }
  }

  resolveHitboxes(s) {
    const p = this.player;
    for (const hb of this.hitboxes) {
      hb.life -= s;
      if (!hb.hit && !p.dead && hitboxTouches(hb, p)) {
        hb.hit = true;
        this.applyPlayerHit({ damage: hb.dmg, fromX: hb.x, fromZ: hb.z, knock: hb.knock, stagger: hb.stagger });
      }
    }
    this.hitboxes = this.hitboxes.filter((hb) => hb.life > 0);
  }

  resolveProjectiles(s) {
    for (const pr of this.projectiles) {
      pr.age += s;
      pr.life -= s;
      pr.x += pr.vx * s;
      pr.z += pr.vz * s;
      if (pr.owner === 'enemy') {
        const p = this.player;
        if (!p.dead && circleHit(pr.x, pr.z, pr.radius, p.x, p.z, p.radius)) {
          pr.life = 0;
          this.applyPlayerHit({ damage: pr.dmg, fromX: pr.x - pr.vx * s, fromZ: pr.z - pr.vz * s, knock: 0.6 });
        }
      } else {
        for (const e of this.enemies) {
          if (e.dead) continue;
          if (circleHit(pr.x, pr.z, pr.radius, e.x, e.z, e.radius)) {
            pr.life = 0;
            this.damageEnemy(e, { dmg: pr.dmg, crit: false }, { source: 'player', knock: 0.3 });
            break;
          }
        }
      }
      if (Math.abs(pr.x) > this.half + 2 || Math.abs(pr.z) > this.half + 2) pr.life = 0;
      if (pr.life > 0 && this.obstacles.some((o) => dist(pr.x, pr.z, o.x, o.z) < o.r + pr.radius)) pr.life = 0;
    }
    this.projectiles = this.projectiles.filter((pr) => pr.life > 0);
  }

  resolveLoot() {
    const p = this.player;
    for (const drop of this.loot) {
      if (drop.taken) continue;
      if (dist(p.x, p.z, drop.x, drop.z) <= PLAYER.pickupRadius) {
        if (addToBag(this.progress, drop.item)) {
          drop.taken = true;
          this.stats.items += 1;
          this.emit('pickup', { item: drop.item });
        } else {
          this.emit('bagFull', {});
          drop.taken = true;
        }
      }
    }
    this.loot = this.loot.filter((d) => !d.taken);
  }

  // A wave is over when no counted monster is alive. Summons never count toward a wave.
  updateWaves(dt) {
    const monsters = this.enemies.filter((e) => e.kind === 'monster' && !e.dead && !e.summoned).length;
    if (this.waveIndex >= this.stage.waves.length) {
      if (monsters === 0) {
        this.clear = true;
        this.emit('stageClear', { level: this.progress.level });
      }
      return;
    }
    if (monsters > 0) return;
    this.waveTimer -= dt;
    if (this.waveTimer <= 0) {
      this.spawnWave(this.waveIndex);
      this.waveIndex += 1;
      this.waveTimer = WAVE_GAP;
    }
  }

  compact() {
    this.enemies = this.enemies.filter((e) => !e.dead);
    this.projectiles = this.projectiles.filter((p) => p.life > 0);
  }

  // Phase-change slow motion and a short camera push-in are requested by the renderer through this.
  requestSlowMo() {
    this.slowT = FEEL.slowMoTime;
  }

  get liveBoss() {
    return this.enemies.find((e) => e.type === 'boss' && !e.dead) ?? null;
  }
}

function hitboxTouches(hb, p) {
  const r = p.radius;
  if (hb.shape === 'sector') return inSector(hb.x, hb.z, hb.yaw, hb.arc, hb.reach, p.x, p.z, r);
  if (hb.shape === 'line') return inLine(hb.x, hb.z, hb.yaw, hb.length, hb.width, p.x, p.z, r);
  if (hb.shape === 'circle') return circleHit(hb.x, hb.z, hb.radius, p.x, p.z, r);
  return false;
}

function clampTo(v, half) {
  return Math.max(-half + 2, Math.min(half - 2, v));
}

function buildObstacles(rng, stage, half) {
  const out = [];
  const theme = OBSTACLE_THEME[stage.theme] ?? OBSTACLE_THEME.plain;
  for (const group of theme) {
    for (let i = 0; i < group.n; i++) {
      for (let tries = 0; tries < 40; tries++) {
        const x = rng.range(-half + 3, half - 3);
        const z = rng.range(-half + 3, half - 3);
        const clearOfSpawn = Math.hypot(x, z - (half - 6)) > 4.5 && Math.hypot(x, z) > 4;
        const clearOfOthers = out.every((o) => Math.hypot(o.x - x, o.z - z) > o.r + group.r + 2.2);
        if (clearOfSpawn && clearOfOthers) {
          out.push({ x, z, r: group.r, kind: group.kind });
          break;
        }
      }
    }
  }
  return out;
}
