import { BLOCK, PLAYER, MADNESS } from '../config/balance.js';
import { SCHOOLS, UNIQUES } from '../config/content.js';

// Adds `src` into `target`. Numbers sum, booleans OR together.
export function addMods(target, src) {
  for (const [k, v] of Object.entries(src)) {
    if (typeof v === 'boolean') target[k] = Boolean(target[k]) || v;
    else target[k] = (target[k] ?? 0) + v;
  }
  return target;
}

export function itemMods(item) {
  const mods = {};
  if (!item) return mods;
  addMods(mods, item.implicit);
  for (const a of item.affixes) addMods(mods, { [a.stat]: a.value });
  if (item.unique) addMods(mods, UNIQUES[item.unique].mods);
  return mods;
}

export function talentById(id) {
  for (const [schoolId, school] of Object.entries(SCHOOLS)) {
    const talent = school.talents.find((t) => t.id === id);
    if (talent) return { schoolId, talent };
  }
  return null;
}

// Stats derived from progression and equipment only. Madness is applied per tick in player.js.
export function computeStats(progress) {
  const equipped = Object.values(progress.equipped);
  const mods = {};
  for (const item of equipped) addMods(mods, itemMods(item));
  for (const id of progress.talents) {
    const found = talentById(id);
    if (found) addMods(mods, found.talent.mods);
  }

  const attr = {
    str: progress.attrs.str + (mods.str ?? 0),
    agi: progress.attrs.agi + (mods.agi ?? 0),
    vit: progress.attrs.vit + (mods.vit ?? 0),
    wil: progress.attrs.wil + (mods.wil ?? 0),
  };
  const weaponDmg = progress.equipped.weapon?.dmg || PLAYER.unarmedDamage;
  const level = progress.level;

  const maxHp = (PLAYER.hpBase + attr.vit * PLAYER.hpPerVit + level * PLAYER.hpPerLevel + (mods.hp ?? 0));
  const atk = (weaponDmg + attr.str * PLAYER.atkPerStr + level * PLAYER.atkPerLevel + (mods.atk ?? 0)) * (1 + (mods.atkPct ?? 0));
  const crit = Math.min(0.75, PLAYER.critBase + attr.agi * PLAYER.critPerAgi + (mods.crit ?? 0));

  return {
    attr,
    mods,
    level,
    maxHp,
    atk,
    crit,
    critDmg: PLAYER.critMult + (mods.critDmg ?? 0),
    atkSpeed: 1 + attr.agi * PLAYER.attackSpeedPerAgi + (mods.atkSpeed ?? 0),
    speed: PLAYER.speed * (1 + attr.agi * PLAYER.speedPerAgi),
    armor: attr.vit * PLAYER.armorPerVit + (mods.armor ?? 0),
    block: Math.min(BLOCK.maxReduction, BLOCK.reduction + (mods.block ?? 0)),
    guardMax: BLOCK.guardMax + (mods.guardMax ?? 0),
    cdr: Math.min(0.35, mods.cdr ?? 0),
    madGain: (1 + attr.wil * PLAYER.madnessGainPerWil) * (1 + (mods.madGain ?? 0)),
    maxMad: MADNESS.max + (mods.maxMad ?? 0),
    staggerLimit: maxHp * PLAYER.staggerFraction,
  };
}

// Madness is a 0..max resource. These are its per-tick multipliers.
export function madnessEffects(madness, maxMad, mods = {}) {
  const t = Math.min(1, madness / maxMad);
  const tier = currentTier(madness);
  const mania = tier.id === 'mania' ? 1 : 0;
  return {
    t,
    tier,
    atkMult: 1 + MADNESS.atkBonus * t + mania * (mods.madAtkBonus ?? 0),
    critBonus: MADNESS.critBonus * t,
    damageTakenMult: 1 + MADNESS.damageTakenBonus * t,
    staggerMult: 1 - MADNESS.staggerDiscount * t,
  };
}

export function currentTier(madness) {
  let tier = MADNESS.tiers[0];
  for (const candidate of MADNESS.tiers) {
    if (madness >= candidate.min) tier = candidate;
  }
  return tier;
}
