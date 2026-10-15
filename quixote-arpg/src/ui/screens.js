import { ATTR_KEYS, allocateAttr, discardFromBag, equipFromBag, learnTalent, xpNeeded } from '../sim/progression.js';
import { computeStats } from '../sim/stats.js';
import { RARITIES, SCHOOLS, SLOT_NAMES, STAGES, SLOTS } from '../config/content.js';
import { describeAffix } from '../sim/items.js';

const ATTR_NAMES = { str: '力量', agi: '敏捷', vit: '体魄', wil: '意志' };
const ATTR_HINT = {
  str: '每点 +2 攻击，+1.2 每级',
  agi: '每点 +1% 攻速/移速，+0.4% 暴击',
  vit: '每点 +10 生命，+0.6 护甲',
  wil: '每点 +2% 疯狂获取',
};

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const pct = (v) => `${Math.round(v * 1000) / 10}%`;

// Full-screen and overlay menus. Each screen is HTML built from the current progress; actions come back through `on`.
export class Screens {
  constructor({ root, session, settings, on }) {
    this.root = root;
    this.session = session;
    this.settings = settings;
    this.on = on;
    this.selectedItem = null;
    this.current = null;
    root.addEventListener('click', (e) => this.onClick(e));
    root.addEventListener('input', (e) => this.onInput(e));
    root.addEventListener('change', (e) => this.onInput(e));
  }

  get progress() {
    return this.session.progress;
  }

  show(name, html, { overlay = false } = {}) {
    this.current = name;
    this.root.className = `screen ${overlay ? 'overlay' : ''}`;
    this.root.dataset.screen = name;
    this.root.innerHTML = `<div class="sheet">${html}</div>`;
    this.root.classList.remove('hidden');
  }

  hide() {
    this.current = null;
    this.root.classList.add('hidden');
    this.root.innerHTML = '';
  }

  onClick(e) {
    const seg = e.target.closest?.('button[data-input][data-value]');
    if (seg) {
      this.on.input?.(seg.dataset.input, seg.dataset.value, seg.dataset, this);
      return;
    }
    const btn = e.target.closest?.('[data-act]');
    if (!btn || btn.disabled) return;
    this.on.action?.(btn.dataset.act, btn.dataset, this);
  }

  onInput(e) {
    const el = e.target.closest?.('[data-input]');
    if (!el) return;
    this.on.input?.(el.dataset.input, el.value ?? el.checked, el.dataset, this);
  }

  // ---- menus -------------------------------------------------------------------

  menu(hasSave) {
    const p = this.progress;
    const stage = STAGES[this.session.stageIndex];
    const cont = hasSave ? `继续冒险 · ${stage.name}` : '开始冒险';
    this.show(
      'menu',
      `
      <div class="title-hero">
        <h1>风车与疯狂</h1>
        <p>堂吉诃德式横屏动作 RPG · 疯狂越深，攻击越狠，防御越薄</p>
      </div>
      <div class="menu-grid">
        <button class="btn primary" data-act="start">${esc(cont)}</button>
        <button class="btn" data-act="build">角色与装备 <small class="dim">Lv ${p.level}</small></button>
        <button class="btn" data-act="settings">设置与按键</button>
        <button class="btn danger" data-act="clear-save">清除存档</button>
      </div>
      <div class="card">
        <h3>操作</h3>
        <p class="note">左半屏拖动虚拟摇杆移动。右半屏：攻击 · 重击（按住蓄力）· 闪避（无敌帧）· 格挡（刚举盾时 0.2 秒内被击中会完美格挡并反击）· 突刺 · 冲锋 · 盾击 · 风车旋风（疯狂满 60 时可释放）。</p>
        <p class="note">键盘：WASD 移动，J 攻击，K 重击，Space 闪避，L 格挡，Q 突刺，E 冲锋，R 盾击，F 旋风，Esc 暂停。</p>
      </div>
      <div class="stage-cards">
        ${STAGES.map((s, i) => `<div class="card ${i === this.session.stageIndex ? 'cur' : ''}"><h3>${i + 1}. ${esc(s.name)}</h3><span class="note">${esc(s.subtitle)}</span></div>`).join('')}
      </div>`,
    );
  }

  pause() {
    this.show(
      'pause',
      `
      <div class="sheet-head"><h2 class="sheet-title">暂停</h2><span class="sheet-sub">切后台会自动暂停</span></div>
      <div class="menu-grid">
        <button class="btn primary" data-act="resume">继续战斗</button>
        <button class="btn" data-act="settings-from-pause">设置与按键</button>
        <button class="btn" data-act="quit">保存并返回主菜单</button>
      </div>
      <p class="note">存档记录角色成长。关卡从开始处重来，已获得的等级与装备不会丢失。</p>`,
      { overlay: true },
    );
  }

