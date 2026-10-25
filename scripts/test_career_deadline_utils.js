"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const deadlines = require("../src/main/resources/static/career/deadline-utils.js");

test("calculates calendar-day countdown without time-zone drift", () => {
  assert.equal(deadlines.daysUntil("2026-10-18", "2026-10-18"), 0);
  assert.equal(deadlines.daysUntil("2026-10-25", "2026-10-18"), 7);
  assert.equal(deadlines.daysUntil("2026-10-17", "2026-10-18"), -1);
});

test("rejects invalid calendar dates", () => {
  assert.equal(deadlines.parseDate("2026-02-30"), null);
  assert.equal(deadlines.daysUntil("not-a-date", "2026-10-18"), null);
});

test("collects and sorts application and offer deadlines", () => {
  const result = deadlines.collect(
    [{ id: "a", companyName: "A", positionTitle: "Intern", deadline: "2026-10-20" }],
    [{ id: "b", companyName: "B", positionTitle: "Graduate", deadline: "2026-10-19" }],
    "2026-10-18"
  );

  assert.deepEqual(result.map(item => item.source), ["OFFER", "APPLICATION"]);
  assert.deepEqual(result.map(item => item.category), ["URGENT", "URGENT"]);
});
