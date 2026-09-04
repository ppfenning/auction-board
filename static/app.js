// static/app.js — the whole client.
//
// Data flow: one module-level `view` variable holds the last View the
// server sent. `load()` GETs /api/view, replaces `view`, and calls
// `renderAll()`, which redraws every tab from that one object. `load()`
// runs once at startup, again after every mutating POST (via `post()`),
// and on a 5s timer so a second window stays current. `load()` tags each
// fetch with an increasing sequence number and only applies the response
// if no newer fetch has started since, so a slow poll response can never
// clobber a faster mutation's response with stale data. A network failure
// in `load()` or `post()` is caught and shown, never left as an unhandled
// rejection.
//
// The View is not the ONLY state: a typed sell price/team, an open "$"
// override editor, and an in-progress team rename all live in the DOM (or
// in the small drafts below) between renders. Every render that could
// otherwise discard them either skips the DOM it does not own (teams, in
// `renaming` mode) or reapplies the draft/focus it finds — the board AND
// the nominate tab both rebuild through `preserveFocus()`, which captures
// the focused control's `data-key` and cursor before a render and restores
// them after, so a 5s poll during a live bid never eats what the user
// typed on either tab. `el()`/`fill()` always go through DOM nodes, never
// string-built HTML, so a player name can never inject markup.
//
// The strategy tab keeps its markdown handling split in two: `parseMarkdown`
// and `tokenizeInline` are a pure core (text in, plain block/token data
// out, no DOM); `renderMarkdown`/`renderInline` are the thin edge that
// turns that data into nodes. This is also the code that shipped a
// nonterminating loop twice, so every branch in the pure half is written to
// advance its index/line on every pass, including its "nothing matched"
// fallback.

let view = null;
let strategyText = null;
let loadSeq = 0;

function el(tag, attrs, ...kids) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (k === "class") { if (v) node.className = v; }
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else if (v !== null && v !== undefined) node.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid === null || kid === undefined) continue;
    node.appendChild(typeof kid === "string" ? document.createTextNode(kid) : kid);
  }
  return node;
}
function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }
function fill(node, ...kids) {
  clear(node);
  for (const k of kids.flat()) if (k !== null && k !== undefined) node.appendChild(typeof k === "string" ? document.createTextNode(k) : k);
}

// Captures which control (by data-key) inside `container` has focus, runs
// `renderFn` (which typically clears and rebuilds `container`'s children),
// then restores focus and, where the input type supports it, the cursor
// position to the freshly-built control with the same data-key. Wrapped in
// try/catch throughout: `selectionStart`/`setSelectionRange` are undefined
// for some input types in some engines, and losing the cursor position is
// acceptable, throwing out of a render is not.
function preserveFocus(container, renderFn) {
  let key = null;
  let sel = null;
  try {
    const active = document.activeElement;
    if (container.contains(active)) {
      key = active.getAttribute("data-key");
      if (key && active.type !== "number" && "selectionStart" in active) sel = active.selectionStart;
    }
  } catch (e) { /* selection APIs are not defined for every input type in every engine */ }
  renderFn();
  if (!key) return;
  const restore = container.querySelector(`[data-key="${key}"]`);
  if (!restore) return;
  restore.focus();
  if (sel === null || typeof restore.setSelectionRange !== "function") return;
  try { restore.setSelectionRange(sel, sel); } catch (e) { /* e.g. number inputs do not support selection */ }
}

async function load() {
  const seq = ++loadSeq;
  let data;
  try {
    const res = await fetch("/api/view");
    data = await res.json();
  } catch (e) {
    showError("network error: could not reach the server");
    return;
  }
  if (seq !== loadSeq) return; // a newer load started while this one was in flight; its answer is stale
  view = data;
  renderAll();
}

