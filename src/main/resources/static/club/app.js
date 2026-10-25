(function () {
  "use strict";
  const store = window.CampusClubStore;
  const form = document.querySelector("#preference-form");
  const groupList = document.querySelector("#group-list");
  const activityList = document.querySelector("#activity-list");
  const template = document.querySelector("#group-template");
  const status = document.querySelector("#save-status");

  function checkedValues(selector) {
    return Array.from(document.querySelectorAll(`${selector} input:checked`)).map(input => input.value);
  }

  function currentProfile() {
    return {
      goal: form.elements.goal.value,
      interests: checkedValues("#interest-options"),
      modes: checkedValues("#mode-options"),
      weeklyHours: document.querySelector("#weekly-hours").value
    };
  }

  function applyProfile(profile) {
    const goal = form.querySelector(`input[name="goal"][value="${profile.goal}"]`);
    if (goal) goal.checked = true;
    document.querySelector("#weekly-hours").value = profile.weeklyHours;
    document.querySelectorAll("#interest-options input, #mode-options input").forEach(input => {
      input.checked = profile.interests.includes(input.value) || profile.modes.includes(input.value);
    });
  }

  function savedSet() {
    return new Set(store.loadSaved(window.localStorage));
  }

  function buttonLabel(saved) {
    return saved ? "已收藏" : "收藏";
  }

  function renderGroups(profile) {
    const saved = savedSet();
    groupList.replaceChildren();
    store.recommend(profile).forEach((group, index) => {
      const card = template.content.firstElementChild.cloneNode(true);
      card.querySelector(".rank").textContent = index < 2 ? `建议先看 ${index + 1}` : `方向 ${index + 1}`;
      card.querySelector(".english-name").textContent = group.englishName;
      card.querySelector("h3").textContent = group.name;
      card.querySelector(".summary").textContent = group.summary;
      const reasons = card.querySelector(".reason-list");
      group.reasons.forEach(reason => {
        const span = document.createElement("span");
        span.textContent = reason;
        reasons.appendChild(span);
      });
      card.querySelector(".starter").textContent = group.starter;
      const button = card.querySelector(".save-button");
      button.textContent = buttonLabel(saved.has(group.id));
      button.classList.toggle("saved", saved.has(group.id));
      button.addEventListener("click", () => {
        const next = new Set(store.toggleSaved(window.localStorage, group.id));
        button.textContent = buttonLabel(next.has(group.id));
        button.classList.toggle("saved", next.has(group.id));
      });
      groupList.appendChild(card);
    });
  }

  function renderActivities() {
    const saved = savedSet();
    activityList.replaceChildren();
    store.ACTIVITIES.forEach(activity => {
      const article = document.createElement("article");
      article.className = "activity-item";
      const copy = document.createElement("div");
      const meta = document.createElement("p");
      meta.className = "activity-meta";
      meta.textContent = `${activity.period} · ${activity.type} · ${activity.sourceStatus}`;
      const title = document.createElement("h3");
      title.textContent = activity.title;
      const summary = document.createElement("p");
      summary.textContent = activity.summary;
      copy.append(meta, title, summary);
      const button = document.createElement("button");
      button.className = "save-button";
      button.type = "button";
      button.textContent = buttonLabel(saved.has(activity.id));
      button.classList.toggle("saved", saved.has(activity.id));
      button.addEventListener("click", () => {
        const next = new Set(store.toggleSaved(window.localStorage, activity.id));
        button.textContent = buttonLabel(next.has(activity.id));
        button.classList.toggle("saved", next.has(activity.id));
      });
      article.append(copy, button);
      activityList.appendChild(article);
    });
  }

  form.addEventListener("submit", event => {
    event.preventDefault();
    const profile = store.saveProfile(window.localStorage, currentProfile());
    renderGroups(profile);
    status.textContent = "偏好已保存在当前浏览器";
    window.setTimeout(() => { status.textContent = ""; }, 2400);
  });

  const profile = store.loadProfile(window.localStorage);
  applyProfile(profile);
  renderGroups(profile);
  renderActivities();
})();
