/* ============================================================
 * schedule.js — 课表与空闲时段管理
 * 7 天 × 4 大段 × 30min 粒度；支持 busy/available 切换
 * ============================================================ */

// 状态：0=空，1=busy（有课），2=available（高效学习）
let scheduleState = []; // scheduleState[dayIndex][slotIndex] = 0|1|2
const SLOTS_PER_DAY = (12 - 9) * 2 + (17 - 13) * 2 + (22 - 18) * 2 + (24 - 22) * 2;
// 上午 3h=6格, 下午 4h=8格, 晚上 4h=8格, 深夜 2h=4格 => 26 格/天

function initScheduleState() {
  scheduleState = [];
  for (let d = 0; d < 7; d++) {
    const row = new Array(SLOTS_PER_DAY).fill(0);
    // 默认预填：周一至周五 上午+下午 busy（模拟上课）
    if (d < 5) {
      for (let s = 0; s < 14; s++) row[s] = 1; // 上午+下午
    }
    scheduleState.push(row);
  }
}

// 根据 slot index 计算时间标签
function slotToTime(slotIdx) {
  // 段边界
  const boundaries = [
    { start: 540, count: 6 },   // 09:00 = 540min, 6 格
    { start: 780, count: 8 },   // 13:00
    { start: 1080, count: 8 },  // 18:00
    { start: 1320, count: 4 },  // 22:00
  ];
  let acc = 0;
  for (const seg of boundaries) {
    if (slotIdx < acc + seg.count) {
      const mins = seg.start + (slotIdx - acc) * SLOT_MINUTES;
      const h = String(Math.floor(mins / 60)).padStart(2, '0');
      const m = String(mins % 60).padStart(2, '0');
      return `${h}:${m}`;
    }
    acc += seg.count;
  }
  return '00:00';
}

function renderSchedule() {
  const grid = $('#schedule-grid');
  grid.innerHTML = '';

  // 表头
  grid.innerHTML += `<div class="sched-head time">时间</div>`;
  DAYS.forEach(d => { grid.innerHTML += `<div class="sched-head">${d}</div>`; });

  // 每行一个 slot
  for (let s = 0; s < SLOTS_PER_DAY; s++) {
    const timeLabel = slotToTime(s);
    grid.innerHTML += `<div class="sched-cell time-col">${timeLabel}</div>`;
    for (let d = 0; d < 7; d++) {
      const state = scheduleState[d][s];
      const cls = state === 1 ? 'busy' : state === 2 ? 'avail' : 'empty';
      grid.innerHTML += `<div class="sched-cell ${cls}" data-day="${d}" data-slot="${s}" title="${timeLabel}"></div>`;
    }
  }

  // 绑定点击
  $$('.sched-cell[data-day]').forEach(cell => {
    cell.addEventListener('click', () => {
      const d = +cell.dataset.day;
      const s = +cell.dataset.slot;
      // 循环切换：0 -> 2(available) -> 1(busy) -> 0
      scheduleState[d][s] = (scheduleState[d][s] + 1) % 3;
      renderSchedule();
      updateSummary();
    });
  });

  updateSummary();
}

function updateSummary() {
  let busyMin = 0, availMin = 0;
  for (let d = 0; d < 7; d++) {
    for (let s = 0; s < SLOTS_PER_DAY; s++) {
      if (scheduleState[d][s] === 1) busyMin += SLOT_MINUTES;
      else if (scheduleState[d][s] === 2) availMin += SLOT_MINUTES;
    }
  }
  $('#schedule-summary').innerHTML = `
    <span>📚 有课时段：<b>${(busyMin / 60).toFixed(1)} h</b></span>
    <span>✅ 高效学习时段：<b>${(availMin / 60).toFixed(1)} h</b></span>
    <span>🕐 空闲总时长：<b>${((7 * 24 * 60 - busyMin) / 60).toFixed(1)} h</b></span>
  `;
}

// 导出某天的空闲块（供排程引擎使用）
// 返回 [{start: "HH:MM", end: "HH:MM", minutes: n}]
function getFreeSlotsForDay(dayIndex, minMinutes = 30) {
  const blocks = [];
  const row = scheduleState[dayIndex] || [];
  let start = null;
  for (let s = 0; s <= SLOTS_PER_DAY; s++) {
    const isFree = s < SLOTS_PER_DAY && row[s] !== 1; // 非 busy 即为可用（含 avail 标记和空）
    if (isFree && start === null) {
      start = s;
    } else if (!isFree && start !== null) {
      const minutes = (s - start) * SLOT_MINUTES;
      if (minutes >= minMinutes) {
        blocks.push({
          start: slotToTime(start),
          end: slotToTime(s),
          minutes,
        });
      }
      start = null;
    }
  }
  return blocks;
}

function clearSchedule() {
  scheduleState = [];
  for (let d = 0; d < 7; d++) scheduleState.push(new Array(SLOTS_PER_DAY).fill(0));
  renderSchedule();
}

// 暴露
window.Schedule = {
  init: () => { initScheduleState(); renderSchedule(); },
  render: renderSchedule,
  getFreeSlotsForDay,
  clear: clearSchedule,
  state: () => scheduleState,
};
