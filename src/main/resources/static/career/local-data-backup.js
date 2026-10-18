(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.AmiLingoLocalDataBackup = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const FORMAT = "amilingo-local-backup";
  const VERSION = 1;
  const MAX_FILE_BYTES = 2 * 1024 * 1024;
  const DEFINITIONS = Object.freeze([
    { key: "amilingo.career.profile.v1", group: "就业实习", label: "求职资料", limit: 80 * 1024 },
    { key: "amilingo.career.applications.v1", group: "就业实习", label: "投递记录", limit: 350 * 1024 },
    { key: "amilingo.career.offers.v1", group: "就业实习", label: "Offer 对比", limit: 150 * 1024 },
    { key: "amilingo.career.interviews.v1", group: "就业实习", label: "面试复盘", limit: 600 * 1024 },
    { key: "amilingo.career.written-practice.v1", group: "就业实习", label: "笔试练习", limit: 500 * 1024 },
    { key: "amilingo.club.profile.v1", group: "校园探索", label: "社团偏好", limit: 40 * 1024 },
    { key: "amilingo.club.saved.v1", group: "校园探索", label: "收藏内容", limit: 40 * 1024 },
    { key: "amilingo.club.experiences.v1", group: "校园探索", label: "参与经历", limit: 300 * 1024 }
  ]);
  const BY_KEY = new Map(DEFINITIONS.map(item => [item.key, item]));

  function byteLength(value) {
    const text = String(value || "");
    if (typeof TextEncoder !== "undefined") return new TextEncoder().encode(text).length;
    return unescape(encodeURIComponent(text)).length;
  }

  function readPayload(raw, definition) {
    if (byteLength(raw) > definition.limit) throw new Error(`${definition.label}数据超过允许大小`);
    let payload;
    try { payload = JSON.parse(raw); } catch (_error) { throw new Error(`${definition.label}数据格式不正确`); }
    if (!payload || typeof payload !== "object" || Array.isArray(payload) || payload.version !== 1) {
      throw new Error(`${definition.label}版本不受支持`);
    }
    return payload;
  }

  function selectedDefinitions(keys) {
    if (keys === undefined || keys === null) return [...DEFINITIONS];
    const selected = new Set(Array.isArray(keys) ? keys : []);
    return DEFINITIONS.filter(item => selected.has(item.key));
  }

  function itemCount(payload) {
    if (Array.isArray(payload.items)) return payload.items.length;
    if (Array.isArray(payload.offers)) return payload.offers.length;
    if (Array.isArray(payload.ids)) return payload.ids.length;
    if (Array.isArray(payload.experiences)) return payload.experiences.length;
    if (payload.profile && typeof payload.profile === "object") {
      return Object.values(payload.profile).filter(value => Array.isArray(value) ? value.length : Boolean(value)).length;
    }
    return 1;
  }

  function inspectStorage(storage) {
    return DEFINITIONS.map(definition => {
      const raw = storage.getItem(definition.key);
      if (!raw) return { ...definition, available: false, count: 0, bytes: 0 };
      try {
        const payload = readPayload(raw, definition);
        return { ...definition, available: true, count: itemCount(payload), bytes: byteLength(raw) };
      } catch (error) {
        return { ...definition, available: false, invalid: true, count: 0, bytes: byteLength(raw), error: error.message };
      }
    });
  }

  function createBackup(storage, keys, now) {
    const data = {};
    selectedDefinitions(keys).forEach(definition => {
      const raw = storage.getItem(definition.key);
      if (raw) data[definition.key] = readPayload(raw, definition);
    });
    if (!Object.keys(data).length) throw new Error("没有可导出的本地数据");
    return {
      format: FORMAT,
      version: VERSION,
      exportedAt: (now || new Date()).toISOString(),
      data
    };
  }

  function parseBackup(text) {
    if (typeof text !== "string" || !text.trim()) throw new Error("请选择有效的备份文件");
    if (byteLength(text) > MAX_FILE_BYTES) throw new Error("备份文件不能超过 2 MB");
    let backup;
    try { backup = JSON.parse(text); } catch (_error) { throw new Error("备份文件不是有效的 JSON"); }
    if (!backup || backup.format !== FORMAT || backup.version !== VERSION || !backup.data || typeof backup.data !== "object" || Array.isArray(backup.data)) {
      throw new Error("这不是受支持的 AmiLingo 本地备份文件");
    }
    const data = {};
    const items = [];
    Object.keys(backup.data).forEach(key => {
      const definition = BY_KEY.get(key);
      if (!definition) return;
      const raw = JSON.stringify(backup.data[key]);
      const payload = readPayload(raw, definition);
      data[key] = payload;
      items.push({ ...definition, count: itemCount(payload), bytes: byteLength(raw) });
    });
    if (!items.length) throw new Error("备份文件中没有可导入的数据");
    return { format: FORMAT, version: VERSION, exportedAt: backup.exportedAt || "", data, items };
  }

  function restoreBackup(storage, backup, keys) {
    if (!backup || backup.format !== FORMAT || backup.version !== VERSION || !backup.data) throw new Error("备份内容无效");
    const restored = [];
    selectedDefinitions(keys).forEach(definition => {
      if (!Object.prototype.hasOwnProperty.call(backup.data, definition.key)) return;
      const raw = JSON.stringify(backup.data[definition.key]);
      readPayload(raw, definition);
      storage.setItem(definition.key, raw);
      restored.push(definition.key);
    });
    if (!restored.length) throw new Error("请至少选择一项需要恢复的数据");
    return restored;
  }

  function clearSelected(storage, keys) {
    const removed = [];
    selectedDefinitions(keys).forEach(definition => {
      if (storage.getItem(definition.key) !== null) {
        storage.removeItem(definition.key);
        removed.push(definition.key);
      }
    });
    return removed;
  }

  return Object.freeze({ FORMAT, VERSION, MAX_FILE_BYTES, DEFINITIONS, byteLength, inspectStorage, createBackup, parseBackup, restoreBackup, clearSelected });
});
