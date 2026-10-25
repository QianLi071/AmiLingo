/* ============================================================
 * app.js — 主应用：路由、仪表盘、时间线、Checklist、练习
 * ============================================================ */

const VIEW_META = {
  dashboard: { title: '仪表盘', sub: '雅思备考全流程陪跑' },
  schedule: { title: '时间资源', sub: '录入 24h 时段与类型标签，AI 据此切片排程' },
  cycle: { title: '长周期规划', sub: '自定义备考周期 → 月配额 → 周配额 → 日切片任务' },
  plan: { title: 'AI 排程引擎', sub: '按空闲块生成 N 周学习任务' },
  timeline: { title: '考试流程时间线', sub: 'T-180 至 T+15 全节点提醒' },
  checklist: { title: '考前 Checklist', sub: '证件 / 通勤 / 物品逐项检查' },
  practice: { title: '听说读写', sub: '阅读出题判分 + 写作 AI 评分' },
};

/* ---------- 路由 ---------- */
function switchView(name) {
  $$('.view').forEach(v => v.classList.remove('active'));
  $$('.nav-item').forEach(n => n.classList.remove('active'));
  $(`#view-${name}`).classList.add('active');
  $(`.nav-item[data-view="${name}"]`).classList.add('active');
  $('#view-title').textContent = VIEW_META[name].title;
  $('#view-sub').textContent = VIEW_META[name].sub;
}

/* ---------- 倒计时 ---------- */
function renderCountdown() {
  const examDate = new Date(EXAM_GOAL.exam_date);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = daysBetween(today, examDate);
  $('#exam-countdown').textContent = days > 0 ? `距考试 ${days} 天` : (days === 0 ? '今天考试！' : `考试已过 ${-days} 天`);
}