let errorTimer = null;
// Any non-2xx status (400 or otherwise) is a failure: show it, refresh
// nothing. A transport failure (server down, laptop asleep) is caught here
// too, so a click never leaves an unhandled promise rejection behind.
async function post(path, body) {
  let res;
  try {
    res = await fetch(path, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body || {}) });
  } catch (e) {
    showError("network error: could not reach the server");
    return false;
  }
  if (!res.ok) {
    const data = await res.json().catch(() => ({ error: `request failed (${res.status})` }));
    showError(data.error || `request failed (${res.status})`);
    return false;
  }
  await load();
  return true;
}
function showError(message) {
  const header = document.querySelector("header");
  let box = document.getElementById("st-error");
  if (!box) { box = el("div", { id: "st-error", class: "stat" }); header.insertBefore(box, header.querySelector("nav")); }
  fill(box, el("b", null, message));
  if (errorTimer) clearTimeout(errorTimer);
  errorTimer = setTimeout(() => box.remove(), 4000);
}

function myTeam() { return view.teams[view.myTeam]; }

function renderAll() {
  if (!view) return;
  const me = myTeam();
  document.getElementById("st-inflation").textContent = view.inflation.toFixed(2);
  // Prices paid over sheet value so far. Above 1.20 the room is hotter than
  // the tier-1 cap: raise starter caps to it rather than sit on money.
  const premiumEl = document.getElementById("st-premium");
  premiumEl.textContent = view.premium.toFixed(2);
  premiumEl.classList.toggle("over", view.premium > 1.2);
  document.getElementById("st-remaining").textContent = me.remaining;
  document.getElementById("st-maxbid").textContent = me.maxBid;
  const plan = document.getElementById("st-plan");
  plan.textContent = view.planRemaining;
  plan.classList.toggle("over", view.planRemaining > me.remaining);
  // Money the plan has no home for. The strategy's rule: add it to the cap
  // of the next starter you bid on, never let it reach the endgame unspent.
  const surplus = Math.max(0, me.remaining - view.planRemaining);
  const surplusEl = document.getElementById("st-surplus");
  surplusEl.textContent = surplus;
  surplusEl.classList.toggle("under", surplus > 0);
  document.getElementById("st-slots").textContent = me.slotsOpen;
  document.getElementById("st-room").textContent = view.moneyLeft;
  renderBoard();
  renderTeams();
  renderPlan();
  renderNominations();
}

// ── tabs ─────────────────────────────────────────────────────────────────

function initTabs() {
  const buttons = document.querySelectorAll("nav button[data-tab]");
  for (const btn of buttons) {
    btn.addEventListener("click", () => {
      for (const b of buttons) {
        b.classList.toggle("active", b === btn);
        document.getElementById("tab-" + b.getAttribute("data-tab")).hidden = b !== btn;
      }
      if (btn.getAttribute("data-tab") === "strategy") renderStrategy();
    });
  }
}

// ── board ────────────────────────────────────────────────────────────────

let sortCol = null, sortDir = 1;
const NUMERIC_COLS = new Set(["rank", "tier", "value", "target", "max"]);

// Pure core: plain players in, plain players out. No DOM read here, so this
// is exercisable with array literals.
// A player's tag is the research label at the front of its note: VALUE,
// SLEEPER, HANDCUFF or AVOID. "INJURY" matches any other non-empty note.
function noteTag(note) {
  const upper = (note || "").toUpperCase();
  for (const t of ["VALUE", "SLEEPER", "HANDCUFF", "AVOID"]) if (upper.includes(t)) return t;
  return upper ? "INJURY" : "";
}

function filterSortPlayers(players, query, pos, hideSold, col, dir, tag) {
  const q = query.trim().toLowerCase();
  const rows = players.filter((p) => (!q || p.name.toLowerCase().includes(q)) && (!pos || p.pos === pos) && (!hideSold || p.soldTo === null) && (!tag || noteTag(p.note) === tag));
  if (!col) return rows;
  return rows.slice().sort((a, b) => (NUMERIC_COLS.has(col) ? a[col] - b[col] : String(a[col]).localeCompare(String(b[col]))) * dir);
}

