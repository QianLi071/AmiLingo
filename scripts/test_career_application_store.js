"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const store = require("../src/main/resources/static/career/application-store.js");

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  };
}

test("adds an official job once and preserves user progress on refresh", () => {
  const storage = memoryStorage();
  const job = {
    id: "912345678901234567",
    companyName: "Example Limited",
    positionTitle: "Software Intern",
    region: "HONG_KONG",
    jobType: "INTERNSHIP",
    officialUrl: "https://example.com/jobs/1"
  };

  store.addOrUpdateFromJob(storage, job);
  const first = store.load(storage)[0];
  store.update(storage, first.id, { status: "INTERVIEW", notes: "Prepare system design" });
  store.addOrUpdateFromJob(storage, { ...job, positionTitle: "Software Engineering Intern" });
  const items = store.load(storage);

  assert.equal(items.length, 1);
  assert.equal(items[0].positionTitle, "Software Engineering Intern");
  assert.equal(items[0].status, "INTERVIEW");
  assert.equal(items[0].notes, "Prepare system design");
});

test("rejects unsafe URLs and unsupported statuses", () => {
  const item = store.normalizeItem({
    companyName: "Example",
    positionTitle: "Analyst",
    officialUrl: "javascript:alert(1)",
    status: "UNKNOWN"
  });

  assert.equal(item.officialUrl, "");
  assert.equal(item.status, "SAVED");
});

test("supports manual records, updates and deletion", () => {
  const storage = memoryStorage();
  store.addManual(storage, { companyName: "Manual Co", positionTitle: "Graduate Trainee" });
  const item = store.load(storage)[0];

  store.update(storage, item.id, { status: "APPLIED", applicationDate: "2026-10-18" });
  assert.equal(store.load(storage)[0].status, "APPLIED");

  store.remove(storage, item.id);
  assert.equal(store.load(storage).length, 0);
});
