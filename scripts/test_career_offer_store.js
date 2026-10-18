"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const store = require("../src/main/resources/static/career/offer-store.js");

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  };
}

test("calculates a transparent weighted score using answered dimensions", () => {
  const offer = store.normalizeOffer({
    companyName: "Example",
    positionTitle: "Engineer",
    scores: { roleFit: 5, growth: 4, compensation: 3 }
  });
  const weights = { roleFit: 5, growth: 5, compensation: 0, location: 0, workLife: 0, stability: 0 };

  assert.equal(store.calculateScore(offer, weights), 90);
});

test("deduplicates offers imported from the same application", () => {
  const storage = memoryStorage();
  store.add(storage, { applicationId: "app-1", companyName: "A", positionTitle: "Analyst" });
  store.add(storage, { applicationId: "app-1", companyName: "A", positionTitle: "Analyst" });

  assert.equal(store.load(storage).offers.length, 1);
});

test("persists weight changes and score updates", () => {
  const storage = memoryStorage();
  let state = store.add(storage, { companyName: "A", positionTitle: "Analyst" });
  const id = state.offers[0].id;
  state = store.update(storage, id, { scores: { roleFit: 4, growth: 5 } });
  state = store.setWeights(storage, { ...state.weights, roleFit: 2, growth: 4 });

  assert.equal(state.offers[0].scores.growth, 5);
  assert.equal(state.weights.roleFit, 2);
  assert.equal(store.calculateScore(state.offers[0], state.weights), 93);
});
