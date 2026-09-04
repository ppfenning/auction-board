package auction

class NominateTest extends munit.FunSuite:
  def player(
      id: String,
      pos: Pos,
      value: Int,
      note: String = "",
      rank: Int = 1,
      soldTo: Option[Int] = None,
  ): PlayerView =
    PlayerView(
      id = id,
      rank = rank,
      pos = pos,
      name = id,
      team = "X",
      tier = 1,
      note = note,
      value = value,
      target = value,
      max = value,
      soldTo = soldTo,
      price = soldTo.map(_ => value),
    )

  def team(index: Int, slotsOpen: Int, maxBid: Int): TeamView =
    TeamView(
      index = index,
      name = s"Team $index",
      spent = 0,
      remaining = maxBid,
      slotsOpen = slotsOpen,
      maxBid = maxBid,
      perSlot = 0.0,
      roster = Vector.empty,
    )

  test("an AVOID-tagged player is a nomination even at a position still needed") {
    val teams = Vector(team(0, 17, 184), team(1, 17, 184), team(2, 17, 184))
    val p = player("jeanty-rb", Pos.RB, value = 24, note = "AVOID at sheet price: ankle")
    val (score, reason) = Nominate.score(p, teams, 0, Set(Pos.RB))
    assertEquals(score, 52)
    assertEquals(reason, "Avoid: 2 rivals can pay $24, AVOID at sheet price: ankle")
  }

  test("a $20+ QB is bait even with the QB slot open") {
    val teams = Vector(team(0, 17, 184), team(1, 17, 184))
    val (score, reason) = Nominate.score(player("allen-qb", Pos.QB, value = 25), teams, 0, Set(Pos.QB))
    assertEquals(score, 45)
    assertEquals(reason, "Bait: 1 rivals can pay $25, you are not paying $25 for a QB")
  }

  test("needs ignores FLEX, bench and already-filled slots") {
    val plan = Vector(
      PlanView("QB", 10, Some(5), Some("Josh Allen")), // filled: does not count
      PlanView("RB1", 46, None, None), // open: RB
      PlanView("FLEX", 11, None, None), // FLEX: never counts
      PlanView("BN1", 8, None, None), // bench: never counts
      PlanView("TE", 9, None, None), // open: TE
    )
    assertEquals(Nominate.needs(plan), Set(Pos.RB, Pos.TE))
  }

  test("canAfford excludes me, full teams and poor teams") {
    val teams = Vector(
      team(index = 0, slotsOpen = 17, maxBid = 184), // me: excluded regardless
      team(index = 1, slotsOpen = 17, maxBid = 184), // rich rival: affords
      team(index = 2, slotsOpen = 0, maxBid = 184), // full: excluded by slotsOpen alone, not by maxBid
      team(index = 3, slotsOpen = 5, maxBid = 6), // poor: excluded by maxBid alone, not by slotsOpen
    )
    assertEquals(Nominate.canAfford(teams, myTeam = 0, price = 58), 1)
  }

  test("drain fires on a cheap-need star with rivals who can pay") {
    val teams = Vector(team(0, 17, 184), team(1, 17, 184), team(2, 17, 184))
    val needs = Set(Pos.WR)
    val p = player("gibbs-rb", Pos.RB, value = 20)
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 50)
    assertEquals(reason, "Drain: 2 rivals can pay $20, you don't need an RB")
  }

  test("risk fires on a flagged player at a covered position") {
    val teams = Vector(team(0, 17, 184), team(1, 17, 184), team(2, 17, 184))
    val needs = Set(Pos.RB)
    val p = player("jeanty-rb", Pos.WR, value = 15, note = "ankle, no return date")
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 40)
    assertEquals(reason, "Risk: 2 rivals can pay $15, ankle, no return date")
  }

  test("bait fires on a cheap QB/DST/K at a covered position") {
    val teams = Vector(team(0, 17, 184), team(1, 17, 184))
    val needs = Set(Pos.RB)
    val p = player("some-k", Pos.K, value = 6)
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 26)
    assertEquals(reason, "Bait: 1 rivals can pay $6, you don't need a K")
  }

  test("steal fires on a needed player few rivals can afford") {
    val teams = Vector(team(0, 17, 184), team(1, 5, 4), team(2, 5, 4))
    val needs = Set(Pos.RB)
    val p = player("sleeper-rb", Pos.RB, value = 8)
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 15 + 8 + 5 * 2) // afford = 0
    assertEquals(reason, "Steal: 0 rivals can pay $8, you need an RB")
  }

  test("filler is everything that fires no other rule") {
    val teams = Vector(team(0, 17, 184), team(1, 17, 184))
    val needs = Set.empty[Pos]
    val p = player("boring-te", Pos.TE, value = 10)
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 10 / 4)
    assertEquals(reason, "Filler: 1 rivals can pay $10")
  }

  test("drain is checked before risk: a player meeting both fires drain") {
    // value >= 20, note non-empty, pos not in needs, afford >= 2: satisfies
    // both drain's and risk's conditions. If the branches were swapped this
    // would score 45 ("Risk: ...") instead of 50 ("Drain: ...").
    val teams = Vector(team(0, 17, 184), team(1, 17, 184), team(2, 17, 184))
    val needs = Set(Pos.WR)
    val p = player("edge-drain-risk", Pos.RB, value = 20, note = "something")
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 50)
    assertEquals(reason, "Drain: 2 rivals can pay $20, you don't need an RB")
  }

  test("risk is checked before bait: a flagged bait-position player fires risk") {
    // pos in {QB, DST, K}, value >= 15, note non-empty, pos not in needs:
    // satisfies both risk's and bait's conditions. If the branches were
    // swapped this would score 35 ("Bait: ...") instead of 40 ("Risk: ...").
    val teams = Vector(team(0, 17, 184), team(1, 17, 184), team(2, 17, 184))
    val needs = Set(Pos.RB)
    val p = player("edge-risk-bait", Pos.QB, value = 15, note = "questionable practice ramp")
    val (s, reason) = Nominate.score(p, teams, myTeam = 0, needs = needs)
    assertEquals(s, 40)
    assertEquals(reason, "Risk: 2 rivals can pay $15, questionable practice ramp")
  }

  test("suggest excludes sold players, respects n, and orders by score then value then rank") {
    // Three rivals, all rich: canAfford is 3 at every price used below, so
    // drain (needs >= 2) fires and steal (needs <= 2) does not, even for the
    // player whose position is a roster need.
    val teams = Vector(team(0, 17, 184), team(1, 17, 184), team(2, 17, 184), team(3, 17, 184))
    val plan = Vector(
      PlanView("QB", 10, Some(5), Some("Someone")),
      PlanView("RB1", 46, None, None), // open: needs = Set(RB)
      PlanView("WR1", 33, Some(30), Some("Someone")),
      PlanView("TE", 9, Some(8), Some("Someone")),
      PlanView("DST", 2, Some(2), Some("Someone")),
    )
    // p1: bait (QB, not a need), value 6  -> score 26
    // p2: filler (RB, IS a need, afford 3 > 2 so steal can't fire), value 40 -> score 10
    //     Higher value than p1 but a lower score: a value-only sort would
    //     rank p2 above p1; the correct score-based sort does not.
    // p3, p4: drain (WR/TE, not needs), value 25 each -> score 55 each, tied.
    //     p3 (rank 9) is listed before p4 (rank 3): only a real rank
    //     ascending tie-break, not input order, puts p4 first.
    // p5: sold, must never appear.
    val p1 = player("p1", Pos.QB, value = 6, rank = 1)
    val p2 = player("p2", Pos.RB, value = 40, rank = 2)
    val p3 = player("p3", Pos.WR, value = 25, rank = 9)
    val p4 = player("p4", Pos.TE, value = 25, rank = 3)
    val p5 = player("p5", Pos.DST, value = 25, rank = 5, soldTo = Some(2))
    val players = Vector(p1, p2, p3, p4, p5)

    val top3 = Nominate.suggest(players, teams, myTeam = 0, plan = plan, n = 3)
    assertEquals(top3.map(_.playerId), Vector("p4", "p3", "p1"))

    val top2 = Nominate.suggest(players, teams, myTeam = 0, plan = plan, n = 2)
    assertEquals(top2.map(_.playerId), Vector("p4", "p3"))
  }
