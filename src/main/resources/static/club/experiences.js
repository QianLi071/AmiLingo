(function () {
  "use strict";
  const store = window.CampusClubStore;
  const form = document.querySelector("#experience-form");
  const list = document.querySelector("#record-list");
  const status = document.querySelector("#form-status");
  const fields = {
    id: document.querySelector("#experience-id"), activityName: document.querySelector("#activity-name"),
    groupName: document.querySelector("#group-name"), role: document.querySelector("#role"),
    startDate: document.querySelector("#start-date"), endDate: document.querySelector("#end-date"),
    actions: document.querySelector("#actions"), outcome: document.querySelector("#outcome"),
    skills: document.querySelector("#skills"), reflection: document.querySelector("#reflection")
  };

  function values() {
    return Object.fromEntries(Object.entries(fields).map(([key, input]) => [key, input.value]));
  }
  function fill(item) {
    Object.entries(fields).forEach(([key, input]) => { input.value = item && item[key] ? item[key] : ""; });
    fields.activityName.focus();
  }
  async function copyText(text, button) {
    await navigator.clipboard.writeText(text);
    const previous = button.textContent; button.textContent = "已复制";
    window.setTimeout(() => { button.textContent = previous; }, 1500);
  }
  function render() {
    const items = store.loadExperiences(window.localStorage);
    list.replaceChildren();
    if (!items.length) {
      const empty = document.createElement("div"); empty.className = "empty-state";
      empty.textContent = "还没有记录。参加一次分享、竞赛或项目后，就可以从左侧开始整理。";
      list.appendChild(empty); return;
    }
    items.forEach(item => {
      const card = document.createElement("article"); card.className = "record-card";
      const heading = document.createElement("div"); heading.className = "record-heading";
      const title = document.createElement("h3"); title.textContent = item.activityName;
      const meta = document.createElement("p");
      meta.textContent = [item.groupName, item.role, [item.startDate, item.endDate].filter(Boolean).join(" 至 ")].filter(Boolean).join(" · ");
      heading.append(title, meta);
      const draft = document.createElement("p"); draft.className = "resume-draft"; draft.textContent = store.resumeDraft(item);
      const actions = document.createElement("div"); actions.className = "record-actions";
      const edit = document.createElement("button"); edit.type = "button"; edit.textContent = "编辑"; edit.addEventListener("click", () => fill(item));
      const copy = document.createElement("button"); copy.type = "button"; copy.textContent = "复制简历素材草稿";
      copy.addEventListener("click", () => copyText(store.resumeDraft(item), copy).catch(() => { status.textContent = "复制失败，请手动选择文字"; }));
      const remove = document.createElement("button"); remove.type = "button"; remove.className = "danger-button"; remove.textContent = "删除";
      remove.addEventListener("click", () => { if (window.confirm("删除这条经历记录？")) { store.removeExperience(window.localStorage, item.id); render(); } });
      actions.append(edit, copy, remove); card.append(heading, draft, actions); list.appendChild(card);
    });
  }
  form.addEventListener("submit", event => {
    event.preventDefault();
    try { store.saveExperience(window.localStorage, values()); fill(null); status.textContent = "已保存"; render(); }
    catch (error) { status.textContent = error.message; }
  });
  document.querySelector("#reset-form").addEventListener("click", () => fill(null));
  render();
})();
