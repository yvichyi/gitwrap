// Touch controls: a dynamic joystick that appears where a finger lands on the thumb-free half, and a button cluster
// placed in the right-thumb zone (mirrored for left-handed play). Positions are stored as fractions of the
// screen height, so they keep their shape on 16:9, 19.5:9 and 20:9 screens.

export const BUTTONS = [
  { id: 'attack', label: '攻击', cls: 'b-attack', pos: { x: 0.2, y: 0.2, s: 0.27 } },
  { id: 'dodge', label: '闪避', cls: 'b-dodge', pos: { x: 0.48, y: 0.19, s: 0.19 } },
  { id: 'heavy', label: '重击', cls: 'b-heavy', pos: { x: 0.2, y: 0.47, s: 0.19 } },
  { id: 'block', label: '格挡', cls: 'b-block', pos: { x: 0.39, y: 0.39, s: 0.17 } },
  { id: 'thrust', label: '突刺', cls: 'b-skill', pos: { x: 0.56, y: 0.42, s: 0.14 }, skill: true },
  { id: 'charge', label: '冲锋', cls: 'b-skill', pos: { x: 0.36, y: 0.62, s: 0.14 }, skill: true },
  { id: 'bash', label: '盾击', cls: 'b-skill', pos: { x: 0.72, y: 0.22, s: 0.14 }, skill: true },
  { id: 'whirl', label: '旋风', cls: 'b-skill b-ult', pos: { x: 0.53, y: 0.66, s: 0.15 }, skill: true },
];

const JOY_RADIUS = 0.12; // fraction of screen height
const JOY_DEAD = 0.14; // dead zone as a fraction of the radius

export function layoutFor(settings) {
  return BUTTONS.map((b) => {
    const saved = settings.buttons?.[b.id];
    return { ...b, pos: saved ? { ...saved } : { ...b.pos } };
  });
}

export class TouchUI {
  constructor({ root, controls, settings, onLayoutChange }) {
    this.root = root;
    this.controls = controls;
    this.settings = settings;
    this.onLayoutChange = onLayoutChange;
    this.joyPointer = null;
    this.joyOrigin = null;
    this.buttonEls = new Map();
    this.editing = false;
    this.editDrag = null;
    this.layout = layoutFor(settings);

    this.stick = document.createElement('div');
    this.stick.className = 'joy';
    this.stick.innerHTML = '<div class="joy-base"></div><div class="joy-knob"></div>';
    this.root.appendChild(this.stick);

    this.buildButtons();
    this.bindPointers();
    this.applySettings();
  }

  buildButtons() {
    for (const el of this.buttonEls.values()) el.remove();
    this.buttonEls.clear();
    for (const b of this.layout) {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = `tbtn ${b.cls}`;
      el.dataset.id = b.id;
      el.innerHTML = `<span class="tbtn-label">${b.label}</span><span class="tbtn-cd"></span>`;
      el.setAttribute('aria-label', b.label);
      this.root.appendChild(el);
      this.buttonEls.set(b.id, el);
      this.placeButton(b, el);
    }
  }

  // (x, y) is the button centre, measured from the thumb-side edge and the bottom, in screen heights.
  placeButton(b, el) {
    const half = b.pos.s / 2;
    el.style.setProperty('--s', String(b.pos.s));
    const edge = this.settings.hand === 'left' ? 'left' : 'right';
    const other = edge === 'left' ? 'right' : 'left';
    el.style[edge] = `calc(env(safe-area-inset-${edge}, 0px) + var(--vh) * ${((b.pos.x - half) * 100).toFixed(2)})`;
    el.style[other] = 'auto';
    el.style.bottom = `calc(env(safe-area-inset-bottom, 0px) + var(--vh) * ${((b.pos.y - half) * 100).toFixed(2)})`;
  }

  applySettings() {
    this.root.dataset.hand = this.settings.hand;
    this.root.style.setProperty('--tscale', String(this.settings.touchScale));
    this.root.style.setProperty('--topacity', String(this.settings.touchOpacity));
    for (const b of this.layout) this.placeButton(b, this.buttonEls.get(b.id));
  }

  setHand(hand) {
    this.settings.hand = hand;
    this.applySettings();
  }

  // Joystick: the zone is the half of the screen away from the buttons.
  joyZone(x) {
    const half = window.innerWidth / 2;
    return this.settings.hand === 'left' ? x > half : x < half;
  }

  bindPointers() {
    this.root.addEventListener('pointerdown', (e) => this.onDown(e));
    this.root.addEventListener('pointermove', (e) => this.onMove(e));
    this.root.addEventListener('pointerup', (e) => this.onUp(e));
    this.root.addEventListener('pointercancel', (e) => this.onUp(e));
    this.root.addEventListener('lostpointercapture', (e) => this.onUp(e));
  }

