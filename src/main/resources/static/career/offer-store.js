(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  } else {
    root.CareerOfferStore = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const STORAGE_KEY = "amilingo.career.offers.v1";
  const VERSION = 1;
  const DIMENSIONS = Object.freeze([
    "roleFit", "growth", "compensation", "location", "workLife", "stability"
  ]);
  const DEFAULT_WEIGHTS = Object.freeze({
    roleFit: 5,
    growth: 5,
    compensation: 4,
    location: 3,
    workLife: 4,
    stability: 3
  });

  function text(value, limit) {
    return typeof value === "string" ? value.trim().slice(0, limit) : "";
  }

  function clampNumber(value, min, max, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback;
  }

  function createId() {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
    return `offer-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function normalizeWeights(value) {
    const source = value && typeof value === "object" ? value : {};
    const result = {};
    DIMENSIONS.forEach(key => {
      result[key] = clampNumber(source[key], 0, 5, DEFAULT_WEIGHTS[key]);
    });
    return result;
  }

  function normalizeScores(value) {
    const source = value && typeof value === "object" ? value : {};
    const result = {};
    DIMENSIONS.forEach(key => {
      const parsed = Number(source[key]);
      result[key] = Number.isFinite(parsed) && parsed >= 1 && parsed <= 5 ? parsed : null;
    });
    return result;
  }

  function normalizeOffer(value) {
    const source = value && typeof value === "object" ? value : {};
    const deadline = text(source.deadline, 10);
    return {
      id: text(source.id, 100) || createId(),
      applicationId: text(source.applicationId, 100),
      companyName: text(source.companyName, 128),
      positionTitle: text(source.positionTitle, 128),
      deadline: /^\d{4}-\d{2}-\d{2}$/.test(deadline) ? deadline : "",
      scores: normalizeScores(source.scores),
      notes: text(source.notes, 2000),
      createdAt: text(source.createdAt, 40),
      updatedAt: text(source.updatedAt, 40)
    };
  }

  function emptyState() {
    return { weights: normalizeWeights(DEFAULT_WEIGHTS), offers: [] };
  }

  function load(storage) {
    try {
      const raw = storage.getItem(STORAGE_KEY);
      if (!raw) return emptyState();
      const payload = JSON.parse(raw);
      if (!payload || payload.version !== VERSION || !Array.isArray(payload.offers)) return emptyState();
      return {
        weights: normalizeWeights(payload.weights),
        offers: payload.offers.map(normalizeOffer).filter(item => item.companyName && item.positionTitle).slice(0, 10)
      };
    } catch (_error) {
      return emptyState();
    }
  }

  function persist(storage, state) {
    const normalized = {
      weights: normalizeWeights(state.weights),
      offers: (state.offers || []).map(normalizeOffer).filter(item => item.companyName && item.positionTitle).slice(0, 10)
    };
    storage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, ...normalized }));
    return normalized;
  }

  function add(storage, value) {
    const state = load(storage);
    const now = new Date().toISOString();
    const offer = normalizeOffer({ ...value, id: createId(), createdAt: now, updatedAt: now });
    if (!offer.companyName || !offer.positionTitle) throw new Error("公司和岗位名称不能为空");
    if (offer.applicationId && state.offers.some(item => item.applicationId === offer.applicationId)) return state;
    state.offers.unshift(offer);
    return persist(storage, state);
  }

  function update(storage, id, changes) {
    const state = load(storage);
    const index = state.offers.findIndex(item => item.id === id);
    if (index < 0) return state;
    state.offers[index] = normalizeOffer({ ...state.offers[index], ...changes, id, updatedAt: new Date().toISOString() });
    return persist(storage, state);
  }

  function remove(storage, id) {
    const state = load(storage);
    state.offers = state.offers.filter(item => item.id !== id);
    return persist(storage, state);
  }

  function setWeights(storage, weights) {
    const state = load(storage);
    state.weights = normalizeWeights(weights);
    return persist(storage, state);
  }

  function calculateScore(offer, weights) {
    const normalizedOffer = normalizeOffer(offer);
    const normalizedWeights = normalizeWeights(weights);
    let earned = 0;
    let possible = 0;
    DIMENSIONS.forEach(key => {
      const score = normalizedOffer.scores[key];
      const weight = normalizedWeights[key];
      if (score !== null && weight > 0) {
        earned += score * weight;
        possible += 5 * weight;
      }
    });
    return possible ? Math.round(earned * 100 / possible) : null;
  }

  return Object.freeze({
    STORAGE_KEY, DIMENSIONS, DEFAULT_WEIGHTS, normalizeOffer, normalizeWeights,
    load, add, update, remove, setWeights, calculateScore
  });
});