/* ---------- 仪表盘 ---------- */
function renderDashboard() {
  // 读取测评档案
  let profile = null;
  try {
    const saved = localStorage.getItem('alimigo_profile');
    if (saved) profile = JSON.parse(saved);
  } catch (e) { /* ignore */ }

  // 目标分大字
  $('#goal-total').textContent = EXAM_GOAL.target_total;
  const minPart = Math.min(...Object.values(EXAM_GOAL.target_parts));
  $('#goal-minpart').textContent = minPart;

  // 目标分
  const tgt = $('#score-targets');
  tgt.innerHTML = Object.entries(EXAM_GOAL.target_parts).map(([k, v]) =>
    `<div class="score-cell"><div class="label">${partLabel(k)}</div><div class="val">${v}</div></div>`
  ).join('');

  // 当前分大字 + 差距
  $('#current-total').textContent = CURRENT_SCORE.total;
  const gapTotal = (EXAM_GOAL.target_total - CURRENT_SCORE.total).toFixed(1);
  $('#score-gap').textContent = gapTotal;

  // 当前分
  const cur = $('#score-current');
  cur.innerHTML = ['L', 'R', 'W', 'S'].map(k =>
    `<div class="score-cell"><div class="label">${partLabel(k)}</div><div class="val">${CURRENT_SCORE[k]}</div></div>`
  ).join('');

  // 渲染基本信息（问卷）
  const profileInfo = $('#profile-info');
  if (profile && profile.questionnaire) {
    const q = profile.questionnaire;
    profileInfo.innerHTML = `
      <div>📅 考试日期：<b>${q.examDate || '未设置'}</b></div>
      <div>🌍 目标地区：<b>${q.region || '未设置'}</b></div>
      <div>⏱️ 每周投入：<b>${q.weeklyHours || 0} 小时</b></div>
      <div>📝 自评水平：<b>${q.selfRating || '未设置'}</b></div>
    `;
  } else {
    profileInfo.innerHTML = `<div class="muted">完成个性化测评后显示你的备考信息</div>`;
  }

  // 渲染测评结果信息
  const currentInfo = $('#current-info');
  if (profile) {
    const weakestName = partLabel(profile.weakest) || '写作';
    currentInfo.innerHTML = `
      <div>📚 词汇量：约 <b>${profile.vocabulary || '—'} 词</b></div>
      <div>⚠️ 薄弱分科：<b style="color:var(--red)">${weakestName}</b></div>
    `;
  } else {
    currentInfo.innerHTML = `<div class="muted">未完成测评</div>`;
  }

  // 目标院校
  $('#school-list').innerHTML = SCHOOLS.map(s => `
    <li>
      <span>${s.name}</span>
      <span class="muted">总分 ≥${s.min_total} · <span class="code">${s.ielts_code}</span></span>
    </li>
  `).join('');

  // 差距分析条
  const gap = Scheduler.getGap();
  const bars = $('#gap-bars');
  const maxVal = 9;
  bars.innerHTML = ['L', 'R', 'W', 'S'].map(k => {
    const target = EXAM_GOAL.target_parts[k];
    const current = CURRENT_SCORE[k];
    const diff = (target - current).toFixed(1);
    const color = diff > 1 ? '#ef4444' : diff > 0.5 ? '#f59e0b' : '#10b981';
    return `
      <div class="gap-row">
        <div class="gap-label">
          <span>${partLabel(k)} <span class="muted">目标 ${target} · 当前 ${current}</span></span>
          <span style="color:${color};font-weight:600">${diff > 0 ? '差 ' + diff : '已达标'}</span>
        </div>
        <div class="gap-bar">
          <div class="gap-bar-fill target" style="width:${(target / maxVal) * 100}%"></div>
          <div class="gap-bar-fill current" style="width:${(current / maxVal) * 100}%"></div>
        </div>
      </div>
    `;
  }).join('');

  // 今日任务（优先读真实后端计划，无则显示默认）
  $('#today-date').textContent = fmtDate(new Date());
  let todayTasks = [];
  const remotePlan = window.__remotePlan;
  const todayStr = fmtDate(new Date());
  if (remotePlan && remotePlan.tasks) {
    todayTasks = remotePlan.tasks
      .filter(t => t.start.slice(0, 10) === todayStr)
      .map(t => ({
        category: SEC_TO_CAT[t.section] || 'vocab',
        title: t.title,
        start: t.start.slice(11, 16),
        end: t.end.slice(11, 16),
        minutes: t.durationMinutes,
      }));
  }
  if (todayTasks.length === 0) {
    todayTasks = [
      { category: 'vocab', title: '高频词 500 背诵', start: '20:00', end: '21:00', minutes: 60 },
      { category: 'writing', title: 'Task 2 议论文', start: '21:00', end: '22:00', minutes: 60 },
    ];
  }
  $('#today-tasks').innerHTML = todayTasks.map(t => `
    <li>
      <span class="task-cat ${CATEGORIES[t.category].cls}">${CATEGORIES[t.category].name}</span>
      <div class="task-info">
        <div class="t-title">${t.title}</div>
        <div class="t-meta">${t.start} - ${t.end} · ${t.minutes} 分钟</div>
      </div>
    </li>
  `).join('');
}

function partLabel(k) {
  return { L: '听力', R: '阅读', W: '写作', S: '口语' }[k];
}

/* ---------- 时间资源页 ---------- */
function initSchedulePage() {
  $('#btn-clear-schedule').addEventListener('click', () => {
    Schedule.clear();
    toast('已清空全部时段');
  });

  // 精确录入：循环规则切换（周循环=选星期；一次性=选日期）
  $('#pf-recurrence').addEventListener('change', (e) => {
    const once = e.target.value === 'ONCE';
    $('#pf-day-wrap').style.display = once ? 'none' : '';
    $('#pf-date-wrap').style.display = once ? '' : 'none';
  });
  $('#pf-add').addEventListener('click', () => {
    const recurrence = $('#pf-recurrence').value;
    const ok = Schedule.addPreciseBlock({
      recurrence,
      dayOfWeek: +$('#pf-day').value,
      date: $('#pf-date').value,
      startTime: $('#pf-start').value,
      endTime: $('#pf-end').value,
      typeKey: $('#pf-type').value,
      title: $('#pf-title').value.trim(),
    });
    if (ok) toast('已添加时段（保存后生效到排程）');
  });

  $('#btn-save-schedule').addEventListener('click', async () => {
    const blocks = collectBusyBlocks();
    try {
      if (window.AmiAPI && window.AmiAPI.ensureDemoLogin) {
        await window.AmiAPI.ensureDemoLogin();
      }
      const resp = await fetch('/api/v1/schedule', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ blocks }),
      });
      const json = await resp.json();
      if (json.success) {
        toast(`已保存 ${blocks.length} 个时段块，AI 排程将按类型标签分配任务`);
      } else {
        toast('保存失败：' + (json.message || '未知错误'));
      }
    } catch (e) {
      toast('保存失败：' + e.message);
    }
  });
}

