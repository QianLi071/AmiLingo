"use strict";
const assert = require("assert");
const backup = require("../src/main/resources/static/career/local-data-backup.js");

function storage(initial) {
  const values = new Map(Object.entries(initial || {}));
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    values
  };
}

const source = storage({
  "amilingo.career.profile.v1": JSON.stringify({ version: 1, profile: { fullName: "Yolanda" } }),
  "amilingo.career.applications.v1": JSON.stringify({ version: 1, items: [{ id: "1" }] }),
  "unrelated.key": JSON.stringify({ version: 1, secret: true })
});

const snapshot = backup.createBackup(source, null, new Date("2026-10-18T10:00:00.000Z"));
assert.equal(snapshot.format, "amilingo-local-backup");
assert.equal(Object.keys(snapshot.data).length, 2);
assert.equal(snapshot.data["unrelated.key"], undefined);

const injected = JSON.stringify({ ...snapshot, data: { ...snapshot.data, "unknown.key": { version: 1 }, "amilingo.career.offers.v1": { version: 2, offers: [] } } });
assert.throws(() => backup.parseBackup(injected), /版本不受支持/);

const safeText = JSON.stringify({ ...snapshot, data: { ...snapshot.data, "unknown.key": { version: 1 } } });
const parsed = backup.parseBackup(safeText);
assert.equal(parsed.items.length, 2);

const target = storage({ "amilingo.career.profile.v1": JSON.stringify({ version: 1, profile: { fullName: "Old" } }) });
const restored = backup.restoreBackup(target, parsed, ["amilingo.career.profile.v1"]);
assert.deepEqual(restored, ["amilingo.career.profile.v1"]);
assert.equal(JSON.parse(target.getItem("amilingo.career.profile.v1")).profile.fullName, "Yolanda");
assert.equal(target.getItem("amilingo.career.applications.v1"), null);

assert.deepEqual(backup.clearSelected(target, ["amilingo.career.profile.v1"]), ["amilingo.career.profile.v1"]);
assert.equal(target.getItem("amilingo.career.profile.v1"), null);
assert.throws(() => backup.createBackup(storage(), []), /没有可导出/);
assert.throws(() => backup.parseBackup("{}"), /受支持/);

console.log("local data backup tests passed");
