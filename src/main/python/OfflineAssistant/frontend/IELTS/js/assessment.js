/* ============================================================
 * assessment.js — 个性化测评题库 + 评分引擎 + AI 计划生成
 * 纯前端 mock 实现，预留真实 LLM 接入点（AI.call）
 * ============================================================ */

/* ---------- 题库 ---------- */

// 词汇题：20 题，覆盖不同难度（CEFR A2-C1）
const VOCAB_QUESTIONS = [
  { word: 'abandon', options: ['放弃', '拥抱', '加强', '隐藏'], answer: 0, level: 'B1' },
  { word: 'benefit', options: ['损失', '益处', '危险', '负担'], answer: 1, level: 'A2' },
  { word: 'comprehensive', options: ['简单的', '全面的', '快速的', '昂贵的'], answer: 1, level: 'B2' },
  { word: 'deteriorate', options: ['改善', '恶化', '消失', '增加'], answer: 1, level: 'C1' },
  { word: 'elaborate', options: ['详细的', '简短的', '错误的', '便宜的'], answer: 0, level: 'B2' },
  { word: 'facilitate', options: ['阻碍', '促进', '忽视', '批评'], answer: 1, level: 'B2' },
  { word: 'genuine', options: ['假的', '真正的', '昂贵的', '稀有的'], answer: 1, level: 'B1' },
  { word: 'hypothesis', options: ['结论', '假设', '证据', '方法'], answer: 1, level: 'B2' },
  { word: 'inevitable', options: ['可避免的', '不可避免的', '罕见的', '随机的'], answer: 1, level: 'B2' },
  { word: 'justify', options: ['证明...正当', '拒绝', '忽视', '模仿'], answer: 0, level: 'B1' },
  { word: 'keen', options: ['冷漠的', '热衷的', '疲惫的', '年老的'], answer: 1, level: 'B1' },
  { word: 'lucrative', options: ['无利可图的', '有利可图的', '危险的', '合法的'], answer: 1, level: 'C1' },
  { word: 'maintain', options: ['破坏', '维持', '减少', '忽视'], answer: 1, level: 'A2' },
  { word: 'notion', options: ['事实', '概念', '物品', '价格'], answer: 1, level: 'B1' },
  { word: 'obstacle', options: ['机会', '障碍', '工具', '结果'], answer: 1, level: 'B1' },
  { word: 'phenomenon', options: ['问题', '现象', '理论', '历史'], answer: 1, level: 'B2' },
  { word: 'reluctant', options: ['渴望的', '不情愿的', '高兴的', '确定的'], answer: 1, level: 'B2' },
  { word: 'sustainable', options: ['可持续的', '暂时的', '昂贵的', '复杂的'], answer: 0, level: 'B2' },
  { word: 'tremendous', options: ['微小的', '巨大的', '普通的', '缓慢的'], answer: 1, level: 'B1' },
  { word: 'ubiquitous', options: ['稀有的', '无处不在的', '古老的', '危险的'], answer: 1, level: 'C1' },
];

// 阅读：1 篇短文 + 5 题
const READING_TEST = {
  passage: `The concept of urban green spaces has gained significant attention in recent decades as cities expand and environmental concerns grow. Research shows that parks, gardens, and tree-lined streets offer far more than aesthetic value; they play a crucial role in improving public health, reducing urban heat island effects, and supporting biodiversity.

A 2023 study conducted across 15 major cities found that residents living within 300 meters of a green space reported 23% lower stress levels than those who did not. Furthermore, urban parks have been shown to reduce local temperatures by up to 4°C during summer months, providing much-needed relief from heatwaves.

However, the distribution of green spaces is often unequal. Wealthier neighbourhoods tend to have larger and better-maintained parks, while lower-income areas frequently lack adequate green infrastructure. This disparity, known as the "green gap," has prompted city planners to adopt more equitable policies, ensuring that all residents have access to nature regardless of their socioeconomic status.`,
  questions: [
    {
      text: 'According to the passage, what is the main benefit of urban green spaces mentioned first?',
      options: ['Economic growth', 'Aesthetic value only', 'Improving public health', 'Increasing property prices'],
      answer: 2,
    },
    {
      text: 'By how much can urban parks reduce local temperatures in summer?',
      options: ['Up to 2°C', 'Up to 4°C', 'Up to 6°C', 'Up to 8°C'],
      answer: 1,
    },
    {
      text: 'What does the "green gap" refer to?',
      options: [
        'The distance between parks',
        'The unequal distribution of green spaces',
        'The lack of funding for parks',
        'The seasonal change in greenery',
      ],
      answer: 1,
    },
    {
      text: 'According to the study, residents near green spaces reported how much lower stress?',
      options: ['13%', '23%', '33%', '43%'],
      answer: 1,
    },
    {
      text: 'What have city planners done in response to the green gap?',
      options: [
        'Closed some parks',
        'Adopted more equitable policies',
        'Reduced green space budgets',
        'Moved parks to wealthier areas',
      ],
      answer: 1,
    },
  ],
};