/* ---------- AI 排程页 ---------- */

/** 分钟数 → "HH:MM"（1440 分钟即 24:00，落库时收敛为 23:59）。 */
function toHM(mins) {
  if (mins >= 24 * 60) return '23:59';
  const h = String(Math.floor(mins / 60)).padStart(2, '0');
  const m = String(mins % 60).padStart(2, '0');
  return `${h}:${m}`;
}

/** 把时间资源状态合并成后端 BlockInput 列表（周网格分钟级合并 + 一次性临时事件）。 */
function collectBusyBlocks() {
  const blocks = [];
  const state = window.Schedule.state();
  const types = window.Schedule.types;
  const MINS = window.Schedule.minutesPerDay;

  // 周循环：同一天的同类型连续分钟合并为一个块
  for (let d = 0; d < 7; d++) {
    const row = state[d] || [];
    let runStart = -1, runType = 0;
    for (let m = 0; m <= MINS; m++) {
      const t = m < MINS ? row[m] : 0;
      if (t !== 0 && t === runType) continue;      // 延续当前 run
      if (runType !== 0) {                          // 结束一个 run
        blocks.push({
          dayOfWeek: d + 1, // JS 0=周一 → 后端 1=周一
          startTime: toHM(runStart),
          endTime: toHM(m),
          blockType: types[runType - 1].key,
          title: types[runType - 1].name,
          recurrence: 'WEEKLY',
        });
      }
      runStart = t !== 0 ? m : -1;
      runType = t;
    }
  }

  // 一次性临时事件
  (window.Schedule.onceEvents() || []).forEach(ev => {
    blocks.push({
      dayOfWeek: ev.dayOfWeek,
      startTime: toHM(ev.startMin),
      endTime: toHM(ev.endMin),
      blockType: types[ev.typeIdx - 1].key,
      title: ev.title || types[ev.typeIdx - 1].name,
      recurrence: 'ONCE',
      specificDate: ev.date,
    });
  });

  return blocks;
}

function initPlanPage() {
  $('#cfg-exam-date').value = EXAM_GOAL.exam_date;
  $('#btn-generate').addEventListener('click', () => generateRemotePlan());
}

/** 调真实 Java 排程引擎并渲染。 */
async function generateRemotePlan() {
  const btn = $('#btn-generate');
  btn.disabled = true;
  btn.textContent = '引擎排程中…';
  try {
    if (window.AmiAPI && window.AmiAPI.ensureDemoLogin) {
      await window.AmiAPI.ensureDemoLogin();
    }
    const resp = await fetch('/api/v1/planner/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        examType: 'IELTS',
        examDate: $('#cfg-exam-date').value,
        weeklyHours: +$('#cfg-hours').value,
      }),
    });
    const json = await resp.json();
    if (!json.success) {
      throw new Error(json.message || '生成失败');
    }
    window.__remotePlan = json.data;
    renderRemotePlan(json.data);
    syncTodayTasks();
    toast(`计划已生成：${json.data.taskCount} 个任务，点击任务可勾选完成`);
  } catch (e) {
    toast('生成失败：' + e.message);
  } finally {
    btn.disabled = false;
    btn.textContent = '生成备考计划';
  }
}

/** 页面加载时：已有计划则展示，否则自动生成一次。 */
async function bootstrapRemotePlan() {
  try {
    const resp = await fetch('/api/v1/planner/current');
    const json = await resp.json();
    if (json.success && json.data && json.data.taskCount > 0) {
      window.__remotePlan = json.data;
      renderRemotePlan(json.data);
      syncTodayTasks();
      return;
    }
  } catch (e) { /* ignore，继续自动生成 */ }
  await generateRemotePlan();
}

/** 勾选 / 取消勾选任务（真实落库）。 */
async function toggleRemoteTask(taskId) {
  try {
    const resp = await fetch(`/api/v1/planner/tasks/${taskId}/toggle`, { method: 'POST' });
    const json = await resp.json();
    if (json.success && json.data) {
      window.__remotePlan = json.data;
      renderRemotePlan(json.data);
      syncTodayTasks();
    }
  } catch (e) {
    toast('操作失败：' + e.message);
  }
}

