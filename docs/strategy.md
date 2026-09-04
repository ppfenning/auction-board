# Auction plan — ESPN, 10 teams, $200, half-PPR, no TE slot

Lineup: QB, RB, RB, WR, WR, WR, FLEX (RB/WR/TE), D/ST, K, 7 bench. 16 spots,
$200, so $1 per spot is the floor and $185 is the most any one bid can be.
Values are from FFToday's 2026 table (Aug 20), rescaled to this league:
10 teams, half-PPR tilt (RB up 4%, WR down 4%), replacement level at the
depth a 10-team room actually rosters, and TE cut to flex-only value.
Injury flags are as of Sep 2, 2026. Nothing here is a projection of its own;
the edge is in the rescale, the plan, and the discipline.

## The three facts that shape this room

1. **No TE slot.** In a normal league the room spends $50-70 on tight ends.
   Here it spends about $15 (Bowers and McBride as flex plays, maybe). That
   money chases RB and WR instead, so the top of the board runs hotter than a
   generic sheet says. Do not pay for a TE. If Bowers goes for $12+, smile.
2. **Ten teams.** Waivers are deep: the RB28 and WR40 are free all season.
   Replacement level is high, which means the difference-makers are worth
   MORE, not less. This is a stars-and-scrubs room. Spend big on 3 players,
   then fill with $1-4 guys who have a path to a starting job.
3. **Six flex-eligible starters (2 RB, 3 WR, FLEX) and one QB.** QB is one
   slot in a 10-team league: the QB10 is a fine starter and costs $3. Never
   pay $20+ for Josh Allen. Pay $9-11 for the second tier only if it falls.

## Budget by slot ($200)

| Slot | Plan | Who fits |
|------|-----:|----------|
| QB   | $10  | Maye / Lamar / Hurts / Burrow at $9-11 if one falls; else Daniels / C.Williams $6; else Dak / Nix / Herbert / Purdy $3-4 |
| RB1  | $48  | Gibbs or Bijan ($55-60 ceiling), or CMC/Taylor ($42-48) if the top two go over $62 |
| RB2  | $30  | Cook, Hampton, Saquon, Chase Brown, Achane (T3, $30-36); Walker / Henry / Love / Hall / Kyren ($26-29) if T3 is gone |
| WR1  | $34  | London, Jefferson, Lamb, Olave ($33-38); or Puka / Chase / JSN / ARSB if one goes at value ($47-53) and RB1 becomes a T3 |
| WR2  | $22  | Rice, Collins, AJB, G.Wilson, Pickens, DeVonta, Flowers ($24-29) |
| WR3  | $13  | Nabers, Egbuka, McMillan, Adams, Jamo, McConkey, Waddle ($17-22) or a T5 at $10-14 |
| FLEX | $12  | The best remaining RB/WR under $14: Etienne, Swift, Higgins, Watson, DJ Moore, Evans |
| D/ST | $2   | Whatever is left; nominate a top D/ST early so someone else pays $4 |
| K    | $1   | Last pick |
| BN1  | $8   | One upside RB with a path to touches (Skattebo, Javonte, Irving, Henderson, RJ Harvey-type) |
| BN2  | $6   | One upside WR (Burden, Tate, Golden, Ayomanor-type) |
| BN3-7| $12  | $1-3 each: handcuffs to MY RBs first, then rookies, then boring vets |

Starters $172, bench $28. Three players at $48 + $34 + $30 = $112 (56%) is
the intended shape. If you land two T1s (say Gibbs $58 + Chase $50), drop to
$20 RB2 / $15 WR2 / $8 WR3 and you are still whole.

## Cut lines on price

The board computes MAX per player live: `max = value * inflation * tierCap`,
where inflation is (money left in the room) / (value left on the board for
the slots still open), and tierCap is 1.10 for tiers 1-2, 1.05 for tier 3,
1.00 for tier 4-5, and 0.90 below that. Rules that never move:

- **Never exceed MAX.** The whole point of the sheet is that you decided
  this before the adrenaline. One exception: the last T1/T2 RB, if you have
  no RB1 yet and the money to absorb it, +$3.
- **Tier 1 RB (Gibbs, Bijan): walk at $64.** Tier 1 WR (Puka, Chase, JSN,
  ARSB): walk at $56. CMC: walk at $48 (age 30, August "tightness").
- **Tier 3 RB ($30-36 values): walk at $38.** This is the zone where the
  room overpays most; let them.
