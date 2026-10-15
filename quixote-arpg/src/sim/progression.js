import { MAX_LEVEL, PLAYER, XP } from '../config/balance.js';
import { SCHOOLS, SLOTS, STARTING_WEAPON } from '../config/content.js';
import { talentById } from './stats.js';

export const BAG_MAX = 30;
export const ATTR_KEYS = ['str', 'agi', 'vit', 'wil'];

export function createProgress() {
  return {
    level: 1,
    xp: 0,
    attrPoints: 0,
    talentPoints: 0,
    attrs: { ...PLAYER.startAttrs },
    school: null,
    talents: [],
    equipped: Object.fromEntries(SLOTS.map((s) => [s, s === 'weapon' ? structuredClone(STARTING_WEAPON) : null])),
    bag: [],
  };
}

// Grants XP, levels up as far as needed, and returns how many levels were gained.
export function addXp(progress, amount) {
  let gained = 0;
  progress.xp += amount;
  while (progress.level < MAX_LEVEL) {
    const need = XP.curve(progress.level);
    if (progress.xp < need) break;
    progress.xp -= need;
    progress.level += 1;
    progress.attrPoints += PLAYER.attrPointsPerLevel;
    progress.talentPoints += PLAYER.talentPointsPerLevel;
    gained += 1;
  }
  if (progress.level >= MAX_LEVEL) progress.xp = 0;
  return gained;
}

export function xpNeeded(progress) {
  return progress.level >= MAX_LEVEL ? 0 : XP.curve(progress.level);
}

export function allocateAttr(progress, key) {
  if (!ATTR_KEYS.includes(key) || progress.attrPoints <= 0) return false;
  progress.attrs[key] += 1;
  progress.attrPoints -= 1;
  return true;
}

// Talents unlock in a fixed order inside one school. The first talent picks the school.
export function learnTalent(progress, talentId) {
  if (progress.talentPoints <= 0) return false;
  const found = talentById(talentId);
  if (!found) return false;
  if (progress.school && progress.school !== found.schoolId) return false;
  const next = SCHOOLS[found.schoolId].talents[progress.talents.length];
  if (!next || next.id !== talentId) return false;
  progress.school = found.schoolId;
  progress.talents.push(talentId);
  progress.talentPoints -= 1;
  return true;
}

export function addToBag(progress, item) {
  if (progress.bag.length >= BAG_MAX) return false;
  progress.bag.push(item);
  return true;
}

// Swaps the bag item into its slot. The previous item goes back to the bag.
export function equipFromBag(progress, itemId) {
  const idx = progress.bag.findIndex((i) => i.id === itemId);
  if (idx < 0) return false;
  const item = progress.bag[idx];
  const old = progress.equipped[item.slot];
  progress.bag.splice(idx, 1);
  progress.equipped[item.slot] = item;
  if (old) progress.bag.push(old);
  return true;
}

export function discardFromBag(progress, itemId) {
  const idx = progress.bag.findIndex((i) => i.id === itemId);
  if (idx < 0) return false;
  progress.bag.splice(idx, 1);
  return true;
}
