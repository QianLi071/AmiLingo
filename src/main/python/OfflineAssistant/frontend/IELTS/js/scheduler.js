/* ============================================================
 * scheduler.js — AI 学习计划排程引擎
 * 输入：考试日、课表空闲块、当前分、目标分、每周小时数
 * 输出：N 周 × 每日任务块（落到具体空闲时段切片）
 * 算法：阶段模型 + 差距缩放 + 贪心填充 + 连续性约束
 * ============================================================ */

function getGap() {
  return {
    total: +(EXAM_GOAL.target_total - CURRENT_SCORE.total).toFixed(1),
    L: +(EXAM_GOAL.target_parts.L - CURRENT_SCORE.L).toFixed(1),
    R: +(EXAM_GOAL.target_parts.R - CURRENT_SCORE.R).toFixed(1),
    W: +(EXAM_GOAL.target_parts.W - CURRENT_SCORE.W).toFixed(1),
    S: +(EXAM_GOAL.target_parts.S - CURRENT_SCORE.S).toFixed(1),
  };
}

// 按差距档位计算缩放系数
function gapScaler(gap) {
  if (gap <= 0.5) return 0.6;
  if (gap > 1.5) return 1.3;
  return 1.0;
}

// 计算某周某分科的任务数量
function weeklyTaskCount(stage, category, gap) {
  const base = stage.week_rhythm[category] || 0;
  // 薄弱分科（差距最大的）额外加练
  return Math.ceil(base * gapScaler(gap));
}

// 根据日期偏移量返回所在阶段
function getStageByOffset(offset) {
  // offset 是距考试日的天数（负数表示考前）
  for (const st of STAGES) {
    if (offset >= st.from && offset < st.to) return st;
  }
  if (offset >= STAGES[STAGES.length - 1].to) return STAGES[STAGES.length - 1];
  return STAGES[0];
}

// 给任务选标题
function pickTaskTitle(category, weekIdx) {
  const templates = TASK_TEMPLATES[category];
  return templates[weekIdx % templates.length];
}

/**
 * 主排程函数
 * @param {Object} cfg { examDate, weeklyHours, minBlock, maxBlock, weekendExtra, avoidEvening }
 */
