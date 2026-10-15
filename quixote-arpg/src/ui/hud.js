import { FRESTON } from '../config/balance.js';
import { RARITIES, STAGES, STAT_NAMES } from '../config/content.js';
import { xpNeeded } from '../sim/progression.js';

const $ = (id) => document.getElementById(id);

// Battle HUD: bars, boss bar, labels, banners, toasts and the minimap. Reads the world; never mutates it.
export class Hud {
  constructor() {
    this.el = {
      root: $('hud'),
      lvl: $('hud-lvl'),
      hp: $('hud-hp'),
      hpT: $('hud-hp-t'),
      mad: $('hud-mad'),
      madT: $('hud-mad-t'),
      madBar: $('hud-mad-bar'),
      guard: $('hud-guard'),
      guardBar: $('hud-guard-bar'),
      xp: $('hud-xp'),
      buffs: $('hud-buffs'),
      stage: $('hud-stage'),
      wave: $('hud-wave'),
      boss: $('hud-boss'),
      bossName: $('hud-boss-name'),
      bossHp: $('hud-boss-hp'),
      bossShield: $('hud-boss-shield'),
      objective: $('hud-objective'),
      kills: $('hud-kills'),
      elites: $('hud-elites'),
      dodges: $('hud-dodges'),
      perfect: $('hud-perfect'),
      panel: $('hud-panel'),
      minimap: $('minimap'),
      banner: $('hud-banner'),
      toast: $('hud-toast'),
    };
    this.mapCtx = this.el.minimap.getContext('2d');
    this.mapTimer = 0;
    this.toastTimer = null;
    this.lastBannerKey = '';
    this.panelOpen = true;
  }

  show(on) {
    this.el.root.classList.toggle('hidden', !on);
  }

  setPanel(open) {
    this.panelOpen = open;
    this.el.panel.classList.toggle('collapsed', !open);
  }

  update(session, dt) {
    const world = session.world;
    const p = session.progress;
    if (!world) return;
    const pl = world.player;

    this.el.lvl.textContent = `Lv ${p.level}`;
    this.el.hp.style.transform = `scaleX(${clamp01(pl.hp / pl.maxHp)})`;
    this.el.hpT.textContent = `${Math.ceil(Math.max(0, pl.hp))} / ${Math.round(pl.maxHp)}`;

    const tier = pl.mad.tier;
    this.el.madBar.dataset.tier = tier.id;
    this.el.mad.style.transform = `scaleX(${clamp01(pl.madness / pl.stats.maxMad)})`;
    this.el.madT.textContent = `${tier.name} ${Math.floor(pl.madness)}`;

    const guardFrac = clamp01(pl.guard / pl.stats.guardMax);
    this.el.guard.style.transform = `scaleX(${guardFrac})`;
    this.el.guardBar.classList.toggle('hidden', pl.state !== 'block' && guardFrac >= 0.999);

    const need = xpNeeded(p);
    this.el.xp.style.transform = `scaleX(${need ? clamp01(p.xp / need) : 1})`;

    const buffs = [];
    if (pl.buffs.encourage > 0) buffs.push(`<span class="buff">鼓舞 ${Math.ceil(pl.buffs.encourage)}s</span>`);
    if (pl.buffs.chargeCrit > 0) buffs.push('<span class="buff">冲撞暴击</span>');
    if (pl.guardBroken) buffs.push('<span class="buff" style="color:#ff9a8a;border-color:#ff9a8a">破防</span>');
    this.el.buffs.innerHTML = buffs.join('');

    const stage = STAGES[session.stageIndex];
    this.el.stage.textContent = stage.name;
    const total = stage.waves.length;
    const cur = Math.min(world.waveIndex, total);
    const alive = world.enemies.filter((e) => e.kind === 'monster' && !e.dead && !e.summoned).length;
    this.el.wave.textContent = world.clear ? '已清场' : `第 ${cur}/${total} 波 · 剩余 ${alive}`;

    const boss = world.liveBoss;
    this.el.boss.classList.toggle('hidden', !boss);
    if (boss) {
      this.el.bossHp.style.transform = `scaleX(${clamp01(boss.hp / boss.maxHp)})`;
      this.el.bossName.textContent = `${FRESTON.name} · ${FRESTON.phases[boss.phase].name}`;
      const shielded = world.enemies.some((e) => e.kind === 'stone' && e.parent === boss.id && !e.dead);
      this.el.bossShield.textContent = shielded ? '魔力护盾：击碎三枚魔力石' : '护盾已碎';
      this.el.bossShield.classList.toggle('off', !shielded);
    }

    const objective = boss ? `目标：击败${FRESTON.name}，击碎魔力石` : world.clear ? '目标：已完成，前往下一关' : `目标：清空第 ${Math.max(1, cur)} 波`;
    this.el.objective.textContent = objective;

    this.el.kills.textContent = String(world.stats.kills);
    this.el.elites.textContent = String(world.stats.eliteKills);
    this.el.dodges.textContent = String(world.stats.dodges);
    this.el.perfect.textContent = String(world.stats.perfectBlocks);

    this.mapTimer -= dt;
    if (this.panelOpen && this.mapTimer <= 0) {
      this.mapTimer = 0.1;
      this.drawMinimap(world);
    }
  }

