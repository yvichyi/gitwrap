import { TICK } from './config/balance.js';
import { STAGES } from './config/content.js';
import { Session } from './sim/session.js';
import { createProgress } from './sim/progression.js';
import { clearSave, loadSave, loadSettings, writeSettings } from './sim/save.js';
import { Renderer } from './render/scene.js';
import { Controls } from './input/controls.js';
import { TouchUI, layoutFor } from './input/touch.js';
import { Hud } from './ui/hud.js';
import { Screens } from './ui/screens.js';

const $ = (id) => document.getElementById(id);

// ---- boot ---------------------------------------------------------------------

const settings = loadSettings();
const save = loadSave();
const session = new Session({ progress: save?.progress ?? createProgress(), stageIndex: save?.stageIndex ?? 0 });
const controls = new Controls(window);
const renderer = new Renderer({ canvas: $('gl'), damageLayer: $('fx'), quality: settings.quality });
renderer.settings = { damageNumbers: settings.damageNumbers, shake: settings.shake };
const touch = new TouchUI({ root: $('touch'), controls, settings, onLayoutChange: () => persistSettings() });
const hud = new Hud();
hud.setPanel(!settings.hideSecondary);
$('btn-hand').textContent = settings.hand === 'left' ? '右手' : '左手';

let screens;
let lastWorld = null;
let fpsCap = settings.quality === 'performance' ? 30 : 60;
let lastRender = 0;

function persistSettings() {
  writeSettings(settings);
}

function resize() {
  const w = window.innerWidth;
  const h = window.innerHeight;
  document.documentElement.style.setProperty('--vh', `${(h / 100).toFixed(3)}px`);
  renderer.resize(w, h);
  touch.applySettings();
}
window.addEventListener('resize', resize);
window.addEventListener('orientationchange', resize);

function touchVisible() {
  if (settings.showTouch === 'always') return true;
  if (settings.showTouch === 'never') return false;
  return matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;
}

function showBattle(on) {
  hud.show(on);
  touch.setVisible(on && touchVisible());
}

// ---- flow ---------------------------------------------------------------------

function tryImmersive() {
  try {
    document.documentElement.requestFullscreen?.().catch(() => {});
  } catch {
    // fullscreen is optional
  }
  try {
    screen.orientation?.lock?.('landscape').catch(() => {});
  } catch {
    // orientation lock is optional (iOS ignores it)
  }
}

function startOrResume() {
  tryImmersive();
  if (session.state === 'paused') {
    session.resume();
  } else {
    session.startStage(session.stageIndex);
    lastWorld = session.world;
    renderer.buildStage(session.world);
    hud.banner(STAGES[session.stageIndex].name, STAGES[session.stageIndex].subtitle);
    hud.hint('左手摇杆移动 · 右手攻击 / 闪避 / 格挡 · 举盾瞬间被击中可完美反击');
  }
  screens.hide();
  showBattle(true);
}

function toMenu() {
  session.backToMenu();
  lastWorld = null;
  renderer.clearStage();
  showBattle(false);
  screens.menu(Boolean(loadSave()));
}

function startNextStage() {
  session.nextStage();
  lastWorld = session.world;
  renderer.buildStage(session.world);
  hud.banner(STAGES[session.stageIndex].name, STAGES[session.stageIndex].subtitle);
  screens.hide();
  showBattle(true);
}

function handleEvents(events) {
  if (!events.length) return;
  renderer.handleEvents(events);
  hud.onEvents(events, session);
  for (const ev of events) {
    if (ev.type === 'stageClear') {
      setTimeout(() => {
        if (session.state === 'clear') {
          showBattle(false);
          screens.clear(STAGES[session.stageIndex], session.world);
        }
      }, 1600);
    }
    if (ev.type === 'death') {
      renderer.shake(0.6);
      setTimeout(() => {
        if (session.state === 'dead') {
          showBattle(false);
          screens.dead(STAGES[session.stageIndex]);
        }
      }, 1300);
    }
  }
  if (session.state === 'victory') {
    showBattle(false);
    screens.victory(lastWorld);
  }
}

