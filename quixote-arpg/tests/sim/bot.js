import { dist } from '../../src/sim/geometry.js';

// A scripted player for playtesting the sim. It chases the nearest enemy, swings when close,
// rolls away from telegraphs and incoming hitboxes, and holds block when a roll is not ready.
export function botInput(w) {
  const p = w.player;
  const target = w.nearestEnemy(p.x, p.z, 60);
  const move = { x: 0, z: 0 };
  let d = Infinity;
  if (target) {
    const dx = target.x - p.x;
    const dz = target.z - p.z;
    d = Math.hypot(dx, dz) || 1;
    if (d > 1.8) {
      move.x = dx / d;
      move.z = dz / d;
    }
  }
  const threat = w.hitboxes.some((hb) => dist(hb.x, hb.z, p.x, p.z) < 2.6)
    || w.projectiles.some((pr) => pr.owner === 'enemy' && dist(pr.x, pr.z, p.x, p.z) < 2.4)
    || w.enemies.some((e) => e.telegraph && dist(e.x, e.z, p.x, p.z) < (e.telegraph.reach ?? e.telegraph.radius ?? e.telegraph.length ?? 3) + 1.2)
    || w.enemies.some((e) => (e.state === 'charge' || e.state === 'dashing') && dist(e.x, e.z, p.x, p.z) < 3);
  const free = !p.action || p.action.kind === 'light' && p.action.phase === 'recovery' || p.action.kind === 'block';
  const dodge = threat && p.dodgeCd <= 0 && free;
  const attack = !threat && d <= 2.0 && (!p.action || (p.action.kind === 'light' && p.action.phase !== 'windup') || p.action.kind === 'block') && free;
  const skill = !threat && p.cooldowns.thrust <= 0 && d > 2.0 && d < 4 && free ? 'thrust' : null;
  const block = threat && !dodge;
  return {
    move,
    attack,
    heavyDown: false,
    heavyHeld: false,
    dodge,
    blockDown: block && p.state !== 'block',
    blockHeld: block,
    skill,
  };
}