  drawMinimap(world) {
    const ctx = this.mapCtx;
    const size = this.el.minimap.width;
    const half = world.half;
    const k = size / (half * 2);
    const to = (x, z) => [size / 2 + x * k, size / 2 + z * k];
    ctx.fillStyle = 'rgba(58, 42, 28, 0.85)';
    ctx.fillRect(0, 0, size, size);
    ctx.strokeStyle = 'rgba(255, 230, 180, 0.4)';
    ctx.strokeRect(1, 1, size - 2, size - 2);
    ctx.fillStyle = '#8a6a46';
    for (const o of world.obstacles) {
      const [x, y] = to(o.x, o.z);
      ctx.beginPath();
      ctx.arc(x, y, Math.max(2, o.r * k), 0, Math.PI * 2);
      ctx.fill();
    }
    for (const e of world.enemies) {
      if (e.dead) continue;
      const [x, y] = to(e.x, e.z);
      ctx.fillStyle = e.kind === 'stone' ? '#7fd0ff' : e.type === 'boss' ? '#b57bff' : e.elite ? '#ffb347' : '#e0484a';
      ctx.beginPath();
      ctx.arc(x, y, e.elite || e.type === 'boss' ? 4.5 : 3, 0, Math.PI * 2);
      ctx.fill();
    }
    for (const d of world.loot) {
      const [x, y] = to(d.x, d.z);
      ctx.fillStyle = RARITIES[d.item.rarity].color;
      ctx.fillRect(x - 2, y - 2, 4, 4);
    }
    const p = world.player;
    const [px, py] = to(p.x, p.z);
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(px, py, 4, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#ffd60a';
    ctx.beginPath();
    ctx.moveTo(px, py);
    ctx.lineTo(px + Math.sin(p.yaw) * 12, py + Math.cos(p.yaw) * 12);
    ctx.stroke();
  }

  // Banners for big moments; toasts for small notices.
  banner(big, small = '') {
    const el = this.el.banner;
    el.innerHTML = `<div class="big">${escapeHtml(big)}</div>${small ? `<div class="small">${escapeHtml(small)}</div>` : ''}`;
    el.classList.remove('show');
    void el.offsetWidth;
    el.classList.add('show');
  }

  toast(text, ms = 2200) {
    const el = this.el.toast;
    el.textContent = text;
    el.classList.add('show');
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => el.classList.remove('show'), ms);
  }

  // Turns the important sim events into HUD feedback.
  onEvents(events, session) {
    for (const ev of events) {
      switch (ev.type) {
        case 'wave':
          if (ev.index > 0) this.banner(`第 ${ev.index + 1} 波`, `${STAGES[session.stageIndex].name}`);
          break;
        case 'levelUp':
          this.banner(`升级！Lv ${ev.level}`, '获得属性点与天赋点');
          break;
        case 'bossPhase':
          this.banner(ev.name, FRESTON.name);
          break;
        case 'shieldBroken':
          this.toast('魔力护盾已碎，弗雷斯顿暴露了');
          break;
        case 'drop': {
          const r = RARITIES[ev.item.rarity];
          if (r.id === 'epic' || r.id === 'legendary') this.toast(`${r.name}装备：${ev.item.name}`);
          break;
        }
        case 'pickup':
          this.toast(`拾取 ${RARITIES[ev.item.rarity].name}：${ev.item.name}`, 1400);
          break;
        case 'bagFull':
          this.toast('背包已满，装备被丢下了');
          break;
        case 'open':
          this.toast('镜中骑士露出破绽！');
          break;
        default:
          break;
      }
    }
  }

  // Shows the first-run tip once per stage start.
  hint(text) {
    this.toast(text, 4200);
  }
}

function clamp01(v) {
  return Math.max(0, Math.min(1, Number.isFinite(v) ? v : 0));
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

export { STAT_NAMES };
