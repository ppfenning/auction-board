package auction

class DraftTest extends munit.FunSuite:
  val league = League.espn10

  val rb1 = Player("rb1-id", 1, Pos.RB, "RB One", "AAA", 50, 1, "")
  val rb2 = Player("rb2-id", 2, Pos.RB, "RB Two", "AAA", 45, 1, "")
  val rb3 = Player("rb3-id", 3, Pos.RB, "RB Three", "AAA", 30, 2, "")
  val rb4 = Player("rb4-id", 4, Pos.RB, "RB Four", "AAA", 20, 3, "")
  val wr1 = Player("wr1-id", 5, Pos.WR, "WR One", "BBB", 35, 1, "")
  val te1 = Player("te1-id", 6, Pos.TE, "TE One", "CCC", 40, 1, "")
  val qb1 = Player("qb1-id", 7, Pos.QB, "QB One", "DDD", 35, 1, "")
  val dst1 = Player("dst1-id", 8, Pos.DST, "DST One", "EEE", 5, 3, "")
  val k1 = Player("k1-id", 9, Pos.K, "K One", "FFF", 5, 3, "")

  val players = Vector(rb1, rb2, rb3, rb4, wr1, te1, qb1, dst1, k1)

  val teamPicks = Vector(
    Pick(rb1.id, 0, 10, 1),
    Pick(rb2.id, 0, 20, 2),
    Pick(te1.id, 0, 5, 3),
    Pick(qb1.id, 0, 8, 4),
    Pick(rb3.id, 0, 12, 5),
    Pick(rb4.id, 0, 3, 6),
    Pick(k1.id, 0, 2, 7),
  )

  def fresh: DraftState = DraftState.fresh(league)
  def withPicks: DraftState = fresh.copy(picks = teamPicks)

  // ── sell ─────────────────────────────────────────────────────────────────

  test("sell refuses an unknown player") {
    assertEquals(Draft.sell(fresh, league, players, "nope", 0, 10).isLeft, true)
  }

  test("sell refuses a player already sold") {
    val state = fresh.copy(picks = Vector(Pick(rb1.id, 1, 10, 1)))
    assertEquals(Draft.sell(state, league, players, rb1.id, 0, 5).isLeft, true)
  }

  test("sell refuses a team outside 0 until league.teams") {
    assertEquals(Draft.sell(fresh, league, players, rb1.id, -1, 10).isLeft, true)
    assertEquals(Draft.sell(fresh, league, players, rb1.id, league.teams, 10).isLeft, true)
  }

  test("sell refuses a price under 1") {
    assertEquals(Draft.sell(fresh, league, players, rb1.id, 0, 0).isLeft, true)
  }

  test("sell refuses a team with no open slot") {
    val filler = Vector.tabulate(league.rosterSize)(i => Pick(s"filler-$i", 0, 1, i + 1))
    val state = fresh.copy(picks = filler)
    val result = Draft.sell(state, league, players, rb1.id, 0, 1)
    assertEquals(result, Left("team 0 has no open slot"))
  }

  test("sell refuses a price over maxBid") {
    val cap = league.maxBid(0, 0)
    assertEquals(cap, 184)
    assertEquals(Draft.sell(fresh, league, players, rb1.id, 0, cap + 1).isLeft, true)
  }

  test("sell records a pick at nextSeq on the happy path") {
    val afterFirst = Draft.sell(fresh, league, players, rb1.id, 0, 10)
    assertEquals(afterFirst.map(_.picks.last), Right(Pick(rb1.id, 0, 10, 1)))
    val afterSecond = afterFirst.flatMap(s => Draft.sell(s, league, players, rb2.id, 0, 5))
    assertEquals(afterSecond.map(_.picks.last), Right(Pick(rb2.id, 0, 5, 2)))
  }

  // ── undo ─────────────────────────────────────────────────────────────────

  test("undo on an empty state is unchanged") {
    assertEquals(Draft.undo(fresh), fresh)
  }

  test("undo drops the pick with the highest seq") {
    val undone = Draft.undo(withPicks)
    assertEquals(undone.picks.map(_.playerId), teamPicks.init.map(_.playerId))
  }

  // ── overrides ────────────────────────────────────────────────────────────

  test("setOverride sets then clears a value") {
    val set = Draft.setOverride(fresh, rb1.id, Some(99))
    assertEquals(set.overrides.get(rb1.id), Some(99))
    val cleared = Draft.setOverride(set, rb1.id, None)
    assertEquals(cleared.overrides.get(rb1.id), None)
  }

  // ── setMyTeam / rename ───────────────────────────────────────────────────

  test("setMyTeam accepts an in-range team and refuses an out-of-range one") {
    assertEquals(Draft.setMyTeam(fresh, league, 3).map(_.myTeam), Right(3))
    assertEquals(Draft.setMyTeam(fresh, league, league.teams).isLeft, true)
  }

  test("rename refuses the wrong number of names") {
    assertEquals(Draft.rename(fresh, league, Vector("Only One")).isLeft, true)
  }

  test("rename refuses a blank name") {
    val names = Vector.tabulate(league.teams)(i => if i == 2 then "  " else s"Team $i")
    assertEquals(Draft.rename(fresh, league, names).isLeft, true)
  }

  test("rename accepts exactly league.teams non-blank names") {
    val names = Vector.tabulate(league.teams)(i => s"Squad $i")
    assertEquals(Draft.rename(fresh, league, names), Right(fresh.copy(teamNames = names)))
  }

  // ── roster ───────────────────────────────────────────────────────────────

  test("roster fills RB1/RB2/FLEX by value, a fourth RB to bench, and TE/QB/K to their own slots") {
    val lines = Draft.roster(withPicks, league, players, 0).map(l => l.slot -> l.playerId).toMap
    assertEquals(lines("RB1"), Some(rb1.id))
    assertEquals(lines("RB2"), Some(rb2.id))
    assertEquals(lines("FLEX"), Some(rb3.id))
    assertEquals(lines("BN1"), Some(rb4.id))
    assertEquals(lines("TE"), Some(te1.id))
    assertEquals(lines("QB"), Some(qb1.id))
    assertEquals(lines("K"), Some(k1.id))
  }

  test("unfilled roster slots carry None in every optional field") {
    val wr1Line = Draft.roster(withPicks, league, players, 0).find(_.slot == "WR1").get
    assertEquals(wr1Line, RosterLine("WR1", None, None, None, None))
  }

  test("roster assigns by value, not by the order picks were sold in") {
    // Sale order is rb3, rb1, rb4, rb2 — the reverse of their value order.
    // If assignment used seq or insertion order instead of effectiveValue,
    // RB1/RB2/FLEX/BN1 would come out rb3/rb1/rb4/rb2 instead of the
    // value-descending rb1/rb2/rb3/rb4 asserted below.
    val shuffled = fresh.copy(picks = Vector(
      Pick(rb3.id, 0, 12, 1),
      Pick(rb1.id, 0, 10, 2),
      Pick(rb4.id, 0, 3, 3),
      Pick(rb2.id, 0, 20, 4),
    ))
    val lines = Draft.roster(shuffled, league, players, 0).map(l => l.slot -> l.playerId).toMap
    assertEquals(lines("RB1"), Some(rb1.id))
    assertEquals(lines("RB2"), Some(rb2.id))
    assertEquals(lines("FLEX"), Some(rb3.id))
    assertEquals(lines("BN1"), Some(rb4.id))
  }

  test("an override that raises a later pick's value moves it ahead of earlier picks into RB1") {
    // rb4 was sold last (seq 6) and bench-ranked on its base value of 20.
    // Overriding it to 999 makes it the highest effective value on the team,
    // so it displaces rb1 out of RB1 and pushes every other RB down a slot.
    val overridden = Draft.setOverride(withPicks, rb4.id, Some(999))
    val lines = Draft.roster(overridden, league, players, 0).map(l => l.slot -> l.playerId).toMap
    assertEquals(lines("RB1"), Some(rb4.id))
    assertEquals(lines("RB2"), Some(rb1.id))
    assertEquals(lines("FLEX"), Some(rb2.id))
    assertEquals(lines("BN1"), Some(rb3.id))
  }

  test("roster and planView isolate picks by team; a second team's pick never leaks into another team's view") {
    // Give team 1 a single pick (dst1, in a slot none of team 0's picks can
    // reach). If roster dropped its `.filter(_.team == team)`, team 1's view
    // would also show team 0's seven picks in RB1/RB2/TE/QB/FLEX/BN1/K. If
    // planView used a literal 0 instead of state.myTeam, switching myTeam to
    // 1 would still report team 0's RB1 as filled.
    val state = withPicks.copy(picks = teamPicks :+ Pick(dst1.id, 1, 3, 8))

    val team0Lines = Draft.roster(state, league, players, 0).map(l => l.slot -> l.playerId).toMap
    assertEquals(team0Lines("DST"), None)

    val team1Lines = Draft.roster(state, league, players, 1).map(l => l.slot -> l.playerId).toMap
    assertEquals(team1Lines("DST"), Some(dst1.id))
    assertEquals(team1Lines("RB1"), None)
    assertEquals(team1Lines.values.count(_.isDefined), 1)

    val (planLines, _) = Draft.planView(state.copy(myTeam = 1), league, players, Plan.default)
    assertEquals(planLines.find(_.slot == "DST").get.actual, Some(3))
    assertEquals(planLines.find(_.slot == "RB1").get.actual, None)
  }

  // ── teamView ─────────────────────────────────────────────────────────────

  test("teamView on a fresh team") {
    val view = Draft.teamView(fresh, league, players, 0)
    assertEquals(view.spent, 0)
    assertEquals(view.remaining, 200)
    assertEquals(view.slotsOpen, 17)
    assertEquals(view.maxBid, 184)
    assertEquals(view.perSlot, 200.0 / 17)
  }

  test("teamView after picks") {
    val view = Draft.teamView(withPicks, league, players, 0)
    assertEquals(view.spent, 60)
    assertEquals(view.remaining, 140)
    assertEquals(view.slotsOpen, 10)
    assertEquals(view.maxBid, league.maxBid(60, 7))
    assertEquals(view.perSlot, 140.0 / 10)
  }

  test("teamView falls back to a default name instead of throwing when teamNames is short") {
    val short = fresh.copy(teamNames = Vector("Only Team"))
    assertEquals(Draft.teamView(short, league, players, 5).name, "Team 6")
  }

  test("teamViews returns one view per league.teams") {
    assertEquals(Draft.teamViews(withPicks, league, players).size, league.teams)
  }

  // ── planView ─────────────────────────────────────────────────────────────

  test("planView reports actuals for filled slots and sums planRemaining for the rest") {
    val (lines, planRemaining) = Draft.planView(withPicks, league, players, Plan.default)
    assertEquals(lines.size, 17)
    assertEquals(lines.find(_.slot == "RB1").get.actual, Some(10))
    assertEquals(lines.find(_.slot == "RB1").get.name, Some(rb1.name))
    assertEquals(lines.find(_.slot == "WR1").get.actual, None)
    assertEquals(planRemaining, 87)
  }
