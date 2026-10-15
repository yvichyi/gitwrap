import * as THREE from '../../vendor/three/three.module.js';

// Procedural low-poly models. Nothing is loaded from disk: every unit is built from primitives so the
// game runs from a static server with no asset pipeline.

const matCache = new Map();
export function mat(color, opts = {}) {
  const key = `${color}|${JSON.stringify(opts)}`;
  if (!matCache.has(key)) {
    matCache.set(key, new THREE.MeshStandardMaterial({ color, flatShading: true, roughness: 0.82, metalness: 0.05, ...opts }));
  }
  return matCache.get(key);
}

const PAL = {
  steel: '#7d818a',
  brass: '#b8862b',
  leather: '#6b3b2a',
  plume: '#a33a2a',
  skin: '#d9b08c',
  cloak: '#4a6fa5',
  wool: '#f1ece0',
  face: '#3a332e',
  bookCover: '#5b2a6e',
  page: '#efe6cc',
  wood: '#8a6a46',
  blade: '#d8c7a0',
  robe: '#2c2340',
  silver: '#c9dbe8',
  stone: '#7fd0ff',
  hay: '#d9b26a',
  post: '#6b4a2b',
  rock: '#6e6a64',
  pillar: '#8d8a86',
  dark: '#2b2a33',
};

const glow = (color, intensity = 1.2) => mat(color, { emissive: color, emissiveIntensity: intensity, roughness: 0.4 });

function mesh(geo, material, { x = 0, y = 0, z = 0, rx = 0, ry = 0, rz = 0, sx = 1, sy = 1, sz = 1, parent } = {}) {
  const m = new THREE.Mesh(geo, material);
  m.position.set(x, y, z);
  m.rotation.set(rx, ry, rz);
  m.scale.set(sx, sy, sz);
  if (parent) parent.add(m);
  return m;
}

function pivot(parent, x = 0, y = 0, z = 0) {
  const g = new THREE.Group();
  g.position.set(x, y, z);
  parent.add(g);
  return g;
}

// Knight of the sad face: steel armour, brass trim, long lance, round shield, faded cape.
export function buildKnight() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  const rig = {};

  rig.torso = mesh(new THREE.CylinderGeometry(0.42, 0.5, 0.95, 7), mat(PAL.steel), { y: 1.25, parent: body });
  mesh(new THREE.TorusGeometry(0.46, 0.05, 5, 12), mat(PAL.brass), { y: 1.62, rx: Math.PI / 2, parent: body });
  rig.legL = pivot(body, -0.2, 0.8, 0);
  mesh(new THREE.BoxGeometry(0.22, 0.8, 0.26), mat(PAL.steel), { y: -0.4, parent: rig.legL });
  rig.legR = pivot(body, 0.2, 0.8, 0);
  mesh(new THREE.BoxGeometry(0.22, 0.8, 0.26), mat(PAL.steel), { y: -0.4, parent: rig.legR });

  rig.head = pivot(body, 0, 1.95, 0);
  mesh(new THREE.SphereGeometry(0.28, 8, 6), mat(PAL.skin), { parent: rig.head, y: 0.05 });
  mesh(new THREE.CylinderGeometry(0.33, 0.36, 0.34, 8), mat(PAL.steel), { parent: rig.head, y: 0.18 });
  mesh(new THREE.BoxGeometry(0.42, 0.06, 0.04), mat(PAL.dark), { parent: rig.head, y: 0.02, z: 0.27 });
  mesh(new THREE.ConeGeometry(0.1, 0.5, 6), mat(PAL.plume), { parent: rig.head, y: 0.62 });

  rig.armR = pivot(body, 0.55, 1.5, 0);
  mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.7, 6), mat(PAL.steel), { y: -0.3, parent: rig.armR });
  // The lance is couched forward (+z): rotate its +y shaft onto +z and let it span z 0..2.4 from the hand.
  rig.lance = pivot(rig.armR, 0, -0.55, 0.1);
  rig.lance.rotation.x = Math.PI / 2;
  mesh(new THREE.CylinderGeometry(0.04, 0.04, 2.4, 5), mat(PAL.leather), { y: 1.2, parent: rig.lance });
  mesh(new THREE.ConeGeometry(0.1, 0.4, 5), mat(PAL.silver, { metalness: 0.5 }), { y: 2.6, parent: rig.lance });

  rig.armL = pivot(body, -0.55, 1.5, 0);
  mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.7, 6), mat(PAL.steel), { y: -0.3, parent: rig.armL });
  rig.shield = pivot(rig.armL, -0.1, -0.3, 0.1);
  mesh(new THREE.CylinderGeometry(0.42, 0.42, 0.08, 10), mat(PAL.wood), { rx: Math.PI / 2, parent: rig.shield });
  mesh(new THREE.TorusGeometry(0.4, 0.04, 5, 12), mat(PAL.brass), { rx: 0, parent: rig.shield });
  mesh(new THREE.SphereGeometry(0.09, 6, 4), mat(PAL.brass), { parent: rig.shield, z: 0.05 });

  rig.cape = mesh(new THREE.BoxGeometry(0.7, 1.4, 0.04), mat(PAL.leather), { y: 1.1, z: -0.36, parent: body });
  root.userData.rig = rig;
  root.userData.body = body;
  return root;
}

