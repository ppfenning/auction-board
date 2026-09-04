# Live sync: keeping the board current from the ESPN draft room

A Claude Code session with Claude in Chrome watches the ESPN draft room,
reads each completed auction, and posts it to the board. You bid in the
room; the session keeps the numbers straight and talks in chat. It never
clicks in the room and never bids.

## Before the draft (Saturday mock first)

1. Start the board: `cd ~/repos/auction-board && scala-cli run . -- --port 8080 --state draft.json`
2. Open http://localhost:8080, go to **Teams → Rename teams**, and type the
   ten team names EXACTLY as ESPN shows them. Set **I am**. The sync posts
   sales by ESPN's team name; a mismatch is a refused sale, not a wrong one.
3. In a new Claude Code session, say:
   `Live-sync dry run: follow docs/live-sync.md in ~/repos/auction-board.`
   The session creates a Chrome tab (its own tab group); open the ESPN
   draft room **in that tab**, logged in as you. If ESPN pops the room into
   a new window, copy its URL into the Claude tab instead.
4. The session runs `tools/espn-extract.js` in the room tab, fixes its
   selectors against the real page (the file says which are guesses), and
   proves the loop with the mock's first few picks.

## During the draft

The session loops every 10-15 seconds:

1. Run the extractor in the room tab → the list of completed sales.
2. Diff against what the board already has (`GET /api/view`, players with
   `soldTo` set, matched by name).
3. For each new sale, `POST /api/sell-by-name {"name", "team", "price", "pos"}`.
   - 200: recorded; the response is the new View.
   - 400 "no confident match ... closest: ...": the label did not match at
     0.8. Retry with the closest candidate's exact name if it is obviously
     the same player; otherwise ask in chat.
   - 400 "not on the sheet": `POST /api/sell-unlisted {"name","pos","team","price"}`
     adds the player at $1 and records the sale, so the room's money counts.
   - 400 "unknown team": the team names do not match ESPN's; rename on the
     Teams tab and retry.
4. Say what changed when it matters: a star's price against MAX, the room
   premium crossing 1.20, your surplus, the Nominate tab's top pick when
   your nomination is next.

`GET /api/match?name=Ken%20Walker%20III&pos=RB` shows what a label would
match and its score, without recording anything.

## Rules for the session

- Read-only in the room tab: page text and the extractor script only. No
  clicks, no screenshots of that tab while a bid is live (a screenshot is
  harmless, but a click is not).
- Never record a sale from a label below 0.8 without asking.
- If the room tab is lost, say so immediately; the board keeps its state
  in draft.json and the loop resumes from the diff.
- Undo on the board is for the human. A mis-recorded sale: say it, and
  let Pat click Undo or fix it.

## What can go wrong

- ESPN renders the pick history lazily or paginated: the extractor may
  need to scroll the panel or read a "draft recap" view instead.
- Two players with the same surname (Monangai): pass `pos` and, if still
  ambiguous, ask.
- Kickers and deep fliers are not on the sheet: sell-unlisted handles
  them; the value is $1 so they barely move inflation.
