package auction

class PricingTest extends munit.FunSuite:
  val p1 = Player("p1-rb", 1, Pos.RB, "Alpha", "AAA", 60, 1, "")
  val p2 = Player("p2-wr", 2, Pos.WR, "Bravo", "BBB", 50, 3, "")
  val p3 = Player("p3-qb", 3, Pos.QB, "Charlie", "CCC", 40, 4, "")
  val p4 = Player("p4-te", 4, Pos.TE, "Delta", "DDD", 30, 6, "")
  val p5 = Player("p5-k", 5, Pos.K, "Echo", "EEE", 0, 5, "")
  val players = Vector(p1, p2, p3, p4, p5)
  val fresh = DraftState.fresh(League.espn10)

  test("moneyLeft and slotsOpen on a fresh state") {
    assertEquals(Pricing.moneyLeft(fresh, League.espn10), 2000)
    assertEquals(Pricing.slotsOpen(fresh, League.espn10), 170)
  }

  test("moneyLeft and slotsOpen after two picks") {
    val state = fresh.copy(picks = Vector(Pick(p1.id, 0, 60, 1), Pick(p2.id, 1, 45, 2)))
    assertEquals(Pricing.moneyLeft(state, League.espn10), 1895)
    assertEquals(Pricing.slotsOpen(state, League.espn10), 168)
  }

  test("valueLeft counts only unsold players, floored at $1") {
    val state = fresh.copy(picks = Vector(Pick(p1.id, 0, 60, 1), Pick(p2.id, 1, 45, 2)))
    // p1, p2 sold; p3=40, p4=30, p5 floors from 0 to 1
    assertEquals(Pricing.valueLeft(players, state, League.espn10), 71)
  }

  test("valueLeft takes only the top `slotsOpen` unsold players") {
    val miniLeague = League(teams = 1, budget = 100, slots = Vector(Slot("QB", Set(Pos.QB))))
    val miniState = DraftState.fresh(miniLeague)
    assertEquals(Pricing.slotsOpen(miniState, miniLeague), 1)
    // all five unsold, but only room for the top one: p1 at 60
    assertEquals(Pricing.valueLeft(players, miniState, miniLeague), 60)
  }

  test("inflation is 1.0 on an empty board of exact value") {
    val league = League(teams = 1, budget = 60, slots = Vector(Slot("QB", Set(Pos.QB))))
    assertEquals(Pricing.inflation(Vector(p1), DraftState.fresh(league), league), 1.0)
  }

  test("inflation rises when money outpaces value") {
    val league = League(teams = 1, budget = 90, slots = Vector(Slot("QB", Set(Pos.QB))))
    assertEquals(Pricing.inflation(Vector(p1), DraftState.fresh(league), league), 1.5)
  }

  test("inflation is clamped to [0.5, 2.0]") {
    val low = League(teams = 1, budget = 10, slots = Vector(Slot("QB", Set(Pos.QB))))
    val high = League(teams = 1, budget = 1000, slots = Vector(Slot("QB", Set(Pos.QB))))
    assertEquals(Pricing.inflation(Vector(p1), DraftState.fresh(low), low), 0.5)
    assertEquals(Pricing.inflation(Vector(p1), DraftState.fresh(high), high), 2.0)
  }

  test("inflation defaults to 1.0 when valueLeft is 0") {
    assertEquals(Pricing.inflation(Vector.empty, fresh, League.espn10), 1.0)
  }

  test("inflation rounds to two decimals rather than truncating") {
    val league = League(teams = 1, budget = 100, slots = Vector(Slot("QB", Set(Pos.QB))))
    // 100 / 60 = 1.6666...; two-decimal rounding gives 1.67, truncation would give 1.66
    assertEquals(Pricing.inflation(Vector(p1), DraftState.fresh(league), league), 1.67)
  }

  test("tierCap by tier") {
    assertEquals(Pricing.tierCap(1), 1.10)
    assertEquals(Pricing.tierCap(3), 1.05)
    assertEquals(Pricing.tierCap(4), 1.00)
    assertEquals(Pricing.tierCap(6), 0.90)
  }

  test("target and maxBid rounding, with the $1 floor") {
    assertEquals(Pricing.target(100, 1.0), 90)
    assertEquals(Pricing.target(1, 0.5), 1)
    assertEquals(Pricing.maxBid(100, 1, 1.0), 110)
    assertEquals(Pricing.maxBid(1, 6, 0.5), 1)
    // 5 * 1.5 * 0.90 = 6.75: rounds to 7, truncation would give 6
    assertEquals(Pricing.target(5, 1.5), 7)
    // 5 * 1.5 * tierCap(4)=1.00 = 7.5: rounds to 8, truncation would give 7
    assertEquals(Pricing.maxBid(5, 4, 1.5), 8)
  }

  test("playerViews breaks ties in effective value by rank ascending") {
    val tieHighRank = Player("tie-a-rb", 9, Pos.RB, "TieA", "AAA", 25, 2, "")
    val tieLowRank = Player("tie-b-wr", 3, Pos.WR, "TieB", "BBB", 25, 2, "")
    val views = Pricing.playerViews(Vector(tieHighRank, tieLowRank), fresh, League.espn10)
    assertEquals(views.map(_.id), Vector(tieLowRank.id, tieHighRank.id))
  }

  test("playerViews composes target/max from an unclamped, mid-band inflation") {
    // A small custom league so moneyLeft/valueLeft lands strictly inside
    // [0.5, 2.0]: every earlier playerViews fixture sits at the 2.0 ceiling,
    // where target/max would look identical even if playerViews ignored the
    // computed inflation entirely and used a hardcoded 2.0.
    val midLeague = League(teams = 1, budget = 84, slots = Vector(Slot("QB", Set(Pos.QB)), Slot("RB1", Set(Pos.RB))))
    val midState = DraftState.fresh(midLeague)
    val subset = Vector(p1, p3) // values 60 and 40, both unsold, exactly fill the 2 open slots

    assertEquals(Pricing.inflation(subset, midState, midLeague), 0.84) // 84 / (60 + 40)

    val views = Pricing.playerViews(subset, midState, midLeague)
    assertEquals(views.map(_.id), Vector(p1.id, p3.id))

    val v1 = views.find(_.id == p1.id).get
    assertEquals(v1.target, 45) // round(60 * 0.84 * 0.90) = round(45.36)
    assertEquals(v1.max, 55) // round(60 * 0.84 * 1.10), tier 1 = round(55.44)

    val v3 = views.find(_.id == p3.id).get
    assertEquals(v3.target, 30) // round(40 * 0.84 * 0.90) = round(30.24)
    assertEquals(v3.max, 34) // round(40 * 0.84 * 1.00), tier 4 = round(33.6)
  }

  test("playerViews: ordering, override, soldTo/price") {
    val state = fresh.copy(picks = Vector(Pick(p1.id, 0, 55, 1)), overrides = Map(p2.id -> 80))
    val views = Pricing.playerViews(players, state, League.espn10)

    // effective values: p2=80 (override), p1=60, p3=40, p4=30, p5=0
    assertEquals(views.map(_.id), Vector(p2.id, p1.id, p3.id, p4.id, p5.id))

    val v1 = views.find(_.id == p1.id).get
    assertEquals(v1.value, 60)
    assertEquals(v1.soldTo, Some(0))
    assertEquals(v1.price, Some(55))

    val v2 = views.find(_.id == p2.id).get
    assertEquals(v2.value, 80)
    assertEquals(v2.soldTo, None)
    assertEquals(v2.price, None)

    // inflation here is 1945 / 151, clamped to 2.0
    assertEquals(Pricing.inflation(players, state, League.espn10), 2.0)
    assertEquals(v1.target, 108) // round(60 * 2.0 * 0.90)
    assertEquals(v1.max, 132) // round(60 * 2.0 * 1.10), tier 1
    assertEquals(v2.target, 144) // round(80 * 2.0 * 0.90)
    assertEquals(v2.max, 168) // round(80 * 2.0 * 1.05), tier 3

    val v5 = views.find(_.id == p5.id).get
    assertEquals(v5.value, 0)
    assertEquals(v5.target, 1) // floored
    assertEquals(v5.max, 1) // floored
  }
