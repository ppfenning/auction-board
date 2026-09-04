# auction-board

A draft-day board for an ESPN salary-cap (auction) fantasy football draft:
10 teams, $200, half-PPR, 17-man rosters (QB/2RB/3WR/TE/FLEX/DST/K + 7 bench). Scala 3 core (values, inflation, cut
lines, roster math, nomination advice) behind a small Cask server and a
single-page board you keep open next to the ESPN draft room.

## Run on draft day

    scala-cli run . -- --port 8080 --state draft.json

Open http://localhost:8080. Every sale you record is written to `draft.json`
immediately, so a refresh, a crash, or a second browser window never loses
the draft. Use **Teams → I am** to say which team is yours.

On the board: `/` focuses the search box; Enter in a price box records the
sale; the `$` button overrides a player's value when the room disagrees
with the sheet (Enter saves, Escape cancels); the tag filter pulls up the
VALUE, SLEEPER, HANDCUFF, AVOID and injury-flagged rows; **Undo last**
reverses the most recent sale. The header shows live inflation, your
remaining money and max bid, and what your plan still needs for the slots
you have not filled — when "plan left" turns red it exceeds your money.
**Nominate** ranks who to throw out next and says why. **Strategy** is
docs/strategy.md rendered in place.

## Layout

- `data/raw/` — the FFToday table as fetched (never edited).
- `data/build_values.py` — one-shot rescale to this league; writes `data/players.csv`.
- `docs/strategy.md` — the plan: budget by slot, cut lines, phases, nominations.
- `src/Model.scala` — types, JSON contract (`View`), CSV reader.
- `src/Pricing.scala` — inflation, target/MAX per player, team max bid.
- `src/Draft.scala` — sell / undo / override transitions, roster slot math, plan tracking.
- `src/Nominate.scala` — who to nominate next and why.
- `src/Server.scala` — the Cask edge: routes, file persistence, wiring.
- `src/Page.scala` — the HTML shell; `static/app.js` renders `/api/view` into it.

## Checks

    scala-cli test .
    pytest -q          # same suite, for tooling that expects pytest

`pytest -q` runs the same suite through `tests/test_scala.py`, for tooling
that expects pytest.

## Mock auctions

    scala-cli run . --main-class auction.Mock -- --n 300 --seed 1

Nine simulated rivals with randomised pricing bid against the strategy as
written; the report ranks my lineup among the ten teams under each bidding
policy and prints three sample rooms. Results and the model are in
docs/mocks.md.

## Live sync from the ESPN room

A Claude Code session with Claude in Chrome can watch the draft room and
post each sale to the board by its ESPN label; see docs/live-sync.md.
Routes: `GET /api/match?name=...`, `POST /api/sell-by-name`,
`POST /api/sell-unlisted`. Team names on the board must match ESPN's.
