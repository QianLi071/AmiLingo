(function () {
  "use strict";

  const store = window.CareerProfileStore;
  const FIELD_DEFINITIONS = [
    { key: "fullName", label: "姓名", selector: "#full-name", group: "基本信息" },
    { key: "preferredName", label: "英文名 / 常用名", selector: "#preferred-name", group: "基本信息" },
    { key: "phone", label: "电话", selector: "#phone", group: "基本信息" },
    { key: "email", label: "邮箱", selector: "#email", group: "基本信息" },
    { key: "currentCity", label: "当前城市", selector: "#current-city", group: "基本信息" },
    { key: "portfolioUrl", label: "作品集 / 个人主页", selector: "#portfolio-url", group: "基本信息" },
    { key: "school", label: "学校", selector: "#school", group: "教育信息" },
    { key: "major", label: "专业", selector: "#profile-major", group: "教育信息" },
    { key: "degree", label: "学历", selector: "#degree", group: "教育信息" },
    { key: "graduationMonth", label: "毕业年月", selector: "#graduation-month", group: "教育信息" },
    { key: "gpa", label: "GPA / 成绩说明", selector: "#gpa", group: "教育信息" },
    { key: "languageAbility", label: "语言能力", selector: "#language-ability", group: "教育信息" },
    { key: "keySkills", label: "核心技能", selector: "#key-skills", group: "申请内容" },
    { key: "selfIntroduction", label: "自我介绍", selector: "#self-introduction", group: "申请内容" },
    { key: "projectHighlight", label: "项目或经历亮点", selector: "#project-highlight", group: "申请内容" },
    { key: "availability", label: "到岗时间 / 可实习周期", selector: "#availability", group: "申请内容" }
  ];

  const form = document.querySelector("#profile-form");
  const status = document.querySelector("#profile-status");
  const copyList = document.querySelector("#copy-list");
  const copyEmpty = document.querySelector("#copy-empty");
  const search = document.querySelector("#field-search");
  let currentProfile = store.normalizeProfile({});

  function readForm() {
    const profile = {};
    FIELD_DEFINITIONS.forEach(field => {
      profile[field.key] = document.querySelector(field.selector).value;
    });
    return store.normalizeProfile(profile);
  }

  function fillForm(profile) {
    FIELD_DEFINITIONS.forEach(field => {
      document.querySelector(field.selector).value = profile[field.key] || "";
    });
  }

  function updateCompletion(profile) {
    document.querySelector("#completion-badge").textContent = `${store.filledCount(profile)} 项已填写`;
  }

  async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return;
    }
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "");
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand("copy");
    textarea.remove();
    if (!copied) throw new Error("浏览器拒绝了剪贴板访问");
  }

  function renderCopyBoard(profile, keyword) {
    const normalizedKeyword = (keyword || "").trim().toLowerCase();
    copyList.replaceChildren();
    const visibleFields = FIELD_DEFINITIONS.filter(field => {
      const value = profile[field.key];
      if (!value) return false;
      if (!normalizedKeyword) return true;
      return `${field.label} ${field.group} ${value}`.toLowerCase().includes(normalizedKeyword);
    });

    copyEmpty.hidden = visibleFields.length > 0;
    visibleFields.forEach(field => {
      const item = document.createElement("article");
      item.className = "copy-item";
      const meta = document.createElement("div");
      const label = document.createElement("span");
      label.textContent = field.group;
      const heading = document.createElement("strong");
      heading.textContent = field.label;
      meta.append(label, heading);
      const value = document.createElement("p");
      value.textContent = profile[field.key];
      const button = document.createElement("button");
      button.type = "button";
      button.className = "copy-button";
      button.textContent = "复制";
      button.addEventListener("click", async () => {
        try {
          await copyText(profile[field.key]);
          button.textContent = "已复制";
          button.classList.add("copied");
          window.setTimeout(() => {
            button.textContent = "复制";
            button.classList.remove("copied");
          }, 1600);
        } catch (error) {
          status.className = "form-status error";
          status.textContent = `复制失败：${error.message}`;
        }
      });
      item.append(meta, value, button);
      copyList.appendChild(item);
    });
  }

  function setStatus(message, error) {
    status.className = error ? "form-status error" : "form-status success";
    status.textContent = message;
  }

  form.addEventListener("submit", event => {
    event.preventDefault();
    try {
      currentProfile = store.save(window.localStorage, readForm());
      updateCompletion(currentProfile);
      renderCopyBoard(currentProfile, search.value);
      setStatus("已保存到当前浏览器。关闭网页后仍可再次使用。", false);
    } catch (error) {
      setStatus(`保存失败：${error.message}。请检查浏览器是否允许本地存储。`, true);
    }
  });

  document.querySelector("#clear-profile").addEventListener("click", () => {
    const confirmed = window.confirm("确定清除当前浏览器中保存的求职资料吗？此操作无法撤销。");
    if (!confirmed) return;
    try {
      store.clear(window.localStorage);
      currentProfile = store.normalizeProfile({});
      form.reset();
      updateCompletion(currentProfile);
      renderCopyBoard(currentProfile, "");
      search.value = "";
      setStatus("本地求职资料已清除。", false);
    } catch (error) {
      setStatus(`清除失败：${error.message}`, true);
    }
  });

  search.addEventListener("input", () => renderCopyBoard(currentProfile, search.value));

  currentProfile = store.load(window.localStorage);
  fillForm(currentProfile);
  updateCompletion(currentProfile);
  renderCopyBoard(currentProfile, "");
})();
