// Non-numeric-balance content: names, equipment, build branches, drop tables, stages.
// Numbers that drive combat live in balance.js; the numbers here are item stats and drop weights.

export const SLOTS = ['weapon', 'shield', 'helm', 'armor', 'charm'];

export const SLOT_NAMES = {
  weapon: '长矛',
  shield: '圆盾',
  helm: '头盔',
  armor: '胸甲',
  charm: '圣物',
};

export const RARITIES = {
  common: { id: 'common', name: '普通', color: '#cfc6b0', affixes: 0 },
  rare: { id: 'rare', name: '稀有', color: '#5fb0ff', affixes: 2 },
  epic: { id: 'epic', name: '史诗', color: '#b57bff', affixes: 3 },
  legendary: { id: 'legendary', name: '传说', color: '#ffb347', affixes: 4 },
};

// Rarity weights per drop source. Boss drops never roll common.
export const DROP_WEIGHTS = {
  normal: { common: 60, rare: 28, epic: 9, legendary: 3 },
  elite: { common: 30, rare: 40, epic: 22, legendary: 8 },
  boss: { common: 0, rare: 30, epic: 40, legendary: 30 },
};

// Base items. dmg is only used by weapons; implicit stats are always present.
// tier is the minimum stage index (0-based) where the base can drop.
export const BASES = {
  weapon: [
    { id: 'rusty-lance', name: '锈蚀长矛', tier: 0, dmg: 10, implicit: {} },
    { id: 'knight-lance', name: '骑士长矛', tier: 0, dmg: 13, implicit: {} },
    { id: 'dragon-lance', name: '龙骨长矛', tier: 1, dmg: 17, implicit: {} },
  ],
  shield: [
    { id: 'wood-buckler', name: '木圆盾', tier: 0, implicit: { block: 0.08 } },
    { id: 'iron-round', name: '铁圆盾', tier: 1, implicit: { block: 0.14, armor: 4 } },
  ],
  helm: [
    { id: 'pot-helm', name: '锅形盔', tier: 0, implicit: { armor: 3 } },
    { id: 'barbute', name: '巴尔布特盔', tier: 1, implicit: { armor: 6, hp: 8 } },
  ],
  armor: [
    { id: 'leather', name: '皮胸甲', tier: 0, implicit: { armor: 5 } },
    { id: 'plate', name: '残破板甲', tier: 1, implicit: { armor: 10, hp: 12 } },
  ],
  charm: [
    { id: 'ribbon', name: '杜尔西内娅的丝带', tier: 0, implicit: { wil: 2 } },
    { id: 'saint-medal', name: '圣像吊坠', tier: 1, implicit: { hp: 12 } },
  ],
};

// Affix ranges are rolled uniformly and scaled by the item's item level.
export const AFFIXES = [
  { stat: 'str', name: '力量', min: 1, max: 3, pct: false },
  { stat: 'agi', name: '敏捷', min: 1, max: 3, pct: false },
  { stat: 'vit', name: '体魄', min: 1, max: 3, pct: false },
  { stat: 'wil', name: '意志', min: 1, max: 3, pct: false },
  { stat: 'atkPct', name: '攻击', min: 0.06, max: 0.15, pct: true },
  { stat: 'crit', name: '暴击率', min: 0.02, max: 0.06, pct: true },
  { stat: 'critDmg', name: '暴击伤害', min: 0.1, max: 0.3, pct: true },
  { stat: 'atkSpeed', name: '攻速', min: 0.03, max: 0.08, pct: true },
  { stat: 'armor', name: '护甲', min: 3, max: 8, pct: false },
  { stat: 'hp', name: '生命', min: 8, max: 25, pct: false },
  { stat: 'block', name: '格挡效率', min: 0.04, max: 0.1, pct: true },
  { stat: 'cdr', name: '冷却缩减', min: 0.03, max: 0.08, pct: true },
  { stat: 'madGain', name: '疯狂获取', min: 0.1, max: 0.3, pct: true },
  { stat: 'maxMad', name: '疯狂上限', min: 5, max: 15, pct: false },
];

export const STAT_NAMES = {
  str: '力量',
  agi: '敏捷',
  vit: '体魄',
  wil: '意志',
  hp: '生命',
  atkPct: '攻击',
  crit: '暴击率',
  critDmg: '暴击伤害',
  atkSpeed: '攻速',
  armor: '护甲',
  block: '格挡效率',
  cdr: '冷却缩减',
  madGain: '疯狂获取',
  maxMad: '疯狂上限',
};

