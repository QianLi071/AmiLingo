(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.CareerInterviewStore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const STORAGE_KEY = "amilingo.career.interviews.v1";
  const VERSION = 1;
  const DIMENSIONS = Object.freeze(["clarity", "evidence", "relevance", "reflection"]);

  function text(value, limit) {
    return typeof value === "string" ? value.trim().slice(0, limit) : "";
  }
  function createId() {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
    return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
  function rating(value) {
    const number = Number(value);
    return Number.isInteger(number) && number >= 1 && number <= 5 ? number : 0;
  }
  function normalizeQuestion(value) {
    const source = value && typeof value === "object" ? value : {};
    const ratings = source.ratings && typeof source.ratings === "object" ? source.ratings : {};
    return {
      id: text(source.id, 100) || createId(),
      category: text(source.category, 40),
      prompt: text(source.prompt, 1000),
      answer: text(source.answer, 5000),
      ratings: Object.fromEntries(DIMENSIONS.map(name => [name, rating(ratings[name])])),
      improvement: text(source.improvement, 1200)
    };
  }
  function normalizeSession(value) {
    const source = value && typeof value === "object" ? value : {};
    const questions = Array.isArray(source.questions) ? source.questions.map(normalizeQuestion).filter(item => item.prompt).slice(0, 20) : [];
    return {
      id: text(source.id, 100) || createId(),
      sourceJobId: text(source.sourceJobId, 100),
      companyName: text(source.companyName, 128),
      positionTitle: text(source.positionTitle, 128),
      questions,
      createdAt: text(source.createdAt, 40),
      updatedAt: text(source.updatedAt, 40)
    };
  }

  function generateQuestions(job) {
    const source = job && typeof job === "object" ? job : {};
    const position = text(source.positionTitle, 128) || "目标岗位";
    const company = text(source.companyName, 128) || "这家公司";
    return [
      { category: "岗位理解", prompt: `请用自己的话说明${position}主要解决什么问题，以及你为什么对它感兴趣。` },
      { category: "经历证据", prompt: `选择一段与${position}有关的经历，按“情境—任务—行动—结果”说明你具体做了什么。` },
      { category: "问题解决", prompt: "讲述一次信息不完整或方案失败的情况。你如何定位问题、验证假设并调整做法？" },
      { category: "学习能力", prompt: `如果入职${company}后需要快速掌握一项陌生技能，你会如何制定前两周的学习与验证计划？` },
      { category: "协作沟通", prompt: "讲述一次团队意见不一致的经历。你如何理解分歧、推动决定并承担结果？" },
      { category: "反向提问", prompt: `你会向${company}的面试官提出哪两个问题，以核实岗位日常、培养方式或评价标准？` }
    ].map(item => normalizeQuestion({ ...item, id: createId() }));
  }

  function load(storage) {
    try {
      const payload = JSON.parse(storage.getItem(STORAGE_KEY));
      if (!payload || payload.version !== VERSION || !Array.isArray(payload.items)) return [];
      return payload.items.map(normalizeSession).filter(item => item.positionTitle).slice(0, 30);
    } catch (_error) {
      return [];
    }
  }
  function persist(storage, items) {
    const normalized = items.map(normalizeSession).filter(item => item.positionTitle).slice(0, 30);
    storage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, items: normalized }));
    return normalized;
  }
  function createSession(storage, job) {
    const sourceJobId = text(job && job.id, 100);
    const items = load(storage);
    const existing = sourceJobId ? items.find(item => item.sourceJobId === sourceJobId) : null;
    if (existing) return existing;
    const now = new Date().toISOString();
    const session = normalizeSession({
      id: createId(), sourceJobId, companyName: job && job.companyName,
      positionTitle: job && job.positionTitle, questions: generateQuestions(job), createdAt: now, updatedAt: now
    });
    if (!session.positionTitle) throw new Error("岗位名称不能为空");
    persist(storage, [session, ...items]);
    return session;
  }
  function saveSession(storage, value) {
    const session = normalizeSession(value);
    if (!session.positionTitle) throw new Error("岗位名称不能为空");
    const items = load(storage);
    const index = items.findIndex(item => item.id === session.id);
    const now = new Date().toISOString();
    const saved = { ...session, createdAt: session.createdAt || now, updatedAt: now };
    if (index >= 0) items[index] = saved;
    else items.unshift(saved);
    persist(storage, items);
    return saved;
  }
  function removeSession(storage, id) {
    return persist(storage, load(storage).filter(item => item.id !== id));
  }
  function buildReview(value) {
    const session = normalizeSession(value);
    const answered = session.questions.filter(item => item.answer);
    const scored = answered.filter(item => DIMENSIONS.some(name => item.ratings[name]));
    const averages = {};
    DIMENSIONS.forEach(name => {
      const values = scored.map(item => item.ratings[name]).filter(Boolean);
      averages[name] = values.length ? Math.round((values.reduce((sum, item) => sum + item, 0) / values.length) * 10) / 10 : 0;
    });
    const ranked = DIMENSIONS.filter(name => averages[name]).sort((left, right) => averages[right] - averages[left]);
    return {
      answeredCount: answered.length,
      totalCount: session.questions.length,
      averages,
      strength: ranked[0] || "",
      priority: ranked.length > 1 ? ranked[ranked.length - 1] : "",
      improvements: answered.map(item => item.improvement).filter(Boolean),
      unanswered: session.questions.filter(item => !item.answer).map(item => item.prompt)
    };
  }
  function reviewText(value, labels) {
    const session = normalizeSession(value);
    const review = buildReview(session);
    const names = labels || {};
    const scores = DIMENSIONS.filter(name => review.averages[name]).map(name => `${names[name] || name} ${review.averages[name]}/5`);
    const lines = [
      `${session.companyName ? `${session.companyName} · ` : ""}${session.positionTitle} 面试练习复盘`,
      `完成题目：${review.answeredCount}/${review.totalCount}`,
      scores.length ? `自评：${scores.join("；")}` : "自评：尚未填写",
      review.strength ? `相对优势：${names[review.strength] || review.strength}` : "",
      review.priority ? `优先改进：${names[review.priority] || review.priority}` : "",
      review.improvements.length ? `具体改进：${review.improvements.join("；")}` : ""
    ];
    return lines.filter(Boolean).join("\n");
  }

  return Object.freeze({ STORAGE_KEY, DIMENSIONS, generateQuestions, normalizeSession, load, createSession, saveSession, removeSession, buildReview, reviewText });
});
