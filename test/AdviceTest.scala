package auction

class AdviceTest extends munit.FunSuite:
  val league: League = League.espn10
  val players: Vector[Player] = Csv.players(java.nio.file.Files.readString(java.nio.file.Paths.get("data/players.csv")))
  def byId(id: String): Player = players.find(_.id == id).get
  val fresh: DraftState = DraftState.fresh(league)

  test("a fresh board: Gibbs is capped by the RB1 plan, not the MAX") {
    val a = Advice.compute(players, fresh, league, Plan.default, byId("jahmyr-gibbs-rb"))
    assertEquals(a.slot, Some("RB1"))
    assertEquals(a.planned, 52)
    assertEquals(a.surplus, 0)
    assert(a.boardMax >= 65, s"boardMax ${a.boardMax}") // 57 x 1.02 x 1.20
    assertEquals(a.bid, 62) // max(52 + 3, round(52 * 1.2)) = 62, below MAX and the $68 walk-away
    assert(a.reason.contains("RB1 planned $52"))
  }

  test("hard walk-aways win: McCaffrey never above $47, Allen never above $12") {
    assert(Advice.compute(players, fresh, league, Plan.default, byId("christian-mccaffrey-rb")).bid <= 47)
    assertEquals(Advice.compute(players, fresh, league, Plan.default, byId("josh-allen-qb")).bid, 12)
  }

  test("surplus raises a starter's bid") {
    // RB1 bought for $30 against a $52 plan: $22 of surplus.
    val s = Draft.sell(fresh, league, players, "jahmyr-gibbs-rb", 0, 30).toOption.get
    val a = Advice.compute(players, s, league, Plan.default, byId("puka-nacua-wr"))
    assertEquals(a.slot, Some("WR1"))
    assertEquals(a.surplus, 22)
    assert(a.bid > Advice.compute(players, fresh, league, Plan.default, byId("puka-nacua-wr")).bid)
    assert(a.bid <= 61, "the WR tier walk-away still holds")
  }

  test("a bench buy gets $1 plus a little surplus, never above the player's own MAX") {
    val s = Draft.sell(fresh, league, players, "jahmyr-gibbs-rb", 0, 30).toOption.get // $22 surplus
    // A QB while QB is open is a starter; fill QB first so the next QB is bench (QBs have no FLEX).
    val withQb = Draft.sell(s, league, players, "josh-allen-qb", 0, 1).toOption.get
    val maye = Advice.compute(players, withQb, league, Plan.default, byId("drake-maye-qb"))
    assert(maye.slot.exists(_.startsWith("BN")), s"slot ${maye.slot}")
    assertEquals(maye.bid, 1 + Advice.benchSurplus)
    // A $1 kicker on the bench is worth $1, surplus or not.
    val withK = Draft.sell(withQb, league, players, "brandon-aubrey-k", 0, 1).toOption.get
    assertEquals(Advice.compute(players, withK, league, Plan.default, byId("cameron-dicker-k")).bid, 1)
  }

  test("a hot room lifts the MAX to the premium") {
    val sales = Vector(("bijan-robinson-rb", 1, 80), ("puka-nacua-wr", 2, 72), ("ja-marr-chase-wr", 3, 70))
    val s = sales.foldLeft(fresh) { case (st, (id, t, price)) => Draft.sell(st, league, players, id, t, price).toOption.get }
    val a = Advice.compute(players, s, league, Plan.default, byId("jahmyr-gibbs-rb"))
    assert(a.premium > 1.2, s"premium ${a.premium}")
    assert(a.reason.contains("room premium"))
    assert(a.boardMax > Pricing.maxBid(57, 1, a.inflation), "premium above the tier cap")
  }

  test("a sold player and a player with no slot both advise $0") {
    val s = Draft.sell(fresh, league, players, "jahmyr-gibbs-rb", 3, 60).toOption.get
    assertEquals(Advice.compute(players, s, league, Plan.default, byId("jahmyr-gibbs-rb")).bid, 0)
    val full = (1 to 17).foldLeft(fresh) { (st, i) =>
      Draft.sell(st, league, players, players.filter(p => !st.isSold(p.id) && p.pos == Pos.WR)(i).id, 0, 1).toOption.get
    }
    assertEquals(Advice.compute(players, full, league, Plan.default, byId("jahmyr-gibbs-rb")).bid, 0)
  }
