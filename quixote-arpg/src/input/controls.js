// Merges keyboard and touch into one latched input. Edge presses are held until the next sim tick consumes them,
// so a tap that lands between two fixed ticks is never lost.
export const KEY_MAP = {
  KeyJ: 'attack',
  KeyK: 'heavy',
  Space: 'dodge',
  ShiftLeft: 'dodge',
  KeyL: 'block',
  KeyQ: 'thrust',
  KeyE: 'charge',
  KeyR: 'bash',
  KeyF: 'whirl',
};

const MOVE_KEYS = { KeyW: 'up', ArrowUp: 'up', KeyS: 'down', ArrowDown: 'down', KeyA: 'left', ArrowLeft: 'left', KeyD: 'right', ArrowRight: 'right' };

export class Controls {
  constructor(target = globalThis) {
    this.keys = new Set();
    this.joy = { x: 0, y: 0 };
    this.edges = { attack: false, heavyDown: false, dodge: false, blockDown: false, skill: null };
    this.held = { heavy: false, block: false };
    this.enabled = true;
    this.target = target;
    this.onKeyDown = (e) => this.keyDown(e);
    this.onKeyUp = (e) => this.keyUp(e);
    this.onBlur = () => this.reset();
    target.addEventListener?.('keydown', this.onKeyDown);
    target.addEventListener?.('keyup', this.onKeyUp);
    target.addEventListener?.('blur', this.onBlur);
    this.onPress = null;
  }

  dispose() {
    this.target.removeEventListener?.('keydown', this.onKeyDown);
    this.target.removeEventListener?.('keyup', this.onKeyUp);
    this.target.removeEventListener?.('blur', this.onBlur);
  }

  keyDown(e) {
    if (!this.enabled) return;
    if (MOVE_KEYS[e.code]) {
      this.keys.add(MOVE_KEYS[e.code]);
      e.preventDefault?.();
      return;
    }
    const id = KEY_MAP[e.code];
    if (!id) return;
    e.preventDefault?.();
    if (e.repeat) return;
    this.press(id);
  }

  keyUp(e) {
    if (MOVE_KEYS[e.code]) this.keys.delete(MOVE_KEYS[e.code]);
    const id = KEY_MAP[e.code];
    if (id) this.release(id);
  }

  press(id) {
    if (!this.enabled) return;
    switch (id) {
      case 'attack':
        this.edges.attack = true;
        break;
      case 'heavy':
        this.edges.heavyDown = true;
        this.held.heavy = true;
        break;
      case 'dodge':
        this.edges.dodge = true;
        break;
      case 'block':
        this.edges.blockDown = true;
        this.held.block = true;
        break;
      case 'thrust':
      case 'charge':
      case 'bash':
      case 'whirl':
        this.edges.skill = id;
        break;
      default:
        break;
    }
    this.onPress?.(id);
  }

  release(id) {
    if (id === 'heavy') this.held.heavy = false;
    if (id === 'block') this.held.block = false;
  }

  setJoystick(x, y) {
    this.joy = { x, y };
  }

  reset() {
    this.keys.clear();
    this.joy = { x: 0, y: 0 };
    this.held = { heavy: false, block: false };
    this.edges = { attack: false, heavyDown: false, dodge: false, blockDown: false, skill: null };
  }

  // Builds the input for one fixed tick and clears the edge latches.
  tickInput() {
    let x = (this.keys.has('right') ? 1 : 0) - (this.keys.has('left') ? 1 : 0);
    let z = (this.keys.has('down') ? 1 : 0) - (this.keys.has('up') ? 1 : 0);
    x += this.joy.x;
    z += -this.joy.y; // screen up is world -z
    const m = Math.hypot(x, z);
    if (m > 1) {
      x /= m;
      z /= m;
    }
    const out = {
      move: { x, z },
      attack: this.edges.attack,
      heavyDown: this.edges.heavyDown,
      heavyHeld: this.held.heavy,
      dodge: this.edges.dodge,
      blockDown: this.edges.blockDown,
      blockHeld: this.held.block,
      skill: this.edges.skill,
    };
    this.edges.attack = false;
    this.edges.heavyDown = false;
    this.edges.dodge = false;
    this.edges.blockDown = false;
    this.edges.skill = null;
    return out;
  }
}