function generatePlan(cfg) {
  const examDate = new Date(cfg.examDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const totalDays = daysBetween(today, examDate);
  if (totalDays <= 0) {
    return { weeks: [], warnings: ['考试日已过，请重新设置。'] };
  }

  const gap = getGap();
  const warnings = [];

  // 限制最多 12 周展示
  const startOffset = Math.max(-totalDays, -180);
  const weeks = [];

  // 预计算：按周统计薄弱分科
  const maxGapPart = Object.entries({ L: gap.L, R: gap.R, W: gap.W, S: gap.S })
    .sort((a, b) => b[1] - a[1])[0][0];
  const partToCat = { L: 'listening', R: 'reading', W: 'writing', S: 'speaking' };
  const weakCat = partToCat[maxGapPart];

  const weekCount = Math.min(Math.ceil(totalDays / 7), 12);

  for (let w = 0; w < weekCount; w++) {
    const weekStart = addDays(today, w * 7);
    const weekDays = [];
    const weekOffset = daysBetween(weekStart, examDate) * -1; // 距考试的天数（负）
    const stage = getStageByOffset(-weekOffset);

    // 本周各分科任务量
    const weekPlan = {};
    for (const cat of Object.keys(CATEGORIES)) {
      const partKey = { listening: 'L', reading: 'R', writing: 'W', speaking: 'S' }[cat];
      const g = partKey ? gap[partKey] : gap.total;
      weekPlan[cat] = weeklyTaskCount(stage, cat, g);
    }
    // 薄弱分科加练 +1
    if (weekPlan[weakCat]) weekPlan[weakCat] += 1;

    // 按优先级排序：mock > 薄弱分科 > writing/speaking > 其他
    const priority = ['mock', weakCat, 'writing', 'speaking', 'reading', 'listening', 'vocab'];
    const orderedCats = priority.filter((c, i, arr) => arr.indexOf(c) === i);

    // 本周任务队列（展开为单个任务）
    const taskQueue = [];
    for (const cat of orderedCats) {
      for (let i = 0; i < (weekPlan[cat] || 0); i++) {
        taskQueue.push({ category: cat, title: pickTaskTitle(cat, w) });
      }
    }

    // 记录本用于连续性约束
    const last3Days = []; // 最近 3 天的分科集合

    let weekScheduled = 0;
    const totalWeekSlots = [];

    for (let d = 0; d < 7; d++) {
      const date = addDays(weekStart, d);
      const dayOffset = daysBetween(date, examDate) * -1;
      const dayStage = getStageByOffset(-dayOffset);
      const dayName = DAYS[d];
      const dateStr = fmtDate(date);

      // 获取当天空闲块
      const dayIndex = (today.getDay() + d - 1 + 7) % 7; // getDay: 0=周日
      let freeBlocks = Schedule.getFreeSlotsForDay(dayIndex, cfg.minBlock);

      // 避开晚上
      if (cfg.avoidEvening) {
        freeBlocks = freeBlocks.filter(b => {
          const h = parseInt(b.start.split(':')[0]);
          return h < 18;
        });
      }

      // 周末加量：周末块不做额外限制，但优先排周末
      const isWeekend = d >= 5;

      const dayBlocks = [];
      let usedMinutes = 0;
      const dailyCap = isWeekend && cfg.weekendExtra
        ? Math.min(cfg.weeklyHours / 7 * 2.5, 6) * 60
        : Math.min(cfg.weeklyHours / 7 * 1.2, 4) * 60;

      // 连续性：今天不能排的分科（连续 3 天学过的）
      const forbidden = new Set();
      if (last3Days.length >= 3) {
        const counts = {};
        last3Days.forEach(set => set.forEach(c => counts[c] = (counts[c] || 0) + 1));
        for (const [cat, cnt] of Object.entries(counts)) {
          if (cnt >= 3) forbidden.add(cat);
        }
      }

      // 贪心：从 taskQueue 取任务填到空闲块
      const todayCats = new Set();
      const shuffledBlocks = [...freeBlocks].sort((a, b) => b.minutes - a.minutes);

      for (const block of shuffledBlocks) {
        if (usedMinutes >= dailyCap) break;
        // 找一个能放进此块的任务
        const target = block.minutes > cfg.maxBlock ? cfg.maxBlock : block.minutes;
        const targetMin = Math.max(cfg.minBlock, Math.min(target, block.minutes));

        for (let i = 0; i < taskQueue.length; i++) {
          const task = taskQueue[i];
          if (forbidden.has(task.category) && taskQueue.length > forbidden.size * 2) continue;
          // 块够大
          const taskMin = Math.min(targetMin, cfg.maxBlock);
          if (taskMin >= cfg.minBlock && usedMinutes + taskMin <= dailyCap + 30) {
            taskQueue.splice(i, 1);
            // 计算起止时间
            const [sh, sm] = block.start.split(':').map(Number);
            const startMins = sh * 60 + sm;
            const endMins = startMins + taskMin;
            const eh = String(Math.floor(endMins / 60)).padStart(2, '0');
            const em = String(endMins % 60).padStart(2, '0');

            dayBlocks.push({
              start: block.start,
              end: `${eh}:${em}`,
              category: task.category,
              title: task.title,
              minutes: taskMin,
            });
            usedMinutes += taskMin;
            todayCats.add(task.category);
            break;
          }
        }
      }

      last3Days.push(todayCats);
      if (last3Days.length > 3) last3Days.shift();

      weekDays.push({
        date: dateStr,
        dayName,
        blocks: dayBlocks.sort((a, b) => a.start.localeCompare(b.start)),
      });
      weekScheduled += dayBlocks.length;
    }

    if (taskQueue.length > 0) {
      warnings.push(`第 ${w + 1} 周（${stage.name}）还有 ${taskQueue.length} 个任务未排完，建议增加每周投入小时数。`);
    }

    weeks.push({
      week_index: w + 1,
      stage: stage.name,
      stage_key: stage.key,
      days: weekDays,
    });
  }

  // 周末无可用时间检查
  const weekendFree = [5, 6].reduce((acc, d) => acc + Schedule.getFreeSlotsForDay(d, 30).length, 0);
  if (!cfg.weekendExtra && weekendFree === 0) {
    // 仅提示
  }

  return { weeks, warnings };
}

window.Scheduler = { generatePlan, getGap };
