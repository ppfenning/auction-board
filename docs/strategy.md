# Auction plan — ESPN, 10 teams, $200, half-PPR

Lineup: QB, RB, RB, WR, WR, WR, TE, FLEX (RB/WR/TE), D/ST, K, 7 bench. 17
spots, $200, so $1 per spot is the floor and $184 is the most any one bid can
be. Values are from FFToday's 2026 table (Aug 20), rescaled to this league:
10 teams, half-PPR tilt (RB up 4%, WR and TE down 3-4%), and replacement
level at the depth a 10-team room actually rosters. Injury flags are as of
Sep 2, 2026. Nothing here is a projection of its own; the edge is in the
rescale, the plan, and the discipline.

## The three facts that shape this room

1. **Ten teams.** Waivers are deep: the RB28 and WR40 are free all season.
   Replacement level is high, which means the difference-makers are worth
   MORE, not less. This is a stars-and-scrubs room. Spend big on 3 players,
   then fill with $1-4 guys who have a path to a starting job.
2. **TE is two players and a cliff.** Bowers ($26) and McBride ($24) are a
   tier of their own; then Loveland $13, Warren $11, Fannin $10, Pitts $8,
   and Kelce / LaPorta / Andrews at $5-6. In a 10-team room the TE10 is
   free. Buy one of the big two ONLY at value or under, and only if you are
   then taking a T3 RB1 instead of Gibbs/Bijan. Otherwise spend $5-11 and
   move on; the gap between Fannin and Kelce is not worth $5.
3. **Seven flex-eligible starters and one QB.** QB is one slot in a
   10-team league: the QB10 is a fine starter and costs $3. Never pay $20+
   for Josh Allen. Pay $9-11 for the second tier only if it falls.

## Budget by slot ($200)

| Slot | Plan | Who fits |
|------|-----:|----------|
| QB   | $10  | Maye / Lamar / Hurts / Burrow at $9-11 if one falls; else Daniels / C.Williams $6; else Dak / Nix / Herbert / Purdy $3-4 |
| RB1  | $46  | Gibbs or Bijan ($55-60 ceiling), or CMC/Taylor ($42-47) if the top two go over $62 |
| RB2  | $28  | Cook, Hampton, Saquon, Chase Brown, Achane (T3, $30-36); Walker / Henry / Love / Hall / Kyren ($25-28) if T3 is gone |
| WR1  | $33  | London, Jefferson, Lamb, Olave ($32-36); or Puka / Chase / JSN / ARSB if one goes at value ($45-51) and RB1 becomes a T3 |
| WR2  | $21  | Rice, Collins, AJB, G.Wilson, Pickens, DeVonta, Flowers ($23-28) |
| WR3  | $12  | Nabers, Egbuka, McMillan, Adams, Jamo, McConkey, Waddle ($16-21) or a T5 at $10-13 |
| TE   | $9   | Fannin / Pitts / Warren at $8-11; or Kelce / LaPorta / Andrews at $5-6 and bank the rest |
| FLEX | $11  | The best remaining RB/WR under $13: Etienne, Swift, Higgins, Watson, DJ Moore, Evans |
| D/ST | $2   | Whatever is left; nominate a top D/ST early so someone else pays $4 |
| K    | $1   | Last pick |
| BN1  | $8   | One upside RB with a path to touches (Skattebo, Javonte, Irving, Henderson, RJ Harvey-type) |
| BN2  | $6   | One upside WR (Burden, Tate, Golden, Ayomanor-type) |
| BN3-7| $13  | $1-4 each: handcuffs to MY RBs first, then rookies, then boring vets |

Starters $173, bench $27. Three players at $46 + $33 + $28 = $107 (54%) is
the intended shape. If you land two T1s (say Gibbs $57 + Chase $48), drop to
$18 RB2 / $14 WR2 / $8 WR3 / $5 TE and you are still whole. If Bowers falls
to $20, take him and make RB1 a $36 T3 instead.

## Cut lines on price

The board computes MAX per player live: `max = value * inflation * tierCap`,
where inflation is (money left in the room) / (value left on the board for
the slots still open), and tierCap is 1.10 for tiers 1-2, 1.05 for tier 3,
1.00 for tier 4-5, and 0.90 below that. Rules that never move:

- **Never exceed MAX.** The whole point of the sheet is that you decided
  this before the adrenaline. One exception: the last T1/T2 RB, if you have
  no RB1 yet and the money to absorb it, +$3.
- **Tier 1 RB (Gibbs, Bijan): walk at $63.** Tier 1 WR (Puka, Chase, JSN,
  ARSB): walk at $55. CMC: walk at $47 (age 30, August "tightness").
- **Tier 3 RB ($30-36 values): walk at $38.** This is the zone where the
  room overpays most; let them.
