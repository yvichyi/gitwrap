import * as THREE from '../../vendor/three/three.module.js';
import { DEG } from '../sim/geometry.js';
import { RARITIES } from '../config/content.js';
import { DamageText, Particles, Rings } from './fx.js';
import {
  buildBoss,
  buildKnight,
  buildLootBeam,
  buildMirror,
  buildPage,
  buildProp,
  buildSancho,
  buildSheep,
  buildSpellbook,
  buildStone,
  buildWindmill,
  parchmentTexture,
} from './assets.js';

const BUILDERS = {
  sheep: buildSheep,
  spellbook: buildSpellbook,
  windmill: buildWindmill,
  mirror: buildMirror,
  boss: buildBoss,
  stone: buildStone,
};

const THEMES = {
  plain: { skyTop: '#3b2f55', skyHorizon: '#f2b872', ground: '#d6bf8e', fog: '#e0a878', ambient: '#ffe2b8', silhouette: '#2e2420' },
  hill: { skyTop: '#243450', skyHorizon: '#a9c0d4', ground: '#c9b07a', fog: '#93a8ba', ambient: '#dbe8f5', silhouette: '#22262e' },
  castle: { skyTop: '#0d0a18', skyHorizon: '#5a2f4a', ground: '#7d7368', fog: '#2b2030', ambient: '#c0a8b8', silhouette: '#120e16' },
};

// Stance of camera zoom and shake amounts, tuned to read clearly on a phone.
const CAM = { height: 12.5, back: 9.5, lookHeight: 1.0, fov: 46 };

function gradientTexture(top, bottom) {
  const c = document.createElement('canvas');
  c.width = 4;
  c.height = 256;
  const ctx = c.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, 0, 256);
  g.addColorStop(0, top);
  g.addColorStop(0.65, bottom);
  g.addColorStop(1, bottom);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 4, 256);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