/** 把远程计划同步给仪表盘「今日任务」。 */
function syncTodayTasks() {
  renderDashboard();
}

const SEC_TO_CAT = { L: 'listening', R: 'reading', W: 'writing', S: 'speaking' };
const STAGE_NAME = { FOUNDATION: '基础期', INTENSIVE: '强化期', SPRINT: '冲刺期' };

/** 渲染真实后端返回的扁平任务：按周 → 按日分组。 */
function renderRemotePlan(data) {
  const out = $('#plan-output');
  out.innerHTML = '';

  // 本周目标横幅
  if (data.weeklyGoal) {
    const banner = document.createElement('div');
    banner.className = 'warnings';
    banner.style.background = '#eff6ff';
    banner.style.color = '#1d4ed8';
    banner.textContent = '🎯 ' + data.weeklyGoal;
    out.appendChild(banner);
  }
  if (data.warnings && data.warnings.length) {
    const warn = document.createElement('div');
    warn.className = 'warnings';
    warn.innerHTML = `⚠️ ${data.warnings.join('<br>⚠️ ')}`;
    out.appendChild(warn);
  }

  const todayStr = fmtDate(new Date());
  // weekIndex -> (date -> tasks)
  const weekMap = new Map();
  for (const t of data.tasks) {
    const d = t.start.slice(0, 10);
    const diff = daysBetween(new Date(todayStr), new Date(d));
    const wIdx = Math.max(0, Math.floor(diff / 7));
    if (!weekMap.has(wIdx)) weekMap.set(wIdx, new Map());
    const dm = weekMap.get(wIdx);
    if (!dm.has(d)) dm.set(d, []);
    dm.get(d).push(t);
  }

  [...weekMap.entries()].sort((a, b) => a[0] - b[0]).forEach(([wIdx, dm]) => {
    const stages = new Set();
    let count = 0;
    dm.forEach(v => v.forEach(t => { stages.add(t.stage); count++; }));
    const stageText = [...stages].map(s => STAGE_NAME[s] || s).join('/');

    const weekEl = document.createElement('div');
    weekEl.className = 'week-block';
    weekEl.innerHTML = `
      <div class="week-title">
        <span>第 ${wIdx + 1} 周 · ${stageText}</span>
        <span class="stage">${count} 个任务</span>
      </div>
    `;

    [...dm.entries()].sort((a, b) => a[0].localeCompare(b[0])).forEach(([d, tasks]) => {
      const dow = (new Date(d).getDay() + 6) % 7; // 0=周一
      const dayEl = document.createElement('div');
      dayEl.className = 'day-row';

      const chipsHtml = tasks
        .slice().sort((a, b) => a.start.localeCompare(b.start))
        .map(t => {
          const cat = SEC_TO_CAT[t.section] || 'vocab';
          const opacity = t.done ? '0.55' : '1';
          const deco = t.done ? 'text-decoration:line-through;' : '';
          return `
            <span class="task-chip" data-task-id="${t.taskId}"
              style="background:${catBg(cat)};color:${catFg(cat)};opacity:${opacity};${deco}cursor:pointer"
              title="点击${t.done ? '取消勾选' : '勾选完成'}">
              <span class="time">${t.start.slice(11, 16)}-${t.end.slice(11, 16)}</span>
              ${CATEGORIES[cat] ? CATEGORIES[cat].name : t.section}·${t.title}${t.done ? ' ✅' : ''}
            </span>
          `;
        }).join('');

      dayEl.innerHTML = `
        <div class="day-label">${DAYS[dow]}<br><span class="muted" style="font-size:11px">${d.slice(5)}</span></div>
        <div class="day-blocks">${chipsHtml || '<span class="muted" style="font-size:12px">无安排</span>'}</div>
      `;
      weekEl.appendChild(dayEl);
    });

    out.appendChild(weekEl);
  });

  // 点击任务 → toggle
  out.querySelectorAll('.task-chip[data-task-id]').forEach(chip => {
    chip.addEventListener('click', () => toggleRemoteTask(chip.dataset.taskId));
  });
}

