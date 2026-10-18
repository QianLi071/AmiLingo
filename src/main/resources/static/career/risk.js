(function () {
  "use strict";

  const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080" : "";
  const LEVEL_LABELS = { LOW: "较低风险", MEDIUM: "需要进一步核实", HIGH: "高风险信号" };

  const form = document.querySelector("#risk-form");
  const submit = document.querySelector("#risk-submit");
  const result = document.querySelector("#risk-result");
  const placeholder = document.querySelector("#risk-placeholder");
  const content = document.querySelector("#risk-content");

  function triState(id) {
    const value = document.querySelector(id).value;
    return value === "" ? null : value === "true";
  }

  function optionalNumber(id) {
    const value = document.querySelector(id).value.trim();
    return value === "" ? null : Number(value);
  }

  function optionalText(id) {
    const value = document.querySelector(id).value.trim();
    return value || null;
  }

  function requestBody() {
    return {
      companyName: document.querySelector("#company-name").value.trim(),
      registrationDate: optionalText("#registration-date"),
      registeredCapitalAmount: optionalNumber("#capital-amount"),
      registeredCapitalCurrency: optionalText("#capital-currency"),
      employeeCount: optionalNumber("#employee-count"),
      verifiedBusinessAddress: triState("#verified-address"),
      officialWebsite: triState("#official-website"),
      salaryUnusuallyHigh: triState("#unusual-salary"),
      chargesApplicantFees: triState("#applicant-fees"),
      requestsSensitiveDataBeforeOffer: triState("#sensitive-data"),
      usesOnlyPersonalMessaging: triState("#personal-messaging"),
      jobDescriptionClear: triState("#clear-description"),
      providesWrittenTerms: triState("#written-terms")
    };
  }

  function appendListItem(list, text, className) {
    const item = document.createElement("li");
    if (className) item.className = className;
    item.textContent = text;
    list.appendChild(item);
    return item;
  }

  function render(data) {
    result.classList.remove("empty");
    placeholder.hidden = true;
    content.hidden = false;
    document.querySelector("#result-company").textContent = data.companyName;
    document.querySelector("#risk-score").textContent = String(data.riskScore);
    document.querySelector("#score-ring").style.setProperty("--score-angle", `${data.riskScore * 3.6}deg`);

    const level = document.querySelector("#risk-level");
    level.className = `risk-level ${data.riskLevel.toLowerCase()}`;
    level.textContent = LEVEL_LABELS[data.riskLevel] || data.riskLevel;

    const signalList = document.querySelector("#signal-list");
    signalList.replaceChildren();
    if (!data.signals.length) {
      appendListItem(signalList, "根据目前填写的信息，没有发现明确风险信号。仍建议完成基础核实。", "signal");
    } else {
      data.signals.forEach(signal => {
        const item = appendListItem(signalList, "", `signal ${signal.severity.toLowerCase()}`);
        const title = document.createElement("strong");
        title.textContent = signal.title;
        const explanation = document.createElement("p");
        explanation.textContent = signal.explanation;
        item.append(title, explanation);
      });
    }

    const verificationList = document.querySelector("#verification-list");
    verificationList.replaceChildren();
    data.verificationChecklist.forEach(text => appendListItem(verificationList, text));
    document.querySelector("#risk-disclaimer").textContent = data.disclaimer;
    result.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function renderError(message) {
    result.classList.remove("empty");
    placeholder.hidden = false;
    content.hidden = true;
    placeholder.querySelector("h2").textContent = "暂时无法生成结果";
    placeholder.querySelector("p").textContent = message;
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    submit.disabled = true;
    submit.textContent = "正在检查…";
    try {
      const response = await fetch(`${API_BASE}/api/v1/career/risk-assessments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody())
      });
      const payload = await response.json();
      if (!response.ok || !payload.success) throw new Error(payload.message || `HTTP ${response.status}`);
      render(payload.data);
    } catch (error) {
      renderError(`检查失败：${error.message}。请确认 Java 后端已经启动。`);
    } finally {
      submit.disabled = false;
      submit.textContent = "生成核验结果";
    }
  });

  const company = new URLSearchParams(window.location.search).get("company");
  if (company) document.querySelector("#company-name").value = company;
})();