// ---- screen actions -------------------------------------------------------------

function onAction(act, data, s) {
  switch (act) {
    case 'start':
      startOrResume();
      break;
    case 'resume':
      session.resume();
      screens.hide();
      showBattle(true);
      break;
    case 'pause':
      session.pause();
      showBattle(false);
      screens.pause();
      break;
    case 'quit':
      toMenu();
      break;
    case 'build':
      s.prev = s.current;
      s.build();
      break;
    case 'back':
      if (s.prev === 'pause') {
        s.pause();
      } else if (s.prev === 'clear') {
        screens.clear(STAGES[session.stageIndex], session.world);
      } else if (s.prev === 'dead') {
        screens.dead(STAGES[session.stageIndex]);
      } else {
        screens.menu(Boolean(loadSave()));
      }
      break;
    case 'menu':
      screens.menu(Boolean(loadSave()));
      break;
    case 'settings':
      screens.settingsScreen();
      break;
    case 'settings-from-pause':
      screens.settingsScreen({ fromPause: true });
      break;
    case 'attr':
      s.applyAttr(data.key);
      refreshPlayerStats();
      s.build();
      break;
    case 'talent':
      s.applyTalent(data.id);
      refreshPlayerStats();
      s.build();
      break;
    case 'select':
      s.select(data.id);
      s.build();
      break;
    case 'equip':
      s.applyEquip(data.id);
      refreshPlayerStats();
      s.build();
      break;
    case 'discard':
      s.applyDiscard(data.id);
      s.build();
      break;
    case 'next-stage':
      startNextStage();
      break;
    case 'retry':
      session.retryStage();
      lastWorld = session.world;
      renderer.buildStage(session.world);
      screens.hide();
      showBattle(true);
      hud.banner(STAGES[session.stageIndex].name, '再来一次');
      break;
    case 'victory-continue':
      session.nextStage();
      showBattle(false);
      screens.victory(lastWorld);
      break;
    case 'new-game':
      if (window.confirm('清除存档，从第一关重新开始？')) newGame();
      break;
    case 'clear-save':
      if (window.confirm('清除存档？角色的等级与装备会一并删除。')) newGame();
      break;
    case 'edit-layout':
      openLayoutEditor();
      break;
    case 'reset-layout':
      touch.resetLayout();
      persistSettings();
      s.settingsScreen();
      break;
    default:
      break;
  }
}

function newGame() {
  clearSave();
  session.progress = createProgress();
  session.stageIndex = 0;
  session.world = null;
  session.state = 'menu';
  lastWorld = null;
  renderer.clearStage();
  showBattle(false);
  screens.menu(false);
}

function refreshPlayerStats() {
  if (session.world) session.world.player.refresh(session.progress);
  session.save();
}

function onInput(name, value, data, s) {
  switch (name) {
    case 'hand':
      touch.setHand(value);
      break;
    case 'touchScale':
      settings.touchScale = Number(value);
      touch.applySettings();
      break;
    case 'touchOpacity':
      settings.touchOpacity = Number(value);
      touch.applySettings();
      break;
    case 'showTouch':
      settings.showTouch = value;
      touch.setVisible(session.state === 'stage' && touchVisible());
      break;
    case 'damageNumbers':
      settings.damageNumbers = value === '1';
      renderer.settings.damageNumbers = settings.damageNumbers;
      break;
    case 'shake':
      settings.shake = value === '1';
      renderer.settings.shake = settings.shake;
      break;
    case 'quality':
      settings.quality = value;
      renderer.setQuality(value);
      fpsCap = value === 'performance' ? 30 : 60;
      break;
    case 'hideSecondary':
      settings.hideSecondary = value === '1';
      hud.setPanel(!settings.hideSecondary);
      break;
    default:
      return;
  }
  persistSettings();
  if (name !== 'touchScale' && name !== 'touchOpacity') s.settingsScreen({ fromPause: session.state === 'paused' });
}