  onDown(e) {
    if (this.editing) {
      const el = e.target.closest?.('.tbtn');
      if (el) {
        const b = this.layout.find((x) => x.id === el.dataset.id);
        this.editDrag = { id: b.id, pointer: e.pointerId, startX: e.clientX, startY: e.clientY, orig: { ...b.pos } };
        el.setPointerCapture?.(e.pointerId);
      }
      return;
    }
    const btn = e.target.closest?.('.tbtn');
    if (btn) {
      e.preventDefault();
      btn.setPointerCapture?.(e.pointerId);
      btn.dataset.pointer = String(e.pointerId);
      btn.classList.add('down');
      this.controls.press(btn.dataset.id);
      return;
    }
    if (this.joyPointer === null && this.joyZone(e.clientX) && e.clientY > window.innerHeight * 0.18) {
      e.preventDefault();
      this.joyPointer = e.pointerId;
      this.joyOrigin = { x: e.clientX, y: e.clientY };
      this.showStick(e.clientX, e.clientY);
      this.controls.setJoystick(0, 0);
    }
  }

  onMove(e) {
    if (this.editing && this.editDrag && e.pointerId === this.editDrag.pointer) {
      const b = this.layout.find((x) => x.id === this.editDrag.id);
      const vh = window.innerHeight;
      const dx = (e.clientX - this.editDrag.startX) / vh;
      const dy = (e.clientY - this.editDrag.startY) / vh;
      const sign = this.settings.hand === 'left' ? 1 : -1;
      const half = b.pos.s / 2 + 0.03;
      b.pos.x = clamp(this.editDrag.orig.x + dx * sign, half, 0.9);
      b.pos.y = clamp(this.editDrag.orig.y - dy, half, 0.9);
      this.placeButton(b, this.buttonEls.get(b.id));
      return;
    }
    if (this.joyPointer !== null && e.pointerId === this.joyPointer) {
      const radius = JOY_RADIUS * window.innerHeight * this.settings.touchScale;
      const dx = e.clientX - this.joyOrigin.x;
      const dy = e.clientY - this.joyOrigin.y;
      const d = Math.hypot(dx, dy);
      const ux = d > 0 ? dx / d : 0;
      const uy = d > 0 ? dy / d : 0;
      const raw = Math.min(1, d / radius);
      const m = raw < JOY_DEAD ? 0 : (raw - JOY_DEAD) / (1 - JOY_DEAD);
      // Screen y grows downward; the controls expect y up.
      this.controls.setJoystick(ux * m, -uy * m);
      const knob = Math.min(d, radius);
      this.stick.querySelector('.joy-knob').style.transform = `translate(${ux * knob}px, ${uy * knob}px)`;
    }
  }

  onUp(e) {
    if (this.editing && this.editDrag && e.pointerId === this.editDrag.pointer) {
      this.editDrag = null;
      return;
    }
    if (this.joyPointer !== null && e.pointerId === this.joyPointer) {
      this.joyPointer = null;
      this.controls.setJoystick(0, 0);
      this.stick.classList.remove('on');
    }
    const el = this.root.querySelector(`.tbtn[data-pointer="${e.pointerId}"]`);
    if (el) {
      delete el.dataset.pointer;
      el.classList.remove('down');
      this.controls.release(el.dataset.id);
    }
  }

  showStick(x, y) {
    this.stick.style.left = `${x}px`;
    this.stick.style.top = `${y}px`;
    this.stick.classList.add('on');
    this.stick.querySelector('.joy-knob').style.transform = 'translate(0px, 0px)';
  }

  // Cooldown sweeps and the ultimate's readiness. Called every frame from the HUD.
  updateCooldowns(player) {
    if (!player) return;
    for (const id of ['thrust', 'charge', 'bash']) {
      const el = this.buttonEls.get(id);
      const max = player.cdMax[id] || 1;
      if (el) el.style.setProperty('--cd', String(Math.max(0, Math.min(1, player.cooldowns[id] / max))));
    }
    const whirl = this.buttonEls.get('whirl');
    if (whirl) whirl.classList.toggle('ready', player.madness >= player.whirlCost());
    const dodge = this.buttonEls.get('dodge');
    if (dodge) dodge.style.setProperty('--cd', String(Math.max(0, Math.min(1, player.dodgeCd / player.dodgeMax))));
  }

  setEditing(on) {
    this.editing = on;
    this.root.classList.toggle('editing', on);
    this.stick.classList.remove('on');
  }

  resetLayout() {
    this.layout = BUTTONS.map((b) => ({ ...b, pos: { ...b.pos } }));
    this.settings.buttons = {};
    for (const b of this.layout) this.placeButton(b, this.buttonEls.get(b.id));
  }

  // Writes the dragged positions back into settings (the caller saves).
  commitLayout() {
    this.settings.buttons = Object.fromEntries(this.layout.map((b) => [b.id, { x: round(b.pos.x), y: round(b.pos.y), s: b.pos.s }]));
    this.onLayoutChange?.(this.settings);
  }

  setVisible(on) {
    this.root.classList.toggle('hidden', !on);
  }
}

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}

function round(v) {
  return Math.round(v * 1000) / 1000;
}