// Owns the Three.js scene: stage environment, actors, telegraphs, projectiles, loot, camera and effects.
export class Renderer {
  constructor({ canvas, damageLayer, quality = 'balanced' }) {
    this.canvas = canvas;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: quality === 'quality', powerPreference: 'high-performance' });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.setQuality(quality);

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(CAM.fov, 16 / 9, 0.1, 400);
    this.cameraDist = 1;
    this.camShake = 0;
    this.camPush = 0;
    this.camPos = new THREE.Vector3();
    this.lookAt = new THREE.Vector3();

    this.hemi = new THREE.HemisphereLight('#fff3e0', '#5a4632', 1.25);
    this.sun = new THREE.DirectionalLight('#ffd9a0', 1.5);
    this.sun.position.set(-12, 22, 10);
    this.scene.add(this.hemi, this.sun);

    this.stageGroup = new THREE.Group();
    this.scene.add(this.stageGroup);
    this.actors = new Map();
    this.telegraphs = new Map();
    this.hitShapes = new WeakMap();
    this.pages = new Map();
    this.loot = new Map();

    this.particles = new Particles(this.scene);
    this.rings = new Rings(this.scene);
    this.texts = new DamageText(damageLayer);
    this.settings = { damageNumbers: true, shake: true };
    this.time = 0;
    this.lastPlayerPos = null;
    this.walkT = 0;
  }

  setQuality(quality) {
    const dpr = globalThis.devicePixelRatio || 1;
    const cap = quality === 'performance' ? 1 : quality === 'quality' ? 2 : 1.5;
    this.renderer.setPixelRatio(Math.min(dpr, cap));
    this.quality = quality;
  }

  resize(w, h) {
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / Math.max(1, h);
    this.camera.updateProjectionMatrix();
  }

  // ---- stage environment --------------------------------------------------------

  buildStage(world) {
    this.clearStage();
    const theme = THEMES[world.stage.theme] ?? THEMES.plain;
    const size = world.stage.size;

    this.scene.background = null;
    this.renderer.setClearColor(theme.fog);
    this.scene.fog = new THREE.Fog(theme.fog, 26, 78);
    this.hemi.color.set(theme.ambient);

    const sky = new THREE.Mesh(
      new THREE.SphereGeometry(220, 16, 10),
      new THREE.MeshBasicMaterial({ map: gradientTexture(theme.skyTop, theme.skyHorizon), side: THREE.BackSide, fog: false }),
    );
    this.stageGroup.add(sky);

    const groundTex = parchmentTexture(theme.ground);
    groundTex.repeat.set(size / 8, size / 8);
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(size * 3, size * 3),
      new THREE.MeshStandardMaterial({ map: groundTex, roughness: 0.95, metalness: 0 }),
    );
    ground.rotation.x = -Math.PI / 2;
    this.stageGroup.add(ground);

    // Silhouettes on the horizon: windmills at the edge of the plain, faded by fog.
    const silhouette = new THREE.MeshBasicMaterial({ color: theme.silhouette, fog: false });
    for (let i = 0; i < 11; i++) {
      const a = (i / 11) * Math.PI * 2 + 0.3;
      const r = 120 + (i % 3) * 8;
      const s = buildWindmill();
      s.traverse((o) => {
        if (o.isMesh) o.material = silhouette;
      });
      s.position.set(Math.sin(a) * r, 0, Math.cos(a) * r);
      s.scale.setScalar(4.2 + (i % 2));
      s.rotation.y = -a;
      this.stageGroup.add(s);
    }

    // Arena boundary: low fence rails.
    const rail = new THREE.MeshStandardMaterial({ color: '#7a5a38', flatShading: true });
    const half = size / 2;
    for (const [x, z, sx, sz] of [
      [0, half, size, 0.4],
      [0, -half, size, 0.4],
      [half, 0, 0.4, size],
      [-half, 0, 0.4, size],
    ]) {
      const railMesh = new THREE.Mesh(new THREE.BoxGeometry(sx, 0.7, sz), rail);
      railMesh.position.set(x, 0.35, z);
      this.stageGroup.add(railMesh);
    }

    for (const o of world.obstacles) {
      const prop = buildProp(o.kind, o.r);
      prop.position.set(o.x, 0, o.z);
      prop.rotation.y = (o.x * 37 + o.z * 17) % 6;
      this.stageGroup.add(prop);
    }
  }

  clearStage() {
    for (const child of [...this.stageGroup.children]) {
      this.stageGroup.remove(child);
      child.traverse?.((o) => {
        if (o.isMesh && o.material && !o.material.userData?.shared) o.material.dispose?.();
      });
    }
    for (const [, a] of this.actors) this.scene.remove(a.obj);
    for (const [, t] of this.telegraphs) this.scene.remove(t.obj);
    for (const [, p] of this.pages) this.scene.remove(p);
    for (const [, l] of this.loot) this.scene.remove(l.obj);
    this.actors.clear();
    this.telegraphs.clear();
    this.pages.clear();
    this.loot.clear();
    this.texts.clear();
    this.lastPlayerPos = null;
  }

  // ---- per-frame sync -------------------------------------------------------------

  // Reads the sim world and updates every mesh. dt is real frame time.
  sync(world, dt) {
    this.time += dt;
    this.syncPlayer(world, dt);
    this.syncCompanion(world);
    this.syncEnemies(world, dt);
    this.syncTelegraphs(world);
    this.syncProjectiles(world);
    this.syncLoot(world, dt);
    this.updateCamera(world, dt);
    this.particles.update(dt);
    this.rings.update(dt);
    this.texts.update(dt, (x, y, z) => this.project(x, y, z));
  }

  ensureActor(key, type, build) {
    let a = this.actors.get(key);
    if (!a) {
      const obj = build();
      obj.traverse((o) => {
        if (o.isMesh && o.material && !o.material.userData?.shared) o.material = o.material.clone();
      });
      this.scene.add(obj);
      a = { obj, type, baseY: 0, flashMats: [] };
      obj.traverse((o) => {
        if (o.isMesh && o.material?.emissive) a.flashMats.push(o.material);
      });
      this.actors.set(key, a);
    }
    return a;
  }

  syncPlayer(world, dt) {
    const p = world.player;
    const a = this.ensureActor('player', 'knight', buildKnight);
    const { rig, body } = a.obj.userData;
    a.obj.position.set(p.x, 0, p.z);
    a.obj.rotation.y = p.yaw;

    const moved = this.lastPlayerPos ? Math.hypot(p.x - this.lastPlayerPos.x, p.z - this.lastPlayerPos.z) / Math.max(dt, 1e-3) : 0;
    this.lastPlayerPos = { x: p.x, z: p.z };
    this.walkT += dt * Math.min(moved, 6) * 2.4;
    const walk = Math.min(1, moved / 3);
    const idle = Math.sin(this.time * 2.2) * 0.04;

    rig.legL.rotation.x = Math.sin(this.walkT) * 0.7 * walk;
    rig.legR.rotation.x = -Math.sin(this.walkT) * 0.7 * walk;
    rig.armL.rotation.x = idle;
    rig.armR.rotation.x = -idle;
    rig.armL.rotation.z = 0;
    rig.armR.rotation.z = 0;
    rig.shield.rotation.set(0, 0, 0);
    rig.lance.rotation.set(Math.PI / 2, 0, 0);
    body.rotation.set(0, 0, 0);
    body.position.set(0, 0, 0);
    rig.head.rotation.set(0, 0, 0);

    const act = p.action;
    const k = act ? act.t / (act.dur ?? 1) : 0;
    switch (act?.kind) {
      case 'light': {
        const ramp = act.phase === 'windup' ? -1.7 * (act.t / act.w) : act.phase === 'active' ? -1.7 + 2.5 * ((act.t - act.w) / Math.max(act.a, 1e-3)) : 0.8 * (1 - (act.t - act.w - act.a) / Math.max(act.r, 1e-3));
        rig.armR.rotation.x = Math.max(-1.7, Math.min(1.1, ramp));
        break;
      }
      case 'heavyCharge': {
        const charge = Math.min(1, p.heavyCharge / 0.9);
        rig.armR.rotation.x = -1.8 - charge * 0.3;
        body.rotation.x = -0.05 * charge;
        break;
      }
      case 'heavy': {
        const phase = act.phase;
        const v = phase === 'windup' ? -2.1 * (act.t / act.w) : phase === 'active' ? -2.1 + 3.3 * ((act.t - act.w) / Math.max(act.a, 1e-3)) : 1.2 * (1 - (act.t - act.w - act.a) / Math.max(act.r, 1e-3));
        rig.armR.rotation.x = Math.max(-2.1, Math.min(1.3, v));
        body.rotation.y = 0.25 * Math.sin(Math.min(1, k) * Math.PI);
        break;
      }
      case 'thrust': {
        rig.armR.rotation.x = -1.2 + Math.min(1, act.t / 0.14) * 1.2;
        body.position.z = Math.min(0.9, act.t * 4);
        break;
      }
      case 'bash': {
        rig.armL.rotation.x = -1.3 + Math.min(1, act.t / 0.12) * 0.9;
        body.position.z = Math.min(0.4, act.t * 2);
        break;
      }
      case 'charge': {
        body.rotation.x = 0.35;
        rig.armR.rotation.x = -1.2;
        break;
      }
      case 'whirl': {
        body.rotation.y = act.t * 14;
        rig.armR.rotation.set(0, 0, -Math.PI / 2);
        rig.armR.rotation.x = -Math.PI / 2;
        break;
      }
      case 'dodge': {
        body.rotation.x = -Math.PI * 2 * Math.min(1, k) * 0.9;
        body.position.y = Math.sin(Math.min(1, k) * Math.PI) * 0.2;
        break;
      }
      case 'block': {
        rig.armL.rotation.x = -1.1;
        rig.shield.rotation.x = 0.3;
        body.position.y = -0.1;
        break;
      }
      case 'stagger': {
        body.rotation.z = Math.sin(act.t * 36) * 0.18 * (1 - k);
        break;
      }
      default:
        break;
    }

    if (world.player.state === 'dead') {
      body.rotation.x = Math.min(Math.PI / 2, (body.rotation.x || 0) + dt * 2.2);
    }
    this.applyFlash(a, world.player.state === 'block' ? 0.18 : 0);
    if (world.player.invulnerable && Math.floor(this.time * 18) % 2 === 0) {
      this.setOpacity(a, 0.35);
    } else {
      this.setOpacity(a, 1);
    }
  }

  setOpacity(a, opacity) {
    if (a.opacity === opacity) return;
    a.opacity = opacity;
    a.obj.traverse((o) => {
      if (o.isMesh && o.material) {
        o.material.transparent = opacity < 1;
        o.material.opacity = opacity;
        o.material.depthWrite = opacity >= 1;
      }
    });
  }

  applyFlash(a, emissive) {
    for (const m of a.flashMats) {
      if (m.userData.baseEmissive === undefined) m.userData.baseEmissive = m.emissive.clone();
      m.emissive.copy(m.userData.baseEmissive).lerp(new THREE.Color('#ffffff'), emissive);
    }
  }

  syncCompanion(world) {
    const c = world.companion;
    const a = this.ensureActor('sancho', 'sancho', buildSancho);
    a.obj.position.set(c.x, 0, c.z);
    a.obj.rotation.y = c.yaw;
    const { body, sack } = a.obj.userData;
    body.position.y = Math.abs(Math.sin(this.time * 9)) * 0.05;
    sack.rotation.y = this.time * 2;
  }

  syncEnemies(world, dt) {
    const alive = new Set();
    for (const e of world.enemies) {
      if (e.dead) continue;
      if (e.kind === 'stone') {
        alive.add(`e${e.id}`);
        const a = this.ensureActor(`e${e.id}`, 'stone', buildStone);
        a.obj.position.set(e.x, 0, e.z);
        a.obj.rotation.y += dt * 1.6;
        a.obj.position.y = Math.sin(this.time * 2 + e.orbit) * 0.15;
        this.applyFlash(a, e.flash > 0 ? 0.8 : 0);
        continue;
      }
      alive.add(`e${e.id}`);
      const builder = BUILDERS[e.type] ?? buildSheep;
      const a = this.ensureActor(`e${e.id}`, e.type, builder);
      a.obj.position.set(e.x, 0, e.z);
      a.obj.rotation.y = e.yaw;
      this.animateEnemy(e, a, dt);
      this.applyFlash(a, e.flash > 0 ? 0.8 : 0);
      if (e.type === 'sheep' && e.state === 'stun') a.obj.position.y = 0.05;
    }
    for (const [key, a] of [...this.actors]) {
      if (key === 'player' || key === 'sancho') continue;
      if (!alive.has(key)) {
        this.scene.remove(a.obj);
        this.actors.delete(key);
      }
    }
  }

  animateEnemy(e, a, dt) {
    const u = a.obj.userData;
    switch (e.type) {
      case 'sheep': {
        const moving = e.state === 'charge' || e.state === 'flee';
        const hop = moving ? Math.abs(Math.sin(this.time * 14 + e.id)) * 0.18 : Math.abs(Math.sin(this.time * 2 + e.id)) * 0.04;
        u.body.position.y = hop;
        u.body.rotation.x = e.state === 'charge' ? 0.2 : 0;
        if (e.state === 'warn') u.body.position.x = Math.sin(this.time * 40) * 0.04;
        else u.body.position.x = 0;
        if (e.state === 'stun') u.body.rotation.z = Math.sin(this.time * 20) * 0.4;
        else u.body.rotation.z = 0;
        break;
      }
      case 'spellbook': {
        const chanting = e.state === 'chant';
        u.cover.rotation.z = chanting ? Math.sin(this.time * 22) * 0.12 : 0;
        u.cover.position.y = 0.9 + Math.sin(this.time * 3 + e.id) * 0.1;
        if (u.runes) {
          u.runes.visible = true;
          u.runes.material.emissiveIntensity = chanting ? 2.4 + Math.sin(this.time * 30) * 1.2 : 1.1;
          u.runes.rotation.z += dt * (chanting ? 8 : 1.5);
        }
        break;
      }
      case 'windmill': {
        const fast = e.state === 'telegraph' || e.state === 'recover' ? 14 : 3.5;
        u.arms.rotation.z += dt * fast;
        break;
      }
      case 'mirror': {
        const glowing = e.state === 'guard' || e.state === 'open' || e.state === 'pending';
        const c = e.state === 'open' ? '#ffd60a' : e.state === 'guard' ? '#7fd0ff' : '#ffffff';
        a.obj.traverse((o) => {
          if (o.isMesh && o.material?.emissive) o.material.emissive.set(glowing ? c : '#000000');
        });
        break;
      }
      case 'boss': {
        u.body.position.y = Math.sin(this.time * 1.6) * 0.12;
        u.body.rotation.z = e.transformT > 0 ? Math.sin(this.time * 30) * 0.1 : 0;
        break;
      }
      default:
        break;
    }
  }

  // Telegraphs are drawn in the arena; hitboxes that are active flash in the same shape language.
  syncTelegraphs(world) {
    const live = new Set();
    for (const e of world.enemies) {
      if (e.dead || !e.telegraph) continue;
      live.add(`t${e.id}`);
      const tg = e.telegraph;
      this.drawTelegraph(`t${e.id}`, tg, tg.total ? 1 - tg.left / tg.total : 0);
    }
    for (const hb of world.hitboxes) {
      if (!(hb.life > 0)) continue;
      let key = this.hitShapes.get(hb);
      if (!key) {
        key = `h${this.hitSeq = (this.hitSeq ?? 0) + 1}`;
        this.hitShapes.set(hb, key);
      }
      live.add(key);
      this.drawTelegraph(key, hb, 1, hb.life);
    }
    for (const [key, t] of [...this.telegraphs]) {
      if (!live.has(key)) {
        this.scene.remove(t.obj);
        t.obj.traverse((o) => {
          o.geometry?.dispose?.();
          o.material?.dispose?.();
        });
        this.telegraphs.delete(key);
      }
    }
  }

  // `tg` is either a telegraph (warning) or an active hitbox (hit = true). Geometry is rebuilt only when the object changes.
  drawTelegraph(key, tg, progress, life) {
    const isHit = life !== undefined;
    let t = this.telegraphs.get(key);
    if (!t || t.tg !== tg) {
      if (t) {
        this.scene.remove(t.obj);
        t.obj.traverse((o) => {
          o.geometry?.dispose?.();
          o.material?.dispose?.();
        });
      }
      const obj = geometryFor(tg);
      this.scene.add(obj);
      t = { obj, tg };
      this.telegraphs.set(key, t);
    }
    const color = isHit ? '#ffffff' : tg.tone === 'danger' ? '#ff3b30' : '#ffd60a';
    const pulse = 0.5 + 0.5 * Math.sin(this.time * 14);
    const opacity = isHit ? Math.min(0.7, 0.25 + life * 3) : 0.18 + 0.4 * Math.min(1, progress) + 0.12 * pulse * progress;
    t.obj.traverse((o) => {
      if (o.isMesh) {
        o.material.color.set(color);
        o.material.opacity = opacity;
      }
    });
  }

  syncProjectiles(world) {
    const live = new Set();
    for (const p of world.projectiles) {
      live.add(p);
      let m = this.pages.get(p);
      if (!m) {
        m = buildPage();
        this.scene.add(m);
        this.pages.set(p, m);
      }
      m.position.set(p.x, 0, p.z);
      m.rotation.y = Math.atan2(p.vx, p.vz);
      m.children[0].rotation.z = this.time * 9 + (p.age ?? 0) * 3;
      m.children[0].material.color.set(p.owner === 'player' ? '#ffd27a' : '#fff4d6');
    }
    for (const [p, m] of [...this.pages]) {
      if (!live.has(p)) {
        this.scene.remove(m);
        this.pages.delete(p);
      }
    }
  }

  syncLoot(world, dt) {
    const live = new Set();
    for (const drop of world.loot) {
      live.add(drop.id);
      let l = this.loot.get(drop.id);
      if (!l) {
        const color = RARITIES[drop.item.rarity].color;
        const obj = buildLootBeam(color);
        obj.position.set(drop.x, 0, drop.z);
        this.scene.add(obj);
        l = { obj };
        this.loot.set(drop.id, l);
      }
      l.obj.userData.gem.rotation.y += dt * 2;
      l.obj.userData.gem.position.y = 0.5 + Math.sin(this.time * 3 + drop.x) * 0.1;
    }
    for (const [id, l] of [...this.loot]) {
      if (!live.has(id)) {
        this.scene.remove(l.obj);
        this.loot.delete(id);
      }
    }
  }

  // ---- camera --------------------------------------------------------------------

  updateCamera(world, dt) {
    const p = world.player;
    const aspect = this.camera.aspect || 1.78;
    const widen = Math.max(1, Math.min(1.45, 1.78 / aspect));
    const boss = world.liveBoss;
    const push = boss && boss.transformT > 0 ? 0.78 : 1;
    this.cameraDist += ((widen * push) - this.cameraDist) * Math.min(1, dt * 3);

    const target = new THREE.Vector3(p.x, CAM.lookHeight, p.z);
    const desired = new THREE.Vector3(p.x, CAM.height * this.cameraDist, p.z + CAM.back * this.cameraDist);
    this.camPos.lerp(desired, Math.min(1, dt * 6));
    if (this.lastPlayerPos === null || this.camPos.lengthSq() === 0) this.camPos.copy(desired);
    this.lookAt.lerp(target, Math.min(1, dt * 8));

    let shake = 0;
    if (this.settings.shake && this.camShake > 0) {
      shake = this.camShake;
      this.camShake = Math.max(0, this.camShake - dt * 2.4);
    }
    const sx = (Math.random() - 0.5) * shake;
    const sy = (Math.random() - 0.5) * shake * 0.6;
    this.camera.position.set(this.camPos.x + sx, this.camPos.y + sy, this.camPos.z);
    this.camera.lookAt(this.lookAt);
  }

  shake(amount) {
    this.camShake = Math.max(this.camShake, amount);
  }

  project(x, y, z) {
    const v = new THREE.Vector3(x, y, z).project(this.camera);
    if (v.z > 1 || v.z < -1) return null;
    const w = this.canvas.clientWidth;
    const h = this.canvas.clientHeight;
    return { x: (v.x * 0.5 + 0.5) * w, y: (-v.y * 0.5 + 0.5) * h };
  }

  // ---- events -------------------------------------------------------------------

  // Turns sim events into particles, rings, shake and damage numbers.
  handleEvents(events) {
    for (const ev of events) {
      switch (ev.type) {
        case 'hit': {
          const col = ev.crit ? '#ffd60a' : ev.back ? '#ff7a3d' : ev.weak ? '#9aa0a6' : '#fff4e0';
          this.particles.emit(ev.x, 1.1, ev.z, { color: col, count: ev.crit ? 14 : 7, speed: ev.heavy ? 5 : 3 });
          if (this.settings.damageNumbers) {
            const cls = ev.crit ? 'crit' : ev.back ? 'back' : ev.weak ? 'weak' : 'dealt';
            const label = ev.back ? `核心 ${ev.amount}` : ev.crit ? `暴击 ${ev.amount}` : `${ev.amount}`;
            this.texts.spawn(label, cls, ev.x, 2.1, ev.z);
          }
          if (ev.heavy || ev.crit) this.shake(0.12);
          break;
        }
        case 'playerHit':
          this.particles.emit(ev.x, 1.2, ev.z, { color: '#ff3b30', count: 14, speed: 4 });
          if (this.settings.damageNumbers) this.texts.spawn(`-${ev.amount}`, 'taken', ev.x, 2.3, ev.z);
          this.shake(ev.staggered ? 0.4 : 0.22);
          break;
        case 'blocked':
          this.particles.emit(ev.x, 1.2, ev.z, { color: '#cfd8e3', count: 8, speed: 3 });
          if (this.settings.damageNumbers) this.texts.spawn(ev.broken ? '破防!' : '格挡', 'block', ev.x, 2.2, ev.z);
          this.shake(0.1);
          break;
        case 'perfect':
          this.rings.spawn(ev.x, ev.z, { color: '#7fd0ff', radius: 2.2, life: 0.45 });
          this.particles.emit(ev.x, 1.3, ev.z, { color: '#7fd0ff', count: 16, speed: 5 });
          if (this.settings.damageNumbers) this.texts.spawn('完美格挡!', 'perfect', ev.x, 2.5, ev.z);
          break;
        case 'counter':
          this.particles.emit(ev.x, 1.1, ev.z, { color: '#ffd60a', count: 10, speed: 4 });
          break;
        case 'dodged':
          if (this.settings.damageNumbers) this.texts.spawn('闪避', 'dodge', ev.x, 2.0, ev.z);
          break;
        case 'kill':
          this.particles.emit(ev.x, 0.8, ev.z, { color: ev.elite || ev.boss ? '#ffb347' : '#f1ece0', count: ev.elite || ev.boss ? 30 : 14, speed: 5, life: 0.8 });
          if (ev.elite || ev.boss) this.shake(0.3);
          break;
        case 'spawn':
          this.rings.spawn(ev.x, ev.z, { color: ev.elite ? '#b57bff' : '#ffd60a', radius: ev.elite ? 3.2 : 1.6, life: 0.6 });
          break;
        case 'drop': {
          const rarity = ev.item.rarity;
          this.rings.spawn(ev.x, ev.z, { color: RARITIES[rarity].color, radius: 1.5, life: 0.5 });
          if (this.settings.damageNumbers && (rarity === 'epic' || rarity === 'legendary')) this.texts.spawn(RARITIES[rarity].name, 'loot', ev.x, 2.0, ev.z);
          break;
        }
        case 'levelUp':
          {
            const at = this.lastPlayerPos ?? { x: 0, z: 0 };
            this.rings.spawn(at.x, at.z, { color: '#ffd60a', radius: 3, life: 0.7 });
            this.particles.emit(at.x, 1.0, at.z, { color: '#ffd60a', count: 40, speed: 6, life: 1 });
          }
          break;
        case 'stun':
          this.particles.emit(ev.x, 0.8, ev.z, { color: '#ffffff', count: 6, speed: 2 });
          break;
        case 'interrupt':
          if (this.settings.damageNumbers) this.texts.spawn('打断!', 'interrupt', ev.x, 2.6, ev.z);
          this.rings.spawn(ev.x, ev.z, { color: '#ffd60a', radius: 1.4, life: 0.35 });
          this.shake(0.15);
          break;
        case 'reflect':
          this.particles.emit(ev.x, 1.1, ev.z, { color: '#ffd27a', count: 10, speed: 4 });
          break;
        case 'open':
          if (this.settings.damageNumbers) this.texts.spawn('破绽!', 'interrupt', ev.x, 2.8, ev.z);
          break;
        case 'bossPhase':
          this.shake(0.8);
          this.rings.spawn(ev.x, ev.z, { color: '#b57bff', radius: 6, life: 0.9 });
          this.particles.emit(ev.x, 1.6, ev.z, { color: '#b57bff', count: 60, speed: 8, life: 1.1 });
          break;
        case 'shieldBroken':
          this.particles.emit(ev.x, 1.6, ev.z, { color: '#7fd0ff', count: 30, speed: 6 });
          if (this.settings.damageNumbers) this.texts.spawn('护盾破碎', 'interrupt', ev.x, 3.2, ev.z);
          break;
        case 'stoneBroken':
          this.particles.emit(ev.x, 1.0, ev.z, { color: '#7fd0ff', count: 18, speed: 5 });
          this.shake(0.2);
          break;
        case 'immune':
          if (this.settings.damageNumbers) this.texts.spawn('无效', 'block', ev.x, 2.4, ev.z);
          break;
        case 'bossDown':
          this.shake(1);
          this.particles.emit(ev.x, 1.4, ev.z, { color: '#ffd60a', count: 80, speed: 9, life: 1.4 });
          break;
        case 'companionAttack':
          this.particles.emit(ev.x, 0.2, ev.z, { color: '#c9a27a', count: 5, speed: 2 });
          break;
        case 'rally':
          if (this.settings.damageNumbers) this.texts.spawn('桑乔鼓舞', 'rally', ev.x, 2.2, ev.z);
          break;
        case 'death':
          this.shake(0.6);
          break;
        case 'pickup':
          break;
        default:
          break;
      }
    }
  }

  render() {
    this.renderer.render(this.scene, this.camera);
  }

  dispose() {
    this.clearStage();
    this.renderer.dispose();
  }
}

