const $ = (sel) => document.querySelector(sel);

const fileInput = $("#fileInput");
const dropzone = $("#dropzone");
const fileNameEl = $("#fileName");
const uploadBtn = $("#uploadBtn");
const uploadStatus = $("#uploadStatus");
const docList = $("#docList");
const docSelect = $("#docSelect");
const chat = $("#chat");
const askForm = $("#askForm");
const questionInput = $("#questionInput");
const askBtn = $("#askBtn");
const healthBadge = $("#healthBadge");

let selectedFile = null;

/* ---------- helpers ---------- */
function setBusy(btn, busy) {
  const label = btn.querySelector(".btn-label");
  const spinner = btn.querySelector(".spinner");
  btn.disabled = busy;
  if (label) label.style.opacity = busy ? "0.6" : "1";
  if (spinner) spinner.hidden = !busy;
}

function showStatus(el, msg, kind) {
  el.hidden = false;
  el.className = "status " + kind;
  el.textContent = msg;
}

function escapeHtml(s) {
  return (s ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[c]));
}

function timeAgo(iso) {
  try {
    const d = new Date(iso);
    return d.toLocaleString();
  } catch { return ""; }
}

/* ---------- health ---------- */
async function checkHealth() {
  try {
    const r = await fetch("/health");
    const j = await r.json();
    if (r.ok && j.status === "ok") {
      healthBadge.textContent = "API online";
      healthBadge.className = "badge badge-ok";
      return;
    }
    throw new Error();
  } catch {
    healthBadge.textContent = "API offline";
    healthBadge.className = "badge badge-down";
  }
}

/* ---------- documents ---------- */
async function loadDocuments() {
  try {
    const r = await fetch("/api/documents");
    if (!r.ok) throw new Error();
    const docs = await r.json();
    renderDocuments(docs);
  } catch {
    // keep silent; list stays as-is
  }
}

function renderDocuments(docs) {
  // Selector
  const prev = docSelect.value;
  docSelect.innerHTML = "";
  if (!docs.length) {
    docList.innerHTML = '<li class="doc-empty">No documents yet. Upload one above.</li>';
    const opt = document.createElement("option");
    opt.value = ""; opt.textContent = "— no documents —";
    docSelect.appendChild(opt);
    updateAskEnabled();
    return;
  }

  docList.innerHTML = "";
  docs.forEach((d) => {
    const li = document.createElement("li");
    li.className = "doc-item";
    li.innerHTML = `
      <span class="doc-ic">📄</span>
      <span class="doc-meta">
        <span class="doc-fn">${escapeHtml(d.filename)}</span>
        <span class="doc-sub">${d.chunkCount} chunks · ${escapeHtml(timeAgo(d.createdAt))}</span>
      </span>`;
    docList.appendChild(li);

    const opt = document.createElement("option");
    opt.value = d.documentId;
    opt.textContent = d.filename;
    docSelect.appendChild(opt);
  });

  if (prev && docs.some((d) => d.documentId === prev)) docSelect.value = prev;
  updateAskEnabled();
}

function updateAskEnabled() {
  askBtn.disabled = !docSelect.value || !questionInput.value.trim();
}

/* ---------- file selection ---------- */
function handleFile(file) {
  if (!file) return;
  if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
    showStatus(uploadStatus, "Please choose a PDF file.", "err");
    return;
  }
  selectedFile = file;
  fileNameEl.hidden = false;
  fileNameEl.textContent = file.name;
  uploadBtn.disabled = false;
  uploadStatus.hidden = true;
}

fileInput.addEventListener("change", (e) => handleFile(e.target.files[0]));

["dragenter", "dragover"].forEach((ev) =>
  dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.add("dragover"); })
);
["dragleave", "drop"].forEach((ev) =>
  dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.remove("dragover"); })
);
dropzone.addEventListener("drop", (e) => {
  const file = e.dataTransfer.files?.[0];
  if (file) handleFile(file);
});