- **Any QB: walk at $12,** except Allen, whom you are not buying.
- **Any TE: walk at $8.** Bowers/McBride only, only as FLEX, only if the
  flex money is otherwise going to a T5 WR.
- **D/ST $3, K $1.** No exceptions.
- **Bench: $4 cap** except one $6-8 upside RB.
- **Slot math.** Your max bid is always `remaining - (open slots - 1)`. The
  board shows it; the plan tab shows what is planned for the slots you have
  not filled. When "plan remaining" is more than "remaining budget", you are
  over and must downgrade a slot on purpose, not by accident.

## Phases by nomination round (16 rounds x 10 nominations)

**Rounds 1-3: let the room set the price, buy one star.** The first 20-30
sales are the most expensive and the most informative. Track inflation on the
board: if the first five T1/T2 players go under value the room is tight and
you should buy the NEXT T1 RB at or below value; if they go 10%+ over, wait,
because the mid tiers will be cheap. Target exactly one of {Gibbs, Bijan,
Puka, Chase, JSN, ARSB} in this window at MAX or less. Nominate: Josh Allen
(someone pays $30), Jeanty (ankle, no return date; let someone else own the
risk), CMC, Bowers (make a TE believer pay), a top D/ST.

**Rounds 4-7: the value zone.** Money is thin at the top, the T3/T4 RBs and
T2/T3 WRs ($22-36) go at or under value here. Buy RB2 and WR1/WR2 here.
Nominate players in tiers you have already filled so the rich teams spend
against each other. If you already have RB1, nominate every remaining T3 RB.

**Rounds 8-11: fill starters, hunt the $5-15 bin.** WR3, FLEX, QB. Watch
the teams column: a team with $60 and 9 open slots must spend $6 a slot and
will overpay for the last decent starters. Let them. Buy the starter after
theirs. QB happens here: whoever is left of Maye/Lamar/Hurts/Burrow at
$9-11, else the $3-6 tier.

**Rounds 12-16: the $1 endgame.** Most teams are at $1 per slot. Your $28
bench budget makes you the richest bidder for every $2-4 player. Nominate
your own sleepers now, opening at $1. Handcuff your RB1 if he is still on the
board. K and D/ST last.

## Nomination rules

1. **Nominate at $1, always,** unless you are price-enforcing.
2. **Never nominate a player you want before round 10** unless the room is
   quiet and three or more teams cannot afford him.
3. **Early: nominate expensive players you do not want** at positions where
   the teams with money have holes. Allen, Jeanty, CMC, Bowers, Henry (age),
   any kicker or D/ST someone might chase.
4. **Middle: nominate the tier you already own.** Filled RB1? Nominate RBs.
5. **Late: nominate your targets** when the teams with money are out of
   slots and the teams with slots are out of money. The board's Nominate
   tab ranks these for you: it scores each available player by how many
   teams can still afford him at value and whether he is in a tier you
   have covered.
6. **Price enforce only with a purpose.** Bidding a player up to 90% of
   value to drain a rival is fine if you would be happy owning him at that
   number. Never enforce a player you would hate to win.

## Injury flags you must not forget on Sunday

- Jeanty (LV RB): ankle sprain, on the 53 but no return date as of Sep 2. Value
  $32 on the sheet; I would not go past $22.
- Nabers (NYG WR): ACL return, expected Week 1 but ramping. $22, fine at $18.
- Zay Flowers (BAL WR): quad, day-to-day. $24, fine at $20.
- CMC (SF RB): late-August tightness, age 30. $48 sheet, walk at $48.
- Charbonnet (SEA RB): reserve/PUP, out 4 games. Bench only, $1.
- Jordyn Tyson (NO WR): out until October. Skip.
- Pearsall (SF WR), Jayden Higgins (HOU WR): out for the season. Skip.
- Puka, Breece Hall, Egbuka, Henderson, Jacobs, Burden: camp injuries, all
  expected Week 1. Pay full value.

## Sources

- FFToday 2026 auction values (12-team PPR, Aug 20, 2026) — the raw table in
  data/raw; rescaled by data/build_values.py.
- Cross-checks: Draft Sharks half-PPR top 10 (Gibbs $61, Bijan $57, Puka
  $56, Chase $56, CMC $53, JSN $50, Taylor $53) and Fantasy Nerds 10-team
  consensus (Gibbs $49, Bijan $46, Taylor $43) bracket this sheet's numbers.
- Injuries: FantasyPros "13 injuries to monitor" (Aug 29, 2026) and Football
  Nation USA Week 1 injury report (Sep 2, 2026).