// Edge: reads the DOM, hands plain values to the pure core above.
function boardRows() {
  return filterSortPlayers(
    view.players,
    document.getElementById("search").value,
    document.getElementById("pos-filter").value,
    document.getElementById("hide-sold").checked,
    sortCol,
    sortDir,
    document.getElementById("tag-filter").value,
  );
}

// A typed-but-not-yet-submitted sell (team + price), keyed by player id, so
// a poll mid-bid — on the board OR the nominate tab, both use this — reapplies
// what the user typed instead of resetting it.
const sellDrafts = new Map();

function sellControl(id, defaultPrice) {
  const draft = sellDrafts.get(id) || { team: view.myTeam, price: defaultPrice };
  const select = el(
    "select", { "data-key": `${id}:team` },
    view.teams.map((t) => el("option", { value: t.index, selected: t.index === draft.team ? "selected" : null }, t.name)),
  );
  const price = el("input", { type: "number", value: String(draft.price), "data-key": `${id}:price` });
  const saveDraft = () => sellDrafts.set(id, { team: Number(select.value), price: price.value });
  select.addEventListener("change", saveDraft);
  price.addEventListener("input", saveDraft);
  const doSell = async () => {
    const ok = await post("/api/sell", { playerId: id, team: Number(select.value), price: Number(price.value) });
    if (ok) sellDrafts.delete(id);
  };
  price.addEventListener("keydown", (e) => { if (e.key === "Enter") doSell(); });
  return el("span", { class: "sell" }, select, price, el("button", { class: "act", onclick: doSell }, "Sold"));
}

// Which players have an open "$" override editor, and what has been typed
// into it, so a poll reopens the same editor with the same draft instead of
// deleting it.
const overrideOpen = new Set();
const overrideDrafts = new Map();

function valueCell(p) {
  const cell = el("td");
  const closed = () => {
    const btn = el("button", { class: "act" }, "$");
    btn.addEventListener("click", () => { overrideOpen.add(p.id); open(true); });
    fill(cell, el("span", null, String(p.value)), " ", btn);
  };
  const open = (autofocus) => {
    const input = el("input", { type: "number", value: overrideDrafts.has(p.id) ? overrideDrafts.get(p.id) : String(p.value), "data-key": `${p.id}:override` });
    input.addEventListener("input", () => overrideDrafts.set(p.id, input.value));
    // Close the editor BEFORE the post: post() reloads and re-renders the
    // board, and a render that still finds p.id in overrideOpen reopens the
    // editor the user just committed. A failed post reopens it with the draft.
    const commit = async () => {
      const raw = input.value.trim();
      overrideOpen.delete(p.id);
      const ok = await post("/api/override", { playerId: p.id, value: raw === "" ? null : Number(raw) });
      if (ok) overrideDrafts.delete(p.id);
      else { overrideOpen.add(p.id); renderBoard(); }
    };
    const cancel = () => { overrideOpen.delete(p.id); overrideDrafts.delete(p.id); closed(); };
    // Enter commits, Escape cancels. Blur does neither: a stray click away
    // from an editor opened by accident must not silently write a value.
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") commit();
      else if (e.key === "Escape") cancel();
    });
    fill(cell, input);
    if (autofocus) input.focus();
  };
  if (overrideOpen.has(p.id)) open(false); else closed();
  return cell;
}

function renderBoard() {
  if (!view) return; // the search box has autofocus and fires before the first load
  const body = document.getElementById("board-body");
  preserveFocus(body, () => {
    clear(body);
    for (const p of boardRows()) {
      const sold = p.soldTo !== null;
      const mine = p.soldTo === view.myTeam;
      const soldCell = sold ? `${view.teams[p.soldTo].name} $${p.price}` : sellControl(p.id, p.target);
      body.appendChild(el(
        "tr", { class: [`t${p.tier}`, sold ? "sold" : null, mine ? "mine" : null].filter(Boolean).join(" ") },
        el("td", null, String(p.rank)), el("td", null, p.pos), el("td", null, p.name), el("td", null, p.team),
        el("td", { class: "tier" }, String(p.tier)), valueCell(p), el("td", null, String(p.target)), el("td", null, String(p.max)),
        el("td", null, soldCell), el("td", { class: "note" + (noteTag(p.note) ? " tag-" + noteTag(p.note).toLowerCase() : "") }, p.note),
      ));
    }
  });
}

