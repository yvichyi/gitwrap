import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createProgress, addXp, equipFromBag, addToBag } from '../../src/sim/progression.js';
import { loadSave, writeSave, clearSave, loadSettings, writeSettings, sanitizeProgress, DEFAULT_SETTINGS } from '../../src/sim/save.js';
import { generateItem } from '../../src/sim/items.js';
import { createRng } from '../../src/sim/rng.js';
import { XP } from '../../src/config/balance.js';

// A tiny in-memory localStorage so the save code runs as it would in a browser.
function fakeStorage({ throwing = false } = {}) {
  const data = new Map();
  return {
    data,
    getItem(k) {
      if (throwing) throw new Error('SecurityError');
      return data.has(k) ? data.get(k) : null;
    },
    setItem(k, v) {
      if (throwing) throw new Error('QuotaExceededError');
      data.set(k, String(v));
    },
    removeItem(k) {
      if (throwing) throw new Error('SecurityError');
      data.delete(k);
    },
  };
}

let original;
beforeEach(() => {
  original = globalThis.localStorage;
});
afterEach(() => {
  globalThis.localStorage = original;
});

test('save round-trips the character, gear and stage checkpoint', () => {
  globalThis.localStorage = fakeStorage();
  const progress = createProgress();
  addXp(progress, XP.curve(1) + XP.curve(2));
  progress.attrs.str = 9;
  const item = generateItem(createRng(4), { source: 'elite', stageIndex: 1, id: 'g1' });
  item.slot = 'armor';
  addToBag(progress, item);
  equipFromBag(progress, 'g1');

  assert.equal(writeSave(progress, 2), true);
  const loaded = loadSave();
  assert.equal(loaded.stageIndex, 2);
  assert.equal(loaded.progress.level, progress.level);
  assert.equal(loaded.progress.attrs.str, 9);
  assert.equal(loaded.progress.equipped.armor.id, 'g1');
  assert.deepEqual(loaded.progress.equipped.weapon.name, progress.equipped.weapon.name);
});

test('corrupt, foreign-version, or out-of-range saves are ignored, not thrown', () => {
  globalThis.localStorage = fakeStorage();
  globalThis.localStorage.setItem('quixote-arpg:save:v1', '{not json');
  assert.equal(loadSave(), null);

  globalThis.localStorage.setItem('quixote-arpg:save:v1', JSON.stringify({ version: 99, progress: createProgress() }));
  assert.equal(loadSave(), null);

  const bad = { ...createProgress(), level: 999 };
  globalThis.localStorage.setItem('quixote-arpg:save:v1', JSON.stringify({ version: 1, stageIndex: 0, progress: bad }));
  assert.equal(loadSave(), null);
});

test('sanitizeProgress drops malformed gear instead of crashing the battle', () => {
  const raw = createProgress();
  raw.equipped.armor = { id: 'x', slot: 'helm', name: 'wrong slot' };
  raw.bag = [{ slot: 'armor' }, 'junk', null];
  const clean = sanitizeProgress(raw);
  assert.equal(clean.equipped.armor, null);
  assert.deepEqual(clean.bag, []);
  assert.equal(clean.equipped.weapon.id, 'start-lance', 'the starting weapon always survives');
});

test('storage that throws (private mode, quota) never breaks the game', () => {
  globalThis.localStorage = fakeStorage({ throwing: true });
  assert.equal(writeSave(createProgress(), 0), false);
  assert.equal(loadSave(), null);
  assert.doesNotThrow(() => writeSettings(DEFAULT_SETTINGS));
  assert.deepEqual(loadSettings().buttons, {});
  assert.doesNotThrow(() => clearSave());
});

test('settings are clamped to safe ranges when read back', () => {
  globalThis.localStorage = fakeStorage();
  globalThis.localStorage.setItem(
    'quixote-arpg:settings:v1',
    JSON.stringify({ hand: 'up', touchScale: 9, touchOpacity: -1, buttons: { attack: { x: 'a', y: 0.1 }, dodge: { x: 0.5, y: 0.2, s: 99 } } }),
  );
  const s = loadSettings();
  assert.equal(s.hand, 'right');
  assert.equal(s.touchScale, 1.4);
  assert.equal(s.touchOpacity, 0.3);
  assert.equal(s.buttons.attack, undefined, 'non-numeric positions are dropped');
  assert.equal(s.buttons.dodge.s, 0.4, 'size is clamped');
});
