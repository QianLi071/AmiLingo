/* ============================================================
 * schedule.js — 时间资源模块
 * 快速录入：24 小时 × 每小时 60 个分钟格（CSS 渐变渲染），
 *           粒度可选 15/30/60 分钟，选择类型后点击涂抹；
 * 精确录入：分钟级起止时间表单（含一次性临时事件）。
 * 时段类型标签供 AI 排程匹配任务难度。
 * ============================================================ */

// 时段类型定义（下标 1-5 存入状态数组；0 = 空）
const BLOCK_TYPES = [
  { key: 'UNAVAILABLE', icon: '🔴', name: '完全不可用', hint: '上课/会议，禁排任务', color: '#fee2e2' },
  { key: 'FRAGMENT', icon: '🟡', name: '碎片琐事', hint: '通勤/吃饭，轻量短任务', color: '#fef3c7' },
  { key: 'DEEP', icon: '🟢', name: '高效深度学习', hint: '连续长时段，高难度任务', color: '#dcfce7' },
  { key: 'FREE', icon: '⚪', name: '普通空闲', hint: '中等强度任务', color: '#f1f5f9' },
  { key: 'REST', icon: '🟣', name: '休息时段', hint: '强制休息，不分配任务', color: '#ede9fe' },
];
// 不计入「可排程时间」的类型
const NON_FREE_TYPES = ['UNAVAILABLE', 'FRAGMENT', 'REST'];

const MINUTES_PER_DAY = 24 * 60;   // 状态粒度：1 格 = 1 分钟
const HOUR_CELL_PX = 120;          // 每小时格高度（60 分钟 × 2px）

// scheduleState[dayIndex][minuteOfDay] = 0(空) | 1-5(类型下标)
let scheduleState = [];
// 一次性临时事件（不进周网格，单独列表展示）
let onceEvents = []; // {dayOfWeek, date, startMin, endMin, typeIdx, title}
// 快速录入工具栏状态
let granularity = 30;  // 每次点击填充的分钟数：15 / 30 / 60
let paintType = 1;     // 当前涂抹类型：1-5，0 = 橡皮擦

function initScheduleState() {
  scheduleState = [];
  for (let d = 0; d < 7; d++) scheduleState.push(new Array(MINUTES_PER_DAY).fill(0));
  onceEvents = [];
}

// 分钟数 → "HH:MM"
function hm(mins) {
  const h = String(Math.floor(mins / 60)).padStart(2, '0');
  const m = String(mins % 60).padStart(2, '0');
  return `${h}:${m}`;
}

// "HH:MM" → 当日分钟数
function parseHM(hhmm) {
  const [h, m] = String(hhmm).split(':').map(Number);
  return (h || 0) * 60 + (m || 0);
}

function typeKeyToIdx(key) {
  const i = BLOCK_TYPES.findIndex(t => t.key === key);
  return i >= 0 ? i + 1 : 4; // 默认 FREE
}

/* ---------- 冲突检测 ---------- */
// 在指定日期的分钟区间内找与既有类型块的重叠，返回冲突描述或 null
function findConflict(dayIdx, startMin, endMin) {
  const row = scheduleState[dayIdx];
  for (let m = startMin; m < endMin && m < MINUTES_PER_DAY; m++) {
    if (row[m] !== 0) {
      return { time: hm(m), type: BLOCK_TYPES[row[m] - 1] };
    }
  }
  return null;
}

/* ---------- 远端数据 ---------- */
// 用后端返回的时间块回填（WEEKLY → 分钟网格；ONCE → 临时事件列表）
function applyBlocks(blocks) {
  initScheduleState();
  (blocks || []).forEach(b => {
    const dayIdx = (Number(b.dayOfWeek) || 0) - 1; // 后端 1=周一 → 网格 0=周一
    if (dayIdx < 0 || dayIdx > 6) return;
    const sMin = parseHM(b.startTime);
    const eMin = parseHM(b.endTime);
    const typeIdx = typeKeyToIdx(b.blockType);
    if (b.recurrence === 'ONCE') {
      onceEvents.push({ dayOfWeek: b.dayOfWeek, date: b.specificDate || '', startMin: sMin, endMin: eMin, typeIdx, title: b.title || '' });
      return;
    }
    for (let m = sMin; m < eMin && m < MINUTES_PER_DAY; m++) scheduleState[dayIdx][m] = typeIdx;
  });
}

// 登录后拉取该用户已保存的时间资源；从未保存过时保持空白
async function loadRemoteSchedule() {
  try {
    const resp = await fetch('/api/v1/schedule');
    const json = await resp.json();
    if (json.success && json.data && Array.isArray(json.data.days)) {
      const blocks = json.data.days.flatMap(d =>
        (d.blocks || []).map(b => ({ ...b, dayOfWeek: d.dayOfWeek })));
      applyBlocks(blocks);
      renderSchedule();
    }
  } catch (e) {
    console.warn('[时间资源] 拉取已保存数据失败，保持空白：', e.message);
  }
}