export function buildSancho() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  mesh(new THREE.SphereGeometry(0.42, 8, 6), mat(PAL.cloak), { y: 0.55, sy: 1.3, parent: body });
  mesh(new THREE.SphereGeometry(0.26, 8, 6), mat(PAL.skin), { y: 1.22, parent: body });
  mesh(new THREE.ConeGeometry(0.28, 0.45, 6), mat(PAL.leather), { y: 1.52, parent: body });
  mesh(new THREE.SphereGeometry(0.1, 5, 4), mat(PAL.face), { y: 1.22, z: 0.22, parent: body });
  const sack = mesh(new THREE.BoxGeometry(0.3, 0.3, 0.3), mat(PAL.hay), { x: 0.45, y: 0.7, parent: body });
  root.userData.body = body;
  root.userData.sack = sack;
  return root;
}

export function buildSheep() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  mesh(new THREE.IcosahedronGeometry(0.42, 0), mat(PAL.wool), { y: 0.5, sx: 1.2, parent: body });
  mesh(new THREE.SphereGeometry(0.2, 6, 5), mat(PAL.face), { x: 0, y: 0.7, z: 0.42, parent: body });
  for (const x of [-0.2, 0.2]) {
    for (const z of [-0.2, 0.2]) mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.4, 4), mat(PAL.face), { x, y: 0.2, z, parent: body });
  }
  root.userData.body = body;
  return root;
}

// The spellbook flaps its cover; its runes glow when it chants (see scene.js animation).
export function buildSpellbook() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  mesh(new THREE.BoxGeometry(0.85, 0.16, 0.65), mat(PAL.bookCover), { y: 0.9, parent: body });
  mesh(new THREE.BoxGeometry(0.78, 0.12, 0.58), mat(PAL.page), { y: 0.98, parent: body });
  root.userData.runes = mesh(new THREE.TorusGeometry(0.34, 0.03, 4, 14), glow('#ffd60a', 1.6), { y: 1.25, rx: Math.PI / 2, parent: body });
  root.userData.cover = body;
  body.position.y = 0;
  return root;
}

// Windmill giant: a brown tower with four blade arms, rotating in the sweep, and a core on its back.
export function buildWindmill() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  mesh(new THREE.CylinderGeometry(0.7, 1.0, 2.4, 8), mat(PAL.wood), { y: 1.2, parent: body });
  mesh(new THREE.ConeGeometry(0.85, 0.9, 8), mat(PAL.leather), { y: 2.85, parent: body });
  const arms = pivot(body, 0, 2.6, 0.7);
  for (let i = 0; i < 4; i++) {
    const a = pivot(arms, 0, 0, 0);
    a.rotation.z = (i * Math.PI) / 2;
    mesh(new THREE.BoxGeometry(0.16, 2.6, 0.06), mat(PAL.blade), { y: 1.3, parent: a });
  }
  mesh(new THREE.SphereGeometry(0.32, 8, 6), glow('#ff7a3d', 1.4), { y: 1.5, z: -0.9, parent: body });
  root.userData.arms = arms;
  root.userData.body = body;
  return root;
}

// Mirror knight: the same silhouette as the knight, but in polished silver.
export function buildMirror() {
  const root = buildKnight();
  root.traverse((o) => {
    if (o.isMesh && o.material?.color) {
      o.material = new THREE.MeshStandardMaterial({ color: PAL.silver, metalness: 0.85, roughness: 0.15, flatShading: true });
    }
  });
  return root;
}

// Freston: a robed enchanter with a pointed hat and a staff. His orbiting stones are separate meshes.
export function buildBoss() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  mesh(new THREE.ConeGeometry(0.85, 2.2, 7), mat(PAL.robe), { y: 1.1, parent: body });
  mesh(new THREE.SphereGeometry(0.3, 8, 6), mat(PAL.skin), { y: 2.35, parent: body });
  mesh(new THREE.ConeGeometry(0.42, 1.2, 7), mat(PAL.robe), { y: 3.0, parent: body });
  mesh(new THREE.TorusGeometry(0.42, 0.05, 5, 12), mat(PAL.brass), { y: 2.45, rx: Math.PI / 2, parent: body });
  const staff = pivot(body, 0.95, 0.2, 0.2);
  mesh(new THREE.CylinderGeometry(0.05, 0.05, 2.6, 5), mat(PAL.wood), { y: 1.3, parent: staff });
  mesh(new THREE.OctahedronGeometry(0.2, 0), glow('#7fd0ff', 1.4), { y: 2.7, parent: staff });
  root.userData.body = body;
  return root;
}

