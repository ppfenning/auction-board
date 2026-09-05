// ==UserScript==
// @name         Auction Board sidebar
// @namespace    auction-board
// @version      0.2
// @description  Watches the ESPN auction room, records sales to the local board, and shows what to bid.
// @match        https://fantasy.espn.com/football/draft*
// @match        https://fantasy.espn.com/*draft*
// @grant        GM_xmlhttpRequest
// @connect      localhost
// @run-at       document-idle
// ==/UserScript==

// SKELETON. The three functions marked GUESS read ESPN's DOM and were
// written without seeing the 2026 room; the Saturday mock draft fixes them.
// Everything else — the board calls, the sidebar, the dedupe — is real.
//
// Flow: a MutationObserver marks the page dirty; a 1s tick reads the pick
// history and the player on the block. Each pick the board does not yet
// have is matched through GET /api/match: a confident match that the
// board already shows as sold is skipped (so a page reload mid-draft
// records nothing twice), a confident unsold match is POSTed by label, no
// candidate at all goes to sell-unlisted, and a weak match is parked as
// "needs a human" and shown in the sidebar — it never blocks the picks
// after it. The block's player gets GET /api/advice and the sidebar shows
// the bid. /api/view refreshes the numbers every 5s. Nothing here clicks
// anything in the room.

