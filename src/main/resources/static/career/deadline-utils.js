(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  } else {
    root.CareerDeadlineUtils = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function parseDate(value) {
    if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
    const [year, month, day] = value.split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null;
    return date;
  }

  function todayString(now) {
    const date = now instanceof Date ? now : new Date();
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function daysUntil(deadline, today) {
    const end = parseDate(deadline);
    const start = parseDate(today || todayString());
    if (!end || !start) return null;
    return Math.round((end.getTime() - start.getTime()) / 86400000);
  }

  function classify(days) {
    if (days === null) return "UNKNOWN";
    if (days < 0) return "OVERDUE";
    if (days === 0) return "TODAY";
    if (days <= 7) return "URGENT";
    if (days <= 30) return "UPCOMING";
    return "LATER";
  }

  function collect(applications, offers, today) {
    const referenceDate = today || todayString();
    const items = [];
    (applications || []).forEach(application => {
      if (!application.deadline) return;
      const days = daysUntil(application.deadline, referenceDate);
      if (days === null) return;
      items.push({
        id: `application:${application.id}`,
        source: "APPLICATION",
        companyName: application.companyName,
        positionTitle: application.positionTitle,
        deadline: application.deadline,
        days,
        category: classify(days),
        target: "./applications.html"
      });
    });
    (offers || []).forEach(offer => {
      if (!offer.deadline) return;
      const days = daysUntil(offer.deadline, referenceDate);
      if (days === null) return;
      items.push({
        id: `offer:${offer.id}`,
        source: "OFFER",
        companyName: offer.companyName,
        positionTitle: offer.positionTitle,
        deadline: offer.deadline,
        days,
        category: classify(days),
        target: "./offers.html"
      });
    });
    return items.sort((a, b) => a.days - b.days || a.companyName.localeCompare(b.companyName));
  }

  return Object.freeze({ parseDate, todayString, daysUntil, classify, collect });
});
