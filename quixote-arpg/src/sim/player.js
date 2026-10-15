import { BLOCK, DODGE, HEAVY, LIGHT_COMBO, MADNESS, PLAYER, SKILLS } from '../config/balance.js';
import { angleDiff, DEG, dirOf, pushOutOfObstacles, wrapAngle, yawOf, clampToArena } from './geometry.js';
import { computeStats, madnessEffects } from './stats.js';

// An extra light hit within this many seconds of the last one continues the combo.
const COMBO_WINDOW = 0.55;
const TURN_RATE = 14; // rad/s, how quickly facing follows movement
const KNOCK_DECAY = 12; // knock velocity decay; displacement from a knock equals its distance
const BLOCK_FACE_RANGE = 8; // a guarding knight turns toward the nearest enemy in this range

// Actions that cannot be interrupted by new input. Thrust and bash unlock late in their recovery.
const LOCKED = new Set(['dodge', 'charge', 'whirl', 'stagger']);

// Movement speed multiplier by current action. Actions missing here move the player themselves or not at all.
const LOCOMOTION_MULT = {
  free: 1,
  block: BLOCK.moveMult,
  light: 0.35,
  heavyCharge: 0.4,
  heavy: 0.2,
  whirl: SKILLS.whirl.moveMult,
};

export class Player {
  constructor(progress, rng) {
    this.rng = rng;
    this.x = 0;
    this.z = 0;
    this.yaw = 0;
    this.radius = PLAYER.radius;
    this.madness = 0;
    this.state = 'free';
    this.action = null;
    this.queuedLight = false;
    this.combo = 0;
    this.comboTimer = 0;
    this.cooldowns = { thrust: 0, charge: 0, bash: 0 };
    this.cdMax = { thrust: 1, charge: 1, bash: 1 };
    this.dodgeCd = 0;
    this.dodgeMax = DODGE.cooldown;
    this.guard = 0;
    this.guardBroken = false;
    this.guardRegenDelay = 0;
    this.blockTime = 0;
    this.blockPerfectUsed = false;
    this.heavyCharge = 0;
    this.pendingCrits = 0;
    this.buffs = { encourage: 0, chargeCrit: 0 };
    this.lastDamageAgo = 99;
    this.timeSinceCombat = 99;
    this.knock = { x: 0, z: 0 };
    this.hp = 1;
    this.maxHp = 1;
    this.refresh(progress, { keepHp: false });
  }

  get dead() {
    return this.state === 'dead';
  }

  get invulnerable() {
    if (this.state === 'dead') return true;
    if (this.action?.kind === 'dodge') {
      return this.action.t >= DODGE.iframeStart && this.action.t <= DODGE.iframeEnd;
    }
    return false;
  }

  get mad() {
    return madnessEffects(this.madness, this.stats.maxMad, this.stats.mods);
  }

  get speed() {
    return this.stats.speed;
  }

  // Re-derives stats after equipment or progression changes. Keeps the current HP ratio.
  refresh(progress, { keepHp = true } = {}) {
    const prevMax = this.maxHp;
    this.stats = computeStats(progress);
    this.maxHp = this.stats.maxHp;
    if (!keepHp || prevMax <= 1) this.hp = this.maxHp;
    else this.hp = Math.min(this.maxHp, this.hp * (this.maxHp / prevMax));
    this.guard = this.guard === undefined ? this.stats.guardMax : Math.min(this.guard, this.stats.guardMax);
  }

  addMadness(amount) {
    if (amount <= 0) return;
    this.madness = Math.min(this.stats.maxMad, this.madness + amount * this.stats.madGain);
  }

  heal(amount) {
    if (this.state === 'dead') return;
    this.hp = Math.min(this.maxHp, this.hp + amount);
  }

  // One hit roll: base damage with madness, buffs and crit applied. Each target rolls its own crit.
  hitRoll(mult) {
    const mad = this.mad;
    const encourage = this.buffs.encourage > 0 ? 1 + 0.1 : 1;
    const chargeCrit = this.buffs.chargeCrit > 0 ? (this.stats.mods.chargeCritBonus ?? 0) : 0;
    const crit = Math.min(0.9, this.stats.crit + mad.critBonus + chargeCrit);
    const isCrit = this.rng.next() < crit;
    if (isCrit) this.pendingCrits += 1;
    const dmg = this.stats.atk * mult * mad.atkMult * encourage * (isCrit ? this.stats.critDmg : 1);
    return { dmg, crit: isCrit };
  }