  clear(stage, world) {
    const last = this.session.stageIndex >= STAGES.length - 1;
    const s = world.stats;
    this.show(
      'clear',
      `
      <div class="sheet-head"><h2 class="sheet-title">${last ? '风车停转' : '关卡完成'}</h2><span class="sheet-sub">${esc(stage.name)}</span></div>
      <div class="grid-2">
        <div class="card"><h3>战绩</h3><div class="stat-list">
          <span>击杀</span><b>${s.kills}</b><span>精英</span><b>${s.eliteKills}</b>
          <span>闪避</span><b>${s.dodges}</b><span>完美格挡</span><b>${s.perfectBlocks}</b>
          <span>获得经验</span><b>${s.xp}</b><span>拾取装备</span><b>${s.items}</b>
        </div></div>
        <div class="card"><h3>角色</h3><div class="stat-list">
          <span>等级</span><b>Lv ${this.progress.level}</b><span>属性点</span><b>${this.progress.attrPoints}</b>
          <span>天赋点</span><b>${this.progress.talentPoints}</b><span>背包</span><b>${this.progress.bag.length}/30</b>
        </div></div>
      </div>
      <div class="row">
        <button class="btn" data-act="build">强化角色</button>
        ${last ? '<button class="btn primary" data-act="victory-continue">完成冒险</button>' : '<button class="btn primary" data-act="next-stage">前往下一关</button>'}
        <button class="btn" data-act="quit">返回主菜单</button>
      </div>`,
    );
  }

  dead(stage) {
    this.show(
      'dead',
      `
      <div class="title-hero"><h1>倒下了</h1><p>堂吉诃德倒在${esc(stage.name)}，但风车仍在转</p></div>
      <div class="menu-grid">
        <button class="btn primary" data-act="retry">重新挑战本关</button>
        <button class="btn" data-act="build">强化角色</button>
        <button class="btn" data-act="quit">返回主菜单</button>
      </div>
      <p class="note">本关的击杀与拾取会在重来时重新出现；等级、属性、天赋与装备会保留。</p>`,
      { overlay: true },
    );
  }

  victory(world) {
    this.show(
      'victory',
      `
      <div class="title-hero"><h1>通关</h1><p>弗雷斯顿的魔法烟消云散，堂吉诃德骑着想象中的骏马走入落日</p></div>
      <div class="grid-2">
        <div class="card"><h3>冒险记录</h3><div class="stat-list">
          <span>最终等级</span><b>Lv ${this.progress.level}</b>
          <span>击杀</span><b>${world?.stats.kills ?? '—'}</b>
          <span>精英</span><b>${world?.stats.eliteKills ?? '—'}</b>
          <span>拾取装备</span><b>${world?.stats.items ?? '—'}</b>
        </div></div>
      </div>
      <div class="menu-grid">
        <button class="btn primary" data-act="quit">返回主菜单</button>
        <button class="btn danger" data-act="new-game">重新开始（清除存档）</button>
      </div>`,
    );
  }