// Builds the decal for a telegraph or active hitbox. Shapes match the sim's own geometry rules.
function geometryFor(tg) {
  const group = new THREE.Group();
  const material = () => new THREE.MeshBasicMaterial({ color: '#ff3b30', transparent: true, opacity: 0.3, depthWrite: false, side: THREE.DoubleSide });
  let geo;
  switch (tg.shape) {
    case 'sector': {
      const arc = (tg.arc ?? 90) * DEG;
      const start = (tg.yaw ?? 0) - Math.PI / 2 - arc / 2;
      geo = new THREE.RingGeometry(0.05, tg.reach ?? 2, 40, 1, start, arc);
      geo.rotateX(-Math.PI / 2);
      break;
    }
    case 'line': {
      geo = new THREE.PlaneGeometry(tg.width ?? 1, tg.length ?? 3);
      geo.rotateX(-Math.PI / 2);
      geo.translate(0, 0, (tg.length ?? 3) / 2);
      break;
    }
    case 'ring': {
      const gap = (tg.gapDeg ?? 0) * DEG;
      const gapCenter = (tg.yaw ?? 0) - Math.PI / 2;
      geo = new THREE.RingGeometry((tg.radius ?? 10) - 0.2, tg.radius ?? 10, 96, 1, gapCenter + gap / 2, Math.PI * 2 - gap);
      geo.rotateX(-Math.PI / 2);
      break;
    }
    case 'circle':
    default:
      geo = new THREE.CircleGeometry(tg.radius ?? 1, 40);
      geo.rotateX(-Math.PI / 2);
      break;
  }
  const mesh = new THREE.Mesh(geo, material());
  mesh.position.set(tg.x ?? 0, 0.04, tg.z ?? 0);
  if (tg.shape === 'line') mesh.rotation.y = tg.yaw ?? 0;
  group.add(mesh);
  return group;
}
