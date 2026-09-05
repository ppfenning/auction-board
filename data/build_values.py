"""Derive data/players.csv (10-team, half-PPR, $200; QB/2RB/3WR/TE/FLEX/DST/K
+ 7 bench = 17 spots) from the raw FFToday 12-team PPR table. Pure transform
over the raw file; run once:

    python3 data/build_values.py

Method: value above positional replacement, rescaled so the 170 rostered
spots (10 teams x 17) sum to $2000, with a half-PPR tilt (RB up, WR and TE
down a little, since receptions are worth half as much).
"""
import csv, sys
from pathlib import Path

RAW = Path(__file__).parent / "raw" / "fftoday-2026-08-20-12team-ppr.csv"
OUT = Path(__file__).parent / "players.csv"
TEAMS, SPOTS, BUDGET = 10, 17, 200
REPLACEMENT = {"QB": 14, "RB": 54, "WR": 60, "TE": 14, "DST": 10, "K": 10}
TILT = {"RB": 1.04, "WR": 0.96, "QB": 1.0, "TE": 0.97, "DST": 1.0, "K": 1.0}
TIERS = {
    "RB": [50, 40, 30, 24, 15, 8, 3],
    "WR": [45, 33, 26, 20, 13, 7, 3],
    "QB": [20, 9, 5, 2],
    "TE": [22, 12, 7, 3],
    "DST": [2],
    "K": [2],
}
# Sleepers the Aug 20 table does not list at all; they enter at the $1 floor
# so the board can show them and their note.
EXTRA_ROWS = [
    {"rank": "900", "pos": "TE", "player": "Terrance Ferguson", "team": "LAR", "value_12ppr": "1"},
    {"rank": "901", "pos": "WR", "player": "Zachariah Branch", "team": "ATL", "value_12ppr": "1"},
    {"rank": "902", "pos": "RB", "player": "Jaylen Wright", "team": "MIA", "value_12ppr": "1"},
] + [
    # Every defense the sheet did not list, at the floor, so a live room's
    # "Ravens D/ST" sale has a row to land on.
    {"rank": str(910 + i), "pos": "DST", "player": city, "team": abbr, "value_12ppr": "1"}
    for i, (city, abbr) in enumerate([
        ("Arizona", "ARI"), ("Atlanta", "ATL"), ("Baltimore", "BAL"), ("Carolina", "CAR"), ("Chicago", "CHI"),
        ("Cincinnati", "CIN"), ("Cleveland", "CLE"), ("Dallas", "DAL"), ("Green Bay", "GB"), ("Indianapolis", "IND"),
        ("Kansas City", "KC"), ("Las Vegas", "LV"), ("Miami", "MIA"), ("New Orleans", "NO"), ("New York Giants", "NYG"),
        ("New York Jets", "NYJ"), ("San Francisco", "SF"), ("Tampa Bay", "TB"), ("Tennessee", "TEN"), ("Washington", "WAS"),
    ])
]

# Hand adjustments to the rescaled value where the news is newer than the
# Aug 20 table. Each one is explained in docs/strategy.md.
VALUE_OVERRIDES = {
    "Josh Jacobs": 3,          # commissioner's exempt list Aug 30; next hearing Nov 17
    "MarShawn Lloyd": 12,      # Jacobs' job for months, thin depth chart
    "Jeremiyah Love": 22,      # high-ankle sprain, 50-50 for Wk1
    "Ashton Jeanty": 24,       # ankle, no return date as of Sep 2
    "Zach Charbonnet": 1,      # reserve/PUP, out 4 games
    "Jordyn Tyson": 1,         # hamstring, out until October
}

