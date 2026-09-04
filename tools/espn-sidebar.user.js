// ==UserScript==
// @name         Auction Board sidebar
// @namespace    auction-board
// @version      0.1
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
// history and the player on the block; new sales POST to the board by
// ESPN's own label (sell-by-name, then sell-unlisted if the sheet lacks the
// player); the block's player gets GET /api/advice and the sidebar shows
// the bid. /api/view refreshes the surplus, premium and nomination lines
// every 5s. Nothing here clicks anything in the room.

(() => {
  const BOARD = "http://localhost:8080";
  const TICK_MS = 1000;
  const VIEW_MS = 5000;

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

  // ── recording sales: dedupe on the room's own order ──────────────────
  let recorded = 0; // how many of the room's picks the board already has
  let syncing = false;
  const log = [];
  function note(msg) { log.unshift(new Date().toLocaleTimeString() + " " + msg); log.length = Math.min(log.length, 8); }

  async function syncPicks(picks) {
    if (syncing || picks.length <= recorded) return;
    syncing = true;
    try {
      for (const p of picks.slice(recorded)) {
        let r = await call("POST", "/api/sell-by-name", { name: p.name, team: p.team, price: p.price, pos: p.pos });
        if (!r.ok && /not on the sheet/.test(r.data.error || "")) {
          r = await call("POST", "/api/sell-unlisted", { name: p.name, pos: p.pos || "WR", team: p.team, price: p.price });
        }
        if (r.ok) { recorded += 1; note(`recorded ${p.name} → ${p.team} $${p.price}`); }
        else { note(`NOT recorded ${p.name}: ${r.data.error}`); break; } // stop, keep order; a human fixes the board
      }
    } finally { syncing = false; }
  }

  // ── sidebar ──────────────────────────────────────────────────────────
  const css = `
    #ab-side{position:fixed;top:0;right:0;width:300px;height:100vh;z-index:99999;background:#0f1115;color:#e6e8ee;font:13px/1.35 system-ui,sans-serif;border-left:1px solid #2a2f3a;padding:10px;box-sizing:border-box;overflow:auto}
    #ab-side h1{font-size:13px;margin:0 0 6px;color:#8b93a7}#ab-side .big{font-size:28px;font-weight:700}
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

  let view = null, advice = null, lastBlockName = null;
  function render(block) {
    const side = ensureSidebar();
    while (side.firstChild) side.removeChild(side.firstChild);
    side.appendChild(el("h1", null, "ON THE BLOCK"));
    if (block && advice && advice.player) {
      const p = advice.player;
      side.appendChild(el("div", null, `${p.name} · ${p.pos} · tier ${p.tier}`));
      const bid = el("div", "big " + (block.bid >= advice.bid ? "bad" : "acc"), `bid to $${advice.bid}`);
      side.appendChild(bid);
      side.appendChild(el("div", "dim", `value $${p.value} · target $${p.target} · MAX $${advice.boardMax} · now $${block.bid}`));
      side.appendChild(el("div", "dim", advice.reason));
      if (p.note) side.appendChild(el("div", "warn", p.note));
    } else if (block) {
      side.appendChild(el("div", null, block.name));
      side.appendChild(el("div", "warn", advice && advice.error ? advice.error : "looking up…"));
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
    const nominator = readNominator();
    if (nominator) side.appendChild(el("div", "dim", "on the clock: " + nominator));
    const lg = el("div", "log"); for (const line of log) lg.appendChild(el("div", null, line)); side.appendChild(lg);
  }

  // ── loop ─────────────────────────────────────────────────────────────
  let dirty = true;
  new MutationObserver(() => { dirty = true; }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });

  async function tick() {
    if (!dirty) return;
    dirty = false;
    const block = readBlock();
    if (block && block.name !== lastBlockName) {
      lastBlockName = block.name;
      const r = await call("GET", `/api/advice?name=${encodeURIComponent(block.name)}&pos=${encodeURIComponent(block.pos || "")}`);
      advice = r.ok ? r.data : { error: r.data.error };
    }
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
