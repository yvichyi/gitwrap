import { createProgress } from '../src/sim/progression.js';
import { World } from '../src/sim/world.js';
import { STAGES } from '../src/config/content.js';

export const NO_INPUT = Object.freeze({
  move: { x: 0, z: 0 },
  attack: false,
  heavyDown: false,
  heavyHeld: false,
  dodge: false,
  blockDown: false,
  blockHeld: false,
  skill: null,
});

export const input = (over = {}) => ({ ...NO_INPUT, move: { x: 0, z: 0 }, ...over });

// Deterministic battle: no crits, no random waves, stage 1 layout.
export function makeWorld({ stageIndex = 0, progress = createProgress(), seed = 7 } = {}) {
  const world = new World({ stage: STAGES[stageIndex], stageIndex, progress, seed });
  world.obstacles = [];
  world.waveTimer = 1e9;
  // The player never crits in these tests; the world rng stays seeded and reproducible.
  world.player.rng = { next: () => 0.99 };
  // Sancho is switched off so only the knight deals damage in these tests.
  world.companion.step = () => {};
  return world;
}

// Runs the world for `seconds` of sim time with the given input each tick (edges only on the first tick).
export function run(world, seconds, inp = input()) {
  const ticks = Math.round(seconds * 60);
  const seen = [];
  for (let i = 0; i < ticks; i++) {
    world.step(1 / 60, i === 0 ? inp : input({ move: inp.move, blockHeld: inp.blockHeld, heavyHeld: inp.heavyHeld }));
    seen.push(...world.drainEvents());
  }
  return seen;
}

export function ticks(world, n, inp = input()) {
  const seen = [];
  for (let i = 0; i < n; i++) {
    world.step(1 / 60, inp);
    seen.push(...world.drainEvents());
  }
  return seen;
}

// A passive dummy target in front of the knight. A sheep held in 'warn' with no telegraph never moves or stampedes.
export function dummy(world, distance = 1.4, { type = 'sheep', hp } = {}) {
  const p = world.player;
  const x = p.x + Math.sin(p.yaw) * distance;
  const z = p.z + Math.cos(p.yaw) * distance;
  const e = world.spawnEnemy(type, x, z);
  if (type === 'sheep') e.state = 'warn';
  if (hp !== undefined) {
    e.hp = hp;
    e.maxHp = hp;
  }
  return e;
}

export function clearEnemies(world) {
  world.enemies = [];
}