// Layout editor: drag the buttons, then save or cancel. Snapshot lets cancel restore the old positions.
function openLayoutEditor() {
  const snapshot = JSON.parse(JSON.stringify(settings.buttons));
  screens.hide();
  showBattle(false);
  $('touch').classList.remove('hidden');
  touch.setVisible(true);
  touch.setEditing(true);
  const bar = screens.layoutBar(
    () => {
      touch.commitLayout();
      persistSettings();
      done();
    },
    () => {
      touch.resetLayout();
    },
    () => {
      settings.buttons = snapshot;
      touch.layout = layoutFor(settings);
      touch.applySettings();
      done();
    },
  );
  $('touch').appendChild(bar);
  function done() {
    bar.remove();
    touch.setEditing(false);
    touch.setVisible(false);
    screens.settingsScreen();
  }
}

screens = new Screens({ root: $('screen'), session, settings, on: { action: onAction, input: onInput } });

document.addEventListener('keydown', (e) => {
  if (e.code === 'Escape' || e.code === 'KeyP') {
    if (session.state === 'stage') {
      session.pause();
      showBattle(false);
      screens.pause();
    } else if (session.state === 'paused') {
      session.resume();
      screens.hide();
      showBattle(true);
    }
  }
});

$('btn-pause').addEventListener('click', () => {
  if (session.state !== 'stage') return;
  session.pause();
  showBattle(false);
  screens.pause();
});
$('btn-hand').addEventListener('click', () => {
  settings.hand = settings.hand === 'left' ? 'right' : 'left';
  touch.setHand(settings.hand);
  $('btn-hand').textContent = settings.hand === 'left' ? '右手' : '左手';
  persistSettings();
});
$('btn-panel').addEventListener('click', () => {
  settings.hideSecondary = hud.panelOpen;
  hud.setPanel(!settings.hideSecondary);
  persistSettings();
});

// Background and foreground: a hidden tab pauses the battle and saves; returning shows the pause menu.
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    controls.reset();
    if (session.state === 'stage') {
      session.pause();
      showBattle(false);
      screens.pause();
    } else {
      session.save();
    }
  }
});
window.addEventListener('pagehide', () => session.save());

// ---- loop ---------------------------------------------------------------------

let last = performance.now();
let acc = 0;
let frames = 0;
let fpsTimer = 0;
let fps = 0;

function frame(now) {
  requestAnimationFrame(frame);
  const elapsed = now - last;
  const dt = Math.min(0.1, elapsed / 1000);
  last = now;

  if (session.state === 'stage') {
    acc += dt;
    let steps = 0;
    while (acc >= TICK && steps < 5) {
      const events = session.tick(controls.tickInput());
      handleEvents(events);
      acc -= TICK;
      steps += 1;
    }
    if (steps === 5) acc = 0;
  } else {
    acc = 0;
    controls.tickInput();
  }

  if (now - lastRender < 1000 / fpsCap - 2) return;
  lastRender = now;

  const world = session.world;
  if (world && (session.state === 'stage' || session.state === 'clear' || session.state === 'dead' || session.state === 'paused')) {
    renderer.sync(world, dt);
    hud.update(session, dt);
    touch.updateCooldowns(world.player);
  }
  renderer.render();

  frames += 1;
  fpsTimer += dt;
  if (fpsTimer >= 1) {
    fps = frames / fpsTimer;
    frames = 0;
    fpsTimer = 0;
    globalThis.__quixoteFps = Math.round(fps);
  }
}

resize();
touch.setVisible(false);
screens.menu(Boolean(save));
requestAnimationFrame((t) => {
  last = t;
  frame(t);
});

// Test hook for the end-to-end checks. Read-only snapshot of the sim.
globalThis.__quixote = {
  get state() {
    return session.state;
  },
  get stage() {
    return session.stageIndex;
  },
  get world() {
    return session.world;
  },
  session,
  settings,
};
