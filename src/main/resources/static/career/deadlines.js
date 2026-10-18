(function () {
  "use strict";

  const utils = window.CareerDeadlineUtils;
  const CATEGORY_LABELS = {
    OVERDUE: "已逾期",
    TODAY: "今天截止",
    URGENT: "7 天内",
    UPCOMING: "30 天内",
    LATER: "稍后"
  };
  const filter = document.querySelector("#deadline-filter");
  const list = document.querySelector("#deadline-list");
  const empty = document.querySelector("#deadline-empty");
  const today = utils.todayString();
  const items = utils.collect(
    window.CareerApplicationStore.load(window.localStorage),
    window.CareerOfferStore.load(window.localStorage).offers,
    today
  );

  function countdownText(item) {
    if (item.days < 0) return `已超过 ${Math.abs(item.days)} 天`;
    if (item.days === 0) return "今天截止";
    return `还剩 ${item.days} 天`;
  }

  function renderSummary() {
    const container = document.querySelector("#deadline-summary");
    container.replaceChildren();
    ["OVERDUE", "TODAY", "URGENT", "UPCOMING"].forEach(category => {
      const row = document.createElement("div");
      const strong = document.createElement("strong");
      strong.textContent = String(items.filter(item => item.category === category).length);
      const span = document.createElement("span");
      span.textContent = CATEGORY_LABELS[category];
      row.append(strong, span);
      container.appendChild(row);
    });
    document.querySelector("#urgent-count").textContent = String(
      items.filter(item => item.days >= 0 && item.days <= 7).length
    );
  }

  function buildItem(item) {
    const card = document.createElement("a");
    card.className = `deadline-item ${item.category.toLowerCase()}`;
    card.href = item.target;
    const date = document.createElement("div");
    date.className = "deadline-date";
    const day = document.createElement("strong");
    day.textContent = item.deadline.slice(8, 10);
    const month = document.createElement("span");
    month.textContent = item.deadline.slice(0, 7);
    date.append(day, month);
    const info = document.createElement("div");
    const source = document.createElement("span");
    source.textContent = item.source === "OFFER" ? "签约截止" : "申请截止";
    const title = document.createElement("h3");
    title.textContent = item.positionTitle;
    const company = document.createElement("p");
    company.textContent = item.companyName;
    info.append(source, title, company);
    const countdown = document.createElement("b");
    countdown.textContent = countdownText(item);
    card.append(date, info, countdown);
    return card;
  }

  function render() {
    const value = filter.value;
    const visible = items.filter(item => {
      if (!value) return true;
      if (value === "ACTIVE") return item.days >= 0;
      if (value === "OVERDUE") return item.days < 0;
      return item.source === value;
    });
    list.replaceChildren();
    visible.forEach(item => list.appendChild(buildItem(item)));
    empty.hidden = visible.length > 0;
  }

  document.querySelector("#today-label").textContent = `今天：${today}`;
  filter.addEventListener("change", render);
  renderSummary();
  render();
})();
