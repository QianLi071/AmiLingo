(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.CareerFitPrefill = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function text(value, limit) {
    return typeof value === "string" ? value.trim().slice(0, limit) : "";
  }

  function splitSkills(value) {
    return Array.from(new Set(text(value, 4000).split(/[，,、;；\n]/).map(item => item.trim()).filter(Boolean))).slice(0, 30);
  }

  function mergeSkills(existing, incoming) {
    return Array.from(new Set([...splitSkills(existing), ...splitSkills(incoming)])).slice(0, 30).join("、");
  }

  function mergeText(existing, additions, limit) {
    const current = text(existing, limit);
    const values = (Array.isArray(additions) ? additions : [additions]).map(value => text(value, limit)).filter(Boolean);
    return [current, ...values.filter(value => !current.includes(value))].filter(Boolean).join("\n").slice(0, limit);
  }

  function fromCareerProfile(profile) {
    const source = profile && typeof profile === "object" ? profile : {};
    return {
      major: text(source.major, 128),
      skills: mergeSkills("", source.keySkills),
      experienceSummary: mergeText("", source.projectHighlight, 2000)
    };
  }

  function fromClubExperiences(items, makeDraft) {
    const source = Array.isArray(items) ? items : [];
    const draft = typeof makeDraft === "function" ? makeDraft : () => "";
    return source.map(item => text(draft(item), 1000)).filter(Boolean).slice(0, 8);
  }

  return Object.freeze({ splitSkills, mergeSkills, mergeText, fromCareerProfile, fromClubExperiences });
});
