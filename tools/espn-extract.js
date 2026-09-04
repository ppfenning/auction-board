// Runs inside the ESPN draft-room tab (via Claude in Chrome's javascript
// tool, or the console) and returns the completed auctions as JSON:
//   [{"name": "Jahmyr Gibbs", "pos": "RB", "team": "Pat's Picks", "price": 60}, ...]
//
// The selectors below are PLACEHOLDERS until the Saturday mock draft: open
// the room, run `document.querySelector(...)` by hand on the pick-history
// panel, and replace them. Keep this file honest: if a selector is a guess,
// say so in a comment.
//
// What the runbook needs from this script:
//   - every completed sale, oldest first, in the order the room shows them;
//   - the exact label ESPN prints for the player (the board matches it);
//   - the drafting team's name exactly as ESPN prints it (the board's team
//     names should be renamed to match before the draft starts);
//   - the winning price as a number.
(() => {
  // GUESS: ESPN's draft room renders the pick history as rows inside a
  // panel whose test id or class contains "pick" / "history". Replace.
  const rows = Array.from(document.querySelectorAll('[class*="pick-history"] [class*="row"], [data-testid*="pick"] tr'));
  const picks = rows.map((row) => {
    const text = (sel) => (row.querySelector(sel) || {}).textContent || "";
    const name = text('[class*="player-name"], [class*="playerName"], .name').trim();
    const pos = text('[class*="position"], .pos').trim().replace(/[^A-Z/]/g, "").replace("D/ST", "DST");
    const team = text('[class*="team-name"], [class*="teamName"], .team').trim();
    const price = Number((text('[class*="price"], [class*="bid"], .price').match(/\d+/) || [0])[0]);
    return { name, pos, team, price };
  }).filter((p) => p.name && p.price > 0);
  return JSON.stringify(picks);
})();
