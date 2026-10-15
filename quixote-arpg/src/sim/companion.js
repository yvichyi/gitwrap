import { COMPANION } from '../config/balance.js';
import { dirOf, dist, pushOutOfObstacles, yawOf, clampToArena } from './geometry.js';

// Sancho follows the knight, strikes the nearest enemy in reach, and every so often rallies the knight (+attack).
export class Companion {
  constructor() {
    this.x = 0;
    this.z = 0;
    this.yaw = 0;
    this.radius = COMPANION.radius;
    this.cd = 1.2;
    this.rallyCd = COMPANION.encourageCooldown;
    this.attacks = 0;
  }

  step(dt, world) {
    const p = world.player;
    if (p.dead) return;

    const back = dirOf(p.yaw + Math.PI);
    const tx = p.x + back.x * COMPANION.followDist;
    const tz = p.z + back.z * COMPANION.followDist;
    const toTarget = dist(this.x, this.z, tx, tz);
    if (toTarget > 0.3) {
      const dir = dirOf(yawOf(tx - this.x, tz - this.z));
      const speed = toTarget > 4 ? COMPANION.speed * 1.5 : COMPANION.speed;
      const step = Math.min(toTarget, speed * dt);
      this.x += dir.x * step;
      this.z += dir.z * step;
      this.yaw = yawOf(dir.x, dir.z);
    }
    pushOutOfObstacles(this, this.radius, world.obstacles);
    clampToArena(this, world.half, this.radius);

    this.cd = Math.max(0, this.cd - dt);
    if (this.cd <= 0) {
      const target = world.nearestEnemy(this.x, this.z, COMPANION.attackRange);
      if (target) {
        this.cd = COMPANION.attackCooldown;
        this.yaw = yawOf(target.x - this.x, target.z - this.z);
        const dmg = COMPANION.dmgBase + COMPANION.dmgPerLevel * p.stats.level;
        world.damageEnemy(target, { dmg, crit: false }, { source: 'companion', knock: 0.4 });
        this.attacks += 1;
        world.emit('companionAttack', { x: this.x, z: this.z });
      }
    }

    this.rallyCd -= dt;
    if (this.rallyCd <= 0) {
      this.rallyCd = COMPANION.encourageCooldown;
      p.buffs.encourage = COMPANION.encourageDuration;
      world.emit('rally', { x: this.x, z: this.z });
    }
  }
}
