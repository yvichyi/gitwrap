import { AFFIXES, BASES, DROP_WEIGHTS, RARITIES, SLOTS, UNIQUES } from '../config/content.js';

// Item level scales affix ranges: +15% per stage.
export const ilvlScale = (ilvl) => 1 + 0.15 * (ilvl - 1);

function rollAffixes(rng, count, scale) {
  const pool = [...AFFIXES];
  const out = [];
  for (let i = 0; i < count && pool.length; i++) {
    const idx = rng.int(0, pool.length - 1);
    const [def] = pool.splice(idx, 1);
    const raw = rng.range(def.min, def.max) * scale;
    const value = def.pct ? Math.round(raw * 1000) / 1000 : Math.max(1, Math.round(raw));
    out.push({ stat: def.stat, value });
  }
  return out;
}

// Roll one item for a drop source ('normal' | 'elite' | 'boss') at a stage index.
// `id` is supplied by the caller so ids stay deterministic inside one run.
export function generateItem(rng, { source = 'normal', stageIndex = 0, ilvl = stageIndex + 1, id }) {
  const rarityId = rng.weighted(DROP_WEIGHTS[source]);
  const rarity = RARITIES[rarityId];
  const slot = rng.pick(SLOTS);
  const eligible = BASES[slot].filter((b) => b.tier <= stageIndex);
  let base = rng.pick(eligible);
  let unique = null;

  if (rarityId === 'legendary' && rng.chance(0.5)) {
    const entry = Object.entries(UNIQUES).find(([, u]) => u.slot === slot);
    const uniqueBase = entry && BASES[slot].find((b) => b.id === entry[1].baseId);
    if (entry && uniqueBase.tier <= stageIndex) {
      unique = entry[0];
      base = uniqueBase;
    }
  }

  const scale = ilvlScale(ilvl);
  const affixes = rollAffixes(rng, rarity.affixes, scale);
  return {
    id,
    slot,
    baseId: base.id,
    baseName: base.name,
    name: unique ? UNIQUES[unique].name : base.name,
    rarity: rarityId,
    ilvl,
    dmg: slot === 'weapon' ? Math.round(base.dmg * scale * 10) / 10 : 0,
    implicit: { ...base.implicit },
    affixes,
    unique,
  };
}

export function describeAffix(affix) {
  const def = AFFIXES.find((a) => a.stat === affix.stat);
  const v = def.pct ? `+${Math.round(affix.value * 1000) / 10}%` : `+${affix.value}`;
  return `${def.name}${v}`;
}
