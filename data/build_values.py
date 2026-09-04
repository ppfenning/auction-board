"""Derive data/players.csv (10-team, half-PPR, no TE slot, $200) from the raw
FFToday 12-team PPR table. Pure transform over the raw file; run once:

    python3 data/build_values.py

Method: value above positional replacement, rescaled so the 160 rostered
spots (10 teams x 16) sum to $2000, with a half-PPR tilt (RB up, WR down) and
TE cut to flex-only value because this league starts no TE.
"""
import csv, sys
from pathlib import Path

RAW = Path(__file__).parent / "raw" / "fftoday-2026-08-20-12team-ppr.csv"
OUT = Path(__file__).parent / "players.csv"
TEAMS, SPOTS, BUDGET = 10, 16, 200
REPLACEMENT = {"QB": 14, "RB": 56, "WR": 62, "TE": 4, "DST": 10, "K": 10}
TILT = {"RB": 1.04, "WR": 0.96, "QB": 1.0, "TE": 0.45, "DST": 1.0, "K": 1.0}
TIERS = {
    "RB": [50, 40, 30, 24, 15, 8, 3],
    "WR": [45, 33, 26, 20, 13, 7, 3],
    "QB": [20, 9, 5, 2],
    "TE": [6, 2],
    "DST": [2],
    "K": [2],
}
NOTES = {
    "Ashton Jeanty": "ankle sprain; on 53-man, no return date as of Sep 2 - discount",
    "Malik Nabers": "back from ACL; expected Wk1, ramp risk",
    "Zach Charbonnet": "reserve/PUP - out first 4 games",
    "Jordyn Tyson": "hamstring - out until October",
    "Zay Flowers": "quad contusion, day-to-day as of Sep 2",
    "Christian McCaffrey": "late-Aug 'tightness'; age 30 - price the risk",
    "Ricky Pearsall": "OUT for season (PCL)",
    "Jayden Higgins": "OUT for season (ACL)",
    "Puka Nacua": "groin soreness in camp, back at practice, expected Wk1",
    "Breece Hall": "groin strain, expected Wk1",
    "Emeka Egbuka": "toe sprain, expected Wk1",
    "TreVeyon Henderson": "ankle, expected Wk1",
    "Josh Jacobs": "groin, expected to play Wk1",
    "Luther Burden III": "groin, Bears optimistic for Wk1",
    "Alec Pierce": "ankle post-surgery, gradual workload",
    "Tank Dell": "IR with return designation",
    "Kyle Monangai": "hyperextended knee, week-to-week",
    "Tyler Warren": "groin, likely Wk1 (no TE slot anyway)",
    "George Kittle": "Achilles return (no TE slot anyway)",
}


def tier(pos: str, value: int) -> int:
    cuts = TIERS[pos]
    return next((i + 1 for i, c in enumerate(cuts) if value >= c), len(cuts) + 1)


def replacement_floor(values: list[float], depth: int) -> float:
    """The 12-team value of the player at this position's 10-team rostered depth."""
    ordered = sorted(values, reverse=True)
    return ordered[depth - 1] if len(ordered) >= depth else 1.0


def derive(rows):
    """Pure: rows in, derived rows out. Each binding is defined once; no
    structure is mutated after it is built."""
    positions = {r["pos"] for r in rows}
    floors = {
        pos: replacement_floor([float(r["value_12ppr"]) for r in rows if r["pos"] == pos], REPLACEMENT[pos])
        for pos in positions
    }
    above = {
        r["player"]: max(0.0, float(r["value_12ppr"]) - floors[r["pos"]]) * TILT[r["pos"]]
        for r in rows
    }
    pool = TEAMS * BUDGET - TEAMS * SPOTS
    top = sorted(above.values(), reverse=True)[: TEAMS * SPOTS]
    k = pool / sum(top)
    valued = [dict(r, value=round(1 + above[r["player"]] * k)) for r in rows]
    ordered = sorted(valued, key=lambda r: (-r["value"], int(r["rank"])))
    return [
        {
            "rank": i,
            "pos": r["pos"],
            "player": r["player"],
            "team": r["team"],
            "value": r["value"],
            "tier": tier(r["pos"], r["value"]),
            "ppr12": int(float(r["value_12ppr"])),
            "note": NOTES.get(r["player"], ""),
        }
        for i, r in enumerate(ordered, 1)
    ]


if __name__ == "__main__":
    rows = list(csv.DictReader(RAW.open()))
    out = derive(rows)
    with OUT.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(out[0].keys()))
        w.writeheader()
        w.writerows(out)
    print(f"wrote {len(out)} players; top-160 sum = {sum(r['value'] for r in out[:160])}", file=sys.stderr)
