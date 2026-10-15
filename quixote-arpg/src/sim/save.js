import { MAX_LEVEL } from '../config/balance.js';
import { SLOTS } from '../config/content.js';
import { createProgress, ATTR_KEYS } from './progression.js';

export const SAVE_VERSION = 1;
const SAVE_KEY = 'quixote-arpg:save:v1';
const SETTINGS_KEY = 'quixote-arpg:settings:v1';

// localStorage can throw (private mode, blocked storage, quota). Every access goes through these two helpers.
function readKey(key) {
  try {
    return globalThis.localStorage?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function writeKey(key, value) {
  try {
    globalThis.localStorage?.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}

function removeKey(key) {
  try {
    globalThis.localStorage?.removeItem(key);
  } catch {
    // storage unavailable; nothing to remove
  }
}

function isItem(v, slot) {
  return (
    v &&
    typeof v === 'object' &&
    typeof v.id === 'string' &&
    v.slot === slot &&
    typeof v.name === 'string' &&
    typeof v.rarity === 'string' &&
    Number.isFinite(v.ilvl) &&
    Number.isFinite(v.dmg ?? 0) &&
    v.implicit &&
    Array.isArray(v.affixes)
  );
}

// Returns a valid progress object, or null when the stored data is unusable.
export function sanitizeProgress(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const base = createProgress();
  const level = Math.floor(raw.level);
  if (!Number.isFinite(level) || level < 1 || level > MAX_LEVEL) return null;

  base.level = level;
  base.xp = Math.max(0, Math.floor(Number(raw.xp) || 0));
  base.attrPoints = Math.max(0, Math.floor(Number(raw.attrPoints) || 0));
  base.talentPoints = Math.max(0, Math.floor(Number(raw.talentPoints) || 0));
  for (const k of ATTR_KEYS) {
    const v = Math.floor(Number(raw.attrs?.[k]));
    if (Number.isFinite(v) && v >= 1 && v <= 999) base.attrs[k] = v;
  }
  base.school = typeof raw.school === 'string' ? raw.school : null;
  base.talents = Array.isArray(raw.talents) ? raw.talents.filter((t) => typeof t === 'string') : [];
  for (const slot of SLOTS) {
    const item = raw.equipped?.[slot];
    base.equipped[slot] = isItem(item, slot) ? item : slot === 'weapon' ? base.equipped.weapon : null;
  }
  base.bag = Array.isArray(raw.bag) ? raw.bag.filter((i) => SLOTS.includes(i?.slot) && isItem(i, i.slot)).slice(0, 30) : [];
  return base;
}

// A save holds the character and the furthest stage reached. Stage state itself is not saved (checkpoint = stage start).
export function loadSave() {
  const text = readKey(SAVE_KEY);
  if (!text) return null;
  try {
    const data = JSON.parse(text);
    if (data?.version !== SAVE_VERSION) return null;
    const progress = sanitizeProgress(data.progress);
    if (!progress) return null;
    const stageIndex = Number.isInteger(data.stageIndex) && data.stageIndex >= 0 ? data.stageIndex : 0;
    return { progress, stageIndex };
  } catch {
    return null;
  }
}

export function writeSave(progress, stageIndex) {
  return writeKey(SAVE_KEY, JSON.stringify({ version: SAVE_VERSION, savedAt: Date.now(), stageIndex, progress }));
}

export function clearSave() {
  removeKey(SAVE_KEY);
}

export const DEFAULT_SETTINGS = {
  hand: 'right',
  touchScale: 1,
  touchOpacity: 0.85,
  buttons: {},
  damageNumbers: true,
  shake: true,
  quality: 'balanced',
  showTouch: 'auto',
  hideSecondary: true,
};

export function loadSettings() {
  const text = readKey(SETTINGS_KEY);
  if (!text) return { ...DEFAULT_SETTINGS, buttons: {} };
  try {
    const raw = JSON.parse(text);
    const s = { ...DEFAULT_SETTINGS, ...raw, buttons: {} };
    if (!['right', 'left'].includes(s.hand)) s.hand = 'right';
    s.touchScale = clampNum(s.touchScale, 0.7, 1.4, 1);
    s.touchOpacity = clampNum(s.touchOpacity, 0.3, 1, 0.85);
    if (raw.buttons && typeof raw.buttons === 'object') {
      for (const [id, pos] of Object.entries(raw.buttons)) {
        if (pos && Number.isFinite(pos.x) && Number.isFinite(pos.y)) {
          s.buttons[id] = { x: clampNum(pos.x, 0, 0.9, 0), y: clampNum(pos.y, 0, 0.9, 0), s: clampNum(pos.s, 0.08, 0.4, 0.16) };
        }
      }
    }
    return s;
  } catch {
    return { ...DEFAULT_SETTINGS, buttons: {} };
  }
}

export function writeSettings(settings) {
  return writeKey(SETTINGS_KEY, JSON.stringify(settings));
}

function clampNum(v, lo, hi, fallback) {
  const n = Number(v);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(lo, Math.min(hi, n));
}