  // Advances the player by dt (already time-scaled). `input` holds latched edges from the controls.
  step(dt, input, world) {
    if (this.state === 'dead') return;
    this.tickTimers(dt);

    if (this.state === 'stagger') {
      this.action.t += dt;
      if (this.action.t >= this.action.dur) this.endAction();
    } else {
      if (this.action) this.updateAction(dt, input, world);
      if (this.state !== 'stagger' && this.state !== 'dead') this.handleNewInput(input, world);
      if (this.state !== 'dead') this.locomote(dt, input, world);
    }

    this.applyKnock(dt, world);
    if (this.state !== 'dead') {
      pushOutOfObstacles(this, this.radius, world.obstacles);
      clampToArena(this, world.half, this.radius);
    }
  }

  tickTimers(dt) {
    this.lastDamageAgo += dt;
    this.timeSinceCombat += dt;
    this.dodgeCd = Math.max(0, this.dodgeCd - dt);
    this.cooldowns.thrust = Math.max(0, this.cooldowns.thrust - dt);
    this.cooldowns.charge = Math.max(0, this.cooldowns.charge - dt);
    this.cooldowns.bash = Math.max(0, this.cooldowns.bash - dt);
    this.comboTimer = Math.max(0, this.comboTimer - dt);
    this.buffs.encourage = Math.max(0, this.buffs.encourage - dt);
    this.buffs.chargeCrit = Math.max(0, this.buffs.chargeCrit - dt);

    this.guardRegenDelay = Math.max(0, this.guardRegenDelay - dt);
    if (this.guardRegenDelay === 0 && this.state !== 'block' && this.guard < this.stats.guardMax) {
      this.guard = Math.min(this.stats.guardMax, this.guard + BLOCK.guardRegenPerSec * dt);
      if (this.guardBroken && this.guard >= this.stats.guardMax * 0.5) this.guardBroken = false;
    }
    if (this.timeSinceCombat > MADNESS.decayDelay && this.madness > 0) {
      this.madness = Math.max(0, this.madness - MADNESS.decayPerSec * dt);
    }
    if (this.lastDamageAgo > PLAYER.regenDelay && this.hp < this.maxHp) {
      this.hp = Math.min(this.maxHp, this.hp + this.maxHp * PLAYER.regenPerSec * dt);
    }
  }

  updateAction(dt, input, world) {
    const a = this.action;
    a.t += dt;
    switch (a.kind) {
      case 'light':
        this.updateLight(world);
        break;
      case 'heavyCharge':
        this.heavyCharge = Math.min(HEAVY.chargeTime * 1.5, this.heavyCharge + dt);
        if (!input.heavyHeld) this.releaseHeavy();
        break;
      case 'heavy':
        this.updateHeavy(world);
        break;
      case 'thrust':
        this.updateThrust(dt, world);
        break;
      case 'bash':
        this.updateBash(world);
        break;
      case 'charge':
        this.updateCharge(dt, world);
        break;
      case 'whirl':
        this.updateWhirl(dt, world);
        break;
      case 'dodge':
        this.updateDodge(dt);
        break;
      case 'block':
        this.updateBlock(dt, world, input);
        break;
      default:
        break;
    }
  }

  // Movement from input. Melee lunges, dodges and charges move the player themselves.
  locomote(dt, input, world) {
    const move = input.move;
    if (Math.hypot(move.x, move.z) < 0.05) return;
    const kind = this.action?.kind ?? 'free';
    const speedMult = LOCOMOTION_MULT[kind];
    if (speedMult === undefined) return;
    const sp = this.speed * speedMult;
    this.x += move.x * sp * dt;
    this.z += move.z * sp * dt;
    if (kind === 'free' || kind === 'block' || kind === 'whirl') {
      this.faceTo(yawOf(move.x, move.z), dt);
    }
  }

  faceTo(target, dt) {
    const d = wrapAngle(target - this.yaw);
    const maxStep = TURN_RATE * dt;
    this.yaw = wrapAngle(this.yaw + Math.max(-maxStep, Math.min(maxStep, d)));
  }

