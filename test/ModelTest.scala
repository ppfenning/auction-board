package auction

import upickle.default.*

class ModelTest extends munit.FunSuite:
  test("csv row with a quoted note parses") {
    val p = Csv.row("""3,WR,Puka Nacua,LAR,53,1,52,"groin soreness, expected Wk1"""").get
    assertEquals(p.name, "Puka Nacua")
    assertEquals(p.pos, Pos.WR)
    assertEquals(p.value, 53)
    assertEquals(p.note, "groin soreness, expected Wk1")
    assertEquals(p.id, "puka-nacua-wr")
  }

  test("a malformed row is dropped, not raised") {
    assertEquals(Csv.players("rank,pos\n1,RB,Jahmyr Gibbs,DET,58,1,51,\nbad,row\n").size, 1)
  }

  test("plan sums to the budget and covers every slot") {
    assertEquals(Plan.default.map(_.planned).sum, League.espn10.budget)
    assertEquals(Plan.default.map(_.slot), League.espn10.slots.map(_.name))
  }

  test("draft state round-trips through JSON") {
    val s = DraftState.fresh(League.espn10).copy(picks = Vector(Pick("jahmyr-gibbs-rb", 2, 60, 1)), overrides = Map("x" -> 5))
    assertEquals(read[DraftState](write(s)), s)
    assertEquals(s.nextSeq, 2)
    assertEquals(s.spent(2), 60)
  }

class ModelHelpersTest extends munit.FunSuite:
  test("max bid leaves a dollar for every other open slot") {
    assertEquals(League.espn10.maxBid(spent = 0, filled = 0), 185)
    assertEquals(League.espn10.maxBid(spent = 150, filled = 10), 45)
    assertEquals(League.espn10.maxBid(spent = 199, filled = 15), 1)
    assertEquals(League.espn10.maxBid(spent = 200, filled = 16), 0)
  }
  test("effective value prefers the override") {
    val p = Player("x-rb", 1, Pos.RB, "X", "DET", 40, 2, "")
    val s = DraftState.fresh(League.espn10)
    assertEquals(s.effectiveValue(p), 40)
    assertEquals(s.copy(overrides = Map("x-rb" -> 33)).effectiveValue(p), 33)
  }