export function buildStone() {
  const root = new THREE.Group();
  mesh(new THREE.DodecahedronGeometry(0.42, 0), glow(PAL.stone, 1.0), { y: 0.6, parent: root });
  root.userData.spin = true;
  return root;
}

export function buildPage() {
  const root = new THREE.Group();
  mesh(new THREE.PlaneGeometry(0.42, 0.56), new THREE.MeshBasicMaterial({ color: '#fff4d6', side: THREE.DoubleSide, transparent: true, opacity: 0.95 }), { y: 0.9, rx: -Math.PI / 2, parent: root });
  root.userData.flip = true;
  return root;
}

export function buildLootBeam(color) {
  const root = new THREE.Group();
  const beam = new THREE.Mesh(
    new THREE.CylinderGeometry(0.12, 0.2, 3.2, 8, 1, true),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.45, depthWrite: false, side: THREE.DoubleSide }),
  );
  beam.position.y = 1.6;
  root.add(beam);
  const gem = mesh(new THREE.OctahedronGeometry(0.22, 0), glow(color, 1.5), { y: 0.5, parent: root });
  root.userData.gem = gem;
  return root;
}

// Props for each stage theme.
export function buildProp(kind, r) {
  switch (kind) {
    case 'hay': {
      const g = new THREE.Group();
      mesh(new THREE.CylinderGeometry(r, r, r * 1.6, 8), mat(PAL.hay), { y: r * 0.5, rz: Math.PI / 2, parent: g });
      return g;
    }
    case 'post': {
      const g = new THREE.Group();
      mesh(new THREE.CylinderGeometry(r * 0.8, r, 1.4, 6), mat(PAL.post), { y: 0.7, parent: g });
      return g;
    }
    case 'tower': {
      return buildWindmillProp(r);
    }
    case 'rock': {
      const g = new THREE.Group();
      mesh(new THREE.DodecahedronGeometry(r, 0), mat(PAL.rock), { y: r * 0.6, sy: 0.8, parent: g });
      return g;
    }
    case 'pillar': {
      const g = new THREE.Group();
      mesh(new THREE.CylinderGeometry(r * 0.8, r, 4.2, 8), mat(PAL.pillar), { y: 2.1, parent: g });
      mesh(new THREE.BoxGeometry(r * 2.4, 0.4, r * 2.4), mat(PAL.pillar), { y: 4.3, parent: g });
      return g;
    }
    default:
      return new THREE.Group();
  }
}

function buildWindmillProp(r) {
  const g = new THREE.Group();
  mesh(new THREE.CylinderGeometry(r * 0.7, r, 3, 8), mat(PAL.wood), { y: 1.5, parent: g });
  mesh(new THREE.ConeGeometry(r * 0.9, 1.0, 8), mat(PAL.leather), { y: 3.5, parent: g });
  return g;
}

// Round soft sprite for particles.
export function particleTexture() {
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const ctx = c.getContext('2d');
  const g = ctx.createRadialGradient(32, 32, 0, 32, 32, 32);
  g.addColorStop(0, 'rgba(255,255,255,1)');
  g.addColorStop(0.5, 'rgba(255,255,255,0.8)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 64, 64);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

// Parchment ground with faint hatching, used as a repeating texture.
export function parchmentTexture(seedColor = '#d6bf8e') {
  const c = document.createElement('canvas');
  c.width = c.height = 256;
  const ctx = c.getContext('2d');
  ctx.fillStyle = seedColor;
  ctx.fillRect(0, 0, 256, 256);
  for (let i = 0; i < 260; i++) {
    const x = Math.random() * 256;
    const y = Math.random() * 256;
    ctx.fillStyle = `rgba(120,85,40,${0.04 + Math.random() * 0.06})`;
    ctx.beginPath();
    ctx.ellipse(x, y, 4 + Math.random() * 18, 2 + Math.random() * 6, Math.random() * 3, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.strokeStyle = 'rgba(90,60,30,0.08)';
  for (let i = -256; i < 512; i += 18) {
    ctx.beginPath();
    ctx.moveTo(i, 0);
    ctx.lineTo(i + 256, 256);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(c);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export { PAL, glow };