  applyKnock(dt, world) {
    const k = this.knock;
    if (Math.abs(k.x) < 1e-4 && Math.abs(k.z) < 1e-4) return;
    this.x += k.x * dt;
    this.z += k.z * dt;
    const decay = Math.exp(-KNOCK_DECAY * dt);
    k.x *= decay;
    k.z *= decay;
    pushOutOfObstacles(this, this.radius, world.obstacles);
    clampToArena(this, world.half, this.radius);
  }

  endAction() {
    const prev = this.action;
    this.action = null;
    this.state = 'free';
    this.queuedLight = false;
    if (prev?.kind === 'light') {
      this.combo = prev.step >= LIGHT_COMBO.length - 1 ? 0 : prev.step + 1;
      this.comboTimer = COMBO_WINDOW;
    }
    if (prev?.kind === 'block') this.blockTime = 0;
  }

  // Interrupts the player and starts a stagger. Super armor (charge, whirl) ignores it.
  stagger(duration = PLAYER.staggerTime) {
    if (this.action && (this.action.kind === 'charge' || this.action.kind === 'whirl')) return false;
    this.action = { kind: 'stagger', t: 0, dur: duration };
    this.state = 'stagger';
    this.queuedLight = false;
    return true;
  }

  // Resolves one incoming hit. Returns { type, dmg } where type is dodged | perfect | blocked | hit | staggered | dead | ignored.
  takeHit(hit, world) {
    if (this.state === 'dead') return { type: 'ignored', dmg: 0 };
    if (this.invulnerable) return { type: 'dodged', dmg: 0 };

    const armorMult = 1 - this.stats.armor / (this.stats.armor + PLAYER.armorK);
    const incoming = hit.damage * this.mad.damageTakenMult * armorMult;
    const frontal = !hit.noBlock && this.state === 'block' && !this.guardBroken && this.isFrontal(hit.fromX, hit.fromZ);

    if (frontal) return this.takeBlockedHit(incoming, world);

    this.hp -= incoming;
    this.recordDamage(incoming);
    if (hit.knock) this.pushFrom(hit.fromX, hit.fromZ, hit.knock);
    if (this.hp <= 0) {
      this.die();
      return { type: 'dead', dmg: incoming };
    }
    const threshold = this.stats.staggerLimit * this.mad.staggerMult;
    if (hit.stagger || incoming >= threshold) {
      if (this.stagger()) return { type: 'staggered', dmg: incoming };
    }
    return { type: 'hit', dmg: incoming };
  }

  takeBlockedHit(incoming, world) {
    if (this.blockTime <= BLOCK.perfectWindow && !this.blockPerfectUsed) {
      this.blockPerfectUsed = true;
      this.addMadness(MADNESS.gain.perfectBlock);
      const counterMult = BLOCK.perfectCounterMult * (this.stats.mods.counterDouble ? 2 : 1);
      world.playerCounter(this, counterMult);
      if (this.stats.mods.perfectHealPct) this.heal(this.maxHp * this.stats.mods.perfectHealPct);
      this.timeSinceCombat = 0;
      return { type: 'perfect', dmg: 0 };
    }

    const reduced = incoming * (1 - this.stats.block);
    this.guard -= incoming * BLOCK.guardCostPerDmg;
    this.guardRegenDelay = BLOCK.guardRegenDelay;
    this.hp -= reduced;
    this.recordDamage(reduced);
    this.addMadness(MADNESS.gain.blockedHit);
    if (this.stats.mods.blockHealPct) this.heal(this.maxHp * this.stats.mods.blockHealPct);

    if (this.hp <= 0) {
      this.die();
      return { type: 'dead', dmg: reduced };
    }
    if (this.guard <= 0) {
      this.guard = 0;
      this.guardBroken = true;
      this.endAction();
      this.stagger(BLOCK.guardBreakStagger);
      return { type: 'guardBroken', dmg: reduced };
    }
    return { type: 'blocked', dmg: reduced };
  }

  recordDamage(dmg) {
    this.lastDamageAgo = 0;
    this.timeSinceCombat = 0;
    this.addMadness((dmg / 10) * MADNESS.gain.hurtPer10Dmg);
  }

  die() {
    this.hp = 0;
    this.action = null;
    this.state = 'dead';
  }