// 听力：一段对话文本（用 TTS 朗读）+ 5 题
const LISTENING_TEST = {
  audio: `Good morning everyone, and welcome to the university library orientation. My name is Sarah, and I'll be your guide today.

The library is open from 8 AM to 10 PM on weekdays, and from 9 AM to 6 PM on weekends. You'll need your student ID card to enter and to borrow books. Each student can borrow up to 10 books at a time for a period of three weeks.

If you need help finding a book, you can use the online catalogue on the library website, or ask one of our staff at the information desk on the ground floor. We also offer free printing for the first 20 pages per week.

For group study, there are 12 study rooms available on the second floor. You can book them online up to one week in advance. Please remember to keep your voice down in the quiet zones, which are located on the third and fourth floors.

Finally, if you have any questions about referencing or academic writing, our librarians offer free 30-minute consultation sessions. You can book these through the library's online booking system. Thank you, and enjoy your time at the library.`,
  questions: [
    {
      text: 'What are the library opening hours on weekdays?',
      options: ['8 AM to 6 PM', '8 AM to 10 PM', '9 AM to 6 PM', '9 AM to 10 PM'],
      answer: 1,
    },
    {
      text: 'How many books can each student borrow at one time?',
      options: ['5 books', '8 books', '10 books', '15 books'],
      answer: 2,
    },
    {
      text: 'Where is the information desk located?',
      options: ['On the ground floor', 'On the second floor', 'On the third floor', 'On the fourth floor'],
      answer: 0,
    },
    {
      text: 'How many study rooms are available on the second floor?',
      options: ['8', '10', '12', '15'],
      answer: 2,
    },
    {
      text: 'How long is each free consultation session with a librarian?',
      options: ['15 minutes', '20 minutes', '30 minutes', '60 minutes'],
      answer: 2,
    },
  ],
};

// 写作题
const WRITING_TEST_PROMPT = `Task 2: Some people believe that the government should invest more in public transportation, while others think that building more roads is a better solution to traffic problems. Discuss both views and give your own opinion. Write at least 200 words.`;

// 口语题（Part 2 卡片）
const SPEAKING_TEST_PROMPT = {
  part2: `Describe a person who has influenced you in your life.
You should say:
- Who this person is
- How you know them
- What kind of person they are
And explain why they have influenced you.`,
};

/* ---------- 评分引擎 ---------- */

// 词汇量估算：基于正确率 + 难度
function estimateVocabulary(correctCount, total) {
  const rate = correctCount / total;
  // 基础 2000 + 正确率 * 6000，映射到 2000-8000
  return Math.round(2000 + rate * 6000);
}

// 词汇量 → 雅思基础分参考（0-9）
function vocabToIeltsBand(vocab) {
  if (vocab >= 7500) return 7.0;
  if (vocab >= 6500) return 6.5;
  if (vocab >= 5500) return 6.0;
  if (vocab >= 4500) return 5.5;
  if (vocab >= 3500) return 5.0;
  if (vocab >= 2500) return 4.5;
  return 4.0;
}

// 客观题正确率 → 雅思单科分（0-9）
function rateToBand(rate) {
  // 0% → 3.0, 100% → 8.5
  const band = 3.0 + rate * 5.5;
  return Math.round(band * 2) / 2; // 取到 0.5
}

// 写作启发式评分（复用 mockWritingScore，但更通用）
function scoreWriting(essay) {
  if (!essay || essay.trim().length < 30) {
    return { overall: 3.0, breakdown: { TR: 3.0, CC: 3.0, LR: 3.0, GRA: 3.0 } };
  }
  const len = essay.length;
  const sentences = (essay.match(/[.!?。！？]/g) || []).length;
  const avgLen = sentences ? len / sentences : 0;
  const base = Math.min(5.5 + (len > 250 ? 1 : 0) + (avgLen > 12 ? 0.5 : 0), 7.5);
  const tr = Math.round((base + (len > 200 ? 0.5 : 0)) * 2) / 2;
  const cc = Math.round((base - 0.5 + (sentences > 8 ? 0.5 : 0)) * 2) / 2;
  const lr = Math.round((base - 0.5 + (avgLen > 10 ? 0.5 : 0)) * 2) / 2;
  const gra = Math.round((base - 0.5 + (sentences > 10 ? 0.5 : 0)) * 2) / 2;
  const overall = Math.round(((tr + cc + lr + gra) / 4) * 2) / 2;
  return { overall, breakdown: { TR: tr, CC: cc, LR: lr, GRA: gra } };
}

// 真实 AI 写作评分（成功用 AI，失败降级到 scoreWriting mock）
async function scoreWritingRemote(essay, topic) {
  try {
    const r = await window.AmiAPI.callWritingGrade({
      content: essay,
      topic: topic || Assessment.WRITING_TEST_PROMPT,
      examType: 'IELTS_A',
      level: 'Academic',
    });
    return {
      overall: r.overall,
      breakdown: r.breakdown,
      _source: 'ai',
    };
  } catch (e) {
    console.warn('AI 写作评分失败，降级 mock：', e);
    const mock = scoreWriting(essay);
    return { ...mock, _source: 'mock' };
  }
}

