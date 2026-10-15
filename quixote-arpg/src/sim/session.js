import { STAGES } from '../config/content.js';
import { TICK } from '../config/balance.js';
import { World } from './world.js';
import { createProgress } from './progression.js';
import { writeSave } from './save.js';

// Top-level flow: menu -> stage -> (clear | dead) -> next stage -> ... -> victory.
// The session owns the character progress and the current World. The renderer and UI read from it.
export class Session {
  constructor({ progress = createProgress(), stageIndex = 0, seed = 20240601 } = {}) {
    this.progress = progress;
    this.stageIndex = Math.min(Math.max(0, stageIndex), STAGES.length - 1);
    this.seed = seed;
    this.world = null;
    this.state = 'menu';
    this.attempts = 0;
  }

  get stage() {
    return STAGES[this.stageIndex];
  }

  // Starts a stage from its beginning. Character progress carries over; the stage itself is reset.
  startStage(index = this.stageIndex) {
    this.stageIndex = index;
    this.world = new World({ stage: STAGES[index], stageIndex: index, progress: this.progress, seed: this.seed + this.attempts * 131 });
    this.attempts += 1;
    this.state = 'stage';
    this.save();
    return this.world;
  }

  nextStage() {
    if (this.stageIndex + 1 >= STAGES.length) {
      this.state = 'victory';
      this.world = null;
      return false;
    }
    this.startStage(this.stageIndex + 1);
    return true;
  }

  retryStage() {
    this.startStage(this.stageIndex);
  }

  backToMenu() {
    this.save();
    this.world = null;
    this.state = 'menu';
  }

  pause() {
    if (this.state !== 'stage') return;
    this.state = 'paused';
    this.save();
  }

  resume() {
    if (this.state === 'paused' && this.world) this.state = 'stage';
  }

  // Advances one fixed tick. Returns the events the world produced this tick.
  tick(input) {
    if (this.state !== 'stage' || !this.world) return [];
    const world = this.world;
    world.step(TICK, input);
    const events = world.drainEvents();
    for (const ev of events) {
      if (ev.type === 'stageClear') this.onStageClear();
      if (ev.type === 'death') this.state = 'dead';
    }
    return events;
  }

  onStageClear() {
    this.state = 'clear';
    this.save();
  }

  save() {
    writeSave(this.progress, this.stageIndex);
  }
}