  settingsScreen({ fromPause = false } = {}) {
    const s = this.settings;
    const seg = (name, options, value) =>
      `<div class="seg">${options.map(([v, label]) => `<button data-input="${name}" data-value="${v}" class="${value === v ? 'on' : ''}">${label}</button>`).join('')}</div>`;
    this.show(
      'settings',
      `
      <div class="sheet-head"><h2 class="sheet-title">设置与按键</h2><button class="btn small" data-act="${fromPause ? 'pause' : 'menu'}">返回</button></div>
      <div class="card">
        <h3>按键</h3>
        <div class="setting"><span>持握手</span>${seg('hand', [['right', '右手'], ['left', '左手']], s.hand)}</div>
        <div class="setting"><span>按键大小 <small class="dim">${Math.round(s.touchScale * 100)}%</small></span><input type="range" min="0.7" max="1.4" step="0.05" value="${s.touchScale}" data-input="touchScale" /></div>
        <div class="setting"><span>按键透明度 <small class="dim">${Math.round(s.touchOpacity * 100)}%</small></span><input type="range" min="0.3" max="1" step="0.05" value="${s.touchOpacity}" data-input="touchOpacity" /></div>
        <div class="setting"><span>显示触控按键</span>${seg('showTouch', [['auto', '自动'], ['always', '常显'], ['never', '隐藏']], s.showTouch)}</div>
        <div class="setting"><span>按键位置</span><div class="row"><button class="btn small" data-act="edit-layout">拖动调整</button><button class="btn small" data-act="reset-layout">恢复默认</button></div></div>
      </div>
      <div class="card">
        <h3>画面与反馈</h3>
        <div class="setting"><span>伤害数字</span>${seg('damageNumbers', [['1', '开'], ['0', '关']], s.damageNumbers ? '1' : '0')}</div>
        <div class="setting"><span>屏幕震动</span>${seg('shake', [['1', '开'], ['0', '关']], s.shake ? '1' : '0')}</div>
        <div class="setting"><span>画质</span>${seg('quality', [['performance', '流畅 30'], ['balanced', '均衡'], ['quality', '高清 60']], s.quality)}</div>
        <div class="setting"><span>次要信息（小地图）</span>${seg('hideSecondary', [['0', '展开'], ['1', '收起']], s.hideSecondary ? '1' : '0')}</div>
      </div>
      <p class="note">所有设置保存在本机。按键位置以屏幕高度为单位记录，在 16:9 到 20:9 之间都会保持比例。</p>`,
      { overlay: fromPause },
    );
  }

  build() {
    const p = this.progress;
    const stats = computeStats(p);
    const need = xpNeeded(p);
    const attrs = ATTR_KEYS.map(
      (k) => `
      <div class="attr-row">
        <div><b>${ATTR_NAMES[k]}</b> <span class="dim">${p.attrs[k]}</span><div class="note">${ATTR_HINT[k]}</div></div>
        <span class="dim">${stats.attr[k]}</span>
        <button class="btn" data-act="attr" data-key="${k}" ${p.attrPoints > 0 ? '' : 'disabled'}>+</button>
      </div>`,
    ).join('');

    const derived = [
      ['生命', Math.round(stats.maxHp)],
      ['攻击', Math.round(stats.atk)],
      ['暴击率', pct(stats.crit)],
      ['暴击伤害', `${Math.round(stats.critDmg * 100)}%`],
      ['攻速', `${Math.round(stats.atkSpeed * 100)}%`],
      ['护甲', Math.round(stats.armor)],
      ['格挡减伤', pct(stats.block)],
      ['移速', stats.speed.toFixed(2)],
      ['疯狂上限', Math.round(stats.maxMad)],
      ['疯狂获取', `${Math.round(stats.madGain * 100)}%`],
    ];

    const schools = Object.entries(SCHOOLS)
      .map(([id, sc]) => {
        const chosen = p.school === id;
        const locked = p.school && !chosen;
        const rows = sc.talents
          .map((t, i) => {
            const done = p.talents.includes(t.id);
            const next = !done && p.talents.length === i && (!p.school || chosen) && p.talentPoints > 0;
            return `<div class="talent ${done ? 'done' : ''} ${locked ? 'locked' : ''}">
              <span><b>${esc(t.name)}</b><br /><span class="note">${esc(t.desc)}</span></span>
              ${next ? `<button class="btn small" data-act="talent" data-id="${t.id}">学习</button>` : done ? '<span>已学</span>' : ''}
            </div>`;
          })
          .join('');
        return `<div class="school"><b>${esc(sc.name)} ${chosen ? '· 已选择' : locked ? '· 已锁定' : ''}</b><span class="note">${esc(sc.desc)}</span>${rows}</div>`;
      })
      .join('');

    const equipped = SLOTS.map((slot) => {
      const it = p.equipped[slot];
      if (!it) return `<div class="item"><span class="name">${SLOT_NAMES[slot]}</span><span class="sub">空</span></div>`;
      const r = RARITIES[it.rarity];
      const affixes = it.affixes.map((a) => describeAffix(a)).join(' · ') || '无词缀';
      const uniq = it.unique ? ` · 独特` : '';
      return `<div class="item"><span class="name r-${r.id}">${esc(it.name)}</span><span class="sub">${SLOT_NAMES[slot]} · ${r.name}${uniq} · ${esc(affixes)}</span></div>`;
    }).join('');

    const bag = p.bag.length
      ? p.bag
          .map((it) => {
            const r = RARITIES[it.rarity];
            const sel = this.selectedItem === it.id ? 'sel' : '';
            return `<button class="item ${sel}" data-act="select" data-id="${it.id}"><span class="name r-${r.id}">${esc(it.name)}</span><span class="sub">${SLOT_NAMES[it.slot]} · ${r.name}</span></button>`;
          })
          .join('')
      : '<div class="empty">背包是空的。击败怪物，拾取掉落的装备。</div>';

    const sel = p.bag.find((i) => i.id === this.selectedItem);
    const preview = sel ? this.previewBlock(sel) : '<div class="note">点选背包中的装备，可查看数值对比。</div>';

    this.show(
      'build',
      `
      <div class="sheet-head"><h2 class="sheet-title">角色与装备</h2><button class="btn small" data-act="back">返回</button></div>
      <div class="card">
        <div class="row" style="justify-content:space-between">
          <b>Lv ${p.level}</b><span class="note">经验 ${p.xp}/${need || '满级'} · 属性点 ${p.attrPoints} · 天赋点 ${p.talentPoints}</span>
        </div>
        <div class="stat-list">${derived.map(([k, v]) => `<span>${k}</span><b>${v}</b>`).join('')}</div>
      </div>
      <div class="grid-2">
        <div class="card"><h3>属性</h3>${attrs}</div>
        <div class="card"><h3>build 分支（选一条，按顺序学习）</h3>${schools}</div>
      </div>
      <div class="grid-2">
        <div class="card"><h3>当前装备</h3>${equipped}</div>
        <div class="card"><h3>背包 ${p.bag.length}/30</h3><div class="bag">${bag}</div>${preview}</div>
      </div>`,
    );
  }