// 口语启发式评分（基于回答文本长度、词汇丰富度）
function scoreSpeaking(text) {
  if (!text || text.trim().length < 20) {
    return { overall: 3.0, breakdown: { FC: 3.0, LR: 3.0, GRA: 3.0, P: 3.0 } };
  }
  const words = text.trim().split(/\s+/).length;
  const uniqueWords = new Set(text.toLowerCase().split(/\s+/)).size;
  const lexicalDiversity = uniqueWords / Math.max(words, 1);
  const sentences = (text.match(/[.!?。！？]/g) || []).length;
  const base = Math.min(5.0 + (words > 80 ? 1 : 0) + (lexicalDiversity > 0.7 ? 0.5 : 0), 7.5);
  const fc = Math.round((base + (words > 100 ? 0.5 : 0)) * 2) / 2;
  const lr = Math.round((base - 0.5 + (lexicalDiversity > 0.65 ? 0.5 : 0)) * 2) / 2;
  const gra = Math.round((base - 0.5 + (sentences > 6 ? 0.5 : 0)) * 2) / 2;
  const p = Math.round((base - 0.5) * 2) / 2; // 发音无法纯前端评估，给基础分
  const overall = Math.round(((fc + lr + gra + p) / 4) * 2) / 2;
  return { overall, breakdown: { FC: fc, LR: lr, GRA: gra, P: p } };
}

/* ---------- AI 综合评估 + 计划生成（mock，预留真实接入点） ---------- */

// AI 调用入口（mock 模式）
// 真实接入时，把这里换成 fetch('/api/ai/assess', {method:'POST', body: JSON.stringify(results)})
const AI = {
  async assess(results) {
    // 模拟网络延迟
    await new Promise(r => setTimeout(r, 800));

    const vocab = estimateVocabulary(results.vocab.correct, results.vocab.total);
    const vocabBand = vocabToIeltsBand(vocab);

    const listeningRate = results.listening.correct / results.listening.total;
    const readingRate = results.reading.correct / results.reading.total;
    const listeningBand = rateToBand(listeningRate);
    const readingBand = rateToBand(readingRate);

    const writingBand = results.writing.score.overall;
    const speakingBand = results.speaking.score.overall;

    // 综合分：四科平均，词汇量作为微调
    const rawAvg = (listeningBand + readingBand + writingBand + speakingBand) / 4;
    const vocabAdjust = (vocabBand - rawAvg) * 0.15; // 词汇量影响 15%
    const overall = Math.max(3.0, Math.min(8.5, Math.round((rawAvg + vocabAdjust) * 2) / 2));

    // 目标分：当前分 + 0.5~1.0（根据差距空间）
    const target = Math.min(9.0, Math.round((overall + 1.0) * 2) / 2);
    const targetParts = {
      L: Math.min(9.0, Math.round((listeningBand + 1.0) * 2) / 2),
      R: Math.min(9.0, Math.round((readingBand + 1.0) * 2) / 2),
      W: Math.min(9.0, Math.round((writingBand + 1.0) * 2) / 2),
      S: Math.min(9.0, Math.round((speakingBand + 1.0) * 2) / 2),
    };

    // 薄弱分科识别
    const parts = { L: listeningBand, R: readingBand, W: writingBand, S: speakingBand };
    const weakest = Object.entries(parts).sort((a, b) => a[1] - b[1])[0][0];

    // 生成建议文案
    const feedback = generateFeedback(parts, weakest, vocab);

    return {
      current: {
        total: overall,
        L: listeningBand,
        R: readingBand,
        W: writingBand,
        S: speakingBand,
      },
      target: {
        total: target,
        parts: targetParts,
      },
      vocabulary: vocab,
      weakest,
      feedback,
    };
  },
};

function generateFeedback(parts, weakest, vocab) {
  const partName = { L: '听力', R: '阅读', W: '写作', S: '口语' }[weakest];
  const tips = [];
  if (weakest === 'W') {
    tips.push('建议每日练习 Task 2 议论文，重点提升 TR（任务回应）与 CC（连贯衔接）');
    tips.push('积累学术写作连接词与高分句型');
  } else if (weakest === 'S') {
    tips.push('每天跟读 15 分钟 BBC/VOA，纠正发音与语调');
    tips.push('练习 Part 2 卡片题，确保回答覆盖所有提示点');
  } else if (weakest === 'L') {
    tips.push('精听剑桥真题 Section 3/4，训练同义替换捕捉');
    tips.push('练习数字、日期、专有名词速记');
  } else {
    tips.push('限时阅读训练，提升 skimming/scanning 速度');
    tips.push('总结 T/F/NG 判断题的常见陷阱');
  }
  if (vocab < 5000) tips.unshift('词汇量偏低，建议先集中背诵高频 5000 词');
  return tips;
}

window.Assessment = {
  VOCAB_QUESTIONS,
  READING_TEST,
  LISTENING_TEST,
  WRITING_TEST_PROMPT,
  SPEAKING_TEST_PROMPT,
  scoreWriting,
  scoreWritingRemote,
  scoreSpeaking,
  AI,
};