/* ---------- 渲染 ---------- */
// 把某天某小时的 60 分钟状态渲染成纵向渐变（每 2px 一格）
// 注意：必须为空白段显式生成 transparent 色标 —— CSS 渐变会把
// 第一个色标之前 / 最后一个色标之后的区域自动填成首末色标颜色，
// 只写有色段会导致整个小时格都被染色。
function buildHourGradient(dayIdx, hour) {
  const base = hour * 60;
  const stops = [];
  let cur = scheduleState[dayIdx][base], start = 0;
  let anyFilled = cur > 0;
  for (let m = 1; m <= 60; m++) {
    const t = m < 60 ? scheduleState[dayIdx][base + m] : -1;
    if (t !== cur) {
      const s = (start / 60 * 100).toFixed(2), e = (m / 60 * 100).toFixed(2);
      stops.push(cur > 0 ? `${BLOCK_TYPES[cur - 1].color} ${s}% ${e}%` : `transparent ${s}% ${e}%`);
      if (cur > 0) anyFilled = true;
      cur = t; start = m;
    }
  }
  return anyFilled ? `linear-gradient(to bottom, ${stops.join(', ')})` : '';
}

function renderSchedule() {
  const grid = $('#schedule-grid');
  let html = `<div class="sched-head time">时间</div>`;
  DAYS.forEach(d => { html += `<div class="sched-head">${d}</div>`; });

  for (let h = 0; h < 24; h++) {
    html += `<div class="sched-hour-label">${String(h).padStart(2, '0')}:00</div>`;
    for (let d = 0; d < 7; d++) {
      const bg = buildHourGradient(d, h);
      html += `<div class="sched-hour" data-day="${d}" data-hour="${h}"${bg ? ` style="background:${bg}"` : ''}></div>`;
    }
  }
  grid.innerHTML = html;

  renderOnceList();
  updateSummary();
}

// 快速录入：点击/预览定位到的涂抹区间
function granuleRange(hour, offsetY) {
  const minuteInHour = Math.max(0, Math.min(59, Math.floor(offsetY / HOUR_CELL_PX * 60)));
  const startInHour = Math.floor(minuteInHour / granularity) * granularity;
  const startMin = hour * 60 + startInHour;
  return { startMin, endMin: Math.min(startMin + granularity, MINUTES_PER_DAY), startInHour };
}

function bindGridEvents() {
  const grid = $('#schedule-grid');

  grid.addEventListener('click', (e) => {
    const cell = e.target.closest('.sched-hour');
    if (!cell) return;
    const d = +cell.dataset.day, h = +cell.dataset.hour;
    const { startMin, endMin } = granuleRange(h, e.clientY - cell.getBoundingClientRect().top);
    for (let m = startMin; m < endMin; m++) scheduleState[d][m] = paintType;
    const bg = buildHourGradient(d, h);
    cell.style.background = bg || '';
    updateSummary();
  });

  // 悬停预览：显示将要涂抹的分钟区间
  grid.addEventListener('mousemove', (e) => {
    const cell = e.target.closest('.sched-hour');
    $$('.hover-preview').forEach(p => p.remove());
    if (!cell) return;
    const d = +cell.dataset.day, h = +cell.dataset.hour;
    const { startMin, endMin, startInHour } = granuleRange(h, e.clientY - cell.getBoundingClientRect().top);
    const t = BLOCK_TYPES[paintType - 1];
    cell.title = `${DAYS[d]} ${hm(startMin)}-${hm(endMin)} ${paintType ? `${t.icon}${t.name}` : '🧹 擦除'}`;
    const pv = document.createElement('div');
    pv.className = 'hover-preview';
    pv.style.top = `${startInHour * 2}px`;
    pv.style.height = `${(endMin - startMin) * 2}px`;
    if (paintType) pv.style.background = t.color;
    cell.appendChild(pv);
  });

  grid.addEventListener('mouseleave', () => $$('.hover-preview').forEach(p => p.remove()));
}

// 一次性临时事件列表
function renderOnceList() {
  const box = $('#once-list');
  if (!box) return;
  if (onceEvents.length === 0) {
    box.innerHTML = '<span class="muted">暂无一次性临时事件</span>';
    return;
  }
  box.innerHTML = onceEvents.map((ev, i) => {
    const t = BLOCK_TYPES[ev.typeIdx - 1];
    return `<span class="once-chip ${t.key.toLowerCase()}">
      ${t.icon} ${ev.date} ${hm(ev.startMin)}-${hm(ev.endMin)}${ev.title ? ' · ' + ev.title : ''}
      <button class="once-del" data-i="${i}" title="删除">×</button>
    </span>`;
  }).join('');
  $$('.once-del').forEach(btn => btn.addEventListener('click', () => {
    onceEvents.splice(+btn.dataset.i, 1);
    renderOnceList();
  }));
}

