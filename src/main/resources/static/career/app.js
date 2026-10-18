(function () {
  "use strict";

  const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080" : "";
  const REGION_LABELS = {
    MAINLAND_CHINA: "中国内地",
    HONG_KONG: "香港",
    MACAO: "澳门"
  };
  const TYPE_LABELS = {
    INTERNSHIP: "实习",
    FULL_TIME: "全职",
    GRADUATE_PROGRAM: "毕业生项目"
  };

  const elements = {
    form: document.querySelector("#filter-form"),
    keyword: document.querySelector("#keyword"),
    region: document.querySelector("#region"),
    jobType: document.querySelector("#job-type"),
    reset: document.querySelector("#reset-button"),
    refresh: document.querySelector("#refresh-button"),
    list: document.querySelector("#job-list"),
    status: document.querySelector("#status"),
    count: document.querySelector("#job-count"),
    panel: document.querySelector("#detail-panel"),
    detailEmpty: document.querySelector("#detail-empty"),
    detailContent: document.querySelector("#detail-content"),
    closeDetail: document.querySelector("#close-detail")
  };

  let selectedId = null;

  function appendTag(container, text, highlighted) {
    if (!text) return;
    const tag = document.createElement("span");
    tag.className = highlighted ? "tag highlight" : "tag";
    tag.textContent = text;
    container.appendChild(tag);
  }

  function companyInitial(companyName) {
    return (companyName || "?").trim().charAt(0).toUpperCase();
  }

  function buildCard(job) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "job-card";
    card.dataset.jobId = String(job.id);
    card.setAttribute("aria-label", `查看${job.companyName}的${job.positionTitle}`);

    const avatar = document.createElement("span");
    avatar.className = "company-avatar";
    avatar.textContent = companyInitial(job.companyName);

    const content = document.createElement("span");
    const title = document.createElement("h3");
    title.textContent = job.positionTitle;
    const meta = document.createElement("p");
    meta.textContent = [job.companyName, job.location].filter(Boolean).join(" · ");
    const tags = document.createElement("span");
    tags.className = "tags";
    appendTag(tags, REGION_LABELS[job.region] || job.region, false);
    appendTag(tags, TYPE_LABELS[job.jobType] || job.jobType, job.jobType === "INTERNSHIP");
    appendTag(tags, job.industry, false);
    content.append(title, meta, tags);

    const arrow = document.createElement("span");
    arrow.className = "card-arrow";
    arrow.setAttribute("aria-hidden", "true");
    arrow.textContent = "›";
    card.append(avatar, content, arrow);
    card.addEventListener("click", () => selectJob(job, card));
    return card;
  }

  function setText(selector, value, fallback) {
    const node = document.querySelector(selector);
    node.textContent = value || fallback;
  }

  function selectJob(job, card) {
    selectedId = String(job.id);
    document.querySelectorAll(".job-card").forEach(item => item.classList.toggle("active", item === card));
    elements.detailEmpty.hidden = true;
    elements.detailContent.hidden = false;
    elements.panel.classList.add("open");
    setText("#detail-company", job.companyName, "未知企业");
    setText("#detail-position", job.positionTitle, "未命名岗位");
    setText("#detail-description", job.description, "请前往企业官方页面查看岗位说明。");
    setText("#detail-requirements", job.requirements, "请前往企业官方页面查看申请条件。");
    setText("#detail-deadline", job.applicationDeadline, "以官网为准");
    setText("#detail-source", job.sourceName, "企业官方页面");

    const tags = document.querySelector("#detail-tags");
    tags.replaceChildren();
    appendTag(tags, REGION_LABELS[job.region] || job.region, false);
    appendTag(tags, TYPE_LABELS[job.jobType] || job.jobType, job.jobType === "INTERNSHIP");
    appendTag(tags, job.location, false);

    const officialLink = document.querySelector("#official-link");
    let officialUrl;
    try {
      officialUrl = new URL(job.officialUrl);
    } catch (_error) {
      officialUrl = null;
    }
    const safe = officialUrl && officialUrl.protocol === "https:";
    officialLink.href = safe ? officialUrl.href : "#";
    officialLink.hidden = !safe;
    const riskLink = document.querySelector("#risk-link");
    riskLink.href = `./risk.html?company=${encodeURIComponent(job.companyName || "")}`;
    const fitLink = document.querySelector("#fit-link");
    fitLink.href = `./fit.html?job=${encodeURIComponent(String(job.id))}`;
  }

  function renderJobs(jobs) {
    elements.list.replaceChildren();
    elements.count.textContent = String(jobs.length);
    elements.status.classList.remove("error");
    elements.status.textContent = jobs.length ? `共找到 ${jobs.length} 个岗位` : "没有符合条件的岗位";
    elements.list.setAttribute("aria-busy", "false");

    if (!jobs.length) {
      const empty = document.createElement("div");
      empty.className = "empty-results";
      empty.textContent = "换一个关键词或筛选条件试试。";
      elements.list.appendChild(empty);
      return;
    }

    jobs.forEach(job => elements.list.appendChild(buildCard(job)));
    const retained = Array.from(elements.list.children).find(card => card.dataset.jobId === selectedId);
    if (retained) {
      retained.click();
    } else if (window.matchMedia("(min-width: 981px)").matches) {
      elements.list.firstElementChild.click();
    }
  }

  async function loadJobs() {
    elements.list.setAttribute("aria-busy", "true");
    elements.status.classList.remove("error");
    elements.status.textContent = "正在加载岗位…";
    const params = new URLSearchParams();
    if (elements.keyword.value.trim()) params.set("keyword", elements.keyword.value.trim());
    if (elements.region.value) params.set("region", elements.region.value);
    if (elements.jobType.value) params.set("jobType", elements.jobType.value);
    const query = params.toString();

    try {
      const response = await fetch(`${API_BASE}/api/v1/career/jobs${query ? `?${query}` : ""}`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      if (!payload.success || !Array.isArray(payload.data)) throw new Error(payload.message || "响应格式不正确");
      renderJobs(payload.data);
    } catch (error) {
      elements.list.replaceChildren();
      elements.list.setAttribute("aria-busy", "false");
      elements.count.textContent = "—";
      elements.status.classList.add("error");
      elements.status.textContent = `岗位加载失败：${error.message}。请确认 Java 后端和数据库已经启动。`;
    }
  }

  elements.form.addEventListener("submit", event => {
    event.preventDefault();
    loadJobs();
  });
  elements.reset.addEventListener("click", () => {
    elements.form.reset();
    selectedId = null;
    loadJobs();
  });
  elements.refresh.addEventListener("click", loadJobs);
  elements.closeDetail.addEventListener("click", () => elements.panel.classList.remove("open"));

  loadJobs();
})();