- **Any QB: walk at $12,** except Allen, whom you are not buying.
- **Bowers / McBride: walk at $26.** Loveland $14, Warren / Fannin $11,
  Pitts $9, everyone else $6.
- **D/ST $3, K $1.** No exceptions.
- **Bench: $4 cap** except one $6-8 upside RB.
- **Slot math.** Your max bid is always `remaining - (open slots - 1)`. The
  board shows it; the plan tab shows what is planned for the slots you have
  not filled. When "plan remaining" is more than "remaining budget", you are
  over and must downgrade a slot on purpose, not by accident.

## Phases by nomination round (17 rounds x 10 nominations)

**Rounds 1-3: let the room set the price, buy one star.** The first 20-30
sales are the most expensive and the most informative. Track inflation on the
board: if the first five T1/T2 players go under value the room is tight and
you should buy the NEXT T1 RB at or below value; if they go 10%+ over, wait,
because the mid tiers will be cheap. Target exactly one of {Gibbs, Bijan,
Puka, Chase, JSN, ARSB} in this window at MAX or less. Nominate: Josh Allen
(someone pays $30), Jeanty (ankle, no return date; let someone else own the
risk), CMC, Bowers or McBride (make the room set the TE price while money is
loose), a top D/ST.

**Rounds 4-7: the value zone.** Money is thin at the top, the T3/T4 RBs and
T2/T3 WRs ($21-36) go at or under value here. Buy RB2 and WR1/WR2 here.
Nominate players in tiers you have already filled so the rich teams spend
against each other. If you already have RB1, nominate every remaining T3 RB.

**Rounds 8-12: fill starters, hunt the $5-15 bin.** WR3, TE, FLEX, QB.
Watch the teams column: a team with $60 and 9 open slots must spend $6 a
slot and will overpay for the last decent starters. Let them. Buy the starter
after theirs. QB and TE happen here: whoever is left of
Maye/Lamar/Hurts/Burrow at $9-11, else the $3-6 tier; Fannin/Pitts/Warren at
$8-11, else Kelce/LaPorta/Andrews at $5-6.

**Rounds 13-17: the $1 endgame.** Most teams are at $1 per slot. Your $27
bench budget makes you the richest bidder for every $2-4 player. Nominate
your own sleepers now, opening at $1. Handcuff your RB1 if he is still on the
board. K and D/ST last.

## Nomination rules

1. **Nominate at $1, always,** unless you are price-enforcing.
2. **Never nominate a player you want before round 10** unless the room is
   quiet and three or more teams cannot afford him.
3. **Early: nominate expensive players you do not want** at positions where
   the teams with money have holes. Allen, Jeanty, CMC, Henry (age), a big
   TE, any kicker or D/ST someone might chase.
4. **Middle: nominate the tier you already own.** Filled RB1? Nominate RBs.
5. **Late: nominate your targets** when the teams with money are out of
   slots and the teams with slots are out of money. The board's Nominate
   tab ranks these for you: it scores each available player by how many
   teams can still afford him at value and whether he is at a position you
   have covered.
6. **Price enforce only with a purpose.** Bidding a player up to 90% of
   value to drain a rival is fine if you would be happy owning him at that
   number. Never enforce a player you would hate to win.

## Injury flags you must not forget on Sunday

- Jeanty (LV RB): ankle sprain, on the 53 but no return date as of Sep 2. Value
  $32 on the sheet; I would not go past $22.
- Nabers (NYG WR): ACL return, expected Week 1 but ramping. $21, fine at $17.
- Zay Flowers (BAL WR): quad, day-to-day. $23, fine at $19.
- CMC (SF RB): late-August tightness, age 30. $47 sheet, walk at $47.
- Tyler Warren (IND TE): groin, likely Week 1 but the practice ramp was
  questioned. $11, fine at $8.
- Kittle (SF TE): Achilles return, individual work only as of Sep 2. $1, and
  only as a bench flier.
- Charbonnet (SEA RB): reserve/PUP, out 4 games. Bench only, $1.
- Jordyn Tyson (NO WR): out until October. Skip.
- Pearsall (SF WR), Jayden Higgins (HOU WR): out for the season. Skip.
- Puka, Breece Hall, Egbuka, Henderson, Jacobs, Burden, Kraft: camp injuries
  or returns, all expected Week 1. Pay full value.

## Sources

- FFToday 2026 auction values (12-team PPR, Aug 20, 2026) — the raw table in
  data/raw; rescaled by data/build_values.py.
- Cross-checks: Draft Sharks half-PPR top 10 (Gibbs $61, Bijan $57, Puka
  $56, Chase $56, CMC $53, JSN $50, Taylor $53) and Fantasy Nerds 10-team
  consensus (Gibbs $49, Bijan $46, Taylor $43) bracket this sheet's numbers.
- Injuries: FantasyPros "13 injuries to monitor" (Aug 29, 2026) and Football
  Nation USA Week 1 injury report (Sep 2, 2026).
