/* ============================================================
 * data.js — 语言区 Demo 的静态数据与配置
 * ============================================================ */

// 考试目标与当前水平（模拟用户画像）
const EXAM_GOAL = {
  exam_type: 'IELTS',
  target_total: 6.5,
  target_parts: { L: 6.0, R: 6.0, W: 6.0, S: 6.0 },
  exam_date: '2026-12-15',
};

const CURRENT_SCORE = {
  total: 5.5,
  L: 5.5, R: 6.0, W: 5.0, S: 5.5,
};

// 目标院校
const SCHOOLS = [
  { id: 's1', name: '香港大学 HKU', region: 'HK', ielts_code: 'HKH001', min_total: 6.0, min_parts: { L: 5.5, R: 5.5, W: 5.5, S: 5.5 } },
  { id: 's2', name: '香港中文大学 CUHK', region: 'HK', ielts_code: 'HKH002', min_total: 6.5, min_parts: { L: 6.0, R: 6.0, W: 6.0, S: 6.0 } },
  { id: 's3', name: '香港科技大学 HKUST', region: 'HK', ielts_code: 'HKH003', min_total: 6.5, min_parts: { L: 6.0, R: 6.0, W: 5.5, S: 5.5 } },
];

// 一周 7 天 × 4 大段（每段拆为 30min 粒度，共 8 格/段）
const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const SEGMENTS = [
  { label: '上午', start: '09:00', end: '12:00' },
  { label: '下午', start: '13:00', end: '17:00' },
  { label: '晚上', start: '18:00', end: '22:00' },
  { label: '深夜', start: '22:00', end: '24:00' },
];
const SLOT_MINUTES = 30; // 每格 30 分钟

// 阶段模型
const STAGES = [
  { key: 'diagnose', name: '摸底', from: -180, to: -120, week_rhythm: { vocab: 0, listening: 0, reading: 0, writing: 0, speaking: 0, mock: 1 } },
  { key: 'basic', name: '基础', from: -120, to: -60, week_rhythm: { vocab: 4, listening: 2, reading: 3, writing: 1, speaking: 1, mock: 0 } },
  { key: 'intensive', name: '强化', from: -60, to: -30, week_rhythm: { vocab: 3, listening: 3, reading: 2, writing: 3, speaking: 3, mock: 0.25 } },
  { key: 'sprint', name: '冲刺', from: -30, to: -3, week_rhythm: { vocab: 2, listening: 3, reading: 2, writing: 2, speaking: 2, mock: 1 } },
];

// 分科配置
const CATEGORIES = {
  vocab:     { name: '词汇', cls: 'cat-vocab' },
  listening: { name: '听力', cls: 'cat-listening' },
  reading:   { name: '阅读', cls: 'cat-reading' },
  writing:   { name: '写作', cls: 'cat-writing' },
  speaking:  { name: '口语', cls: 'cat-speaking' },
  mock:      { name: '模考', cls: 'cat-mock' },
};

// 任务标题模板
const TASK_TEMPLATES = {
  vocab: [
    '高频词 500 背诵', '词根词缀速记', '写作主题词积累', '同义替换词对',
  ],
  listening: [
    'Section 1 精听', '数字听写训练', '同义替换捕捉', 'Section 3 学术对话',
  ],
  reading: [
    'T/F/NG 判断题', '段落标题匹配', '摘要填空', '细节信息定位',
  ],
  writing: [
    'Task 1 图表描述', 'Task 2 议论文', '高级句型替换', '范文精读仿写',
  ],
  speaking: [
    'Part 1 日常话题', 'Part 2 卡片陈述', 'Part 3 深度讨论', '发音跟读训练',
  ],
  mock: ['全真模考（听说读写）', '单科限时模考'],
};