  previewBlock(item) {
    const p = this.progress;
    const before = computeStats(p);
    const clone = structuredClone(p);
    equipFromBag(clone, item.id);
    const after = computeStats(clone);
    const rows = [
      ['攻击', before.atk, after.atk, (v) => Math.round(v)],
      ['生命', before.maxHp, after.maxHp, (v) => Math.round(v)],
      ['护甲', before.armor, after.armor, (v) => Math.round(v)],
      ['暴击率', before.crit, after.crit, pct],
      ['格挡减伤', before.block, after.block, pct],
      ['疯狂获取', before.madGain, after.madGain, (v) => `${Math.round(v * 100)}%`],
    ]
      .map(([k, a, b, f]) => {
        const d = b - a;
        const cls = Math.abs(d) < 1e-9 ? 'dim' : d > 0 ? '' : 'style="color:#ff9a8a"';
        return `<span>${k}</span><b ${cls}>${f(a)} → ${f(b)}</b>`;
      })
      .join('');
    const r = RARITIES[item.rarity];
    const affixes = item.affixes.map((a) => describeAffix(a)).join(' · ') || '无词缀';
    const uniqueText = item.unique ? `<div class="note">独特效果已生效</div>` : '';
    return `<div class="card">
      <b class="r-${r.id}">${esc(item.name)}</b>
      <span class="note">${SLOT_NAMES[item.slot]} · ${r.name} · 物品等级 ${item.ilvl}</span>
      <span class="note">${esc(affixes)}</span>
      ${uniqueText}
      <div class="stat-list">${rows}</div>
      <div class="row">
        <button class="btn primary small" data-act="equip" data-id="${item.id}">装备</button>
        <button class="btn danger small" data-act="discard" data-id="${item.id}">丢弃</button>
      </div>
    </div>`;
  }

  // Click handlers used by the main module for screen-local actions.
  applyAttr(key) {
    if (allocateAttr(this.progress, key)) this.session.save();
  }

  applyTalent(id) {
    if (learnTalent(this.progress, id)) this.session.save();
  }

  applyEquip(id) {
    if (equipFromBag(this.progress, id)) {
      this.session.save();
      this.selectedItem = null;
    }
  }

  applyDiscard(id) {
    if (discardFromBag(this.progress, id)) {
      this.session.save();
      this.selectedItem = null;
    }
  }

  select(id) {
    this.selectedItem = this.selectedItem === id ? null : id;
  }

  // Layout editor UI: a floating bar over the touch layer.
  layoutBar(onSave, onReset, onCancel) {
    const bar = document.createElement('div');
    bar.className = 'edit-bar';
    bar.innerHTML = `<button class="btn primary small" data-role="save">保存位置</button><button class="btn small" data-role="reset">恢复默认</button><button class="btn small" data-role="cancel">取消</button>`;
    bar.addEventListener('click', (e) => {
      const role = e.target.closest('[data-role]')?.dataset.role;
      if (role === 'save') onSave();
      if (role === 'reset') onReset();
      if (role === 'cancel') onCancel();
    });
    return bar;
  }
}

