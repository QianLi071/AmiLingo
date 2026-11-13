(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  } else {
    root.CareerProfileStore = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const STORAGE_KEY = "amilingo.career.profile.v1";
  const VERSION = 1;
  const FIELD_LIMITS = Object.freeze({
    fullName: 100,
    preferredName: 100,
    phone: 60,
    email: 160,
    currentCity: 100,
    school: 160,
    major: 160,
    degree: 80,
    graduationMonth: 20,
    gpa: 40,
    languageAbility: 500,
    portfolioUrl: 500,
    keySkills: 1000,
    selfIntroduction: 3000,
    projectHighlight: 3000,
    availability: 500
  });

  function normalizeProfile(value) {
    const source = value && typeof value === "object" ? value : {};
    const normalized = {};
    Object.entries(FIELD_LIMITS).forEach(([key, limit]) => {
      const fieldValue = source[key];
      normalized[key] = typeof fieldValue === "string" ? fieldValue.trim().slice(0, limit) : "";
    });
    return normalized;
  }

  function load(storage) {
    try {
      const raw = storage.getItem(STORAGE_KEY);
      if (!raw) return normalizeProfile({});
      const payload = JSON.parse(raw);
      if (!payload || payload.version !== VERSION) return normalizeProfile({});
      return normalizeProfile(payload.profile);
    } catch (_error) {
      return normalizeProfile({});
    }
  }

  function save(storage, profile) {
    const normalized = normalizeProfile(profile);
    const payload = {
      version: VERSION,
      updatedAt: new Date().toISOString(),
      profile: normalized
    };
    storage.setItem(STORAGE_KEY, JSON.stringify(payload));
    return normalized;
  }

  function clear(storage) {
    storage.removeItem(STORAGE_KEY);
  }

  function filledCount(profile) {
    return Object.values(normalizeProfile(profile)).filter(Boolean).length;
  }

  return Object.freeze({ STORAGE_KEY, normalizeProfile, load, save, clear, filledCount });
});
