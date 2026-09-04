package auction

class NamesTest extends munit.FunSuite:
  def p(name: String, pos: Pos, rank: Int = 1): Player = Player(Csv.slug(name, pos.toString), rank, pos, name, "X", 10, 5, "")
  val pool = Vector(
    p("D.J. Moore", Pos.WR), p("Kenneth Walker", Pos.RB), p("Marvin Harrison Jr.", Pos.WR), p("Ja'Marr Chase", Pos.WR),
    p("Pittsburgh", Pos.DST), p("Los Angeles Rams", Pos.DST), p("Kyle Monangai", Pos.RB, 2), p("Ty Monangai", Pos.RB, 3),
  )

  test("normalize drops punctuation, suffixes and D/ST") {
    assertEquals(Names.normalize("D.J. Moore"), "dj moore")
    assertEquals(Names.normalize("Marvin Harrison Jr."), "marvin harrison")
    assertEquals(Names.normalize("Steelers D/ST"), "steelers")
    assertEquals(Names.normalize("Ja'Marr Chase"), "jamarr chase")
  }

  test("exact and near-exact names score high") {
    assertEquals(Names.best(pool, "DJ Moore").map(_.player.name), Some("D.J. Moore"))
    assertEquals(Names.best(pool, "Ken Walker III").map(m => (m.player.name, m.score)), Some(("Kenneth Walker", 0.9)))
    assertEquals(Names.best(pool, "Marvin Harrison").map(_.score), Some(1.0))
    assertEquals(Names.best(pool, "Jamarr Chase").map(_.score), Some(1.0))
  }

  test("a D/ST label resolves through the nickname") {
    assertEquals(Names.best(pool, "Steelers D/ST").map(_.player.name), Some("Pittsburgh"))
    assertEquals(Names.best(pool, "Rams D/ST").map(_.player.name), Some("Los Angeles Rams"))
    assertEquals(Names.best(pool, "LA Rams").map(_.player.name), Some("Los Angeles Rams"))
  }

  test("an unknown name scores nothing, and a position narrows the pool") {
    assertEquals(Names.best(pool, "Nobody Here"), None)
    assertEquals(Names.best(pool, "Monangai", Some(Pos.RB)).map(_.player.name), Some("Kyle Monangai"))
    assertEquals(Names.candidates(pool, "Monangai").size, 2)
  }

  test("teams resolve by number, 'Team N', or name") {
    val names = Vector("Pat's Picks", "Team 2", "Team 3")
    assertEquals(Names.resolveTeam(names, "3"), Some(2))
    assertEquals(Names.resolveTeam(names, "Team 2"), Some(1))
    assertEquals(Names.resolveTeam(names, "pat's picks"), Some(0))
    assertEquals(Names.resolveTeam(names, "11"), None)
  }