function updateSummary() {
  const mins = BLOCK_TYPES.map(() => 0);
  for (let d = 0; d < 7; d++) {
    for (let m = 0; m < MINUTES_PER_DAY; m++) {
      const t = scheduleState[d][m];
      if (t > 0) mins[t - 1]++;
    }
  }
  const schedulable = mins[2] + mins[3]; // DEEP + FREE 可排程总时长
  $('#schedule-summary').innerHTML = `
    ${BLOCK_TYPES.map((t, i) => `<span>${t.icon} ${t.name}：<b>${(mins[i] / 60).toFixed(1)} h</b></span>`).join('')}
    <span>🧠 可排程时长（🟢+⚪）：<b>${(schedulable / 60).toFixed(1)} h</b>/周</span>
  `;
}

/* ---------- 工具栏 ---------- */
function bindToolbar() {
  $$('.gran-btn').forEach(btn => btn.addEventListener('click', () => {
    granularity = +btn.dataset.g;
    $$('.gran-btn').forEach(b => b.classList.toggle('active', b === btn));
  }));
  $$('.type-btn').forEach(btn => btn.addEventListener('click', () => {
    paintType = +btn.dataset.t;
    $$('.type-btn').forEach(b => b.classList.toggle('active', b === btn));
  }));
}

/* ---------- 精确录入 ---------- */
// 供 app.js 调用；返回 true=成功，false=校验/冲突失败（已 toast）
function addPreciseBlock({ dayOfWeek, date, startTime, endTime, typeKey, title, recurrence }) {
  const sMin = parseHM(startTime);
  const eMin = parseHM(endTime);
  if (!(eMin > sMin)) { toast('结束时间必须晚于开始时间'); return false; }
  const typeIdx = typeKeyToIdx(typeKey);

  if (recurrence === 'ONCE') {
    if (!date) { toast('一次性事件请选择日期'); return false; }
    const dow = ((new Date(date + 'T00:00:00').getDay()) + 6) % 7 + 1; // JS 0=周日 → 1=周一…7=周日
    const dup = onceEvents.some(ev => ev.date === date && ev.startMin < eMin && ev.endMin > sMin);
    if (dup) { toast(`时间段冲突：${date} 该时段已有临时事件`); return false; }
    onceEvents.push({ dayOfWeek: dow, date, startMin: sMin, endMin: eMin, typeIdx, title: title || '' });
    renderOnceList();
    updateSummary();
    return true;
  }

  // 周循环：分钟级冲突校验后精确填充
  const conflict = findConflict(dayOfWeek - 1, sMin, eMin);
  if (conflict) {
    toast(`时间段冲突：${DAYS[dayOfWeek - 1]} ${conflict.time} 已有「${conflict.type.name}」时段`);
    return false;
  }
  for (let m = sMin; m < eMin; m++) scheduleState[dayOfWeek - 1][m] = typeIdx;
  renderSchedule();
  return true;
}

/* ---------- 导出 ---------- */
// 导出某天的空闲块（供排程引擎使用）：扣除 UNAVAILABLE/FRAGMENT/REST，分钟级
// 返回 [{start: "HH:MM", end: "HH:MM", minutes: n}]
function getFreeSlotsForDay(dayIndex, minMinutes = 30) {
  const row = scheduleState[dayIndex] || [];
  const isOccupied = m => row[m] > 0 && NON_FREE_TYPES.includes(BLOCK_TYPES[row[m] - 1].key);
  const blocks = [];
  let m = 0;
  while (m < MINUTES_PER_DAY) {
    if (isOccupied(m)) { m++; continue; }
    const start = m;
    while (m < MINUTES_PER_DAY && !isOccupied(m)) m++;
    if (m - start >= minMinutes) {
      blocks.push({ start: hm(start), end: m >= MINUTES_PER_DAY ? '24:00' : hm(m), minutes: m - start });
    }
  }
  return blocks;
}

function clearSchedule() {
  initScheduleState();
  renderSchedule();
}

// 暴露
window.Schedule = {
  init: () => { initScheduleState(); bindToolbar(); bindGridEvents(); renderSchedule(); },
  render: renderSchedule,
  getFreeSlotsForDay,
  clear: clearSchedule,
  loadRemote: loadRemoteSchedule,
  addPreciseBlock,
  state: () => scheduleState,
  onceEvents: () => onceEvents,
  types: BLOCK_TYPES,
  minutesPerDay: MINUTES_PER_DAY,
};
