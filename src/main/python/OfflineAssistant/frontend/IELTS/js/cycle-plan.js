/* ============================================================
 * cycle-plan.js — 长周期规划引擎前端
 * 配置周期 → 月配额/周配额看板 → 每日切片任务（可编辑/删除/回报进度）
 * ============================================================ */
(function () {
  'use strict';

  const $ = (sel) => document.querySelector(sel);
  const SECTIONS = ['L', 'R', 'W', 'S'];
  const SEC_NAMES = { L: '听力', R: '阅读', W: '写作', S: '口语' };
  const SLOT_META = {
    DEEP: { icon: '🟢', name: '高效', cls: 'DEEP' },
    FREE: { icon: '⚪', name: '普通', cls: 'FREE' },
    FRAGMENT: { icon: '🟡', name: '碎片', cls: 'FRAGMENT' },
  };
  const DIFF_META = { HIGH: '高难度', MEDIUM: '中难度', LIGHT: '轻量' };
  const STAGE_NAMES = { FOUNDATION: '基础期', INTENSIVE: '强化期', SPRINT: '冲刺期' };

  let view = null;                 // 后端 CycleView
  let selectedWeek = 0;            // weekIndex
  let editingTaskId = null;
  let weakSet = new Set();

  /* ---------------- 工具 ---------------- */

  function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  function todayStr() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  function hm(iso) {
    return iso && iso.length >= 16 ? iso.slice(11, 16) : '';
  }
  function dateOf(iso) {
    return iso ? iso.slice(0, 10) : '';
  }
  function fmtMin(min) {
    if (min == null) return '0 分钟';
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h > 0 ? `${h}h${m ? String(m).padStart(2, '0') : ''}` : `${m} 分钟`;
  }
  const dowNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

  async function callApi(path, options = {}) {
    const resp = await fetch(`/api/v1/planner${path}`, {
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    const json = await resp.json().catch(() => ({}));
    if (!resp.ok || !json.success) {
      throw new Error(json.message || `请求失败（${resp.status}）`);
    }
    return json.data;
  }

  function toast(msg) {
    const el = $('#toast');
    if (!el) { alert(msg); return; }
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.remove('show'), 2600);
  }

  /* ---------------- 配置表单 ---------------- */

  function initForm() {
    // 默认值：测评档案/全局常量（data.js）
    const cur = window.CURRENT_SCORE || { L: 5.0, R: 5.5, W: 5.0, S: 5.0 };
    const tgt = (window.EXAM_GOAL && EXAM_GOAL.target_parts) || { L: 6.5, R: 6.5, W: 6.0, S: 6.0 };
    $('#cy-start').value = todayStr();
    $('#cy-end').value = (window.EXAM_GOAL && EXAM_GOAL.exam_date) || '';
    $('#cy-total').value = (window.EXAM_GOAL && EXAM_GOAL.target_total) || 6.5;

    $('#cy-score-grid').innerHTML = SECTIONS.map(k => `
      <div class="cy-score-cell">
        <div class="cy-sec-name"><span>${SEC_NAMES[k]}</span><span>${k}</span></div>
        <div style="display:flex;gap:4px;">
          <input type="number" step="0.5" min="0" max="9" data-sec="${k}" data-kind="current" value="${cur[k] ?? 5}" title="当前分" />
          <input type="number" step="0.5" min="0" max="9" data-sec="${k}" data-kind="target" value="${tgt[k] ?? 6.5}" title="目标分" />
        </div>
      </div>
    `).join('');

    // 薄弱项：默认按差距自动取前 2
    const autoWeak = SECTIONS
      .map(k => [k, (tgt[k] ?? 0) - (cur[k] ?? 0)])
      .sort((a, b) => b[1] - a[1])
      .slice(0, 2).map(x => x[0]);
    weakSet = new Set(autoWeak);
    renderWeakChips();

    $('#btn-cycle-generate').addEventListener('click', generate);
  }

  function renderWeakChips() {
    $('#cy-weak-row').innerHTML = SECTIONS.map(k => `
      <button type="button" class="cy-weak-chip ${weakSet.has(k) ? 'active' : ''}" data-sec="${k}">
        ${SEC_NAMES[k]} ${weakSet.has(k) ? '✓' : ''}
      </button>
    `).join('');
    $('#cy-weak-row').querySelectorAll('.cy-weak-chip').forEach(btn => {
      btn.addEventListener('click', () => {
        const k = btn.dataset.sec;
        weakSet.has(k) ? weakSet.delete(k) : weakSet.add(k);
        renderWeakChips();
      });
    });
  }

  function readScores(kind) {
    const out = {};
    document.querySelectorAll(`#cy-score-grid input[data-kind="${kind}"]`).forEach(inp => {
      out[inp.dataset.sec] = Number(inp.value) || 0;
    });
    return out;
  }

  async function generate() {
    const btn = $('#btn-cycle-generate');
    btn.disabled = true;
    btn.textContent = '引擎测算中…';
    try {
      if (window.AmiAPI && window.AmiAPI.ensureDemoLogin) await window.AmiAPI.ensureDemoLogin();
      const payload = {
        examType: 'IELTS',
        startDate: $('#cy-start').value,
        endDate: $('#cy-end').value,
        current: readScores('current'),
        target: readScores('target'),
        targetTotal: Number($('#cy-total').value) || null,
        weak: [...weakSet],
      };
      const data = await callApi('/cycle/generate', { method: 'POST', body: JSON.stringify(payload) });
      applyView(data);
      selectedWeek = 0;
      $('#cycle-config-details').open = false;
      const w = (data.warnings || []).length;
      toast(`规划草稿已生成：${data.months.length} 个月 / ${data.weeks.length} 周 / ${data.taskCount} 个任务${w ? `，${w} 条提醒` : ''}`);
    } catch (e) {
      toast('生成失败：' + e.message);
    } finally {
      btn.disabled = false;
      btn.textContent = '生成月度规划';
    }
  }

  async function loadCurrent() {
    try {
      if (window.AmiAPI && window.AmiAPI.ensureDemoLogin) await window.AmiAPI.ensureDemoLogin();
      const data = await callApi('/cycle/current');
      if (data) applyView(data);
      return !!data;
    } catch (e) {
      return false;
    }
  }

  function applyView(data) {
    view = data;
    // 同步给仪表盘「今日任务」（字段结构兼容）
    window.__remotePlan = { tasks: data.tasks };
    render();
    if (typeof renderDashboard === 'function') {
      try { renderDashboard(); } catch (e) { /* ignore */ }
    }
  }

  /**
   * 进度回报后自动重排在后端异步执行（事件 + AFTER_COMMIT），
   * 立即返回的视图里还没有结果；延迟 2.6s 静默再拉一次，让自动迁移/建议卡片自然出现。
   * 冷却期内的重复回报只刷新最后一次。
   */
  function scheduleAutoRefresh() {
    clearTimeout(scheduleAutoRefresh._t);
    scheduleAutoRefresh._t = setTimeout(() => {
      if (view) loadCurrent();
    }, 2600);
  }

  /* ---------------- 渲染 ---------------- */

  function render() {
    const out = $('#cycle-output');
    if (!view) {
      out.innerHTML = '<div class="card"><div class="empty-hint">尚未生成长周期规划。配置好周期后点击「生成月度规划」。</div></div>';
      return;
    }
    out.innerHTML = `
      ${renderBanner()}
      <div class="cy-warnings">${(view.warnings || []).map(w => `<div class="cy-warning">⚠️ ${esc(w)}</div>`).join('')}</div>
      ${renderAdjustments()}
      ${renderMetrics()}
      <div class="card" style="margin-top:12px;">
        <div class="card-head"><h3>月度工作量配额</h3><span class="muted" style="font-size:12px;">容量基于时间资源实测 · 周配额之和 = 月配额</span></div>
        <div class="cy-month-grid">${view.months.map(renderMonthCard).join('')}</div>
      </div>
      <div class="card" style="margin-top:12px;">
        <div class="card-head"><h3>周任务看板</h3><span class="muted" style="font-size:12px;">点击任务卡可修改时间 / 目标 / 状态</span></div>
        <div class="cy-week-tabs">${view.weeks.map(renderWeekTab).join('')}</div>
        <div id="cy-week-board"></div>
      </div>
    `;
    bindResultEvents();
    renderWeekBoard();
  }

  function renderBanner() {
    const c = view.cycle;
    const lvl = c.autoAdjustLevel || 'SMART';
    const pct = view.taskCount ? Math.round(view.doneCount / view.taskCount * 100) : 0;
    const days = Math.round((new Date(c.endDate) - new Date(c.startDate)) / 86400000) + 1;
    return `
      <div class="card">
        <div class="cy-banner">
          <span class="cy-status ${esc(c.status)}">${c.status === 'ACCEPTED' ? '✅ 已采纳' : '📝 草稿待确认'}</span>
          <span class="cy-dates">${esc(c.startDate)} → ${esc(c.endDate)}（${days} 天）</span>
          <span class="cy-stat">目标总分 ${c.targetTotal ?? '—'}</span>
          <span class="cy-stat">当前均分 ${c.currentTotal ?? '—'}</span>
          <span class="cy-stat">任务 ${view.doneCount}/${view.taskCount}（${pct}%）</span>
          <span class="cy-stat cy-level-box">自动重排
            <span class="cy-level-seg" role="group" aria-label="自动重排级别">
              <button type="button" data-level="OFF" class="${lvl === 'OFF' ? 'on' : ''}" title="全部调整都需你手动确认">关闭</button>
              <button type="button" data-level="SMART" class="${lvl === 'SMART' ? 'on' : ''}" title="少量任务逾期自动迁移；降目标等大调整需你确认（推荐）">智能</button>
              <button type="button" data-level="FULL" class="${lvl === 'FULL' ? 'on' : ''}" title="所有调整（含下调配额）均自动执行，可撤销">全自动</button>
            </span>
          </span>
          <div class="cy-actions">
            ${c.status === 'DRAFT'
              ? '<button class="btn btn-primary" id="cy-btn-accept">✅ 一键采纳</button><button class="btn btn-danger" id="cy-btn-reject">全部拒绝</button>'
              : '<button class="btn btn-danger" id="cy-btn-reject">删除当前规划</button>'}
          </div>
          <div class="cy-progress"><i style="width:${pct}%"></i></div>
        </div>
      </div>
    `;
  }

  /* ---------------- 进度指标面板（第三节） ---------------- */

  function renderMetrics() {
    const m = view.metrics || {};
    const clsToday = (m.todayRate || 0) >= 80 ? 'good' : (m.todayRate || 0) >= 50 ? 'warn' : 'bad';
    const clsRecent = (m.recentAvgRate || 0) >= 80 ? 'good' : (m.recentAvgRate || 0) >= 50 ? 'warn' : 'bad';
    const clsMonth = (m.monthRate || 0) >= 80 ? 'good' : (m.monthRate || 0) >= 50 ? 'warn' : 'bad';
    const overdueCls = (m.overdueTaskCount || 0) > 0 ? 'bad' : 'good';
    const todayPct = Math.min(100, m.todayRate || 0);
    const monthPct = Math.min(100, m.monthRate || 0);
    return `
      <div class="card" style="margin-top:12px;">
        <div class="card-head"><h3>进度看板</h3><span class="muted" style="font-size:12px;">今日完成率 · 近3天平均 · 月累计进度</span></div>
        <div class="cy-metrics">
          <div class="cy-metric ${clsToday}">
            <div class="m-label">今日完成率</div>
            <div class="m-value">${m.todayRate ?? 0}% <small>（${m.todayDone ?? 0}/${m.todayPlanned ?? 0} 分钟）</small></div>
            <div class="cy-progress"><i style="width:${todayPct}%"></i></div>
          </div>
          <div class="cy-metric ${clsRecent}">
            <div class="m-label">近3天平均完成率</div>
            <div class="m-value">${m.recentAvgRate ?? 0}%</div>
            <div class="m-sub">滚动3日窗口平均值</div>
          </div>
          <div class="cy-metric ${clsMonth}">
            <div class="m-label">月累计完成率</div>
            <div class="m-value">${m.monthRate ?? 0}% <small>（${m.monthDone ?? 0}/${m.monthQuota ?? 0} 分钟）</small></div>
            <div class="cy-progress"><i style="width:${monthPct}%"></i></div>
          </div>
          <div class="cy-metric ${overdueCls}">
            <div class="m-label">过期未完成任务</div>
            <div class="m-value">${m.overdueTaskCount ?? 0} <small>个</small></div>
            <div class="m-sub">共 ${m.overdueMinutes ?? 0} 分钟 · 需尽快清理</div>
          </div>
        </div>
      </div>`;
  }

  function renderAdjustments() {
    const list = view.adjustments || [];
    if (!list.length) return '';
    const scenarioTags = {
      SCENARIO1_MIGRATE: '场景1：少量未完成',
      SCENARIO2_REDUCE: '场景2：完成率偏低',
      SCENARIO3_ACCELERATE: '场景3：连续超额',
      PILE_UP_WARNING: '防堆积预警',
    };
    const cards = list.map(a => {
      const auto = a.status === 'AUTO_EXECUTED';
      return `
      <div class="cy-adjust-card ${esc(a.scenario)} ${auto ? 'is-auto' : ''}" data-aid="${esc(a.id)}">
        <div class="cy-adjust-body">
          <div class="cy-adjust-title">${esc(a.title)}<span class="cy-adjust-tag">${scenarioTags[a.scenario] || a.scenario}</span>${auto
            ? '<span class="cy-adjust-badge-auto">🤖 已自动执行 · 可撤销</span>'
            : '<span class="cy-adjust-badge-pending">⏸ 待你确认</span>'}</div>
          <div class="cy-adjust-desc">${esc(a.description)}</div>
          <div class="cy-adjust-meta">触发时完成率 ${a.triggerRate ?? '-'}% · ${a.triggerDate ? a.triggerDate.slice(0, 10) : ''}</div>
        </div>
        <div class="cy-adjust-actions">
          ${auto
            ? '<button type="button" class="btn btn-undo-adj">↩ 撤销</button>'
            : '<button type="button" class="btn btn-primary btn-accept-adj">采纳</button><button type="button" class="btn btn-danger btn-reject-adj">拒绝</button>'}
        </div>
      </div>`;
    }).join('');
    return `
      <div class="card" style="margin-top:12px;">
        <div class="card-head"><h3>计划调整</h3><span class="muted" style="font-size:12px;">🤖 系统自动执行的调整可随时撤销 · ⏸ 待确认建议采纳后生效</span></div>
        <div class="cy-adjust-list">${cards}</div>
      </div>`;
  }

  function renderMonthCard(m) {
    const maxQ = Math.max(1, ...view.months.map(x => x.quotaMinutes));
    const bars = SECTIONS.map(k => {
      const v = m.quotaBySection[k] || 0;
      return `
        <div class="cy-qbar ${k}">
          <span>${k}</span>
          <span class="track"><i style="width:${Math.round(v / maxQ * 100)}%"></i></span>
          <span>${fmtMin(v)}</span>
        </div>`;
    }).join('');
    return `
      <div class="cy-month-card">
        <div class="cy-month-head">
          <span class="cy-month-name">${esc(m.label)}</span>
          <span class="cy-stage-tag ${m.stage}">${esc(m.stageLabel || STAGE_NAMES[m.stage] || m.stage)}</span>
        </div>
        <div class="cy-month-range">${m.start} ~ ${m.end}</div>
        <div class="cy-cap-row"><span>🟢 高效</span><b>${fmtMin(m.deepMinutes)}</b></div>
        <div class="cy-cap-row"><span>⚪ 普通</span><b>${fmtMin(m.freeMinutes)}</b></div>
        <div class="cy-cap-row"><span>🟡 碎片</span><b>${fmtMin(m.fragmentMinutes)}</b></div>
        <div class="cy-quota-bars">${bars}</div>
        <div class="cy-month-foot">
          <span class="muted">可用 ${fmtMin(m.availableMinutes)}</span>
          <span>配额 <b>${fmtMin(m.quotaMinutes)}</b></span>
        </div>
      </div>
    `;
  }

  function renderWeekTab(w) {
    const pct = w.quotaMinutes ? Math.min(100, Math.round(w.allocatedMinutes / w.quotaMinutes * 100)) : 0;
    return `
      <button class="cy-week-tab ${w.weekIndex === selectedWeek ? 'active' : ''}" data-week="${w.weekIndex}">
        <div class="wt-range">第 ${w.weekIndex + 1} 周 · ${w.weekStart.slice(5)}</div>
        <div class="wt-meta">${fmtMin(w.allocatedMinutes)} / ${fmtMin(w.quotaMinutes)} · ${pct}%</div>
      </button>
    `;
  }

  function renderWeekBoard() {
    const board = $('#cy-week-board');
    const w = view.weeks.find(x => x.weekIndex === selectedWeek) || view.weeks[0];
    if (!w) { board.innerHTML = ''; return; }
    const pct = w.quotaMinutes ? Math.min(100, Math.round(w.allocatedMinutes / w.quotaMinutes * 100)) : 0;
    const quotaText = SECTIONS.map(k => `${SEC_NAMES[k]} ${fmtMin(w.quotaBySection[k] || 0)}`).join(' · ');

    // 生成该周日期列（处理周期末日不足 7 天）
    const days = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(w.weekStart);
      d.setDate(d.getDate() + i);
      const ds = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      if (ds <= w.weekEnd) days.push({ ds, dow: i });
    }
    const tasksByDay = new Map(days.map(d => [d.ds, []]));
    for (const t of view.tasks) {
      const ds = dateOf(t.start);
      if (tasksByDay.has(ds)) tasksByDay.get(ds).push(t);
    }
    const today = todayStr();

    board.innerHTML = `
      <div class="cy-week-head">
        <span><b>${esc(w.weekStart)} ~ ${esc(w.weekEnd)}</b></span>
        <span class="muted">配额 ${fmtMin(w.quotaMinutes)}（${quotaText}）</span>
        <span class="${w.allocatedMinutes > w.quotaMinutes ? 'text-danger' : 'muted'}">已排入 ${fmtMin(w.allocatedMinutes)}</span>
        <div class="cy-progress"><i style="width:${pct}%;${w.allocatedMinutes > w.quotaMinutes ? 'background:#ef4444;' : ''}"></i></div>
      </div>
      <div class="cy-days">
        ${days.map(d => {
          const list = (tasksByDay.get(d.ds) || []).slice().sort((a, b) => a.start.localeCompare(b.start));
          return `
            <div class="cy-day-col">
              <div class="cy-day-head ${d.ds === today ? 'today' : ''}">
                <span class="dow">${dowNames[d.dow]}</span>
                <span class="mdate">${d.ds.slice(5)}</span>
              </div>
              <div class="cy-day-tasks">
                ${list.length
                  ? list.map(renderTaskCard).join('')
                  : '<div class="cy-day-empty">无安排<br><span style="font-size:10px;">（配额已留给其他日期）</span></div>'}
              </div>
            </div>`;
        }).join('')}
      </div>
    `;
    board.querySelectorAll('.cy-task').forEach(card => {
      card.addEventListener('click', () => openModal(card.dataset.taskId));
    });
  }

  function renderTaskCard(t) {
    const slot = SLOT_META[t.slotType] || { icon: '', name: t.slotType, cls: 'FREE' };
    const statusCls = t.progressStatus === 'DONE' ? 'done' : t.progressStatus === 'PARTIAL' ? 'partial' : '';
    const statusIcon = t.progressStatus === 'DONE' ? '✅' : t.progressStatus === 'PARTIAL' ? '⏳' : '';
    return `
      <div class="cy-task ${slot.cls} ${statusCls}" data-task-id="${t.taskId}"
           title="${esc(t.objective || '')}">
        <div class="ct-top">
          <span><span class="ct-sec ${t.section}">${t.section}</span> ${hm(t.start)}-${hm(t.end)} · ${t.durationMinutes}m</span>
        </div>
        <div class="ct-title">${esc(t.title)}</div>
        <div class="ct-foot">
          <span>${slot.icon}${slot.name}</span>
          <span class="ct-diff ${t.difficulty}">${DIFF_META[t.difficulty] || t.difficulty}</span>
          <span class="ct-status">${statusIcon}</span>
        </div>
      </div>
    `;
  }

  /* ---------------- 事件绑定 ---------------- */

  function bindResultEvents() {
    document.querySelectorAll('.cy-week-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        selectedWeek = Number(tab.dataset.week);
        document.querySelectorAll('.cy-week-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        renderWeekBoard();
      });
    });
    const acc = $('#cy-btn-accept');
    if (acc) acc.addEventListener('click', accept);
    const rej = $('#cy-btn-reject');
    if (rej) rej.addEventListener('click', rejectCycle);
    // 调整卡片：AUTO_EXECUTED 可撤销；PENDING 可采纳/拒绝（ID 为字符串避免雪花精度丢失）
    document.querySelectorAll('.cy-adjust-card').forEach(card => {
      const aid = card.dataset.aid;
      const undoBtn = card.querySelector('.btn-undo-adj');
      if (undoBtn) {
        undoBtn.addEventListener('click', () => undoAdjustment(aid));
        return;
      }
      card.querySelector('.btn-accept-adj').addEventListener('click', () => resolveAdjustment(aid, 'accept'));
      card.querySelector('.btn-reject-adj').addEventListener('click', () => resolveAdjustment(aid, 'reject'));
    });
    // 自动重排三档开关（不传 cycleId：后端取当前用户最新周期，同时规避长整型精度问题）
    document.querySelectorAll('.cy-level-seg button').forEach(btn => {
      btn.addEventListener('click', () => setAutoLevel(btn.dataset.level));
    });
  }

  async function undoAdjustment(adjustmentId) {
    if (!window.confirm('撤销本次自动调整？任务时间与配额将恢复到调整前。')) return;
    try {
      const data = await callApi(`/adjustments/${encodeURIComponent(adjustmentId)}/undo`, { method: 'POST' });
      if (data) applyView(data);
      toast('已撤销自动调整');
    } catch (e) { toast('撤销失败：' + e.message); }
  }

  async function setAutoLevel(level) {
    if (level === (view.cycle.autoAdjustLevel || 'SMART')) return;
    const labels = {
      OFF: '已关闭自动调整，所有调整都需手动确认',
      SMART: '已切换到智能模式：少量任务逾期将自动迁移',
      FULL: '已切换到全自动模式（同周期最多自动降档 2 次）',
    };
    try {
      const data = await callApi('/cycle/auto-adjust-level', {
        method: 'PUT',
        body: JSON.stringify({ level }),
      });
      view.cycle.autoAdjustLevel = data.level;
      render();
      toast(labels[data.level] || '自动调整级别已更新');
    } catch (e) { toast('切换失败：' + e.message); }
  }

  async function resolveAdjustment(adjustmentId, action) {
    try {
      const data = await callApi(`/adjustments/${encodeURIComponent(adjustmentId)}/${action}`, { method: 'POST' });
      if (data) applyView(data);
      toast(action === 'accept' ? '已采纳调整建议' : '已拒绝调整建议');
    } catch (e) { toast('操作失败：' + e.message); }
  }

  async function accept() {
    try {
      const data = await callApi(`/cycle/${view.cycle.id}/accept`, { method: 'POST' });
      applyView(data);
      toast('已采纳该备考规划');
    } catch (e) { toast('采纳失败：' + e.message); }
  }

  async function rejectCycle() {
    if (!window.confirm('确认拒绝并删除当前规划？删除后需重新生成。')) return;
    try {
      await callApi(`/cycle/${view.cycle.id}/reject`, { method: 'POST' });
      view = null;
      window.__remotePlan = null;
      render();
      if (typeof renderDashboard === 'function') { try { renderDashboard(); } catch (e) { /* ignore */ } }
      toast('已删除该规划');
    } catch (e) { toast('删除失败：' + e.message); }
  }

  /* ---------------- 任务编辑弹窗 ---------------- */

  function openModal(taskId) {
    const t = view.tasks.find(x => String(x.taskId) === String(taskId));
    if (!t) return;
    editingTaskId = taskId;
    const slot = SLOT_META[t.slotType] || { icon: '', name: t.slotType };
    $('#cy-modal-tags').innerHTML = `
      <span class="ct-sec ${t.section}">${SEC_NAMES[t.section]}</span>
      <span>${slot.icon} ${slot.name}时段</span>
      <span class="ct-diff ${t.difficulty}">${DIFF_META[t.difficulty] || t.difficulty}</span>
      <span>${t.durationMinutes} 分钟</span>
      <span>${esc(t.stage ? (STAGE_NAMES[t.stage] || t.stage) : '')}</span>
      <span>状态：${t.progressStatus === 'DONE' ? '已完成' : t.progressStatus === 'PARTIAL' ? '部分完成' : '未开始'}</span>
    `;
    $('#cy-edit-title').value = t.title || '';
    $('#cy-edit-objective').value = t.objective || '';
    $('#cy-edit-start').value = hm(t.start);
    $('#cy-edit-end').value = hm(t.end);
    $('#cy-modal-mask').classList.add('open');
  }

  function closeModal() {
    editingTaskId = null;
    $('#cy-modal-mask').classList.remove('open');
  }

  function initModal() {
    $('#cy-modal-close').addEventListener('click', closeModal);
    $('#cy-modal-mask').addEventListener('click', (e) => {
      if (e.target.id === 'cy-modal-mask') closeModal();
    });
    $('#cy-btn-save').addEventListener('click', async () => {
      try {
        const data = await callApi(`/tasks/${editingTaskId}`, {
          method: 'PUT',
          body: JSON.stringify({
            title: $('#cy-edit-title').value.trim(),
            objective: $('#cy-edit-objective').value.trim(),
            start: $('#cy-edit-start').value,
            end: $('#cy-edit-end').value,
          }),
        });
        applyView(data);
        closeModal();
        toast('任务已修改');
      } catch (e) { toast('保存失败：' + e.message); }
    });
    $('#cy-btn-delete').addEventListener('click', async () => {
      if (!window.confirm('确认删除该切片任务？')) return;
      try {
        const data = await callApi(`/tasks/${editingTaskId}`, { method: 'DELETE' });
        applyView(data);
        closeModal();
        toast('任务已删除');
      } catch (e) { toast('删除失败：' + e.message); }
    });
    const report = async (status, label) => {
      try {
        const data = await callApi(`/tasks/${editingTaskId}/progress`, {
          method: 'POST',
          body: JSON.stringify({ status }),
        });
        applyView(data);
        closeModal();
        toast(`进度已同步：${label}`);
        scheduleAutoRefresh();
      } catch (e) { toast('同步失败：' + e.message); }
    };
    $('#cy-btn-done').addEventListener('click', () => report('DONE', '已完成'));
    $('#cy-btn-partial').addEventListener('click', () => report('PARTIAL', '部分完成'));
    $('#cy-btn-todo').addEventListener('click', () => report('TODO', '未开始'));
  }

  /* ---------------- 启动 ---------------- */

  function init() {
    initForm();
    initModal();
  }

  // 等 DOM 就绪
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.CyclePlan = {
    /** 启动时静默拉取当前周期；返回是否存在有效规划 */
    bootstrap: loadCurrent,
    refresh: loadCurrent,
  };
})();
