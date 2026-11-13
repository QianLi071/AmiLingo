(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  } else {
    root.CampusClubStore = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const PROFILE_KEY = "amilingo.club.profile.v1";
  const SAVED_KEY = "amilingo.club.saved.v1";
  const EXPERIENCE_KEY = "amilingo.club.experiences.v1";
  const VERSION = 1;

  const GROUPS = Object.freeze([
    {
      id: "math-modeling",
      name: "数竞数模组",
      englishName: "Mathematics and Modeling",
      summary: "适合喜欢把现实问题转化为数学模型，并用数据、代码和报告表达结论的同学。",
      interests: ["mathematics", "data", "research", "writing"],
      goals: ["interest", "competition", "graduate", "career"],
      modes: ["team", "project"],
      starter: "先参加一次建模案例拆解，尝试数据分析、建模或论文写作中的一个角色。"
    },
    {
      id: "algorithms",
      name: "算法组",
      englishName: "Algorithms",
      summary: "适合对编程、算法思维和代码竞赛感兴趣，希望通过持续练习解决问题的同学。",
      interests: ["coding", "mathematics", "ai"],
      goals: ["interest", "competition", "graduate", "career"],
      modes: ["individual", "team"],
      starter: "从一场入门分享或基础题训练开始，不要求已有竞赛经历。"
    },
    {
      id: "hardware",
      name: "硬件组",
      englishName: "Hardware",
      summary: "适合希望接触电子电路、机器人、嵌入式系统和动手调试的同学。",
      interests: ["hardware", "robotics", "engineering"],
      goals: ["interest", "competition", "graduate", "career"],
      modes: ["team", "hands-on", "project"],
      starter: "先体验一次硬件或机器人工作坊，再决定是否加入长期项目。"
    },
    {
      id: "medical-tech",
      name: "医疗科技组",
      englishName: "Medical Technology",
      summary: "适合关注健康与工程交叉，希望探索生物医学、数据或智能设备应用的同学。",
      interests: ["health", "ai", "data", "engineering"],
      goals: ["interest", "graduate", "career", "project"],
      modes: ["team", "research", "project"],
      starter: "先从一个医疗场景问题出发，了解技术、伦理与实际需求之间的关系。"
    },
    {
      id: "fintech",
      name: "金融科技组",
      englishName: "Financial Technology",
      summary: "适合对金融场景、数据分析、产品与技术结合感兴趣的同学。",
      interests: ["finance", "data", "coding", "product"],
      goals: ["interest", "graduate", "career", "project"],
      modes: ["team", "research", "project"],
      starter: "先选择一个熟悉的金融场景，尝试做数据观察或产品问题拆解。"
    }
  ]);

  const ACTIVITIES = Object.freeze([
    {
      id: "welcome-and-groups",
      title: "新成员方向交流",
      period: "2026 年 11 月",
      type: "交流",
      summary: "认识不同技术与赛事方向，和老成员交流后再决定是否深入参加。",
      sourceStatus: "计划安排"
    },
    {
      id: "macau-ieee-competition",
      title: "澳门 IEEE 学科竞赛",
      period: "2026 年 12 月",
      type: "竞赛",
      summary: "计划覆盖电路与电子、编程与算法、信号处理等方向。",
      sourceStatus: "拟定时间"
    },
    {
      id: "spring-code-and-robotics",
      title: "春季机器人与代码赛事",
      period: "2027 年 3 月",
      type: "竞赛",
      summary: "机器人专项比赛与入门至进阶的限时编程算法竞赛。",
      sourceStatus: "拟定时间"
    },
    {
      id: "technology-open-day",
      title: "年度技术开放日",
      period: "2027 年 5 月",
      type: "展示",
      summary: "集中展示竞赛作品、阶段成果与技术演讲内容。",
      sourceStatus: "拟定时间"
    }
  ]);

  function text(value, limit) {
    return typeof value === "string" ? value.trim().slice(0, limit) : "";
  }

  function stringList(value, allowed, maximum) {
    const items = Array.isArray(value) ? value : [];
    return [...new Set(items.filter(item => allowed.includes(item)))].slice(0, maximum);
  }

  function readPayload(storage, key) {
    try {
      const payload = JSON.parse(storage.getItem(key));
      return payload && payload.version === VERSION ? payload : null;
    } catch (_error) {
      return null;
    }
  }

  function normalizeProfile(value) {
    const source = value && typeof value === "object" ? value : {};
    return {
      goal: ["interest", "competition", "graduate", "career", "project"].includes(source.goal)
        ? source.goal : "interest",
      interests: stringList(source.interests, [
        "mathematics", "data", "research", "writing", "coding", "ai", "hardware",
        "robotics", "engineering", "health", "finance", "product"
      ], 6),
      modes: stringList(source.modes, ["individual", "team", "hands-on", "research", "project"], 3),
      weeklyHours: ["1-2", "3-5", "6+"].includes(source.weeklyHours) ? source.weeklyHours : "1-2"
    };
  }

  function loadProfile(storage) {
    const payload = readPayload(storage, PROFILE_KEY);
    return normalizeProfile(payload && payload.profile);
  }

  function saveProfile(storage, profile) {
    const normalized = normalizeProfile(profile);
    storage.setItem(PROFILE_KEY, JSON.stringify({ version: VERSION, profile: normalized }));
    return normalized;
  }

  function scoreGroup(group, profile) {
    const normalized = normalizeProfile(profile);
    let score = 0;
    const reasons = [];
    const matchedInterests = group.interests.filter(item => normalized.interests.includes(item));
    const matchedModes = group.modes.filter(item => normalized.modes.includes(item));

    if (group.goals.includes(normalized.goal)) {
      score += 4;
      reasons.push("与你当前的参与目标一致");
    }
    if (matchedInterests.length) {
      score += matchedInterests.length * 3;
      reasons.push(`命中 ${matchedInterests.length} 个兴趣方向`);
    }
    if (matchedModes.length) {
      score += matchedModes.length * 2;
      reasons.push("活动方式与你的偏好接近");
    }
    if (normalized.weeklyHours === "1-2") {
      reasons.push("建议先参加单次活动，再决定长期投入");
    } else {
      score += 1;
      reasons.push("可进一步尝试项目或赛事训练");
    }

    return { ...group, score, reasons };
  }

  function recommend(profile) {
    return GROUPS.map(group => scoreGroup(group, profile))
      .sort((left, right) => right.score - left.score);
  }

  function loadSaved(storage) {
    const payload = readPayload(storage, SAVED_KEY);
    const ids = payload && Array.isArray(payload.ids) ? payload.ids : [];
    const allowed = GROUPS.map(group => group.id).concat(ACTIVITIES.map(activity => activity.id));
    return [...new Set(ids.filter(id => allowed.includes(id)))];
  }

  function toggleSaved(storage, id) {
    const allowed = GROUPS.some(group => group.id === id) || ACTIVITIES.some(activity => activity.id === id);
    if (!allowed) return loadSaved(storage);
    const ids = loadSaved(storage);
    const next = ids.includes(id) ? ids.filter(item => item !== id) : [id, ...ids];
    storage.setItem(SAVED_KEY, JSON.stringify({ version: VERSION, ids: next }));
    return next;
  }

  function dateValue(value) {
    const raw = text(value, 10);
    return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : "";
  }

  function createId() {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
    return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function normalizeExperience(value) {
    const source = value && typeof value === "object" ? value : {};
    return {
      id: text(source.id, 100) || createId(),
      activityName: text(source.activityName, 120),
      groupName: text(source.groupName, 80),
      role: text(source.role, 80),
      startDate: dateValue(source.startDate),
      endDate: dateValue(source.endDate),
      actions: text(source.actions, 1200),
      outcome: text(source.outcome, 800),
      skills: text(source.skills, 400),
      reflection: text(source.reflection, 1200),
      createdAt: text(source.createdAt, 40),
      updatedAt: text(source.updatedAt, 40)
    };
  }

  function loadExperiences(storage) {
    const payload = readPayload(storage, EXPERIENCE_KEY);
    if (!payload || !Array.isArray(payload.items)) return [];
    return payload.items.map(normalizeExperience).filter(item => item.activityName).slice(0, 100);
  }

  function persistExperiences(storage, items) {
    const normalized = items.map(normalizeExperience).filter(item => item.activityName).slice(0, 100);
    storage.setItem(EXPERIENCE_KEY, JSON.stringify({ version: VERSION, items: normalized }));
    return normalized;
  }

  function saveExperience(storage, value) {
    const items = loadExperiences(storage);
    const now = new Date().toISOString();
    const normalized = normalizeExperience({ ...value, updatedAt: now });
    if (!normalized.activityName) throw new Error("活动或项目名称不能为空");
    const index = items.findIndex(item => item.id === normalized.id);
    if (index >= 0) items[index] = { ...normalized, createdAt: items[index].createdAt || now };
    else items.unshift({ ...normalized, createdAt: now });
    return persistExperiences(storage, items);
  }

  function removeExperience(storage, id) {
    return persistExperiences(storage, loadExperiences(storage).filter(item => item.id !== id));
  }

  function resumeDraft(value) {
    const item = normalizeExperience(value);
    if (!item.activityName) return "";
    const subject = [item.groupName, item.activityName].filter(Boolean).join(" · ");
    const parts = [];
    if (item.role) parts.push(`担任${item.role}`);
    if (item.actions) parts.push(item.actions.replace(/[。；;]+$/u, ""));
    if (item.outcome) parts.push(`取得${item.outcome.replace(/[。；;]+$/u, "")}`);
    if (item.skills) parts.push(`运用或提升了${item.skills.replace(/[。；;]+$/u, "")}`);
    return `${subject}：${parts.length ? parts.join("；") : "请补充你的具体行动与结果"}。`;
  }

  return Object.freeze({
    PROFILE_KEY, SAVED_KEY, EXPERIENCE_KEY, GROUPS, ACTIVITIES,
    normalizeProfile, loadProfile, saveProfile, scoreGroup, recommend,
    loadSaved, toggleSaved, normalizeExperience, loadExperiences,
    saveExperience, removeExperience, resumeDraft
  });
});
