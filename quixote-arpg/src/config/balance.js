// Every tunable number lives here. The sim reads only from this file and content.js.
// Units: meters, seconds, and fractions (0.25 = 25%) unless the name says otherwise.

export const TICK = 1 / 60;
export const MAX_LEVEL = 15;

export const PLAYER = {
  radius: 0.45,
  speed: 4.6,
  startAttrs: { str: 5, agi: 5, vit: 5, wil: 5 },
  attrPointsPerLevel: 3,
  talentPointsPerLevel: 1,
  unarmedDamage: 5,
  hpBase: 60,
  hpPerVit: 10,
  hpPerLevel: 6,
  atkPerStr: 2,
  atkPerLevel: 1.2,
  critBase: 0.05,
  critPerAgi: 0.004,
  critMult: 1.5,
  attackSpeedPerAgi: 0.01,
  speedPerAgi: 0.01,
  armorPerVit: 0.6,
  armorK: 120, // damage reduction = armor / (armor + armorK)
  madnessGainPerWil: 0.02,
  staggerFraction: 0.18, // one hit above this share of max HP staggers the player
  staggerTime: 0.42,
  regenDelay: 5, // seconds without damage before regeneration starts
  regenPerSec: 0.008, // share of max HP per second
  pickupRadius: 1.4,
};

// One light attack per entry. Timings are seconds before attack-speed scaling.
export const LIGHT_COMBO = [
  { windup: 0.12, active: 0.1, recovery: 0.2, dmg: 1.0, arc: 110, reach: 2.1, knock: 0.6 },
  { windup: 0.12, active: 0.12, recovery: 0.22, dmg: 1.15, arc: 120, reach: 2.2, knock: 0.8 },
  { windup: 0.18, active: 0.14, recovery: 0.36, dmg: 1.6, arc: 150, reach: 2.4, knock: 2.6 },
];

export const HEAVY = {
  minWindup: 0.22,
  chargeTime: 0.9, // holding this long gives full power
  active: 0.14,
  recovery: 0.4,
  dmgMin: 1.8,
  dmgMax: 3.0,
  arc: 140,
  reach: 2.6,
  knockMin: 1.5,
  knockMax: 3.5,
};

export const DODGE = {
  duration: 0.42,
  iframeStart: 0.03,
  iframeEnd: 0.3, // invulnerable only inside this window of the roll
  cooldown: 0.7,
  distance: 2.6,
};

export const BLOCK = {
  reduction: 0.55, // share of frontal damage absorbed by a normal block
  arc: 130, // frontal arc in degrees
  perfectWindow: 0.2, // seconds after raising the guard that count as a perfect block
  perfectCounterMult: 1.8,
  guardMax: 100,
  guardCostPerDmg: 0.5, // guard durability spent per point of incoming damage
  guardBreakStagger: 1.2,
  guardRegenPerSec: 14,
  guardRegenDelay: 1.2,
  moveMult: 0.5,
  maxReduction: 0.85,
};

export const MADNESS = {
  max: 100,
  atkBonus: 0.5, // +50% attack at 100 madness
  critBonus: 0.2, // +20% crit chance at 100 madness
  damageTakenBonus: 0.35, // +35% damage taken at 100 madness
  staggerDiscount: 0.4, // stagger threshold is 40% lower at 100 madness
  decayPerSec: 1.2,
  decayDelay: 3,
  ultimateCost: 60,
  gain: {
    lightHit: 1.5,
    heavyHit: 3,
    blockedHit: 2,
    perfectBlock: 6,
    kill: 2,
    eliteKill: 10,
    skillCast: 5,
    hurtPer10Dmg: 1,
  },
  tiers: [
    { min: 0, id: 'sane', name: '清醒' },
    { min: 30, id: 'frenzy', name: '痴狂' },
    { min: 70, id: 'mania', name: '癫狂' },
  ],
};

export const SKILLS = {
  thrust: {
    name: '长矛突刺',
    cooldown: 4.5,
    reach: 4.2,
    width: 1.1,
    dmg: 2.2,
    stagger: 0.35,
    lunge: 1.8,
    duration: 0.42,
    activeAt: 0.12,
    activeTime: 0.14,
  },
  charge: {
    name: '冲锋',
    cooldown: 7,
    distance: 7,
    duration: 0.45,
    dmg: 1.6,
    knock: 3.5,
    hitRadius: 1.1,
    hitDuration: 0.34,
  },
  bash: {
    name: '盾击',
    cooldown: 5,
    reach: 2.2,
    arc: 120,
    dmg: 1.2,
    stagger: 1.0,
    duration: 0.5,
    activeAt: 0.1,
    activeTime: 0.12,
  },
  whirl: {
    name: '风车旋风',
    cost: MADNESS.ultimateCost,
    duration: 2.4,
    tickEvery: 0.4,
    radius: 3.2,
    dmg: 0.9,
    moveMult: 0.5,
  },
};