// Legendary uniques: a base that can roll as a unique, with mods the sim understands.
export const UNIQUES = {
  'quixote-lance': {
    name: '堂吉诃德的旧长矛',
    slot: 'weapon',
    baseId: 'dragon-lance',
    mods: { thrustPierce: 1, thrustCooldownSec: -1, madOnHit: 0.5 },
    text: '突刺穿透+1，突刺冷却-1秒，每次命中额外获得疯狂值',
  },
  'sancho-shield': {
    name: '桑乔的木盾',
    slot: 'shield',
    baseId: 'iron-round',
    mods: { block: 0.1, perfectHealPct: 0.04 },
    text: '格挡效率+10%，完美格挡回复4%生命',
  },
};

// Build branches. Mods are summed (or OR-ed for booleans) into the player's stat block.
export const SCHOOLS = {
  lance: {
    name: '长矛突刺流',
    desc: '突刺与冲锋贯穿敌阵，以暴击和攻速取胜',
    talents: [
      { id: 'lance-1', name: '穿刺', desc: '突刺伤害+15%，穿透+1', mods: { thrustDamage: 0.15, thrustPierce: 1 } },
      { id: 'lance-2', name: '骑兵冲撞', desc: '冲锋命中后1.5秒内暴击率+15%', mods: { chargeCritBonus: 0.15 } },
      { id: 'lance-3', name: '骑士誓言', desc: '暴击伤害+25%，暴击时获得疯狂值+4', mods: { critDmg: 0.25, critMad: 4 } },
    ],
  },
  bulwark: {
    name: '圆盾守护流',
    desc: '以格挡与反击为核心，越挨打越稳',
    talents: [
      { id: 'bulwark-1', name: '铁壁', desc: '格挡效率+15%，格挡时不会被重击击退', mods: { block: 0.15 } },
      { id: 'bulwark-2', name: '反击之盾', desc: '完美格挡反击伤害×2', mods: { counterDouble: true } },
      { id: 'bulwark-3', name: '不朽之墙', desc: '格挡耐久上限+50，每次格挡回复1%生命', mods: { guardMax: 50, blockHealPct: 0.01 } },
    ],
  },
  madman: {
    name: '癫狂骑士流',
    desc: '把疯狂化为力量，代价是不计后果的伤害承受',
    talents: [
      { id: 'madman-1', name: '疯魔', desc: '疯狂上限+20，癫狂阶段攻击额外+20%', mods: { maxMad: 20, madAtkBonus: 0.2 } },
      { id: 'madman-2', name: '狂热吸血', desc: '癫狂阶段每次命中回复1.5%生命', mods: { madLifeSteal: 0.015 } },
      { id: 'madman-3', name: '风车之怒', desc: '旋风持续+1秒，消耗降低20点疯狂', mods: { whirlDuration: 1, whirlCostReduce: 20 } },
    ],
  },
};

export const SCHOOL_IDS = Object.keys(SCHOOLS);

// Stages run in order. waves are arrays of [monsterType, count].
// hpMult scales every non-boss hit point pool; ilvl drives item stat rolls.
export const STAGES = [
  {
    id: 'plain',
    name: '拉曼恰平原',
    subtitle: '风车与羊群',
    ilvl: 1,
    hpMult: 1.0,
    size: 40,
    theme: 'plain',
    waves: [
      [['sheep', 6]],
      [['spellbook', 2]],
      [['sheep', 6], ['spellbook', 1]],
      [['windmill', 1]],
    ],
  },
  {
    id: 'hill',
    name: '风车山丘',
    subtitle: '镜子里的骑士',
    ilvl: 2,
    hpMult: 1.2,
    size: 44,
    theme: 'hill',
    waves: [
      [['spellbook', 3]],
      [['mirror', 1]],
      [['sheep', 6], ['spellbook', 2]],
      [['windmill', 1], ['spellbook', 2]],
    ],
  },
  {
    id: 'castle',
    name: '哥特城堡',
    subtitle: '魔法师的终局',
    ilvl: 3,
    hpMult: 1.45,
    size: 48,
    theme: 'castle',
    waves: [
      [['sheep', 6], ['spellbook', 2]],
      [['mirror', 1], ['windmill', 1]],
      [['boss', 1]],
    ],
  },
];

export const STARTING_WEAPON = { id: 'start-lance', slot: 'weapon', base: 'knight-lance', rarity: 'common', ilvl: 1, dmg: 13, implicit: {}, affixes: [], unique: null, name: '骑士长矛' };
