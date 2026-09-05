package auction

class AdviceTest extends munit.FunSuite:
  val league: League = League.espn10
  val players: Vector[Player] = Csv.players(java.nio.file.Files.readString(java.nio.file.Paths.get("data/players.csv")))
  def byId(id: String): Player = players.find(_.id == id).get
  val fresh: DraftState = DraftState.fresh(league)
  def sell(s: DraftState, id: String, team: Int, price: Int): DraftState = Draft.sell(s, league, players, id, team, price).toOption.get
  def advise(s: DraftState, id: String): AdviceView = Advice.compute(players, s, league, Plan.default, byId(id))
  /** Gibbs bought for $30 against a $52 plan: $22 of surplus. */
  val withSurplus: DraftState = sell(fresh, "jahmyr-gibbs-rb", 0, 30)

  test("a fresh board: Gibbs bids to the tier walk-away, the plan is reported not enforced") {
    val a = advise(fresh, "jahmyr-gibbs-rb")
    assertEquals(a.slot, Some("RB1"))
    assertEquals(a.planned, 52)
    assertEquals(a.surplus, 0)
    assert(a.boardMax >= 65, s"boardMax ${a.boardMax}") // 57 x 1.02 x 1.20
    assertEquals(a.walkAway, 68)
    assertEquals(a.bid, 68)
    assert(a.reason.contains("RB1 planned $52") && a.reason.contains("walk-away $68"))
  }

  test("named walk-aways hold with or without surplus") {
    assertEquals(advise(fresh, "christian-mccaffrey-rb").bid, 47)
    assertEquals(advise(withSurplus, "christian-mccaffrey-rb").bid, 47)
    assertEquals(advise(withSurplus, "josh-allen-qb").bid, 12)
    assertEquals(advise(withSurplus, "jonathan-taylor-rb").bid, 50)
  }

  test("surplus lifts a starter, but every tier has a walk-away under it") {
    assertEquals(advise(withSurplus, "puka-nacua-wr").bid, 61) // MAX 63 + 22 surplus, capped by the WR walk-away
    assertEquals(advise(withSurplus, "chase-brown-rb").bid, 39) // tier 3 RB: $39, not MAX + surplus
    assertEquals(advise(withSurplus, "jayden-daniels-qb").bid, 12) // any QB: $12
    assertEquals(advise(withSurplus, "colston-loveland-te").bid, 14)
    // An unnamed tier: surplus lifts the bid above MAX, up to value + 30% + $2.
    val t5 = byId("emeka-egbuka-wr")
    val lifted = advise(withSurplus, t5.id)
    assert(lifted.bid > advise(fresh, t5.id).bid, s"fresh ${advise(fresh, t5.id).bid} lifted ${lifted.bid}")
    assertEquals(lifted.bid, math.round(t5.value * 1.3).toInt + 2)
    assert(lifted.bid > lifted.boardMax, "surplus is what lifted it")
  }

  test("the bid never exceeds my own max bid") {
    // Spend to $190 across fourteen $1-13 players, leaving $10 and three open slots: max bid $8.
    val ids = players.filter(p => !Set("jahmyr-gibbs-rb", "puka-nacua-wr").contains(p.id) && p.pos == Pos.WR).take(14).map(_.id)
    val poor = ids.zipWithIndex.foldLeft(fresh) { case (s, (id, i)) => sell(s, id, 0, if i == 0 then 177 else 1) }
    assertEquals(league.maxBid(poor.spent(0), poor.filled(0)), 8)
    val a = advise(poor, "jahmyr-gibbs-rb")
    assertEquals(a.bid, 8)
    assert(a.reason.contains("capped by my max bid"))
  }

  test("a bench buy gets $1 plus a little surplus, never above the player's own MAX") {
    // A QB while QB is open is a starter; fill QB first so the next QB is bench (QBs have no FLEX).
    val withQb = sell(withSurplus, "josh-allen-qb", 0, 1)
    val maye = advise(withQb, "drake-maye-qb")
    assert(maye.slot.exists(_.startsWith("BN")), s"slot ${maye.slot}")
    assertEquals(maye.bid, 1 + Advice.benchSurplus)
    val withK = sell(withQb, "brandon-aubrey-k", 0, 1)
    assertEquals(advise(withK, "cameron-dicker-k").bid, 1)
  }

  test("a hot room lifts the MAX to the premium, for starters only, only above 1.20") {
    val hot = Vector(("bijan-robinson-rb", 1, 80), ("puka-nacua-wr", 2, 72), ("ja-marr-chase-wr", 3, 70))
      .foldLeft(fresh) { case (st, (id, t, price)) => sell(st, id, t, price) }
    val a = advise(hot, "jahmyr-gibbs-rb")
    assert(a.premium > 1.2, s"premium ${a.premium}")
    assert(a.reason.contains("room premium"))
    assert(a.boardMax > Pricing.maxBid(57, 1, a.inflation), "premium above the tier cap")
    // Warm, not hot: premium 1.10 is above a tier-4 cap of 1.00 but below 1.20, so no chase.
    val warm = Vector(("bijan-robinson-rb", 1, 63), ("puka-nacua-wr", 2, 56)).foldLeft(fresh) { case (st, (id, t, price)) => sell(st, id, t, price) }
    val w = advise(warm, "kenneth-walker-rb")
    assert(w.premium > 1.0 && w.premium < 1.2, s"premium ${w.premium}")
    assert(!w.reason.contains("room premium"))
    // Bench never chases.
    val hotQb = sell(hot, "josh-allen-qb", 0, 1)
    assert(!advise(hotQb, "drake-maye-qb").reason.contains("room premium"))
  }

  test("a sold player advises $0 even when only a bench spot fits, and so does a player with no slot") {
    val soldQb = sell(sell(fresh, "josh-allen-qb", 0, 1), "lamar-jackson-qb", 3, 10)
    assertEquals(advise(soldQb, "lamar-jackson-qb").bid, 0)
    assertEquals(advise(sell(fresh, "jahmyr-gibbs-rb", 3, 60), "jahmyr-gibbs-rb").bid, 0)
    val full = (1 to 17).foldLeft(fresh) { (st, i) => sell(st, players.filter(p => !st.isSold(p.id) && p.pos == Pos.WR)(i).id, 0, 1) }
    assertEquals(advise(full, "jahmyr-gibbs-rb").bid, 0)
  }