function initBoardControls() {
  document.getElementById("search").addEventListener("input", renderBoard);
  document.getElementById("pos-filter").addEventListener("change", renderBoard);
  document.getElementById("hide-sold").addEventListener("change", renderBoard);
  document.getElementById("tag-filter").addEventListener("change", renderBoard);
  document.addEventListener("keydown", (e) => {
    const tag = document.activeElement.tagName;
    if (e.key === "/" && tag !== "INPUT" && tag !== "TEXTAREA" && tag !== "SELECT") { e.preventDefault(); document.getElementById("search").focus(); }
  });
  const keys = ["rank", "pos", "name", "team", "tier", "value", "target", "max", null, null];
  document.querySelectorAll("#board thead th").forEach((th, i) => {
    const key = keys[i];
    if (!key) return;
    th.addEventListener("click", () => { sortDir = sortCol === key ? -sortDir : 1; sortCol = key; renderBoard(); });
  });
}

// ── teams ────────────────────────────────────────────────────────────────

// While true, the rename form owns #teams; renderTeams leaves it alone so a
// poll cannot overwrite ten inputs the user is mid-typing into.
let renaming = false;

function renderTeams() {
  const mySelect = document.getElementById("my-team");
  // Rebuild the select only when the names changed, and never while the
  // user has it open: a poll that rebuilds an open dropdown closes it.
  const wanted = view.teams.map((t) => t.name).join(" ");
  if (document.activeElement !== mySelect && mySelect.getAttribute("data-names") !== wanted) {
    clear(mySelect);
    for (const t of view.teams) mySelect.appendChild(el("option", { value: t.index }, t.name));
    mySelect.setAttribute("data-names", wanted);
  }
  if (document.activeElement !== mySelect) mySelect.value = String(view.myTeam);
  if (renaming) return;
  const wrap = document.getElementById("teams");
  clear(wrap);
  for (const t of view.teams) {
    wrap.appendChild(el(
      "div", { class: "card" + (t.index === view.myTeam ? " me" : "") },
      el("h3", null, t.name),
      el("div", null, `spent $${t.spent} / remaining $${t.remaining} / max bid $${t.maxBid} / $${t.perSlot.toFixed(1)} per slot`),
      ...t.roster.map((r) => el("div", null, r.name ? `${r.slot} ${r.name} $${r.price}` : `${r.slot} —`)),
    ));
  }
}

function initTeamControls() {
  document.getElementById("my-team").addEventListener("change", (e) => post("/api/me", { team: Number(e.target.value) }));

  document.getElementById("rename").addEventListener("click", () => {
    if (!view || renaming) return;
    renaming = true;
    const grid = document.getElementById("teams");
    const fields = view.teams.map((t) => el("input", { type: "text", value: t.name }));
    const save = el("button", { class: "act" }, "Save");
    const cancel = el("button", { class: "act warn" }, "Cancel");
    // `renaming` stays true until the server has accepted the names: a 400
    // (a blank name) leaves the form, and the typed names, exactly as they
    // were, instead of letting the next poll clear them.
    save.addEventListener("click", async () => {
      const ok = await post("/api/teams", { names: fields.map((f) => f.value) });
      if (ok) { renaming = false; renderTeams(); }
    });
    cancel.addEventListener("click", () => { renaming = false; renderTeams(); });
    fill(grid, ...fields, save, " ", cancel);
    fields[0].focus();
  });

  // #reset is never removed: a hidden sibling confirm pair is toggled next
  // to it, so "No" (or a later render) can always bring the button back.
  const resetBtn = document.getElementById("reset");
  const yes = el("button", { class: "act bad" }, "Yes");
  const no = el("button", { class: "act" }, "No");
  const confirmSpan = el("span", null, "Really reset? ", yes, " ", no);
  confirmSpan.hidden = true;
  resetBtn.insertAdjacentElement("afterend", confirmSpan);
  resetBtn.addEventListener("click", () => { resetBtn.hidden = true; confirmSpan.hidden = false; });
  no.addEventListener("click", () => { confirmSpan.hidden = true; resetBtn.hidden = false; });
  yes.addEventListener("click", () => { confirmSpan.hidden = true; resetBtn.hidden = false; post("/api/reset", {}); });
}

