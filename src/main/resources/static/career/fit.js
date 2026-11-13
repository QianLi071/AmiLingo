(function () {
  "use strict";

  const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080" : "";
  const profileStore = window.CareerProfileStore;
  const clubStore = window.CampusClubStore;
  const prefill = window.CareerFitPrefill;
  const LEVEL_LABELS = {
    STRONG_MATCH: "匹配证据较充分",
    POSSIBLE_MATCH: "具备部分匹配点",
    NEEDS_REVIEW: "需要进一步核对"
  };
  const QUALITY_LABELS = { DETAILED: "信息较完整", STANDARD: "信息一般", LIMITED: "信息有限" };

  const form = document.querySelector("#fit-form");
  const submit = document.querySelector("#fit-submit");
  const status = document.querySelector("#fit-form-status");
  const selectedJob = document.querySelector("#selected-job");
  const result = document.querySelector("#fit-result");
  const placeholder = document.querySelector("#fit-placeholder");
  const content = document.querySelector("#fit-content");
  const jobId = new URLSearchParams(window.location.search).get("job");
  let job = null;

  function splitSkills(value) {
    return prefill.splitSkills(value);
  }

  function reportImport(message, error) {
    status.className = error ? "form-status error" : "form-status success";
    status.textContent = message;
  }

  function importCareerProfile() {
    const source = prefill.fromCareerProfile(profileStore.load(window.localStorage));
    const major = document.querySelector("#major");
    const skills = document.querySelector("#skills");
    const experience = document.querySelector("#experience-summary");
    major.value = major.value.trim() || source.major;
    skills.value = prefill.mergeSkills(skills.value, source.skills);
    experience.value = prefill.mergeText(experience.value, source.experienceSummary, 2000);
    const count = [source.major, source.skills, source.experienceSummary].filter(Boolean).length;
    reportImport(count ? `已导入 ${count} 类求职资料，请确认后再分析。` : "求职资料助手中还没有可导入的内容。", !count);
  }

  function importClubExperiences() {
    const items = clubStore.loadExperiences(window.localStorage);
    const drafts = prefill.fromClubExperiences(items, clubStore.resumeDraft);
    const experience = document.querySelector("#experience-summary");
    experience.value = prefill.mergeText(experience.value, drafts, 2000);
    reportImport(drafts.length ? `已导入 ${drafts.length} 条校园经历草稿，请删去与当前岗位无关的内容。` : "校园探索中还没有可导入的参与经历。", !drafts.length);
  }

  function optionalValue(selector) {
    const value = document.querySelector(selector).value.trim();
    return value || null;
  }

  function appendList(list, values, emptyText) {
    list.replaceChildren();
    const items = values && values.length ? values : [emptyText];
    items.forEach(text => {
      const item = document.createElement("li");
      item.textContent = text;
      list.appendChild(item);
    });
  }

  function renderJob(data) {
    selectedJob.replaceChildren();
    const label = document.createElement("span");
    label.textContent = "当前岗位";
    const title = document.createElement("strong");
    title.textContent = data.positionTitle;
    const company = document.createElement("small");
    company.textContent = `${data.companyName} · ${data.location || "地点以官网为准"}`;
    selectedJob.append(label, title, company);
    submit.disabled = false;
  }

  function renderError(message) {
    status.className = "form-status error";
    status.textContent = message;
  }

  async function loadJob() {
    if (!jobId || !/^\d+$/.test(jobId)) {
      selectedJob.textContent = "没有选择岗位，请返回岗位列表后点击“匹配分析”。";
      renderError("请先从岗位列表选择一个岗位。");
      return;
    }
    try {
      const response = await fetch(`${API_BASE}/api/v1/career/jobs/${encodeURIComponent(jobId)}`);
      const payload = await response.json();
      if (!response.ok || !payload.success) throw new Error(payload.message || `HTTP ${response.status}`);
      job = payload.data;
      renderJob(job);
    } catch (error) {
      selectedJob.textContent = "岗位读取失败";
      renderError(`无法读取岗位：${error.message}。请返回列表重新选择。`);
    }
  }

  function requestBody() {
    return {
      major: document.querySelector("#major").value.trim(),
      skills: splitSkills(document.querySelector("#skills").value),
      experienceSummary: optionalValue("#experience-summary"),
      preferredRegion: optionalValue("#preferred-region"),
      preferredJobType: optionalValue("#preferred-job-type"),
      fullJobDescription: optionalValue("#full-description")
    };
  }

  function renderAssessment(data) {
    result.classList.remove("empty");
    placeholder.hidden = true;
    content.hidden = false;

    const level = document.querySelector("#fit-level");
    level.className = `fit-level ${data.fitLevel.toLowerCase()}`;
    level.textContent = `${LEVEL_LABELS[data.fitLevel] || data.fitLevel} · ${QUALITY_LABELS[data.informationQuality] || data.informationQuality}`;
    document.querySelector("#fit-score-value").textContent = String(data.referenceScore);
    document.querySelector("#fit-summary").textContent = data.summary;

    const criterionList = document.querySelector("#criterion-list");
    criterionList.replaceChildren();
    data.criteria.forEach(criterion => {
      const item = document.createElement("article");
      item.className = "criterion-item";
      const heading = document.createElement("div");
      const name = document.createElement("strong");
      name.textContent = criterion.name;
      const score = document.createElement("span");
      score.textContent = `${criterion.score}/${criterion.maxScore}`;
      heading.append(name, score);
      const explanation = document.createElement("p");
      explanation.textContent = criterion.explanation;
      const meter = document.createElement("div");
      meter.className = "criterion-meter";
      const fill = document.createElement("span");
      fill.style.width = `${criterion.maxScore ? (criterion.score / criterion.maxScore) * 100 : 0}%`;
      meter.appendChild(fill);
      item.append(heading, explanation, meter);
      criterionList.appendChild(item);
    });

    appendList(document.querySelector("#evidence-list"), data.matchedEvidence, "目前没有足够文本证据，建议补充完整职位说明。");
    appendList(document.querySelector("#verify-list"), data.itemsToVerify, "目前没有新增核对项。");
    appendList(document.querySelector("#action-list"), data.nextActions, "根据官网要求继续准备申请材料。");
    document.querySelector("#fit-disclaimer").textContent = data.disclaimer;
    result.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const skills = splitSkills(document.querySelector("#skills").value);
    if (!skills.length) {
      renderError("请至少填写一项真实技能。");
      return;
    }
    if (!job) {
      renderError("岗位尚未加载，请稍后重试或返回列表重新选择。");
      return;
    }

    status.textContent = "";
    submit.disabled = true;
    submit.textContent = "正在分析…";
    try {
      const response = await fetch(`${API_BASE}/api/v1/career/jobs/${encodeURIComponent(jobId)}/fit-assessments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody())
      });
      const payload = await response.json();
      if (!response.ok || !payload.success) throw new Error(payload.message || `HTTP ${response.status}`);
      renderAssessment(payload.data);
    } catch (error) {
      renderError(`分析失败：${error.message}。请确认 Java 后端已经启动。`);
    } finally {
      submit.disabled = false;
      submit.textContent = "生成匹配分析";
    }
  });

  document.querySelector("#import-profile").addEventListener("click", importCareerProfile);
  document.querySelector("#import-club-experiences").addEventListener("click", importClubExperiences);

  loadJob();
})();
