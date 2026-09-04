package auction

import java.nio.file.{Files, Paths}

class ServerTest extends munit.FunSuite:
  val league = League.espn10

  // Values sum to 2000: moneyLeft on a fresh espn10 state is teams * budget
  // = 10 * 200 = 2000, and with only these four players unsold, valueLeft
  // takes all of them, so raw inflation is 2000 / 2000 = 1.0 exactly.
  val rb1 = Player("rb1-id", 1, Pos.RB, "RB One", "AAA", 500, 1, "")
  val wr1 = Player("wr1-id", 2, Pos.WR, "WR One", "BBB", 500, 1, "")
  val te1 = Player("te1-id", 3, Pos.TE, "TE One", "CCC", 500, 1, "")
  val qb1 = Player("qb1-id", 4, Pos.QB, "QB One", "DDD", 500, 1, "")

  val players = Vector(rb1, wr1, te1, qb1)

  def fresh: DraftState = DraftState.fresh(league)

  // ── Views.assemble ───────────────────────────────────────────────────────

  test("assemble on a fresh state builds every section of the View") {
    val view = Views.assemble(players, fresh, league)
    assertEquals(view.teams.size, 10)
    assertEquals(view.plan.size, 17)
    assertEquals(view.inflation, 1.0)
    assert(view.nominations.nonEmpty, "nominations should not be empty with unsold players")
    assertEquals(view.lastPick, None)
  }

  test("assemble after one sale reflects it in lastPick, the player, and the team") {
    val sold = Draft.sell(fresh, league, players, rb1.id, 0, 55) match
      case Right(s) => s
      case Left(reason) => fail(s"sell failed: $reason")
    val view = Views.assemble(players, sold, league)
    assertEquals(view.lastPick, Some(Pick(rb1.id, 0, 55, 1)))
    val rb1View = view.players.find(_.id == rb1.id).get
    assertEquals(rb1View.soldTo, Some(0))
    assertEquals(rb1View.price, Some(55))
    val myTeam = view.teams.find(_.index == 0).get
    assertEquals(myTeam.spent, 55)
  }

  // ── persistence ──────────────────────────────────────────────────────────

  test("Server.persist and Server.load round-trip through a temp file") {
    val tmp = Files.createTempFile("draft-test", ".json")
    Files.delete(tmp)
    val path = tmp.toString
    try
      val state = fresh.copy(
        picks = Vector(Pick(rb1.id, 0, 55, 1)),
        overrides = Map(wr1.id -> 45),
      )
      Server.persist(path, state)
      assertEquals(Server.load(path), Some(state))
    finally
      Files.deleteIfExists(Paths.get(path))
  }

  test("Server.persist overwrites an existing file with the newest state") {
    val tmp = Files.createTempFile("draft-overwrite", ".json")
    val path = tmp.toString
    try
      val first = fresh.copy(picks = Vector(Pick(rb1.id, 0, 55, 1)))
      val second = fresh.copy(picks = Vector(Pick(rb1.id, 0, 55, 1), Pick(wr1.id, 1, 40, 2)))
      Server.persist(path, first)
      assert(Files.exists(Paths.get(path)), "first persist should have written the target path")
      Server.persist(path, second)
      assertEquals(Server.load(path), Some(second))
    finally
      Files.deleteIfExists(Paths.get(path))
  }

  test("Server.load returns None when there is nothing at the path yet") {
    val tmp = Files.createTempFile("draft-missing", ".json")
    Files.delete(tmp)
    assertEquals(Server.load(tmp.toString), None)
  }

  // ── POST /api/override body decoding ────────────────────────────────────

  test("parseOverrideValue reads the {playerId, value} body's n-or-null field") {
    val setBody = ujson.read("""{"playerId": "rb1-id", "value": 45}""")
    assertEquals(Server.parseOverrideValue(setBody("value")), Some(45))
    val clearBody = ujson.read("""{"playerId": "rb1-id", "value": null}""")
    assertEquals(Server.parseOverrideValue(clearBody("value")), None)
  }
