(function () {
  "use strict";

  const offerStore = window.CareerOfferStore;
  const applicationStore = window.CareerApplicationStore;
  const DIMENSION_LABELS = {
    roleFit: "岗位契合",
    growth: "成长空间",
    compensation: "薪酬福利",
    location: "地点与通勤",
    workLife: "工作生活",
    stability: "稳定性"
  };
  let state = offerStore.load(window.localStorage);

  const weightList = document.querySelector("#weight-list");
  const offerList = document.querySelector("#offer-list");
  const ranking = document.querySelector("#offer-ranking");
  const rankingList = document.querySelector("#ranking-list");
  const empty = document.querySelector("#offer-empty");
  const status = document.querySelector("#offer-status");
  const applicationSelect = document.querySelector("#application-offer-select");

  function setStatus(message, isError) {
    status.className = isError ? "form-status error" : "form-status success";
    status.textContent = message;
  }

  function scoreOptions(selected) {
    const fragment = document.createDocumentFragment();
    const blank = document.createElement("option");
    blank.value = "";
    blank.textContent = "未评分";
    fragment.appendChild(blank);
    [1, 2, 3, 4, 5].forEach(value => {
      const item = document.createElement("option");
      item.value = String(value);
      item.textContent = `${value} / 5`;
      item.selected = value === selected;
      fragment.appendChild(item);
    });
    return fragment;
  }

  function renderWeights() {
    weightList.replaceChildren();
    offerStore.DIMENSIONS.forEach(key => {
      const row = document.createElement("label");
      row.className = "weight-row";
      const label = document.createElement("span");
      label.textContent = DIMENSION_LABELS[key];
      const select = document.createElement("select");
      select.dataset.weight = key;
      for (let value = 0; value <= 5; value += 1) {
        const item = document.createElement("option");
        item.value = String(value);
        item.textContent = String(value);
        item.selected = state.weights[key] === value;
        select.appendChild(item);
      }
      select.addEventListener("change", () => {
        const weights = { ...state.weights, [key]: Number(select.value) };
        state = offerStore.setWeights(window.localStorage, weights);
        renderOffers();
      });
      row.append(label, select);
      weightList.appendChild(row);
    });
  }

  function buildOfferCard(offer) {
    const card = document.createElement("article");
    card.className = "offer-card";
    const head = document.createElement("div");
    head.className = "application-card-head";
    const heading = document.createElement("div");
    const company = document.createElement("p");
    company.textContent = offer.companyName;
    const title = document.createElement("h2");
    title.textContent = offer.positionTitle;
    heading.append(company, title);
    const score = document.createElement("strong");
    score.className = "offer-score";
    const calculated = offerStore.calculateScore(offer, state.weights);
    score.textContent = calculated === null ? "—" : String(calculated);
    head.append(heading, score);

    const scoreGrid = document.createElement("div");
    scoreGrid.className = "offer-score-grid";
    offerStore.DIMENSIONS.forEach(key => {
      const label = document.createElement("label");
      label.textContent = DIMENSION_LABELS[key];
      const select = document.createElement("select");
      select.dataset.score = key;
      select.appendChild(scoreOptions(offer.scores[key]));
      label.appendChild(select);
      scoreGrid.appendChild(label);
    });

    const details = document.createElement("div");
    details.className = "offer-details";
    const deadlineLabel = document.createElement("label");
    deadlineLabel.textContent = "签约截止";
    const deadline = document.createElement("input");
    deadline.type = "date";
    deadline.dataset.offerDeadline = "true";
    deadline.value = offer.deadline;
    deadlineLabel.appendChild(deadline);
    const notesLabel = document.createElement("label");
    notesLabel.textContent = "事实依据 / 待核对事项";
    const notes = document.createElement("textarea");
    notes.rows = 3;
    notes.maxLength = 2000;
    notes.dataset.offerNotes = "true";
    notes.placeholder = "例如：培养方案已书面确认；奖金计算方式仍需向 HR 核实";
    notes.value = offer.notes;
    notesLabel.appendChild(notes);
    details.append(deadlineLabel, notesLabel);

    const actions = document.createElement("div");
    actions.className = "application-card-actions";
    const save = document.createElement("button");
    save.type = "button";
    save.className = "primary-button";
    save.textContent = "保存评分";
    save.addEventListener("click", () => {
      const scores = {};
      offerStore.DIMENSIONS.forEach(key => {
        const raw = card.querySelector(`[data-score='${key}']`).value;
        scores[key] = raw ? Number(raw) : null;
      });
      try {
        state = offerStore.update(window.localStorage, offer.id, {
          scores,
          deadline: deadline.value,
          notes: notes.value
        });
        setStatus(`已保存：${offer.companyName} · ${offer.positionTitle}`, false);
        renderOffers();
      } catch (error) {
        setStatus(`保存失败：${error.message}`, true);
      }
    });
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "text-button remove-application";
    remove.textContent = "移出比较";
    remove.addEventListener("click", () => {
      if (!window.confirm(`确定移出“${offer.companyName} · ${offer.positionTitle}”吗？`)) return;
      state = offerStore.remove(window.localStorage, offer.id);
      renderOffers();
    });
    actions.append(save, remove);
    card.append(head, scoreGrid, details, actions);
    return card;
  }

  function renderRanking() {
    const scored = state.offers
      .map(offer => ({ offer, score: offerStore.calculateScore(offer, state.weights) }))
      .filter(item => item.score !== null)
      .sort((a, b) => b.score - a.score);
    ranking.hidden = scored.length < 2;
    rankingList.replaceChildren();
    scored.forEach((item, index) => {
      const row = document.createElement("div");
      const rank = document.createElement("span");
      rank.textContent = String(index + 1);
      const label = document.createElement("strong");
      label.textContent = `${item.offer.companyName} · ${item.offer.positionTitle}`;
      const score = document.createElement("b");
      score.textContent = `${item.score} 分`;
      row.append(rank, label, score);
      rankingList.appendChild(row);
    });
  }

  function renderOffers() {
    offerList.replaceChildren();
    state.offers.forEach(offer => offerList.appendChild(buildOfferCard(offer)));
    empty.hidden = state.offers.length > 0;
    renderRanking();
  }

  function renderApplicationOptions() {
    const applications = applicationStore.load(window.localStorage).filter(item => item.status === "OFFER");
    applications.forEach(application => {
      const item = document.createElement("option");
      item.value = application.id;
      item.textContent = `${application.companyName} · ${application.positionTitle}`;
      applicationSelect.appendChild(item);
    });
    document.querySelector("#import-offer").disabled = applications.length === 0;
  }

  document.querySelector("#import-offer").addEventListener("click", () => {
    const application = applicationStore.load(window.localStorage).find(item => item.id === applicationSelect.value);
    if (!application) return;
    try {
      state = offerStore.add(window.localStorage, {
        applicationId: application.id,
        companyName: application.companyName,
        positionTitle: application.positionTitle,
        deadline: application.deadline
      });
      setStatus("已从“我的投递”导入 Offer。", false);
      renderOffers();
    } catch (error) {
      setStatus(`导入失败：${error.message}`, true);
    }
  });

  const manualForm = document.querySelector("#manual-offer-form");
  document.querySelector("#toggle-offer-form").addEventListener("click", () => {
    manualForm.hidden = !manualForm.hidden;
    if (!manualForm.hidden) document.querySelector("#offer-company").focus();
  });
  manualForm.addEventListener("submit", event => {
    event.preventDefault();
    try {
      state = offerStore.add(window.localStorage, {
        companyName: document.querySelector("#offer-company").value,
        positionTitle: document.querySelector("#offer-position").value,
        deadline: document.querySelector("#offer-deadline").value
      });
      manualForm.reset();
      manualForm.hidden = true;
      setStatus("Offer 已加入比较。", false);
      renderOffers();
    } catch (error) {
      setStatus(`添加失败：${error.message}`, true);
    }
  });

  renderWeights();
  renderApplicationOptions();
  renderOffers();
})();