(() => {
  const BOARD = "http://localhost:8080";
  const TICK_MS = 1000;
  const VIEW_MS = 5000;
  const CONFIDENT = 0.8;

  // ── board calls (GM_xmlhttpRequest: the room's CSP cannot block them) ──
  const call = (method, path, body) =>
    new Promise((resolve) => {
      GM_xmlhttpRequest({
        method,
        url: BOARD + path,
        headers: { "content-type": "application/json" },
        data: body ? JSON.stringify(body) : undefined,
        onload: (r) => { let data = null; try { data = JSON.parse(r.responseText); } catch (e) { data = { error: r.responseText }; } resolve({ ok: r.status < 300, status: r.status, data }); },
        onerror: () => resolve({ ok: false, status: 0, data: { error: "board unreachable at " + BOARD } }),
      });
    });

  // ── GUESS: the completed sales, oldest first ─────────────────────────
  // Return [{name, pos, team, price}] exactly as ESPN prints them.
  function readPicks() {
    const rows = Array.from(document.querySelectorAll('[class*="pick-history"] [class*="row"], [data-testid*="pick"] tr'));
    return rows.map((row) => {
      const text = (sel) => ((row.querySelector(sel) || {}).textContent || "").trim();
      const name = text('[class*="player-name"], [class*="playerName"], .name');
      const pos = text('[class*="position"], .pos').replace(/[^A-Z/]/g, "").replace("D/ST", "DST");
      const team = text('[class*="team-name"], [class*="teamName"], .team');
      const price = Number((text('[class*="price"], [class*="bid"], .price').match(/\d+/) || [0])[0]);
      return { name, pos, team, price };
    }).filter((p) => p.name && p.price > 0);
  }

  // ── GUESS: the player on the block and the current high bid ─────────
  function readBlock() {
    const panel = document.querySelector('[class*="auction"], [class*="on-the-block"], [data-testid*="nomination"]');
    if (!panel) return null;
    const text = (sel) => ((panel.querySelector(sel) || {}).textContent || "").trim();
    const name = text('[class*="player-name"], [class*="playerName"], .name');
    const pos = text('[class*="position"], .pos').replace(/[^A-Z/]/g, "").replace("D/ST", "DST");
    const bid = Number((text('[class*="current-bid"], [class*="bid"], .bid').match(/\d+/) || [0])[0]);
    return name ? { name, pos, bid } : null;
  }

  // ── GUESS: whose turn it is to nominate ──────────────────────────────
  function readNominator() {
    const el = document.querySelector('[class*="on-the-clock"], [class*="nominating"]');
    return el ? el.textContent.trim() : "";
  }

  // ── recording sales ──────────────────────────────────────────────────
  // A pick's key is what the room printed; `done` holds keys the board is
  // known to have (recorded here, or found sold on the board after a
  // reload), `pending` the ones a human must settle. Neither blocks the
  // others: every tick retries what is neither done nor pending.
  const pickKey = (p) => `${p.name}|${p.team}|${p.price}`;
  const done = new Set();
  const pending = new Map(); // key -> reason
  let syncing = false;
  const log = [];
  function note(msg) { log.unshift(new Date().toLocaleTimeString() + " " + msg); log.length = Math.min(log.length, 8); }

  async function recordOne(p) {
    const m = await call("GET", `/api/match?name=${encodeURIComponent(p.name)}&pos=${encodeURIComponent(p.pos || "")}`);
    if (!m.ok) { note(`board error: ${m.data.error}`); return "retry"; }
    const top = m.data[0];
    if (top && top.score >= CONFIDENT) {
      const onBoard = view && view.players.find((v) => v.id === top.id);
      if (onBoard && onBoard.soldTo !== null) return "done"; // already there (reload, or recorded by hand)
      const r = await call("POST", "/api/sell-by-name", { name: p.name, team: p.team, price: p.price, pos: p.pos });
      if (r.ok) { note(`recorded ${p.name} → ${p.team} $${p.price}`); view = r.data; return "done"; }
      if (/already sold/.test(r.data.error || "")) return "done";
      pending.set(pickKey(p), r.data.error); return "pending"; // e.g. unknown team: a human renames and it retries after a reload
    }
    if (!top) {
      const r = await call("POST", "/api/sell-unlisted", { name: p.name, pos: p.pos || "WR", team: p.team, price: p.price });
      if (r.ok) { note(`recorded (unlisted) ${p.name} → ${p.team} $${p.price}`); view = r.data; return "done"; }
      pending.set(pickKey(p), r.data.error); return "pending";
    }
    pending.set(pickKey(p), `weak match: ${m.data.map((c) => `${c.name} ${c.score}`).join(", ")}`);
    return "pending";
  }

  async function syncPicks(picks) {
    if (syncing) return;
    syncing = true;
    try {
      for (const p of picks) {
        const key = pickKey(p);
        if (done.has(key) || pending.has(key)) continue;
        const outcome = await recordOne(p);
        if (outcome === "done") done.add(key);
        else if (outcome === "retry") break; // board unreachable: try again next tick, in order
      }
    } finally { syncing = false; }
  }

  // ── sidebar ──────────────────────────────────────────────────────────
  const css = `
    #ab-side{position:fixed;top:0;right:0;width:300px;height:100vh;z-index:99999;background:#0f1115;color:#e6e8ee;font:13px/1.35 system-ui,sans-serif;border-left:1px solid #2a2f3a;padding:10px;box-sizing:border-box;overflow:auto}
    #ab-side h1{font-size:13px;margin:8px 0 6px;color:#8b93a7}#ab-side .big{font-size:28px;font-weight:700}
    #ab-side .row{display:flex;justify-content:space-between;padding:2px 0;border-bottom:1px solid #1d2129}
    #ab-side .acc{color:#5ad1a5}#ab-side .bad{color:#ef6f6c}#ab-side .warn{color:#f0b35c}#ab-side .dim{color:#8b93a7}
    #ab-side .nom{padding:4px 0;border-bottom:1px solid #1d2129}#ab-side .log{font-size:11px;color:#8b93a7;margin-top:8px}
  `;
  const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text !== undefined) n.textContent = text; return n; };
  function ensureSidebar() {
    let side = document.getElementById("ab-side");
    if (side) return side;
    const style = document.createElement("style"); style.textContent = css; document.head.appendChild(style);
    side = el("div"); side.id = "ab-side"; document.body.appendChild(side);
    return side;
  }

  let view = null, advice = null, adviceFor = null, adviceInFlight = null;
  function render(block) {
    const side = ensureSidebar();
    while (side.firstChild) side.removeChild(side.firstChild);
    side.appendChild(el("h1", null, "ON THE BLOCK"));
    if (block && advice && adviceFor === block.name && advice.player) {
      const p = advice.player;
      side.appendChild(el("div", null, `${p.name} · ${p.pos} · tier ${p.tier}`));
      side.appendChild(el("div", "big " + (block.bid >= advice.bid ? "bad" : "acc"), advice.bid > 0 ? `bid to $${advice.bid}` : "pass"));
      side.appendChild(el("div", "dim", `value $${p.value} · target $${p.target} · MAX $${advice.boardMax} · walk-away $${advice.walkAway} · now $${block.bid}`));
      side.appendChild(el("div", "dim", advice.reason));
      if (p.note) side.appendChild(el("div", "warn", p.note));
    } else if (block) {
      side.appendChild(el("div", null, block.name));
      side.appendChild(el("div", "warn", advice && adviceFor === block.name && advice.error ? advice.error : "looking up…"));
    } else {
      side.appendChild(el("div", "dim", "nobody on the block"));
    }
    if (view) {
      const me = view.teams[view.myTeam];
      const surplus = Math.max(0, me.remaining - view.planRemaining);
      const rows = [
        ["my $", me.remaining], ["max bid", me.maxBid], ["plan left", view.planRemaining],
        ["surplus", surplus], ["room premium", view.premium.toFixed(2)], ["inflation", view.inflation.toFixed(2)], ["open slots", me.slotsOpen],
      ];
      side.appendChild(el("h1", null, "ME"));
      for (const [k, v] of rows) { const r = el("div", "row"); r.appendChild(el("span", "dim", k)); r.appendChild(el("span", k === "room premium" && Number(v) > 1.2 ? "bad" : k === "surplus" && v > 0 ? "acc" : "", String(v))); side.appendChild(r); }
      side.appendChild(el("h1", null, "NOMINATE NEXT"));
      for (const n of view.nominations.slice(0, 4)) { const d = el("div", "nom"); d.appendChild(el("b", null, `${n.name} `)); d.appendChild(el("span", "dim", `${n.pos} $${n.value} — ${n.reason}`)); side.appendChild(d); }
    }
    if (pending.size) {
      side.appendChild(el("h1", "bad", `NEEDS A HUMAN (${pending.size})`));
      for (const [key, why] of pending) side.appendChild(el("div", "warn", `${key.replace(/\|/g, " · ")}: ${why}`));
    }
    const nominator = readNominator();
    if (nominator) side.appendChild(el("div", "dim", "on the clock: " + nominator));
    const lg = el("div", "log"); for (const line of log) lg.appendChild(el("div", null, line)); side.appendChild(lg);
  }

  // ── loop ─────────────────────────────────────────────────────────────
  let dirty = true;
  new MutationObserver(() => { dirty = true; }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });

  async function refreshAdvice(block) {
    // One request per block name, at a time; a failed fetch is retried on
    // the next tick because adviceFor is only set on an answer.
    if (adviceInFlight === block.name) return;
    adviceInFlight = block.name;
    try {
      const r = await call("GET", `/api/advice?name=${encodeURIComponent(block.name)}&pos=${encodeURIComponent(block.pos || "")}`);
      if (r.status === 0) return; // unreachable: keep adviceFor as it was, retry next tick
      advice = r.ok ? r.data : { error: r.data.error };
      adviceFor = block.name;
    } finally { adviceInFlight = null; }
  }

  async function tick() {
    if (!dirty) return;
    dirty = false;
    const block = readBlock();
    if (block && adviceFor !== block.name) await refreshAdvice(block);
    await syncPicks(readPicks());
    render(block);
  }
  async function refreshView() {
    const r = await call("GET", "/api/view");
    if (r.ok) { view = r.data; render(readBlock()); }
    else note(r.data.error);
  }

  setInterval(tick, TICK_MS);
  setInterval(refreshView, VIEW_MS);
  refreshView();
})();