function renderPlan(result) {
  const out = $('#plan-output');
  if (result.warnings && result.warnings.length) {
    out.innerHTML = `<div class="warnings">⚠️ ${result.warnings.join('<br>⚠️ ')}</div>`;
  } else {
    out.innerHTML = '';
  }

  result.weeks.forEach(w => {
    const weekEl = document.createElement('div');
    weekEl.className = 'week-block';
    weekEl.innerHTML = `
      <div class="week-title">
        <span>第 ${w.week_index} 周 · ${w.stage} 阶段</span>
        <span class="stage">${w.days.reduce((a, d) => a + d.blocks.length, 0)} 个任务</span>
      </div>
    `;
    w.days.forEach(d => {
      const dayEl = document.createElement('div');
      dayEl.className = 'day-row';
      const blocksHtml = d.blocks.length
        ? d.blocks.map(b => `
          <span class="task-chip" style="background:${catBg(b.category)};color:${catFg(b.category)}">
            <span class="time">${b.start}-${b.end}</span>
            ${CATEGORIES[b.category].name}·${b.title}
          </span>
        `).join('')
        : '<span class="muted" style="font-size:12px">无安排</span>';
      dayEl.innerHTML = `
        <div class="day-label">${d.dayName}<br><span class="muted" style="font-size:11px">${d.date.slice(5)}</span></div>
        <div class="day-blocks">${blocksHtml}</div>
      `;
      weekEl.appendChild(dayEl);
    });
    out.appendChild(weekEl);
  });

  if (result.weeks.length === 0 && result.warnings.length === 0) {
    out.innerHTML = '<div class="empty-hint">无法生成计划，请检查考试日期。</div>';
  }
}

function catBg(cat) {
  return { vocab: '#ede9fe', listening: '#dbeafe', reading: '#d1fae5', writing: '#ffedd5', speaking: '#fce7f3', mock: '#fee2e2' }[cat];
}
function catFg(cat) {
  return { vocab: '#6d28d9', listening: '#1d4ed8', reading: '#047857', writing: '#c2410c', speaking: '#be185d', mock: '#b91c1c' }[cat];
}

/* ---------- 时间线 ---------- */
function renderTimeline() {
  const examDate = new Date(EXAM_GOAL.exam_date);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tl = $('#timeline');
  tl.innerHTML = TIMELINE_EVENTS.map(ev => {
    const evDate = addDays(examDate, ev.offset);
    const diff = daysBetween(today, evDate);
    let cls = 'future';
    if (diff < 0) cls = 'done';
    else if (diff <= 1) cls = 'active';
    const rel = diff > 0 ? `还有 ${diff} 天` : diff === 0 ? '就是今天' : `已过 ${-diff} 天`;
    return `
      <div class="tl-item ${cls}">
        <div class="tl-date">${fmtDate(evDate)} · ${rel}</div>
        <div class="tl-title">${ev.key} · ${ev.title}</div>
        <div class="tl-desc">${ev.desc}</div>
      </div>
    `;
  }).join('');
}

/* ---------- Checklist ---------- */
let checklistState = [];

function initChecklist() {
  // 展平为带 category 的项
  checklistState = [];
  CHECKLIST_TEMPLATE.forEach(g => {
    g.items.forEach((it, idx) => {
      checklistState.push({ ...it, category: g.category, checked: false, id: `${g.category}-${idx}` });
    });
  });
  renderChecklist();
}

function renderChecklist() {
  const groups = {};
  checklistState.forEach(it => {
    if (!groups[it.category]) groups[it.category] = [];
    groups[it.category].push(it);
  });

  const container = $('#checklist-groups');
  container.innerHTML = Object.entries(groups).map(([cat, items]) => `
    <div class="checklist-group">
      <h4>${cat}</h4>
      ${items.map(it => `
        <div class="cl-item ${it.checked ? 'done' : ''}">
          <input type="checkbox" ${it.checked ? 'checked' : ''} data-id="${it.id}" />
          <div>
            <div class="cl-title">${it.title}</div>
            <div class="cl-desc">${it.desc}</div>
          </div>
        </div>
      `).join('')}
    </div>
  `).join('');

  container.querySelectorAll('input[type="checkbox"]').forEach(cb => {
    cb.addEventListener('change', () => {
      const item = checklistState.find(i => i.id === cb.dataset.id);
      if (item) item.checked = cb.checked;
      updateChecklistProgress();
    });
  });

  updateChecklistProgress();
}

function updateChecklistProgress() {
  const done = checklistState.filter(i => i.checked).length;
  const total = checklistState.length;
  const pct = Math.round(done / total * 100);
  $('#checklist-progress').textContent = `${done}/${total} (${pct}%)`;
}