// 考试时间线事件
const TIMELINE_EVENTS = [
  { key: 'T-180', offset: -180, title: '摸底提醒', desc: '建议做第一次全真模考确定起点', type: 'info' },
  { key: 'T-120', offset: -120, title: '进入系统训练', desc: '已生成 N 周任务，请按日历执行', type: 'info' },
  { key: 'T-90', offset: -90, title: '考位核查', desc: '请确认 BC portal 上目标日期仍有考位', type: 'info' },
  { key: 'T-30', offset: -30, title: '全真模考', desc: '建议每周一次全真模考', type: 'info' },
  { key: 'T-7', offset: -7, title: '考前 Checklist', desc: 'T-7 检查证件、住宿、通勤', type: 'warn' },
  { key: 'T-3', offset: -3, title: '强化提醒', desc: '停止上新内容，开始保温', type: 'warn' },
  { key: 'T-1', offset: -1, title: '考前 Checklist', desc: 'T-1 最终检查物品清单', type: 'warn' },
  { key: 'T', offset: 0, title: '考试日 07:00', desc: '过关通勤时间、物品清单', type: 'active' },
  { key: 'T+1', offset: 1, title: '出分提醒', desc: '机考 1-2 天出分，纸笔 10 天', type: 'info' },
  { key: 'T+10', offset: 10, title: '出分日', desc: '纸笔成绩即将发布，触发送分清单', type: 'info' },
  { key: 'T+15', offset: 15, title: '送分清单', desc: '按目标院校列表逐个送分', type: 'info' },
];

// 考前 Checklist 模板
const CHECKLIST_TEMPLATE = [
  {
    category: '证件',
    items: [
      { title: '护照原件 + 复印件 1 份', desc: 'BC 报名时使用护照，考场必查' },
      { title: '准考证（打印件）', desc: '考前 3 天 BC portal 下载' },
      { title: '港澳通行证 / 签注', desc: '签注有效期覆盖考试日' },
    ],
  },
  {
    category: '过关通勤',
    items: [
      { title: '过关预计时长', desc: '澳门→香港：拱北/港珠澳大桥，预留 3 小时' },
      { title: '最晚过关时间', desc: '香港考场 09:00 报到，建议前一晚到港' },
      { title: '八达通/澳门通余额检查', desc: '确保余额充足' },
    ],
  },
  {
    category: '寄存物品',
    items: [
      { title: '禁止带入考场', desc: '手机（关机）、智能手表、电子词典、笔记、食物饮料' },
      { title: '可带入考场', desc: '身份证件、准考证、透明水瓶、橡皮、铅笔' },
    ],
  },
  {
    category: '考前 1 天',
    items: [
      { title: '文具准备', desc: 'HB 铅笔 2 支、橡皮、签字笔、透明塑料笔袋' },
      { title: '服装', desc: '考场空调温度低，建议带薄外套' },
      { title: '住宿确认', desc: '考点 5km 内优先；机考可当日来回' },
    ],
  },
];

// 阅读练习题（mock）
const READING_Q = {
  passage: 'The industrial revolution, which began in Britain in the late 18th century, transformed societies from agrarian to industrial economies. One of its most significant innovations was the steam engine, which powered factories, railways, and ships. However, this rapid industrialisation also brought severe environmental consequences, including air pollution and urban overcrowding. Reformers later pushed for regulations to address these issues, laying the groundwork for modern environmental policy.',
  questions: [
    {
      text: '工业革命最早起源于哪个国家？',
      options: ['美国', '英国', '法国', '德国'],
      answer: 1,
    },
    {
      text: '以下哪项是工业革命的重要发明？',
      options: ['印刷机', '蒸汽机', '电灯', '电话'],
      answer: 1,
    },
    {
      text: '工业革命带来的负面影响不包括？',
      options: ['空气污染', '城市过度拥挤', '农业产量下降', '环境污染'],
      answer: 2,
    },
  ],
};

// 写作题目
const WRITING_PROMPT = `Task 2: Some people believe that technology has made our lives more convenient, while others argue that it has created new problems. Discuss both views and give your own opinion. (200-300 words)`;

// 工具函数
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2200);
}

function fmtDate(d) {
  if (typeof d === 'string') d = new Date(d);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function addDays(date, n) {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}

function daysBetween(a, b) {
  return Math.round((new Date(b) - new Date(a)) / 86400000);
}
