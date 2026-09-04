# auction-board

A draft-day board for an ESPN salary-cap (auction) fantasy football draft:
10 teams, $200, half-PPR, no TE slot. Scala 3 core (values, inflation, cut
lines, roster math, nomination advice) behind a small Cask server and a
single-page board you keep open next to the ESPN draft room.

## Run on draft day

    scala-cli run . -- --port 8080 --state draft.json

Open http://localhost:8080. Every sale you record is written to `draft.json`
immediately, so a refresh, a crash, or a second browser window never loses
the draft. Use **Teams → I am** to say which team is yours.

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

`pytest -q` runs the same suite through `tests/test_scala.py`, for tooling
that expects pytest.