  isFrontal(fx, fz) {
    const toAttacker = yawOf(fx - this.x, fz - this.z);
    return angleDiff(toAttacker, this.yaw) <= (BLOCK.arc * DEG) / 2;
  }

  pushFrom(fx, fz, knock) {
    const dx = this.x - fx;
    const dz = this.z - fz;
    const d = Math.hypot(dx, dz) || 1;
    this.knock.x += (dx / d) * knock * KNOCK_DECAY;
    this.knock.z += (dz / d) * knock * KNOCK_DECAY;
  }

  // ---- input -> action ----------------------------------------------------

  isLocked() {
    const a = this.action;
    if (!a) return false;
    if (LOCKED.has(a.kind)) return true;
    if (a.kind === 'thrust') return a.t < SKILLS.thrust.duration * 0.6;
    if (a.kind === 'bash') return a.t < SKILLS.bash.duration * 0.6;
    return false;
  }

  // Before the commit frame an attack is still a feint when it is cancelled.
  isWindup() {
    const a = this.action;
    if (!a) return false;
    if (a.kind === 'light') return a.phase === 'windup';
    return a.kind === 'heavyCharge';
  }

  handleNewInput(input, world) {
    if (this.isLocked()) return;
    const k = this.action?.kind ?? null;

    if (input.dodge && this.dodgeCd <= 0) {
      this.cancelForNewAction(world, 'dodge');
      this.startDodge(input, world);
      return;
    }
    if (input.skill && this.canStartSkill(input.skill)) {
      this.cancelForNewAction(world, input.skill);
      this.startSkill(input.skill, world);
      return;
    }
    if (input.attack) {
      if (k === 'light') {
        this.queuedLight = true;
      } else if (k !== 'heavyCharge') {
        this.cancelForNewAction(world, 'attack');
        this.startLight(world, this.comboTimer > 0 ? this.combo : 0);
        return;
      }
    }
    if (input.heavyDown && k !== 'heavyCharge') {
      this.cancelForNewAction(world, 'heavy');
      this.startHeavyCharge(world);
      return;
    }
    if (input.blockDown && k !== 'block') {
      this.cancelForNewAction(world, 'block');
      this.startBlock(world);
    }
  }

  cancelForNewAction(world, by) {
    if (this.isWindup()) world.notify('feint', { by });
    if (this.action) this.endAction();
  }

  canStartSkill(name) {
    if (name === 'whirl') return this.madness >= this.whirlCost();
    return this.cooldowns[name] <= 0;
  }

  whirlCost() {
    return SKILLS.whirl.cost - (this.stats.mods.whirlCostReduce ?? 0);
  }

  // ---- light attacks ------------------------------------------------------

  startLight(world, step) {
    const speed = this.stats.atkSpeed;
    const def = LIGHT_COMBO[step];
    this.action = {
      kind: 'light',
      step,
      phase: 'windup',
      t: 0,
      w: def.windup / speed,
      a: def.active / speed,
      r: def.recovery / speed,
      struck: false,
    };
    this.action.dur = this.action.w + this.action.a + this.action.r;
    this.state = 'attack';
    this.queuedLight = false;
    const target = world.nearestEnemy(this.x, this.z, 3.0);
    if (target) this.yaw = yawOf(target.x - this.x, target.z - this.z);
    world.notify('attackStart', { kind: 'light' });
  }

  updateLight(world) {
    const a = this.action;
    const def = LIGHT_COMBO[a.step];
    if (!a.struck && a.t >= a.w) {
      a.struck = true;
      a.phase = 'active';
      world.notify('attackCommit', { kind: 'light' });
      this.strikeArc(world, def.arc, def.reach, def.dmg, def.knock, false);
    }
    if (a.struck && a.t >= a.w + a.a) a.phase = 'recovery';
    if (a.t >= a.dur) {
      const finished = a.step;
      const chain = this.queuedLight && finished < LIGHT_COMBO.length - 1;
      this.endAction();
      if (chain) this.startLight(world, finished + 1);
    }
  }

  // ---- heavy attacks ------------------------------------------------------

  startHeavyCharge(world) {
    this.action = { kind: 'heavyCharge', t: 0, phase: 'charge' };
    this.state = 'attack';
    this.heavyCharge = 0;
    world.notify('attackStart', { kind: 'heavy' });
  }