/* ---------- 听说读写 ---------- */
let currentSection = 'R';

function initPractice() {
  renderPractice('R');
  $$('#practice-tabs .tab').forEach(tab => {
    tab.addEventListener('click', () => {
      $$('#practice-tabs .tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      currentSection = tab.dataset.sec;
      renderPractice(currentSection);
    });
  });
}

function renderPractice(sec) {
  const body = $('#practice-body');
  if (sec === 'R') renderReading(body);
  else if (sec === 'W') renderWriting(body);
  else if (sec === 'L') renderListening(body);
  else renderSpeaking(body);
}

function renderReading(body) {
  body.innerHTML = `
    <div class="reading-passage">${READING_Q.passage}</div>
    <div id="reading-questions">
      ${READING_Q.questions.map((q, i) => `
        <div class="q-item" data-q="${i}">
          <div class="q-text">${i + 1}. ${q.text}</div>
          <div class="q-options">
            ${q.options.map((opt, j) => `<div class="q-opt" data-opt="${j}">${String.fromCharCode(65 + j)}. ${opt}</div>`).join('')}
          </div>
          <div class="q-feedback" style="margin-top:6px;font-size:12px;"></div>
        </div>
      `).join('')}
    </div>
    <button class="btn btn-primary" id="btn-submit-reading" style="margin-top:12px">提交判分</button>
    <div id="reading-result" style="margin-top:14px;"></div>
  `;

  const selected = {};
  body.querySelectorAll('.q-opt').forEach(opt => {
    opt.addEventListener('click', () => {
      const qIdx = opt.closest('.q-item').dataset.q;
      opt.closest('.q-item').querySelectorAll('.q-opt').forEach(o => o.classList.remove('selected'));
      opt.classList.add('selected');
      selected[qIdx] = +opt.dataset.opt;
    });
  });

  $('#btn-submit-reading').addEventListener('click', () => {
    let correct = 0;
    READING_Q.questions.forEach((q, i) => {
      const item = body.querySelector(`.q-item[data-q="${i}"]`);
      const opts = item.querySelectorAll('.q-opt');
      opts.forEach(o => o.classList.remove('selected', 'correct', 'wrong'));
      if (selected[i] === q.answer) {
        opts[q.answer].classList.add('correct');
        correct++;
      } else {
        if (selected[i] !== undefined) opts[selected[i]].classList.add('wrong');
        opts[q.answer].classList.add('correct');
      }
    });
    const total = READING_Q.questions.length;
    $('#reading-result').innerHTML = `
      <div class="score-box">
        <div>阅读得分：<span class="overall">${correct}/${total}</span> · 正确率 ${Math.round(correct / total * 100)}%</div>
        <div class="muted" style="margin-top:6px">客观题自动判分完成。</div>
      </div>
    `;
  });
}

function renderWriting(body) {
  body.innerHTML = `
    <div style="margin-bottom:12px;">
      <div style="font-weight:600;margin-bottom:6px;">写作题目</div>
      <div class="reading-passage" style="font-size:13px;">${WRITING_PROMPT}</div>
    </div>
    <textarea class="writing-area" id="writing-input" placeholder="在此输入你的作文（200-300 字）..."></textarea>
    <div style="display:flex;align-items:center;gap:12px;margin-top:10px;">
      <button class="btn btn-primary" id="btn-score-writing">AI 评分</button>
      <input type="file" id="writing-image-input" accept="image/*" hidden>
      <button class="btn" id="btn-upload-writing">上传图片评分</button>
      <span class="muted" id="word-count">0 字</span>
    </div>
    <div id="writing-result"></div>
  `;

  const ta = $('#writing-input');
  ta.addEventListener('input', () => {
    $('#word-count').textContent = `${ta.value.trim().length} 字`;
  });

  $('#btn-score-writing').addEventListener('click', async () => {
    const essay = ta.value.trim();
    if (essay.length < 50) { toast('作文过短，请至少输入 50 字'); return; }
    const btn = $('#btn-score-writing');
    btn.textContent = 'AI 评分中...';
    btn.disabled = true;
    try {
      const score = await scoreWritingRemote(essay, WRITING_PROMPT);
      const offlineTag = score._source === 'mock' ? ' <span style="color:#888;font-size:12px;">（离线模拟）</span>' : '';
      $('#writing-result').innerHTML = `
        <div class="score-box">
          <div>总分：<span class="overall">${score.overall}</span> / 9${offlineTag}</div>
          <div class="score-breakdown">
            ${Object.entries(score.breakdown).map(([k, v]) => `
              <div class="score-bd"><div class="bd-label">${k}</div><div class="bd-val">${v}</div></div>
            `).join('')}
          </div>
          <div style="margin-top:8px;"><b>改进建议：</b></div>
          <ul style="margin-top:4px;padding-left:20px;font-size:13px;">
            ${(score.next_actions && score.next_actions.length ? score.next_actions : ['（本次未生成具体建议，可再试一次）']).map(a => `<li>${a}</li>`).join('')}
          </ul>
        </div>
      `;
    } catch (e) {
      $('#writing-result').innerHTML = `<div style="color:#c00;">评分失败：${e.message}</div>`;
    } finally {
      btn.textContent = 'AI 评分';
      btn.disabled = false;
    }
  });

  // ---------- 图片上传评分（多模态识别 + 评分） ----------
  $('#btn-upload-writing').addEventListener('click', () => $('#writing-image-input').click());
  $('#writing-image-input').addEventListener('change', async (ev) => {
    const file = ev.target.files[0];
    if (!file) return;
    const btn = $('#btn-upload-writing');
    const wc = $('#word-count');
    if (wc) wc.style.display = 'none'; // 图片评分时隐藏"0 字"计数器
    btn.textContent = 'AI 识别中，约需 2-5 分钟...';
    btn.disabled = true;
    try {
      // FileReader 转 base64 并剥掉 data:image/*;base64, 前缀（后端只收裸 base64）
      const dataUrl = await new Promise((resolve, reject) => {
        const fr = new FileReader();
        fr.onload = () => resolve(fr.result);
        fr.onerror = () => reject(new Error('图片读取失败'));
        fr.readAsDataURL(file);
      });
      const imageBase64 = String(dataUrl).split(',')[1] || '';
      if (!imageBase64) throw new Error('图片转 base64 失败');
      // 'image/png' -> 'png'，默认 jpeg
      const imageType = (file.type.split('/')[1] || 'jpeg').replace('jpg', 'jpeg');
      const r = await window.AmiAPI.callWritingGradeImage({
        imageBase64,
        topic: WRITING_PROMPT,
        examType: 'IELTS_A',
        level: 'Academic',
        imageType,
      });
      const sentencesNote = (r.sentences && r.sentences.length)
        ? ''
        : '<div class="muted" style="margin-top:8px;font-size:12px;">图片模式暂不支持逐句批改</div>';
      $('#writing-result').innerHTML = `
        <details open style="margin-bottom:12px;">
          <summary style="cursor:pointer;color:#666;font-size:13px;">AI 识别出的原文（请核对）</summary>
          <pre style="white-space:pre-wrap;font-size:13px;line-height:1.6;margin-top:6px;">${r.recognizedText}</pre>
        </details>
        <div class="score-box">
          <div>总分：<span class="overall">${r.overallText}</span> / 9${r.band ? ` <span style="color:#888;font-size:12px;">（${r.band}）</span>` : ''}</div>
          <div class="score-breakdown">
            ${Object.entries(r.breakdown).map(([k, v]) => `
              <div class="score-bd"><div class="bd-label">${k}</div><div class="bd-val">${v}</div></div>
            `).join('')}
          </div>
          <div style="margin-top:8px;"><b>改进建议：</b></div>
          <ul style="margin-top:4px;padding-left:20px;font-size:13px;">
            ${(r.nextActions && r.nextActions.length ? r.nextActions : ['（本次未生成具体建议，可再试一次）']).map(a => `<li>${a}</li>`).join('')}
          </ul>
          ${sentencesNote}
        </div>
      `;
    } catch (e) {
      console.warn('[图片评分失败]', e.message);
      $('#writing-result').innerHTML = `<div style="color:#c00;">图片识别失败，请重试或手动输入作文（${e.message}）</div>`;
    } finally {
      btn.textContent = '上传图片评分';
      btn.disabled = false;
      if (wc) wc.style.display = '';
      ev.target.value = ''; // 允许再次选择同一文件
    }
  });
}

function mockWritingScore(essay) {
  const len = essay.length;
  const sentences = (essay.match(/[.!?。！？]/g) || []).length;
  const avgLen = sentences ? len / sentences : 0;
  // 简单启发式评分
  const base = Math.min(6 + (len > 250 ? 1 : 0) + (avgLen > 12 ? 0.5 : 0), 7.5);
  const tr = Math.round((base + (len > 200 ? 0.5 : 0)) * 2) / 2;
  const cc = Math.round((base - 0.5 + (sentences > 8 ? 0.5 : 0)) * 2) / 2;
  const lr = Math.round((base - 0.5 + (avgLen > 10 ? 0.5 : 0)) * 2) / 2;
  const gra = Math.round((base - 0.5 + (sentences > 10 ? 0.5 : 0)) * 2) / 2;
  const overall = ((tr + cc + lr + gra) / 4).toFixed(1);
  return {
    overall,
    breakdown: { TR: tr, CC: cc, LR: lr, GRA: gra },
    next_actions: [
      '增加复合句与连接词提升 CC',
      '替换基础词汇为学术表达提升 LR',
      '检查主谓一致与时态提升 GRA',
    ],
  };
}

async function scoreWritingRemote(essay, topic) {
  try {
    const r = await window.AmiAPI.callWritingGrade({
      content: essay,
      topic: topic || 'IELTS Writing Task 2',
      examType: 'IELTS_A',
      level: 'Academic',
    });
    return {
      overall: r.overallText,          // app.js 要字符串
      breakdown: r.breakdown,          // 字段名已映射
      next_actions: r.nextActions,
      band: r.band,
      _source: 'ai',
    };
  } catch (e) {
    console.warn('[AI fallback]', e.message);
    const m = await mockWritingScore(essay);
    m._source = 'mock';
    return m;
  }
}

function renderListening(body) {
  body.innerHTML = `
    <div class="empty-hint">
      听力练习（MVP）<br><br>
      官方真题音频外链到 BC/YouTube 公开资源，系统记录播放进度。<br>
      练习形式：听写填空 + 关键词捕捉，客观题自动判分。
    </div>
  `;
}

function renderSpeaking(body) {
  body.innerHTML = `
    <div class="empty-hint">
      口语练习（MVP）<br><br>
      官方真题 Part 1/2/3 题卡 → 用户录音 → ASR 转写 → LLM 评分。<br>
      评分维度：流利度 / 词汇 / 语法 / 发音，0-9 分 + 改进建议。<br>
      <span class="muted">（Demo 暂未接入麦克风与 ASR，实际产品中走 AI 网关 zone=lang）</span>
    </div>
  `;
}

/* ---------- 启动 ---------- */
document.addEventListener('DOMContentLoaded', () => {
  // 导航
  $$('.nav-item').forEach(item => {
    item.addEventListener('click', () => switchView(item.dataset.view));
  });

  Schedule.init();
  initSchedulePage();
  initPlanPage();
  initChecklist();
  initPractice();

  // 重新测评按钮
  $('#btn-reassess').addEventListener('click', () => {
    Onboarding.restart();
  });

  // 启动测评引导（内部检查 localStorage，已有 profile 则直接应用并隐藏）
  Onboarding.start();

  // 同步考试日期到排程配置（profile 可能覆盖了默认日期）
  $('#cfg-exam-date').value = EXAM_GOAL.exam_date;

  // 渲染主页（测评完成或已有 profile 时显示）
  renderCountdown();
  renderDashboard();
  renderTimeline();

  // 默认拉取真实计划（未登录先静默登录演示账号；无计划则引擎实时生成）
  (async () => {
    if (window.AmiAPI && window.AmiAPI.ensureDemoLogin) {
      try { await window.AmiAPI.ensureDemoLogin(); } catch (e) { /* 提示由接口处理 */ }
    }
    // 登录后回填该用户已保存的课表；新用户课表默认空白，由用户自己点选时间
    await Schedule.loadRemote();
    // 优先拉取长周期规划；无周期规划时再回退到旧版 AI 排程自动生成
    let hasCycle = false;
    if (window.CyclePlan) {
      try { hasCycle = await window.CyclePlan.bootstrap(); } catch (e) { /* ignore */ }
    }
    if (!hasCycle) await bootstrapRemotePlan();
  })();
});