// Ground-level monsters and elites. dropChance is the chance of a roll; the source
// (normal/elite/boss) decides the rarity table in content.js.
export const SHEEP = {
  name: '受惊的羊',
  hp: 38,
  damage: 12,
  radius: 0.5,
  speed: 4.2,
  aggroRange: 9,
  fleeRange: 3.2, // inside this range a calm flock stampedes
  stampedeDelay: 1.0, // dust line shown before the charge
  stampedeSpeed: 7.2,
  stampedeTime: 1.0,
  fleeTime: 4,
  calmTime: 6, // a flock that just stampeded cannot stampede again for this long
  stunTime: 2.2,
  stunDamageMult: 1.5,
  xp: 6,
  madKill: 1,
  dropChance: 0.04,
};

export const SPELLBOOK = {
  name: '施咒的魔法书',
  hp: 95,
  radius: 0.6,
  speed: 2.0,
  preferRange: 6.5,
  aggroRange: 16,
  cooldown: 3.0,
  chantTime: 1.2,
  pages: 5,
  fanDeg: 60,
  pageSpeed: 5.2,
  pageDamage: 13,
  pageLife: 3.5,
  pageRadius: 0.3,
  interruptStun: 2.0,
  interruptMult: 1.3,
  xp: 22,
  madKill: 2,
  dropChance: 0.15,
};

export const WINDMILL = {
  name: '风车巨人',
  hp: 460,
  radius: 1.0,
  speed: 1.6,
  turnRate: 110, // degrees per second
  aggroRange: 14,
  sweep: {
    cooldown: 3.0,
    telegraph: 1.1,
    arc: 270, // the 90 degrees in front stay safe
    radius: 3.6,
    dmg: 24,
    knock: 3.2,
    activeTime: 0.35,
  },
  gust: {
    cooldown: 6.0,
    telegraph: 0.9,
    arc: 70,
    reach: 6.5,
    dmg: 16,
    knock: 4.5,
    activeTime: 0.25,
  },
  frontMult: 0.3,
  sideMult: 0.7,
  backMult: 1.0,
  coreMult: 1.6, // hits from behind land on the energy core
  coreStagger: 0.5,
  phase2At: 0.5,
  phase2: { sweepTelegraph: 0.7, cooldownMult: 0.7, speedMult: 1.3 },
  xp: 120,
  madKill: 10,
  dropChance: 1,
};

export const MIRROR = {
  name: '镜中骑士',
  hp: 300,
  radius: 0.6,
  speed: 3.6,
  aggroRange: 16,
  holdRange: 3.0,
  reaction: 0.25, // delay after the player starts an attack before the guard goes up
  guard: { duration: 0.6, frontMult: 0.2, arc: 120 },
  counter: { telegraph: 0.5, reach: 3, width: 0.9, dmg: 26, activeTime: 0.25 },
  slam: { telegraph: 0.8, radius: 2.4, dmg: 30 },
  dashSlash: { telegraph: 0.35, speed: 14, distance: 4.5, dmg: 22, activeTime: 0.2 },
  open: { duration: 1.0, mult: 1.6 },
  cooldown: 1.2,
  xp: 90,
  madKill: 10,
  dropChance: 1,
};

export const FRESTON = {
  name: '魔法师弗雷斯顿',
  hp: 1600,
  radius: 1.1,
  speed: 2.4,
  stoneCount: 3,
  stoneHp: 120,
  stoneOrbit: 3.8,
  shieldMult: 0.25, // damage taken while any stone is alive
  phases: [
    { at: 1.0, name: '第一阶段 · 咒语', cooldown: 2.2 },
    { at: 0.66, name: '第二阶段 · 羊群暴走', cooldown: 1.8 },
    { at: 0.33, name: '第三阶段 · 镜像风暴', cooldown: 1.4 },
  ],
  transformTime: 1.4,
  verse: { telegraph: 1.0, pages: 7, fanDeg: 70, pageSpeed: 5.6, dmg: 14, life: 4 },
  ring: { telegraph: 0.9, pages: 12, gapDeg: 60, pageSpeed: 4.6, dmg: 14, life: 5 },
  spiral: { telegraph: 0.6, rounds: 3, perRound: 8, interval: 0.25, pageSpeed: 5.0, dmg: 14, life: 5 },
  ram: { telegraph: 1.0, length: 10, width: 1.6, speed: 12, dmg: 22, activeTime: 0.5 },
  blink: { telegraph: 0.8, slamRadius: 2.6, dmg: 30 },
  summonCap: 2,
  summonCooldown: 12,
  cloneCooldown: 25,
  xp: 600,
  madKill: 40,
};

export const COMPANION = {
  name: '桑乔',
  followDist: 2.2,
  radius: 0.4,
  speed: 5.2,
  attackRange: 3.5,
  attackCooldown: 1.8,
  dmgBase: 8,
  dmgPerLevel: 1.5,
  encourageCooldown: 12,
  encourageDuration: 6,
  encourageAtkBonus: 0.1,
};

export const XP = {
  curve: (level) => Math.floor(40 * Math.pow(level, 1.5) + 20 * level),
};

// Hit-stop and feedback durations shared by the sim and renderer.
export const FEEL = {
  hitStopLight: 0.04,
  hitStopHeavy: 0.08,
  hitStopPlayerHurt: 0.06,
  slowMoTime: 0.8,
  slowMoScale: 0.35,
};
