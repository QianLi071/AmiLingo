(function () {
  "use strict";
  const backup = window.AmiLingoLocalDataBackup;
  const exportList = document.querySelector("#export-list");
  const importList = document.querySelector("#import-list");
  const importActions = document.querySelector("#import-actions");
  const status = document.querySelector("#data-status");
  let pendingBackup = null;

  function sizeLabel(bytes) { return bytes < 1024 ? `${bytes} B` : `${Math.ceil(bytes / 1024)} KB`; }
  function setStatus(message, error) { status.textContent = message; status.className = `form-status ${error ? "error" : "success"}`; }
  function checkedKeys(container) { return [...container.querySelectorAll("input[type=checkbox]:checked")].map(input => input.value); }

  function checkbox(item, checked) {
    const label = document.createElement("label");
    label.className = "data-choice";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.value = item.key;
    input.checked = checked;
    input.disabled = item.available === false;
    const copy = document.createElement("span");
    const title = document.createElement("strong");
    title.textContent = item.label;
    const detail = document.createElement("small");
    detail.textContent = item.invalid ? "本地数据损坏，无法导出" : item.available === false ? "暂无数据" : `${item.count} 项 · ${sizeLabel(item.bytes)}`;
    copy.append(title, detail);
    const group = document.createElement("em");
    group.textContent = item.group;
    label.append(input, copy, group);
    return label;
  }

  function renderExport() {
    exportList.replaceChildren();
    backup.inspectStorage(window.localStorage).forEach(item => exportList.appendChild(checkbox(item, item.available)));
  }

  document.querySelector("#export-button").addEventListener("click", () => {
    try {
      const payload = backup.createBackup(window.localStorage, checkedKeys(exportList));
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
      const link = document.createElement("a");
      const day = payload.exportedAt.slice(0, 10);
      link.href = URL.createObjectURL(blob);
      link.download = `amilingo-backup-${day}.json`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(link.href);
      setStatus("备份文件已生成。文件可能包含个人资料，请妥善保管。", false);
    } catch (error) { setStatus(error.message, true); }
  });

  document.querySelector("#backup-file").addEventListener("change", async event => {
    importList.replaceChildren();
    importActions.hidden = true;
    pendingBackup = null;
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    try {
      if (file.size > backup.MAX_FILE_BYTES) throw new Error("备份文件不能超过 2 MB");
      pendingBackup = backup.parseBackup(await file.text());
      pendingBackup.items.forEach(item => importList.appendChild(checkbox({ ...item, available: true }, true)));
      importActions.hidden = false;
      setStatus(`已读取 ${pendingBackup.items.length} 类数据，请选择需要恢复的内容。`, false);
    } catch (error) { setStatus(error.message, true); }
  });

  document.querySelector("#restore-button").addEventListener("click", () => {
    if (!pendingBackup) return;
    const keys = checkedKeys(importList);
    if (!keys.length) return setStatus("请至少选择一项需要恢复的数据。", true);
    if (!window.confirm("恢复会覆盖所选类别在当前浏览器中的原有数据，是否继续？")) return;
    try {
      const restored = backup.restoreBackup(window.localStorage, pendingBackup, keys);
      renderExport();
      setStatus(`已恢复 ${restored.length} 类本地数据。`, false);
    } catch (error) { setStatus(error.message, true); }
  });

  document.querySelector("#clear-selected").addEventListener("click", () => {
    const keys = checkedKeys(exportList);
    if (!keys.length) return setStatus("请先选择需要清除的数据。", true);
    if (!window.confirm("清除后只能通过之前下载的备份文件恢复，是否继续？")) return;
    const removed = backup.clearSelected(window.localStorage, keys);
    renderExport();
    setStatus(removed.length ? `已清除 ${removed.length} 类本地数据。` : "所选类别没有本地数据。", false);
  });

  renderExport();
})();
