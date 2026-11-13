"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const prefill = require("../src/main/resources/static/career/fit-prefill.js");

test("merges skills without duplicates", () => {
  assert.equal(prefill.mergeSkills("Java、Python", "Python, 数据分析"), "Java、Python、数据分析");
});

test("maps only relevant career profile fields", () => {
  const mapped = prefill.fromCareerProfile({ major:"计算机科学", keySkills:"Java, SQL", projectHighlight:"完成课程项目", fullName:"不应导入" });
  assert.deepEqual(mapped, { major:"计算机科学", skills:"Java、SQL", experienceSummary:"完成课程项目" });
});

test("creates editable summaries from recorded campus experiences", () => {
  const drafts = prefill.fromClubExperiences([{ name:"A" },{ name:"B" }], item => `参与 ${item.name}`);
  assert.deepEqual(drafts, ["参与 A", "参与 B"]);
  assert.equal(prefill.mergeText("已有内容", drafts, 2000), "已有内容\n参与 A\n参与 B");
});
