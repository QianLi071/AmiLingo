(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  } else {
    root.CareerApplicationStore = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const STORAGE_KEY = "amilingo.career.applications.v1";
  const VERSION = 1;
  const STATUSES = Object.freeze([
    "SAVED", "APPLIED", "WRITTEN_TEST", "INTERVIEW", "OFFER",
    "HIRED", "REJECTED", "WITHDRAWN"
  ]);

  function text(value, limit) {
    return typeof value === "string" ? value.trim().slice(0, limit) : "";
  }

  function safeHttpsUrl(value) {
    const raw = text(value, 1000);
    if (!raw) return "";
    try {
      const url = new URL(raw);
      return url.protocol === "https:" ? url.href : "";
    } catch (_error) {
      return "";
    }
  }

  function dateValue(value) {
    const raw = text(value, 10);
    return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : "";
  }

  function createId() {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
      return crypto.randomUUID();
    }
    return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function normalizeItem(value) {
    const source = value && typeof value === "object" ? value : {};
    const status = STATUSES.includes(source.status) ? source.status : "SAVED";
    return {
      id: text(source.id, 100) || createId(),
      sourceJobId: text(source.sourceJobId, 100),
      companyName: text(source.companyName, 128),
      positionTitle: text(source.positionTitle, 128),
      region: text(source.region, 32),
      jobType: text(source.jobType, 32),
      officialUrl: safeHttpsUrl(source.officialUrl),
      status,
      applicationDate: dateValue(source.applicationDate),
      deadline: dateValue(source.deadline),
      notes: text(source.notes, 2000),
      createdAt: text(source.createdAt, 40),
      updatedAt: text(source.updatedAt, 40)
    };
  }

  function load(storage) {
    try {
      const raw = storage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const payload = JSON.parse(raw);
      if (!payload || payload.version !== VERSION || !Array.isArray(payload.items)) return [];
      return payload.items.map(normalizeItem).filter(item => item.companyName && item.positionTitle).slice(0, 200);
    } catch (_error) {
      return [];
    }
  }

  function persist(storage, items) {
    const normalized = items.map(normalizeItem).filter(item => item.companyName && item.positionTitle).slice(0, 200);
    storage.setItem(STORAGE_KEY, JSON.stringify({ version: VERSION, items: normalized }));
    return normalized;
  }

  function addOrUpdateFromJob(storage, job) {
    const items = load(storage);
    const sourceJobId = text(job && job.id, 100);
    const existingIndex = sourceJobId ? items.findIndex(item => item.sourceJobId === sourceJobId) : -1;
    const now = new Date().toISOString();
    const snapshot = normalizeItem({
      id: existingIndex >= 0 ? items[existingIndex].id : createId(),
      sourceJobId,
      companyName: job && job.companyName,
      positionTitle: job && job.positionTitle,
      region: job && job.region,
      jobType: job && job.jobType,
      officialUrl: job && job.officialUrl,
      status: existingIndex >= 0 ? items[existingIndex].status : "SAVED",
      applicationDate: existingIndex >= 0 ? items[existingIndex].applicationDate : "",
      deadline: job && job.applicationDeadline,
      notes: existingIndex >= 0 ? items[existingIndex].notes : "",
      createdAt: existingIndex >= 0 ? items[existingIndex].createdAt : now,
      updatedAt: now
    });
    if (!snapshot.companyName || !snapshot.positionTitle) {
      throw new Error("公司和岗位名称不能为空");
    }
    if (existingIndex >= 0) items[existingIndex] = snapshot;
    else items.unshift(snapshot);
    return persist(storage, items);
  }

  function addManual(storage, item) {
    const now = new Date().toISOString();
    const normalized = normalizeItem({ ...item, id: createId(), createdAt: now, updatedAt: now });
    if (!normalized.companyName || !normalized.positionTitle) {
      throw new Error("公司和岗位名称不能为空");
    }
    return persist(storage, [normalized, ...load(storage)]);
  }

  function update(storage, id, changes) {
    const items = load(storage);
    const index = items.findIndex(item => item.id === id);
    if (index < 0) return items;
    items[index] = normalizeItem({ ...items[index], ...changes, id, updatedAt: new Date().toISOString() });
    return persist(storage, items);
  }

  function remove(storage, id) {
    return persist(storage, load(storage).filter(item => item.id !== id));
  }

  function containsJob(storage, sourceJobId) {
    const normalizedId = text(sourceJobId, 100);
    return Boolean(normalizedId) && load(storage).some(item => item.sourceJobId === normalizedId);
  }

  return Object.freeze({
    STORAGE_KEY, STATUSES, normalizeItem, load, addOrUpdateFromJob, addManual, update, remove, containsJob
  });
});
