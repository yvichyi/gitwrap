// Plane geometry on the XZ floor. Yaw 0 looks down +z; direction = (sin yaw, cos yaw).
export const DEG = Math.PI / 180;

export const dirOf = (yaw) => ({ x: Math.sin(yaw), z: Math.cos(yaw) });
export const yawOf = (dx, dz) => Math.atan2(dx, dz);

export function wrapAngle(a) {
  let r = a % (Math.PI * 2);
  if (r > Math.PI) r -= Math.PI * 2;
  if (r < -Math.PI) r += Math.PI * 2;
  return r;
}

export function angleDiff(a, b) {
  return Math.abs(wrapAngle(a - b));
}

export function dist(ax, az, bx, bz) {
  return Math.hypot(ax - bx, az - bz);
}

export function clamp(v, lo, hi) {
  return v < lo ? lo : v > hi ? hi : v;
}

// Is point (px,pz) inside a circular sector centred at (ox,oz) facing `facing` with full angle `arcDeg`?
export function inSector(ox, oz, facing, arcDeg, reach, px, pz, extraRadius = 0) {
  const dx = px - ox;
  const dz = pz - oz;
  const d = Math.hypot(dx, dz);
  if (d > reach + extraRadius) return false;
  if (d < 1e-6) return true;
  const toPoint = yawOf(dx, dz);
  return angleDiff(toPoint, facing) <= (arcDeg * DEG) / 2 + Math.asin(Math.min(1, extraRadius / Math.max(d, extraRadius + 1e-6)));
}

// Circle vs capsule (segment from (x,z) forward `length`, with radius `width/2`).
export function inLine(ox, oz, facing, length, width, px, pz, extraRadius = 0) {
  const f = dirOf(facing);
  const dx = px - ox;
  const dz = pz - oz;
  const along = dx * f.x + dz * f.z;
  const across = Math.abs(-dx * f.z + dz * f.x);
  return along >= -extraRadius && along <= length + extraRadius && across <= width / 2 + extraRadius;
}

export function circleHit(ax, az, ar, bx, bz, br) {
  const r = ar + br;
  const dx = ax - bx;
  const dz = az - bz;
  return dx * dx + dz * dz <= r * r;
}

// Push circle (x,z,r) out of the rectangle [-half, half]^2 walls.
export function clampToArena(e, half, margin) {
  const lim = half - margin;
  e.x = clamp(e.x, -lim, lim);
  e.z = clamp(e.z, -lim, lim);
}

// Resolve a moving circle against static circular obstacles. Returns the obstacle hit (or null).
export function pushOutOfObstacles(e, radius, obstacles) {
  let hit = null;
  for (const o of obstacles) {
    const dx = e.x - o.x;
    const dz = e.z - o.z;
    const min = radius + o.r;
    const d2 = dx * dx + dz * dz;
    if (d2 < min * min) {
      const d = Math.sqrt(d2) || 1e-4;
      const push = min - d;
      e.x += (dx / d) * push;
      e.z += (dz / d) * push;
      hit = o;
    }
  }
  return hit;
}
