import * as THREE from '../../vendor/three/three.module.js';
import { particleTexture } from './assets.js';

// Point-sprite particles in one draw call. Positions and colours are written into typed arrays each frame.
export class Particles {
  constructor(scene, max = 320) {
    this.max = max;
    this.pos = new Float32Array(max * 3);
    this.col = new Float32Array(max * 3);
    this.vel = new Float32Array(max * 3);
    this.life = new Float32Array(max);
    this.lifeMax = new Float32Array(max).fill(1);
    this.cursor = 0;
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(this.pos, 3));
    geo.setAttribute('color', new THREE.BufferAttribute(this.col, 3));
    const mat = new THREE.PointsMaterial({
      size: 0.32,
      map: particleTexture(),
      vertexColors: true,
      transparent: true,
      depthWrite: false,
      sizeAttenuation: true,
    });
    this.points = new THREE.Points(geo, mat);
    this.points.frustumCulled = false;
    scene.add(this.points);
    this.pos.fill(-999);
  }

  emit(x, y, z, { color = '#ffffff', count = 8, speed = 3, life = 0.5, up = 1.5, spread = 1 } = {}) {
    const c = new THREE.Color(color);
    for (let i = 0; i < count; i++) {
      const idx = this.cursor;
      this.cursor = (this.cursor + 1) % this.max;
      const a = Math.random() * Math.PI * 2;
      const s = speed * (0.4 + Math.random() * 0.6);
      this.pos[idx * 3] = x + (Math.random() - 0.5) * 0.2 * spread;
      this.pos[idx * 3 + 1] = y;
      this.pos[idx * 3 + 2] = z + (Math.random() - 0.5) * 0.2 * spread;
      this.vel[idx * 3] = Math.sin(a) * s;
      this.vel[idx * 3 + 1] = up * (0.5 + Math.random() * 0.8);
      this.vel[idx * 3 + 2] = Math.cos(a) * s;
      this.col[idx * 3] = c.r;
      this.col[idx * 3 + 1] = c.g;
      this.col[idx * 3 + 2] = c.b;
      this.life[idx] = life;
      this.lifeMax[idx] = life;
    }
  }

  update(dt) {
    for (let i = 0; i < this.max; i++) {
      if (this.life[i] <= 0) continue;
      this.life[i] -= dt;
      if (this.life[i] <= 0) {
        this.pos[i * 3 + 1] = -999;
        continue;
      }
      this.vel[i * 3 + 1] -= 9 * dt;
      this.pos[i * 3] += this.vel[i * 3] * dt;
      this.pos[i * 3 + 1] = Math.max(0.05, this.pos[i * 3 + 1] + this.vel[i * 3 + 1] * dt);
      this.pos[i * 3 + 2] += this.vel[i * 3 + 2] * dt;
      const k = Math.max(0, this.life[i] / this.lifeMax[i]);
      this.col[i * 3] *= 1 - (1 - k) * 0.02;
      this.col[i * 3 + 1] *= 1 - (1 - k) * 0.02;
      this.col[i * 3 + 2] *= 1 - (1 - k) * 0.02;
    }
    this.points.geometry.attributes.position.needsUpdate = true;
    this.points.geometry.attributes.color.needsUpdate = true;
  }
}

// Expanding ring decals for spawns, perfect blocks and level-ups.
export class Rings {
  constructor(scene) {
    this.scene = scene;
    this.items = [];
  }

  spawn(x, z, { color = '#ffd60a', radius = 2.6, life = 0.5, y = 0.06 } = {}) {
    const mesh = new THREE.Mesh(
      new THREE.RingGeometry(radius * 0.86, radius, 40),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.9, depthWrite: false, side: THREE.DoubleSide }),
    );
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.set(x, y, z);
    this.scene.add(mesh);
    this.items.push({ mesh, t: 0, life });
  }

  update(dt) {
    for (const r of this.items) {
      r.t += dt;
      const k = Math.min(1, r.t / r.life);
      r.mesh.scale.setScalar(0.4 + k * 0.9);
      r.mesh.material.opacity = 0.9 * (1 - k);
    }
    this.items = this.items.filter((r) => {
      if (r.t < r.life) return true;
      this.scene.remove(r.mesh);
      r.mesh.geometry.dispose();
      r.mesh.material.dispose();
      return false;
    });
  }
}

// Floating combat numbers and labels, projected from world space onto the DOM layer.
export class DamageText {
  constructor(layer, max = 36) {
    this.layer = layer;
    this.max = max;
    this.items = [];
  }

  spawn(text, cls, x, y, z) {
    if (this.items.length >= this.max) this.items.shift().el.remove();
    const el = document.createElement('div');
    el.className = `dmg ${cls}`;
    el.textContent = text;
    this.layer.appendChild(el);
    this.items.push({ el, x, y, z, t: 0, life: 0.9 });
  }

  update(dt, project) {
    for (const it of this.items) {
      it.t += dt;
      const k = it.t / it.life;
      const p = project(it.x, it.y + k * 1.2, it.z);
      if (p) {
        it.el.style.transform = `translate(${p.x.toFixed(1)}px, ${p.y.toFixed(1)}px) translate(-50%, -50%)`;
        it.el.style.opacity = String(Math.max(0, 1 - Math.max(0, k - 0.55) * 2.2));
        it.el.style.display = '';
      } else {
        it.el.style.display = 'none';
      }
    }
    this.items = this.items.filter((it) => {
      if (it.t < it.life) return true;
      it.el.remove();
      return false;
    });
  }

  clear() {
    for (const it of this.items) it.el.remove();
    this.items = [];
  }
}
