(function () {
  "use strict";

  const store = window.CareerApplicationStore;
  const STATUS_LABELS = {
    SAVED: "待投递",
    APPLIED: "已投递",
    WRITTEN_TEST: "笔试",
    INTERVIEW: "面试",
    OFFER: "Offer",
    HIRED: "已入职",
    REJECTED: "已拒绝",
    WITHDRAWN: "已放弃"
  };
  const ACTIVE_STATUSES = ["SAVED", "APPLIED", "WRITTEN_TEST", "INTERVIEW", "OFFER"];

  const list = document.querySelector("#application-list");
  const empty = document.querySelector("#application-empty");
  const status = document.querySelector("#application-status");
  const search = document.querySelector("#application-search");
  const statusFilter = document.querySelector("#status-filter");
  const manualForm = document.querySelector("#manual-application-form");
  let applications = [];

  function today() {
    return new Date().toISOString().slice(0, 10);
  }

  function setStatus(message, isError) {
    status.className = isError ? "status error" : "status success";
    status.textContent = message;
  }

  function option(value, selected) {
    const element = document.createElement("option");
    element.value = value;
    element.textContent = STATUS_LABELS[value];
    element.selected = value === selected;
    return element;
  }

  function saveCard(item, card) {
    const newStatus = card.querySelector("[data-field='status']").value;
    const dateInput = card.querySelector("[data-field='applicationDate']");
    if (!dateInput.value && newStatus !== "SAVED") dateInput.value = today();
    try {
      applications = store.update(window.localStorage, item.id, {
        status: newStatus,
        applicationDate: dateInput.value,
        deadline: card.querySelector("[data-field='deadline']").value,
        notes: card.querySelector("[data-field='notes']").value
      });
      setStatus(`已更新：${item.companyName} · ${item.positionTitle}`, false);
      render();
    } catch (error) {
      setStatus(`更新失败：${error.message}`, true);
    }
  }

  function buildCard(item) {
    const card = document.createElement("article");
    card.className = "application-card";
    const head = document.createElement("div");
    head.className = "application-card-head";
    const heading = document.createElement("div");
    const company = document.createElement("p");
    company.textContent = item.companyName;
    const title = document.createElement("h2");
    title.textContent = item.positionTitle;
    heading.append(company, title);
    const badge = document.createElement("span");
    badge.className = `application-status-badge ${item.status.toLowerCase()}`;
    badge.textContent = STATUS_LABELS[item.status] || item.status;
    head.append(heading, badge);

    const fields = document.createElement("div");
    fields.className = "application-fields";

    const statusLabel = document.createElement("label");
    statusLabel.textContent = "当前进度";
    const statusSelect = document.createElement("select");
    statusSelect.dataset.field = "status";
    store.STATUSES.forEach(value => statusSelect.appendChild(option(value, item.status)));
    statusLabel.appendChild(statusSelect);

    const appliedLabel = document.createElement("label");
    appliedLabel.textContent = "投递日期";
    const applied = document.createElement("input");
    applied.type = "date";
    applied.dataset.field = "applicationDate";
    applied.value = item.applicationDate;
    appliedLabel.appendChild(applied);

    const deadlineLabel = document.createElement("label");
    deadlineLabel.textContent = "截止日期";
    const deadline = document.createElement("input");
    deadline.type = "date";
    deadline.dataset.field = "deadline";
    deadline.value = item.deadline;
    deadlineLabel.appendChild(deadline);
    fields.append(statusLabel, appliedLabel, deadlineLabel);

    const notesLabel = document.createElement("label");
    notesLabel.className = "application-notes";
    notesLabel.textContent = "备注 / 下一步";
    const notes = document.createElement("textarea");
    notes.dataset.field = "notes";
    notes.maxLength = 2000;
    notes.rows = 3;
    notes.placeholder = "例如：10月25日前准备笔试；面试需复习项目经历";
    notes.value = item.notes;
    notesLabel.appendChild(notes);

    const actions = document.createElement("div");
    actions.className = "application-card-actions";
    const save = document.createElement("button");
    save.type = "button";
    save.className = "primary-button";
    save.textContent = "保存更新";
    save.addEventListener("click", () => saveCard(item, card));
    actions.appendChild(save);
    if (item.officialUrl) {
      const official = document.createElement("a");
      official.className = "secondary-link compact-link";
      official.href = item.officialUrl;
      official.target = "_blank";
      official.rel = "noopener noreferrer";
      official.textContent = "打开官方页面";
      actions.appendChild(official);
    }
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "text-button remove-application";
    remove.textContent = "删除记录";
    remove.addEventListener("click", () => {
      if (!window.confirm(`确定删除“${item.companyName} · ${item.positionTitle}”吗？`)) return;
      applications = store.remove(window.localStorage, item.id);
      setStatus("投递记录已删除。", false);
      render();
    });
    actions.appendChild(remove);
    card.append(head, fields, notesLabel, actions);
    return card;
  }

  function renderSummary() {
    const container = document.querySelector("#application-summary");
    container.replaceChildren();
    const summaries = [
      ["进行中", applications.filter(item => ACTIVE_STATUSES.includes(item.status)).length],
      ["面试", applications.filter(item => item.status === "INTERVIEW").length],
      ["Offer", applications.filter(item => item.status === "OFFER").length]
    ];
    summaries.forEach(([label, value]) => {
      const item = document.createElement("div");
      const strong = document.createElement("strong");
      strong.textContent = String(value);
      const span = document.createElement("span");
      span.textContent = label;
      item.append(strong, span);
      container.appendChild(item);
    });
  }

  function render() {
    const keyword = search.value.trim().toLowerCase();
    const selectedStatus = statusFilter.value;
    const filtered = applications.filter(item => {
      if (selectedStatus && item.status !== selectedStatus) return false;
      if (!keyword) return true;
      return `${item.companyName} ${item.positionTitle} ${item.notes}`.toLowerCase().includes(keyword);
    });
    list.replaceChildren();
    filtered.forEach(item => list.appendChild(buildCard(item)));
    empty.hidden = applications.length > 0;
    document.querySelector("#application-count").textContent = String(applications.length);
    renderSummary();
    if (applications.length && !filtered.length) {
      setStatus("没有符合当前筛选条件的记录。", false);
    } else if (!status.classList.contains("error")) {
      status.textContent = filtered.length ? `当前显示 ${filtered.length} 条记录` : "";
    }
  }

  document.querySelector("#toggle-manual-form").addEventListener("click", () => {
    manualForm.hidden = false;
    document.querySelector("#manual-company").focus();
  });
  document.querySelector("#close-manual-form").addEventListener("click", () => {
    manualForm.hidden = true;
  });
  manualForm.addEventListener("submit", event => {
    event.preventDefault();
    const manualStatus = document.querySelector("#manual-status");
    try {
      applications = store.addManual(window.localStorage, {
        companyName: document.querySelector("#manual-company").value,
        positionTitle: document.querySelector("#manual-position").value,
        officialUrl: document.querySelector("#manual-url").value,
        deadline: document.querySelector("#manual-deadline").value
      });
      manualForm.reset();
      manualForm.hidden = true;
      manualStatus.textContent = "";
      setStatus("已添加一条本地投递记录。", false);
      render();
    } catch (error) {
      manualStatus.className = "form-status error";
      manualStatus.textContent = `添加失败：${error.message}`;
    }
  });
  search.addEventListener("input", render);
  statusFilter.addEventListener("change", render);

  applications = store.load(window.localStorage);
  render();
})();