/* ---------- upload ---------- */
uploadBtn.addEventListener("click", async () => {
  if (!selectedFile) return;
  setBusy(uploadBtn, true);
  showStatus(uploadStatus, "Uploading & indexing… this can take a moment.", "info");

  const fd = new FormData();
  fd.append("file", selectedFile);

  try {
    const r = await fetch("/api/ingest-pdf", { method: "POST", body: fd });
    const j = await r.json();
    if (!r.ok) throw new Error(j.message || "Upload failed");
    showStatus(uploadStatus, `Indexed “${j.filename}” (${j.chunkCount} chunks).`, "ok");
    selectedFile = null;
    fileInput.value = "";
    fileNameEl.hidden = true;
    uploadBtn.disabled = true;
    await loadDocuments();
    docSelect.value = j.documentId;
    updateAskEnabled();
  } catch (err) {
    showStatus(uploadStatus, err.message || "Upload failed.", "err");
  } finally {
    setBusy(uploadBtn, false);
  }
});

/* ---------- chat ---------- */
function clearPlaceholder() {
  const ph = chat.querySelector(".chat-placeholder");
  if (ph) ph.remove();
}

function addUserMsg(text) {
  clearPlaceholder();
  const el = document.createElement("div");
  el.className = "msg user";
  el.textContent = text;
  chat.appendChild(el);
  chat.scrollTop = chat.scrollHeight;
}

function addTyping() {
  const el = document.createElement("div");
  el.className = "msg bot";
  el.innerHTML = '<div class="typing"><span></span><span></span><span></span></div>';
  chat.appendChild(el);
  chat.scrollTop = chat.scrollHeight;
  return el;
}

function renderAnswer(el, data) {
  const conf = (data.confidence || "medium").toLowerCase();
  let html = `<div>${escapeHtml(data.answer)}</div>`;
  html += `<span class="conf ${conf}">${conf} confidence</span>`;

  if (Array.isArray(data.citations) && data.citations.length) {
    const id = "c" + Math.random().toString(36).slice(2);
    html += `<div class="cite-toggle" data-target="${id}">▸ ${data.citations.length} source(s)</div>`;
    html += `<div class="cites" id="${id}" hidden>`;
    data.citations.forEach((c) => {
      const page = c.page != null ? ` · p.${c.page}` : "";
      html += `<div class="cite"><b>${escapeHtml(c.source)}${page}</b><br>${escapeHtml(c.snippet)}</div>`;
    });
    html += `</div>`;
  }
  el.className = "msg bot";
  el.innerHTML = html;

  const toggle = el.querySelector(".cite-toggle");
  if (toggle) {
    toggle.addEventListener("click", () => {
      const box = el.querySelector("#" + toggle.dataset.target);
      box.hidden = !box.hidden;
      toggle.textContent = (box.hidden ? "▸ " : "▾ ") + toggle.textContent.slice(2);
    });
  }
  chat.scrollTop = chat.scrollHeight;
}

function renderError(el, msg) {
  el.className = "msg bot err";
  el.textContent = msg;
}

questionInput.addEventListener("input", updateAskEnabled);
docSelect.addEventListener("change", updateAskEnabled);

askForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const documentId = docSelect.value;
  const question = questionInput.value.trim();
  if (!documentId || !question) return;

  addUserMsg(question);
  questionInput.value = "";
  updateAskEnabled();
  setBusy(askBtn, true);
  const typingEl = addTyping();

  try {
    const r = await fetch("/api/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentId, question }),
    });
    const j = await r.json();
    if (!r.ok) throw new Error(j.message || "Request failed");
    renderAnswer(typingEl, j);
  } catch (err) {
    renderError(typingEl, err.message || "Something went wrong.");
  } finally {
    setBusy(askBtn, false);
  }
});

/* ---------- init ---------- */
checkHealth();
loadDocuments();
updateAskEnabled();
