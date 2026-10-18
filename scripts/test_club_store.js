"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const store = require("../src/main/resources/static/club/club-store.js");

function memoryStorage() {
  const values = new Map();
  return { getItem:key => values.has(key) ? values.get(key) : null, setItem:(key,value) => values.set(key,String(value)), removeItem:key => values.delete(key) };
}

test("recommendations are explainable and follow stated interests", () => {
  const results = store.recommend({ goal:"interest", interests:["hardware","robotics"], modes:["hands-on"], weeklyHours:"3-5" });
  assert.equal(results[0].id, "hardware");
  assert.ok(results[0].score > results[1].score);
  assert.ok(results[0].reasons.some(reason => reason.includes("兴趣")));
});

test("profile and saved items use allowlists", () => {
  const storage = memoryStorage();
  const profile = store.saveProfile(storage, { goal:"unknown", interests:["coding","password"], modes:["team","other"], weeklyHours:"99" });
  assert.equal(profile.goal, "interest");
  assert.deepEqual(profile.interests, ["coding"]);
  assert.deepEqual(store.toggleSaved(storage, "not-a-group"), []);
  assert.deepEqual(store.toggleSaved(storage, "algorithms"), ["algorithms"]);
});

test("experiences can be saved, edited, drafted and deleted", () => {
  const storage = memoryStorage();
  store.saveExperience(storage, { activityName:"代码竞赛", groupName:"算法组", role:"队员", actions:"完成两道题。", outcome:"团队完成赛题", skills:"算法与协作" });
  const item = store.loadExperiences(storage)[0];
  assert.match(store.resumeDraft(item), /代码竞赛/);
  assert.match(store.resumeDraft(item), /团队完成赛题/);
  store.saveExperience(storage, { ...item, role:"队长" });
  assert.equal(store.loadExperiences(storage)[0].role, "队长");
  store.removeExperience(storage, item.id);
  assert.equal(store.loadExperiences(storage).length, 0);
});
