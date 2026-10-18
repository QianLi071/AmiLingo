"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const store = require("../src/main/resources/static/career/profile-store.js");

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  };
}

test("saves only supported text fields and trims values", () => {
  const storage = memoryStorage();
  const saved = store.save(storage, {
    fullName: "  Chan Tai Man  ",
    email: " student@example.com ",
    password: "must-not-be-stored"
  });

  assert.equal(saved.fullName, "Chan Tai Man");
  assert.equal(saved.email, "student@example.com");
  assert.equal(Object.hasOwn(saved, "password"), false);
  assert.deepEqual(store.load(storage), saved);
});

test("returns an empty normalized profile for invalid JSON", () => {
  const storage = memoryStorage();
  storage.setItem(store.STORAGE_KEY, "not-json");

  const profile = store.load(storage);

  assert.equal(profile.fullName, "");
  assert.equal(store.filledCount(profile), 0);
});

test("clears the locally stored profile", () => {
  const storage = memoryStorage();
  store.save(storage, { fullName: "Yolanda" });

  store.clear(storage);

  assert.equal(storage.getItem(store.STORAGE_KEY), null);
});