# Per-player caps from docs/strategy.md (Values, Sleepers, Handcuffs, Avoid):
# the most the board will ever advise for the player, surplus or not.
CAPS = {
    "Omarion Hampton": 35, "Cam Skattebo": 24, "Javonte Williams": 22, "Bhayshul Tuten": 16, "Jeremiyah Love": 22,
    "Jadarian Price": 12, "Emeka Egbuka": 21, "Malik Nabers": 17, "Luther Burden III": 15, "D.J. Moore": 15,
    "Matthew Golden": 6, "Michael Pittman Jr.": 10, "Justin Herbert": 7, "Jared Goff": 4, "Brock Purdy": 4,
    "Jaxson Dart": 4, "Dallas Goedert": 5, "Isaiah Likely": 5, "Travis Kelce": 5, "Sam LaPorta": 5,
    "Mark Andrews": 5, "Juwan Johnson": 2, "MarShawn Lloyd": 14, "Mike Washington Jr.": 5,
    "Chris Rodriguez Jr.": 4, "Keaton Mitchell": 2, "Dylan Sampson": 2, "Kenneth Gainwell": 4,
    "De'Zhaun Stribling": 4, "KC Concepcion": 2, "Rashid Shaheed": 2, "Jalen McMillan": 2, "Carnell Tate": 5,
    "Zachariah Branch": 1, "Tyler Shough": 2, "Kyler Murray": 3, "Baker Mayfield": 3, "Terrance Ferguson": 1,
    "Blake Corum": 4, "Tank Bigsby": 3, "Kyle Monangai": 3, "Ray Davis": 2, "Rico Dowdle": 3,
    "Tyler Allgeier": 3, "Jaylen Wright": 2,
    "Josh Jacobs": 3, "Ashton Jeanty": 24, "Christian McCaffrey": 47, "Derrick Henry": 22, "Kenneth Walker": 22,
    "Rashee Rice": 24, "Davante Adams": 15, "Mike Evans": 12, "Bo Nix": 3, "Josh Allen": 12,
    "Jonathan Taylor": 50, "Ja'Marr Chase": 60, "Jaxon Smith-Njigba": 56, "Amon-Ra St. Brown": 54,
    "Colston Loveland": 14, "Tyler Warren": 11, "Harold Fannin Jr.": 11, "Kyle Pitts": 9,
}