// ── plan ─────────────────────────────────────────────────────────────────

function deltaClass(delta) {
  if (delta === null) return null;
  if (delta > 0) return "over";
  if (delta < 0) return "under";
  return null;
}

function renderPlan() {
  const body = document.getElementById("plan-body");
  clear(body);
  for (const line of view.plan) {
    const delta = line.actual === null ? null : line.actual - line.planned;
    body.appendChild(el(
      "tr", null,
      el("td", null, line.slot), el("td", null, String(line.planned)),
      el("td", null, line.actual === null ? "—" : String(line.actual)),
      el("td", null, line.name === null ? "—" : line.name),
      el("td", { class: deltaClass(delta) }, delta === null ? "—" : String(delta)),
    ));
  }
}

// ── nominate ─────────────────────────────────────────────────────────────

function renderNominations() {
  const wrap = document.getElementById("nominations");
  // Same reasoning as renderBoard: this rebuilds every 5s poll too, and its
  // sell control is the same input a bid may be mid-typed into.
  preserveFocus(wrap, () => {
    clear(wrap);
    for (const n of view.nominations) {
      wrap.appendChild(el(
        "div", { class: "nom" },
        el("b", null, n.name), ` ${n.pos} $${n.value} `, sellControl(n.playerId, n.value),
        el("div", { class: "why" }, n.reason),
      ));
    }
  });
}

// ── strategy: a small markdown subset ─────────────────────────────────────
//
// Pure core (text in, plain data out, no DOM): tokenizeInline, parseMarkdown,
// parseListItems, parseTable, isContinuation. Thin edge (data in, nodes
// out): renderInline, renderMarkdown.

// Every branch here MUST advance `i`, including the "no delimiter closed"
// fallback — otherwise an unmatched "**"/backtick spins forever.
function tokenizeInline(text) {
  const tokens = [];
  let i = 0;
  while (i < text.length) {
    if (text.startsWith("**", i)) {
      const end = text.indexOf("**", i + 2);
      if (end !== -1) { tokens.push({ type: "bold", value: text.slice(i + 2, end) }); i = end + 2; continue; }
    }
    if (text[i] === "`") {
      const end = text.indexOf("`", i + 1);
      if (end !== -1) { tokens.push({ type: "code", value: text.slice(i + 1, end) }); i = end + 1; continue; }
    }
    // Neither delimiter closed here (or there is none at i): scan for the
    // next marker starting strictly AFTER i, never at i, so an unmatched
    // "**"/"`" at the cursor cannot make `next` equal `i` and stall.
    let next = text.length;
    for (const m of ["**", "`"]) { const at = text.indexOf(m, i + 1); if (at !== -1 && at < next) next = at; }
    tokens.push({ type: "text", value: text.slice(i, next) });
    i = next;
  }
  return tokens;
}

function renderInline(text) {
  const span = el("span");
  for (const t of tokenizeInline(text)) {
    if (t.type === "bold") span.appendChild(el("b", null, t.value));
    else if (t.type === "code") span.appendChild(el("code", null, t.value));
    else span.appendChild(document.createTextNode(t.value));
  }
  return span;
}

function parseTable(lines) {
  const cells = (row) => row.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim());
  return { type: "table", header: cells(lines[0]), rows: lines.slice(2).map(cells) };
}

