package auction

class PremiumTest extends munit.FunSuite:
  val league: League = League.espn10
  val a = Player("a-rb", 1, Pos.RB, "A", "DET", 50, 1, "")
  val b = Player("b-wr", 2, Pos.WR, "B", "CIN", 30, 2, "")
  val players = Vector(a, b)

  test("premium is 1.0 before any sale") {
    assertEquals(Pricing.premium(players, DraftState.fresh(league)), 1.0)
  }

  test("premium is prices over sheet values of what sold, two decimals") {
    val s = DraftState.fresh(league).copy(picks = Vector(Pick(a.id, 1, 60, 1), Pick(b.id, 2, 36, 2)))
    assertEquals(Pricing.premium(players, s), 1.2) // 96 / 80
  }

  test("premium honours overrides and is clamped") {
    val s = DraftState.fresh(league).copy(picks = Vector(Pick(a.id, 1, 150, 1)), overrides = Map(a.id -> 25))
    assertEquals(Pricing.premium(players, s), 2.0) // 150 / 25 = 6, clamped
  }