  releaseHeavy() {
    const power = Math.min(1, this.heavyCharge / HEAVY.chargeTime);
    const speed = this.stats.atkSpeed;
    this.action = {
      kind: 'heavy',
      t: 0,
      power,
      w: HEAVY.minWindup / speed,
      a: HEAVY.active / speed,
      r: HEAVY.recovery / speed,
      phase: 'windup',
      struck: false,
    };
    this.action.dur = this.action.w + this.action.a + this.action.r;
  }

  updateHeavy(world) {
    const a = this.action;
    if (!a.struck && a.t >= a.w) {
      a.struck = true;
      a.phase = 'active';
      world.notify('attackCommit', { kind: 'heavy' });
      const mult = HEAVY.dmgMin + (HEAVY.dmgMax - HEAVY.dmgMin) * a.power;
      const knock = HEAVY.knockMin + (HEAVY.knockMax - HEAVY.knockMin) * a.power;
      this.strikeArc(world, HEAVY.arc, HEAVY.reach, mult, knock, true);
    }
    if (a.struck && a.t >= a.w + a.a) a.phase = 'recovery';
    if (a.t >= a.dur) this.endAction();
  }

  // Melee strike around the facing. Each enemy in the arc is hit once.
  strikeArc(world, arc, reach, mult, knock, heavy) {
    const hits = world.strikeSector(this.x, this.z, this.yaw, arc, reach, () => this.hitRoll(mult), {
      source: 'player',
      knock,
      stagger: heavy ? 0.5 : 0,
    });
    if (hits > 0) this.onConnect(heavy ? MADNESS.gain.heavyHit : MADNESS.gain.lightHit, hits);
  }

  // Shared bookkeeping for every landed player hit.
  onConnect(madPerHit, count) {
    this.timeSinceCombat = 0;
    this.addMadness(madPerHit * count);
    const onHit = this.stats.mods.madOnHit ?? 0;
    if (onHit) this.addMadness(onHit * count);
    if (this.mad.tier.id === 'mania' && this.stats.mods.madLifeSteal) {
      this.heal(this.maxHp * this.stats.mods.madLifeSteal * count);
    }
    const crits = this.pendingCrits;
    this.pendingCrits = 0;
    if (this.stats.mods.critMad && crits) this.addMadness(this.stats.mods.critMad * crits);
  }

  // ---- skills ----------------------------------------------------------------

  startSkill(name, world) {
    world.notify('skill', { name });
    this.state = 'skill';
    if (name === 'whirl') {
      this.madness = Math.max(0, this.madness - this.whirlCost());
      this.action = { kind: 'whirl', t: 0, dur: SKILLS.whirl.duration + (this.stats.mods.whirlDuration ?? 0), tick: 0 };
      return;
    }
    const cdrCut = name === 'thrust' ? this.stats.mods.thrustCooldownSec ?? 0 : 0;
    this.cdMax[name] = Math.max(1, SKILLS[name].cooldown + cdrCut) * (1 - this.stats.cdr);
    this.cooldowns[name] = this.cdMax[name];
    this.addMadness(MADNESS.gain.skillCast);

    if (name === 'charge') {
      this.action = { kind: 'charge', t: 0, dur: SKILLS.charge.duration, dirX: Math.sin(this.yaw), dirZ: Math.cos(this.yaw), seen: new Set() };
    } else if (name === 'thrust') {
      const target = world.nearestEnemy(this.x, this.z, 4.5);
      if (target) this.yaw = yawOf(target.x - this.x, target.z - this.z);
      this.action = { kind: 'thrust', t: 0, dur: SKILLS.thrust.duration, struck: false };
    } else if (name === 'bash') {
      this.action = { kind: 'bash', t: 0, dur: SKILLS.bash.duration, struck: false };
    }
  }

  updateThrust(dt, world) {
    const a = this.action;
    const def = SKILLS.thrust;
    const lungeEnd = def.activeAt + def.activeTime;
    if (a.t <= lungeEnd) {
      const d = (def.lunge / lungeEnd) * dt;
      this.x += Math.sin(this.yaw) * d;
      this.z += Math.cos(this.yaw) * d;
    }
    if (!a.struck && a.t >= def.activeAt) {
      a.struck = true;
      const mult = def.dmg * (1 + (this.stats.mods.thrustDamage ?? 0));
      const pierce = 1 + (this.stats.mods.thrustPierce ?? 0);
      const hits = world.strikeLine(this.x, this.z, this.yaw, def.reach, def.width, () => this.hitRoll(mult), {
        source: 'player',
        knock: 1.0,
        stagger: def.stagger,
        maxTargets: pierce,
      });
      if (hits > 0) this.onConnect(0, hits);
    }
    if (a.t >= a.dur) this.endAction();
  }

