# Mock auction results

Produced by `scala-cli run . --main-class auction.Mock -- --n 300 --seed 1` on 2026-09-04.

## The model

- Nine rivals, each with a pricing style drawn per room (mean = room heat, sd 0.08) and a bench discount (0.35-0.65). A rival pays sheet value x style x room inflation x noise (uniform, ±15% or ±30%), never more than 36% of its budget on one player or 65% of what it has left, and only for a player it has a slot for.
- "Me" bids by docs/strategy.md: the board MAX (value x inflation x tier cap), the slot plan for the best open slot the player fits, the hard walk-away caps, and the two rules the mocks produced: surplus (money minus plan left) goes onto the next starter, and starter caps rise to the room premium.
- Every sale goes to the top bidder at one dollar over the runner-up; rivals nominate the best available player they can roster 70% of the time, I nominate what the Nominate tab says; a room runs until all 170 spots fill.
- Score: my starting lineup by sheet value, ranked among the ten teams (1 = best). Bench value never plays.

## Results (300 rooms per line, same seeds for every policy)

```
WARNING: Please consider reporting this to the maintainers of class scala.runtime.LazyVals$

#### rival noise ±15% — bench-heavy plan, no surplus rule, old T1-2 cap
rooms: 300; room heat mean 1.00
my lineup value avg 158.9 vs rival mean 165.2; my rank avg 7.04; #1 in 0/300 rooms, top 3 in 8/300
spent avg 179.4 of 200 (unspent 20.6)
RB1 is a tier-1 back in 5/300 rooms, tier-1 or 2 in 91/300; WR1 tier-1 or 2 in 80/300; paid 20+ for a TE in 0/300
avg $ by slot: QB 10, RB1 34, RB2 26, WR1 29, WR2 18, WR3 13, TE 10, FLEX 12, DST 2, K 1, BN1 7, BN2 5, BN3 3, BN4 2, BN5 2, BN6 2, BN7 2
most common starters: Tyler Warren (137), Ka'imi Fairbairn (105), Drake Maye (93), Omarion Hampton (92), Bhayshul Tuten (85), Tee Higgins (84), Jason Myers (84), Drake London (80), Jaylen Waddle (79), Lamar Jackson (79), Jonathan Taylor (74), Chase Brown (74)

#### rival noise ±15% — bench-heavy plan + surplus rule
rooms: 300; room heat mean 1.00
my lineup value avg 167.7 vs rival mean 164.2; my rank avg 5.98; #1 in 4/300 rooms, top 3 in 28/300
spent avg 194.5 of 200 (unspent 5.5)
RB1 is a tier-1 back in 5/300 rooms, tier-1 or 2 in 192/300; WR1 tier-1 or 2 in 110/300; paid 20+ for a TE in 23/300
avg $ by slot: QB 10, RB1 40, RB2 27, WR1 31, WR2 19, WR3 13, TE 11, FLEX 13, DST 2, K 1, BN1 8, BN2 6, BN3 4, BN4 3, BN5 3, BN6 2, BN7 2
most common starters: Jonathan Taylor (176), Drake Maye (116), Drake London (99), Tyler Warren (87), Tee Higgins (81), Omarion Hampton (77), Jason Myers (76), Denver (76), Quinshon Judkins (73), Harold Fannin Jr. (70), Ladd McConkey (69), Chase Brown (67)

#### rival noise ±15% — balanced plan ($14 bench) + surplus rule
rooms: 300; room heat mean 1.00
my lineup value avg 173.6 vs rival mean 163.7; my rank avg 4.76; #1 in 32/300 rooms, top 3 in 103/300
spent avg 192.6 of 200 (unspent 7.4)
RB1 is a tier-1 back in 41/300 rooms, tier-1 or 2 in 190/300; WR1 tier-1 or 2 in 162/300; paid 20+ for a TE in 39/300
avg $ by slot: QB 8, RB1 41, RB2 28, WR1 34, WR2 20, WR3 14, TE 11, FLEX 14, DST 1, K 1, BN1 6, BN2 3, BN3 2, BN4 2, BN5 2, BN6 2, BN7 2
most common starters: Drake London (145), Jonathan Taylor (138), Omarion Hampton (87), Colston Loveland (84), Wil Lutz (77), Drake Maye (69), Jaylen Waddle (68), Tyler Warren (65), Denver (65), Lamar Jackson (62), Minnesota (61), Jason Myers (61)

#### rival noise ±15% — starter-heavy plan (the default) + surplus rule
rooms: 300; room heat mean 1.00
my lineup value avg 176.0 vs rival mean 163.5; my rank avg 4.07; #1 in 64/300 rooms, top 3 in 168/300
spent avg 191.2 of 200 (unspent 8.8)
RB1 is a tier-1 back in 62/300 rooms, tier-1 or 2 in 206/300; WR1 tier-1 or 2 in 178/300; paid 20+ for a TE in 34/300
avg $ by slot: QB 8, RB1 43, RB2 29, WR1 34, WR2 23, WR3 14, TE 10, FLEX 13, DST 2, K 1, BN1 3, BN2 2, BN3 2, BN4 2, BN5 2, BN6 2, BN7 2
most common starters: Drake London (156), Jonathan Taylor (133), James Cook (83), Colston Loveland (83), Nico Collins (74), Drake Maye (65), Ladd McConkey (59), Jason Myers (59), Denver (59), Omarion Hampton (57), Tyler Warren (56), Chase McLaughlin (55)

#### rival noise ±15% — default + surplus + chase the room premium
rooms: 300; room heat mean 1.00
my lineup value avg 181.5 vs rival mean 163.0; my rank avg 3.47; #1 in 38/300 rooms, top 3 in 159/300
spent avg 200.0 of 200 (unspent 0.0)
RB1 is a tier-1 back in 62/300 rooms, tier-1 or 2 in 190/300; WR1 tier-1 or 2 in 180/300; paid 20+ for a TE in 11/300
avg $ by slot: QB 7, RB1 46, RB2 33, WR1 39, WR2 26, WR3 15, TE 8, FLEX 15, DST 1, K 1, BN1 2, BN2 1, BN3 1, BN4 1, BN5 1, BN6 1, BN7 1
most common starters: Drake London (132), Jonathan Taylor (118), James Cook (101), Omarion Hampton (92), Wil Lutz (89), Pittsburgh (72), Los Angeles Rams (69), Colston Loveland (68), Ladd McConkey (58), Jason Myers (57), Nico Collins (56), A.J. Brown (54)

#### rival noise ±30% — bench-heavy plan, no surplus rule, old T1-2 cap
rooms: 300; room heat mean 1.00
my lineup value avg 148.6 vs rival mean 165.8; my rank avg 7.89; #1 in 2/300 rooms, top 3 in 10/300
spent avg 159.4 of 200 (unspent 40.6)
RB1 is a tier-1 back in 2/300 rooms, tier-1 or 2 in 42/300; WR1 tier-1 or 2 in 26/300; paid 20+ for a TE in 0/300
avg $ by slot: QB 9, RB1 28, RB2 21, WR1 24, WR2 15, WR3 12, TE 10, FLEX 11, DST 2, K 1, BN1 7, BN2 6, BN3 4, BN4 3, BN5 2, BN6 2, BN7 2
most common starters: Ka'imi Fairbairn (107), Drake Maye (107), Tyler Warren (106), Bhayshul Tuten (92), Lamar Jackson (86), Jaylen Waddle (80), Tee Higgins (79), Seattle (79), Harold Fannin Jr. (78), Ladd McConkey (73), Houston (71), Colston Loveland (70)

#### rival noise ±30% — bench-heavy plan + surplus rule
rooms: 300; room heat mean 1.00
my lineup value avg 157.1 vs rival mean 164.6; my rank avg 6.65; #1 in 14/300 rooms, top 3 in 50/300
spent avg 182.3 of 200 (unspent 17.7)
RB1 is a tier-1 back in 2/300 rooms, tier-1 or 2 in 99/300; WR1 tier-1 or 2 in 54/300; paid 20+ for a TE in 38/300
avg $ by slot: QB 10, RB1 31, RB2 22, WR1 26, WR2 16, WR3 12, TE 12, FLEX 13, DST 2, K 1, BN1 9, BN2 7, BN3 5, BN4 5, BN5 4, BN6 4, BN7 3
most common starters: Denver (139), Drake Maye (132), Colston Loveland (111), Brandon Aubrey (110), Jonathan Taylor (94), Jaylen Waddle (83), Bhayshul Tuten (77), Tee Higgins (75), Tyler Warren (72), Christian Watson (70), D'Andre Swift (67), Lamar Jackson (63)

#### rival noise ±30% — balanced plan ($14 bench) + surplus rule
rooms: 300; room heat mean 1.00
my lineup value avg 161.3 vs rival mean 164.4; my rank avg 5.84; #1 in 37/300 rooms, top 3 in 103/300
spent avg 178.2 of 200 (unspent 21.8)
RB1 is a tier-1 back in 15/300 rooms, tier-1 or 2 in 109/300; WR1 tier-1 or 2 in 92/300; paid 20+ for a TE in 52/300
avg $ by slot: QB 9, RB1 32, RB2 22, WR1 27, WR2 17, WR3 13, TE 12, FLEX 13, DST 2, K 1, BN1 7, BN2 5, BN3 4, BN4 4, BN5 3, BN6 3, BN7 3
most common starters: Colston Loveland (118), Drake Maye (116), Denver (111), Jonathan Taylor (91), Drake London (83), Brandon Aubrey (82), Travis Etienne (72), D'Andre Swift (72), Ladd McConkey (65), Lamar Jackson (64), Tee Higgins (63), Quinshon Judkins (60)

#### rival noise ±30% — starter-heavy plan (the default) + surplus rule
rooms: 300; room heat mean 1.00
my lineup value avg 164.1 vs rival mean 164.2; my rank avg 5.51; #1 in 63/300 rooms, top 3 in 125/300
spent avg 176.6 of 200 (unspent 23.4)
RB1 is a tier-1 back in 31/300 rooms, tier-1 or 2 in 118/300; WR1 tier-1 or 2 in 104/300; paid 20+ for a TE in 70/300
avg $ by slot: QB 9, RB1 34, RB2 22, WR1 28, WR2 19, WR3 13, TE 13, FLEX 14, DST 2, K 1, BN1 4, BN2 4, BN3 3, BN4 3, BN5 3, BN6 3, BN7 3
most common starters: Denver (117), Drake Maye (108), Drake London (95), Colston Loveland (92), Jonathan Taylor (86), Brandon Aubrey (82), Bhayshul Tuten (70), Travis Etienne (63), Ladd McConkey (61), Jaylen Waddle (59), Trey McBride (58), D'Andre Swift (57)

#### rival noise ±30% — default + surplus + chase the room premium
rooms: 300; room heat mean 1.00
my lineup value avg 180.3 vs rival mean 162.5; my rank avg 2.94; #1 in 97/300 rooms, top 3 in 209/300
spent avg 197.8 of 200 (unspent 2.2)
RB1 is a tier-1 back in 29/300 rooms, tier-1 or 2 in 114/300; WR1 tier-1 or 2 in 112/300; paid 20+ for a TE in 41/300
avg $ by slot: QB 8, RB1 40, RB2 30, WR1 35, WR2 25, WR3 16, TE 11, FLEX 18, DST 1, K 1, BN1 3, BN2 2, BN3 2, BN4 2, BN5 2, BN6 2, BN7 1
most common starters: Drake Maye (86), Jonathan Taylor (84), Drake London (81), Colston Loveland (79), Jason Myers (75), Pittsburgh (74), Saquon Barkley (72), Omarion Hampton (71), Chase Brown (70), James Cook (69), De'Von Achane (63), Ka'imi Fairbairn (62)

==== sample rooms under 'default + surplus + chase the room premium' ====

== tight room ==
seed 1135 heat 0.76 spent 200 lineup value 190 rank 5 (rival mean 164)
  QB    Jalen Hurts              $11
  RB1   Christian McCaffrey      $47
  RB2   Jonathan Taylor          $43
  WR1   Justin Jefferson         $34
  WR2   DeVonta Smith            $21
  WR3   Jaylen Waddle            $17
  TE    Jake Ferguson            $1
  FLEX  D.J. Moore               $15
  DST   Houston                  $3
  K     Chase McLaughlin         $1
  BN1   Alvin Kamara             $1
  BN2   Brock Purdy              $1
  BN3   Jared Goff               $1
  BN4   Keenan Allen             $1
  BN5   Dallas Goedert           $1
  BN6   Calvin Ridley            $1
  BN7   Adonai Mitchell          $1

== typical room ==
seed 1181 heat 1.01 spent 200 lineup value 188 rank 1 (rival mean 160)
  QB    Caleb Williams           $3
  RB1   Saquon Barkley           $32
  RB2   Chase Brown              $32
  WR1   Justin Jefferson         $33
  WR2   Chris Olave              $32
  WR3   Jaylen Waddle            $17
  TE    Trey McBride             $23
  FLEX  Luther Burden III        $14
  DST   Denver                   $3
  K     Jason Myers              $1
  BN1   Brian Thomas Jr.         $4
  BN2   Xavier Worthy            $1
  BN3   Quentin Johnston         $1
  BN4   Alvin Kamara             $1
  BN5   Bo Nix                   $1
  BN6   Tucker Kraft             $1
  BN7   Deebo Samuel             $1

== hot room ==
seed 1021 heat 1.23 spent 192 lineup value 170 rank 3 (rival mean 162)
  QB    Drake Maye               $9
  RB1   Kyren Williams           $25
  RB2   Ashton Jeanty            $24
  WR1   George Pickens           $23
  WR2   Zay Flowers              $22
  WR3   Ladd McConkey            $12
  TE    Trey McBride             $19
  FLEX  Jeremiyah Love           $20
  DST   Houston                  $2
  K     Cameron Dicker           $1
  BN1   TreVeyon Henderson       $5
  BN2   Jaylen Warren            $5
  BN3   Harold Fannin Jr.        $5
  BN4   Rhamondre Stevenson      $5
  BN5   Chuba Hubbard            $5
  BN6   Joe Burrow               $5
  BN7   Courtland Sutton         $5
```

## What it says

1. **Unspent money loses rooms.** The first plan ($27 bench, caps at value+10%) ranked 7th of 10 and left $20-40 unspent. Every improvement below is a way of spending the money.
2. **Spend the surplus on the next starter.** When my money exceeds what the plan still needs, adding the difference to the next starter cap moved the rank a full place.
3. **Put the bench money into starters.** $186 on starters and $1 per bench spot beat a $14 bench by 0.7 places and the $27 bench by two. Bench upside is bought from surplus, and Lloyd-type buys fit the FLEX line.
4. **Chase the room premium.** When the room pays over the sheet, raise starter caps to the premium instead of holding the line: rank 3.5 (rivals ±15%) and 2.9 (±30%); the hot room went from 10th with $90 unspent to 3rd.
5. **Tier caps at value+20% for tiers 1-2, +10% for tier 3.** Against nine noisy bidders, +10% won nearly no stars.

Limits: rivals here have no plan and no favourite players, and the score is sheet value, so it rewards converting dollars into value and ignores injury risk, bench depth and the actual season. Treat the ranks as relative, not absolute.
