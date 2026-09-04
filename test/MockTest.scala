package auction

class MockTest extends munit.FunSuite:
  val players: Vector[Player] = Csv.players(java.nio.file.Files.readString(java.nio.file.Paths.get("data/players.csv")))

  test("a mock room fills every roster and never overspends") {
    val room = Mock.runRoom(players, seed = 7, roomHeat = 1.0)
    for t <- 0 until Mock.league.teams do
      assertEquals(room.state.filled(t), Mock.league.rosterSize, s"team $t")
      assert(room.state.spent(t) <= Mock.league.budget, s"team $t spent ${room.state.spent(t)}")
  }

  test("I never pay above a hard cap or above the board's MAX") {
    val room = Mock.runRoom(players, seed = 11, roomHeat = 1.1)
    val byId = players.map(p => p.id -> p).toMap
    for (id, team, price) <- room.log if team == Mock.me do
      Mock.hardCaps.get(id).foreach(cap => assert(price <= cap, s"$id at $price over cap $cap"))
      assert(price <= Pricing.maxBid(byId(id).value, byId(id).tier, 2.0), s"$id at $price")
  }

  test("the same seed reproduces the same room") {
    assertEquals(Mock.runRoom(players, 3, 1.0).log, Mock.runRoom(players, 3, 1.0).log)
  }