  updateBash(world) {
    const a = this.action;
    const def = SKILLS.bash;
    if (!a.struck && a.t >= def.activeAt) {
      a.struck = true;
      const hits = world.strikeSector(this.x, this.z, this.yaw, def.arc, def.reach, () => this.hitRoll(def.dmg), {
        source: 'player',
        knock: 1.6,
        stagger: def.stagger,
      });
      world.reflectProjectiles(this.x, this.z, this.yaw, def.arc, def.reach + 0.5);
      if (hits > 0) this.onConnect(0, hits);
    }
    if (a.t >= a.dur) this.endAction();
  }

  updateCharge(dt, world) {
    const a = this.action;
    const def = SKILLS.charge;
    const step = (def.distance / def.duration) * dt;
    const nx = this.x + a.dirX * step;
    const nz = this.z + a.dirZ * step;
    const hitWall = Math.abs(nx) > world.half - this.radius || Math.abs(nz) > world.half - this.radius;
    const hitObstacle = world.obstacles.some((o) => Math.hypot(nx - o.x, nz - o.z) < this.radius + o.r);
    if (hitWall || hitObstacle) {
      a.t = a.dur;
    } else {
      this.x = nx;
      this.z = nz;
    }
    const hits = world.strikeCircle(this.x, this.z, def.hitRadius, () => this.hitRoll(def.dmg), {
      source: 'player',
      knock: def.knock,
      stagger: 0,
      seen: a.seen,
    });
    if (hits > 0) {
      this.buffs.chargeCrit = 1.5;
      this.onConnect(0, hits);
    }
    if (a.t >= a.dur) this.endAction();
  }

  updateWhirl(dt, world) {
    const a = this.action;
    const def = SKILLS.whirl;
    a.tick -= dt;
    if (a.tick <= 0) {
      a.tick += def.tickEvery;
      const hits = world.strikeCircle(this.x, this.z, def.radius, () => this.hitRoll(def.dmg), {
        source: 'player',
        knock: 0.9,
        stagger: 0,
      });
      if (hits > 0) this.onConnect(0, hits);
    }
    if (a.t >= a.dur) this.endAction();
  }

  // ---- block -----------------------------------------------------------------

  startBlock(world) {
    this.action = { kind: 'block', t: 0 };
    this.state = 'block';
    this.blockTime = 0;
    this.blockPerfectUsed = false;
    world.notify('block', {});
  }

  updateBlock(dt, world, input) {
    this.blockTime += dt;
    if (!input.blockHeld) {
      this.endAction();
      return;
    }
    const target = world.nearestEnemy(this.x, this.z, BLOCK_FACE_RANGE);
    if (target && Math.hypot(input.move.x, input.move.z) < 0.05) {
      this.faceTo(yawOf(target.x - this.x, target.z - this.z), dt);
    }
  }

  // ---- dodge -----------------------------------------------------------------

  startDodge(input, world) {
    let dx = input.move.x;
    let dz = input.move.z;
    const mag = Math.hypot(dx, dz);
    if (mag > 0.2) {
      dx /= mag;
      dz /= mag;
    } else {
      const back = dirOf(this.yaw + Math.PI);
      dx = back.x;
      dz = back.z;
    }
    this.action = { kind: 'dodge', t: 0, dur: DODGE.duration, dirX: dx, dirZ: dz };
    this.state = 'dodge';
    this.dodgeCd = DODGE.cooldown;
    this.queuedLight = false;
    world.notify('dodge', { x: this.x + dx * DODGE.distance, z: this.z + dz * DODGE.distance });
  }

  updateDodge(dt) {
    const a = this.action;
    const sp = DODGE.distance / DODGE.duration;
    this.x += a.dirX * sp * dt;
    this.z += a.dirZ * sp * dt;
    if (a.t >= a.dur) this.endAction();
  }
}