// A wrapped list item's continuation lines are indented and start with
// neither "-" nor "N.": fold them into the item they follow instead of
// letting each one open a stray one-line paragraph.
function isContinuation(line) {
  return line.trim() !== "" && /^\s+\S/.test(line) && !/^-\s+|^\d+\.\s+/.test(line.trim());
}

// Collects one item's own line plus any continuation lines into an array
// and joins once, rather than reassigning a `text` binding line by line.
function parseListItems(lines, start, marker) {
  const items = [];
  let i = start;
  while (i < lines.length && marker.test(lines[i])) {
    const parts = [lines[i].replace(marker, "")];
    i++;
    while (i < lines.length && isContinuation(lines[i])) { parts.push(lines[i].trim()); i++; }
    items.push(parts.join(" "));
  }
  return { items, next: i };
}

function parseMarkdown(text) {
  const lines = text.split("\n");
  const blocks = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.trim() === "") { i++; continue; }
    if (/^##\s+/.test(line)) { blocks.push({ type: "h2", text: line.replace(/^##\s+/, "") }); i++; }
    else if (/^#\s+/.test(line)) { blocks.push({ type: "h1", text: line.replace(/^#\s+/, "") }); i++; }
    else if (/^\|/.test(line) && i + 1 < lines.length && /^\|?\s*-+/.test(lines[i + 1].replace(/\|/g, ""))) {
      const rows = [];
      while (i < lines.length && lines[i].trim().startsWith("|")) { rows.push(lines[i]); i++; }
      blocks.push(parseTable(rows));
    } else if (/^-\s+/.test(line)) {
      const { items, next } = parseListItems(lines, i, /^-\s+/);
      blocks.push({ type: "ul", items });
      i = next;
    } else if (/^\d+\.\s+/.test(line)) {
      const { items, next } = parseListItems(lines, i, /^\d+\.\s+/);
      blocks.push({ type: "ol", items });
      i = next;
    } else {
      // Any other non-blank line is a paragraph. Consume THIS line
      // unconditionally before testing the continuation condition: the
      // continuation loop below uses the same "not a heading/list/table"
      // test that routed us into this branch, so testing it again on line
      // `i` first could match zero lines forever on input this branch is
      // not otherwise equipped to advance past.
      const para = [line];
      i++;
      while (i < lines.length && lines[i].trim() !== "" && !/^#|^-\s+|^\d+\.\s+|^\|/.test(lines[i])) { para.push(lines[i]); i++; }
      blocks.push({ type: "p", text: para.join(" ") });
    }
  }
  return blocks;
}

function renderMarkdown(blocks) {
  const out = el("div");
  for (const b of blocks) {
    if (b.type === "h1") out.appendChild(el("h1", null, renderInline(b.text)));
    else if (b.type === "h2") out.appendChild(el("h2", null, renderInline(b.text)));
    else if (b.type === "p") out.appendChild(el("p", null, renderInline(b.text)));
    else if (b.type === "ul") out.appendChild(el("ul", null, b.items.map((t) => el("li", null, renderInline(t)))));
    else if (b.type === "ol") out.appendChild(el("ol", null, b.items.map((t) => el("li", null, renderInline(t)))));
    else if (b.type === "table") {
      out.appendChild(el(
        "table", null,
        el("thead", null, el("tr", null, b.header.map((h) => el("th", null, renderInline(h))))),
        el("tbody", null, b.rows.map((row) => el("tr", null, row.map((c) => el("td", null, renderInline(c)))))),
      ));
    }
  }
  return out;
}

async function renderStrategy() {
  if (strategyText === null) strategyText = await (await fetch("/strategy")).text();
  fill(document.getElementById("strategy"), renderMarkdown(parseMarkdown(strategyText)));
}

// ── boot ─────────────────────────────────────────────────────────────────

function init() {
  initTabs();
  initBoardControls();
  initTeamControls();
  document.getElementById("undo").addEventListener("click", () => post("/api/undo", {}));
  load();
  setInterval(load, 5000);
}

init();