# Tags the board shows in the Note column: VALUE (pay to value, the room
# will not), SLEEPER ($1-4 with a path to starting), HANDCUFF, AVOID.
NOTES = {
    # ── injuries and news ──
    "Ashton Jeanty": "AVOID at sheet price: ankle sprain, on 53-man, no return date as of Sep 2; $24 cap",
    "Josh Jacobs": "AVOID: commissioner's exempt list Aug 30, cannot play; hearing Nov 17. $1-3 stash only",
    "Jeremiyah Love": "high-ankle sprain, 50-50 for Wk1 (Sep 13); no surgery. $22 cap, then a steal",
    "Malik Nabers": "back from ACL; expected Wk1, ramp risk. VALUE at $17",
    "Zach Charbonnet": "reserve/PUP - out first 4 games",
    "Jordyn Tyson": "hamstring - out until October",
    "Zay Flowers": "quad contusion, day-to-day as of Sep 2",
    "Christian McCaffrey": "AVOID above $47: 450-touch season, age 30, late-Aug 'tightness'",
    "Ricky Pearsall": "OUT for season (PCL)",
    "Jayden Higgins": "OUT for season (ACL)",
    "Puka Nacua": "groin soreness in camp, back at practice, expected Wk1",
    "Breece Hall": "groin strain, expected Wk1",
    "Emeka Egbuka": "VALUE: toe sprain but expected Wk1; Evans gone, top target for Mayfield",
    "TreVeyon Henderson": "ankle, expected Wk1",
    "Luther Burden III": "VALUE: groin (Bears optimistic Wk1); DJ Moore's 85+ targets vacated",
    "Alec Pierce": "ankle post-surgery, gradual workload",
    "Tank Dell": "IR with return designation",
    "Kyle Monangai": "HANDCUFF to Swift: hyperextended knee, week-to-week; 783 yds as rookie",
    "Tyler Warren": "groin, likely Wk1; practice ramp questioned",
    "George Kittle": "Achilles return; activated from PUP, individual work only as of Sep 2",
    "Tucker Kraft": "ACL return, expected full go Wk1",
    # ── values: the room prices these under what the role says ──
    "Javonte Williams": "VALUE: 252 carries as Dallas's lead back, run-heavy scheme; pay to $22",
    "Cam Skattebo": "VALUE: top-11 pace wks 3-7 before injury; Dart's guy; pay to $24",
    "Omarion Hampton": "VALUE: healthy RB1 upside, Herbert offense; pay to $35",
    "Bhayshul Tuten": "VALUE: Jacksonville lead back after Etienne left; 3.2 YAC/att",
    "D.J. Moore": "VALUE: Allen's new primary read; WR6 in 2023",
    "Matthew Golden": "VALUE: 138 GB targets vacated (Doubs/Wicks/Heath); 4.29 speed",
    "Justin Herbert": "VALUE QB: QB10 with no tackles in 2025; Alt+Slater back; $5-7",
    "Jared Goff": "VALUE QB: 4,564 yds, 30+ TD every year, priced QB16; $3-4",
    "Isaiah Likely": "VALUE TE: $40M deal, Harbaugh reunion, NYG second read; $3-5",
    "Dallas Goedert": "VALUE TE: TE4 in 2025, AJ Brown gone; $3-5",
    "Juwan Johnson": "VALUE TE: 102 targets, 889 yds in 2025; Tyson out; $2-3",
    # ── sleepers: $1-4 with a path ──
    "MarShawn Lloyd": "SLEEPER: Jacobs' job for months; explosive preseason; pay to $14",
    "Mike Washington Jr.": "SLEEPER: 10.0 RAS, 168 preseason yds; Jeanty's ankle; $3-5",
    "Chris Rodriguez Jr.": "SLEEPER: Coen's Kentucky back, co-starter with Tuten, red-zone role; $2-4",
    "Keaton Mitchell": "SLEEPER: 4.37 speed, McDaniel scheme, behind Hampton; $2",
    "Dylan Sampson": "SLEEPER: Monken's pass-catching back behind Judkins; $2",
    "Jadarian Price": "SLEEPER: Seattle first-rounder, Walker gone, Charbonnet on PUP; $8-12",
    "De'Zhaun Stribling": "SLEEPER: SF WR2 after Pearsall/Kirk/Evans injuries; camp star; $2-4",
    "KC Concepcion": "SLEEPER: Browns rookie, elite YAC, Monken moving him around; $2",
    "Rashid Shaheed": "SLEEPER: $51M deal, Darnold rapport, more than a deep threat; $2",
    "Jalen McMillan": "SLEEPER: Mayfield chemistry, Robinson opening the offense; $2",
    "Tyler Shough": "SLEEPER QB: QB9 from Wk10 on, rushing upside, Moore offense; $1-2",
    "Kyler Murray": "SLEEPER QB: O'Connell scheme, Jefferson/Addison; $2-3",
    "Baker Mayfield": "SLEEPER QB: priced QB12+ after a down year; $2-3",
    "Terrance Ferguson": "SLEEPER TE: 18.3 aDOT rookie, McVay big-slot role; $1",
    "Zachariah Branch": "SLEEPER: 4.35 speed, screen game, easy ATL depth chart; $1",
    # ── handcuffs ──
    "Blake Corum": "HANDCUFF to Kyren: 746 yds, 6 TD, 5.1 YPC in 2025; standalone value; $3-4",
    "Tank Bigsby": "HANDCUFF to Saquon: 1st in YAC/att, 2nd missed-tackle rate; $2-3",
    "Ray Davis": "HANDCUFF to Cook: majority of an elite rushing offense if Cook sits; $2",
    "Jaylen Wright": "HANDCUFF to Achane: fantasy-relevant the week Achane misses; $1-2",
    "Rico Dowdle": "HANDCUFF to Warren: 4 of 6 carries inside the 5 were TDs; $2",
    "Tyler Allgeier": "HANDCUFF to Love: early-season role if Love sits; 8 TD in 2025; $2-3",
    # ── avoid at price ──
    "Derrick Henry": "AVOID above $22: age 32, ESPN bust list",
    "Kenneth Walker": "AVOID above $22: new team, drafted competition",
    "Davante Adams": "AVOID above $15: age 33",
    "Mike Evans": "AVOID above $12: age 33, new team",
    "Rashee Rice": "AVOID above $24: target share diluted by additions",
    "Bo Nix": "AVOID above $3: sophomore-slump risk on every bust list",
    "Harold Fannin Jr.": "hard to beat his 2025 total; fine at $8, not $12",
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
    valued = [
        dict(r, value=VALUE_OVERRIDES.get(r["player"], round(1 + above[r["player"]] * k)))
        for r in rows
    ]
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
            "cap": CAPS.get(r["player"], ""),
        }
        for i, r in enumerate(ordered, 1)
    ]


if __name__ == "__main__":
    rows = list(csv.DictReader(RAW.open())) + EXTRA_ROWS
    out = derive(rows)
    with OUT.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(out[0].keys()))
        w.writeheader()
        w.writerows(out)
    rostered = TEAMS * SPOTS
    print(f"wrote {len(out)} players; top-{rostered} sum = {sum(r['value'] for r in out[:rostered])}", file=sys.stderr)
