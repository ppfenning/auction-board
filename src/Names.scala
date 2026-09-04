package auction

/** Matching the names a draft room shows to the names on the sheet. Pure.
  *
  * ESPN writes "D.J. Moore", "Ken Walker III", "Steelers D/ST"; the sheet
  * writes "D.J. Moore", "Kenneth Walker", "Pittsburgh". A live sync that
  * records the wrong player is worse than one that asks, so `best` returns
  * a score and the caller decides the threshold (Server uses 0.8). */
object Names:
  /** Team nickname -> the sheet's D/ST name (the city, or city + nickname
    * where a city has two teams). */
  val dstByNickname: Map[String, String] = Map(
    "cardinals" -> "Arizona", "falcons" -> "Atlanta", "ravens" -> "Baltimore", "bills" -> "Buffalo",
    "panthers" -> "Carolina", "bears" -> "Chicago", "bengals" -> "Cincinnati", "browns" -> "Cleveland",
    "cowboys" -> "Dallas", "broncos" -> "Denver", "lions" -> "Detroit", "packers" -> "Green Bay",
    "texans" -> "Houston", "colts" -> "Indianapolis", "jaguars" -> "Jacksonville", "chiefs" -> "Kansas City",
    "raiders" -> "Las Vegas", "chargers" -> "Los Angeles Chargers", "rams" -> "Los Angeles Rams",
    "dolphins" -> "Miami", "vikings" -> "Minnesota", "patriots" -> "New England", "saints" -> "New Orleans",
    "giants" -> "New York Giants", "jets" -> "New York Jets", "eagles" -> "Philadelphia",
    "steelers" -> "Pittsburgh", "49ers" -> "San Francisco", "seahawks" -> "Seattle",
    "buccaneers" -> "Tampa Bay", "titans" -> "Tennessee", "commanders" -> "Washington",
  )

  private val suffixes = Set("jr", "sr", "ii", "iii", "iv", "v")

  /** Lowercase, punctuation gone, generational suffixes gone, "D/ST" and
    * "DST" gone, single spaces. "D.J. Moore" -> "dj moore". */
  def normalize(s: String): String =
    s.toLowerCase
      .replaceAll("d/st|dst", " ")
      .replaceAll("[.'’`]", "")
      .replaceAll("[^a-z0-9 ]", " ")
      .split("\\s+").filter(t => t.nonEmpty && !suffixes(t)).mkString(" ")

  /** A room's D/ST label as the sheet names it, if it names a team. */
  def dstName(label: String): Option[String] =
    val n = normalize(label)
    dstByNickname.collectFirst { case (nick, city) if n == nick || n.endsWith(" " + nick) || n == normalize(city) => city }

  private def tokenMatch(a: String, b: String): Boolean =
    a == b || (a.length >= 3 && b.length >= 3 && (a.startsWith(b) || b.startsWith(a)))

  /** 1.0 exact; 0.9 when the last token matches and the first tokens agree
    * by prefix (Ken/Kenneth, DJ/D.J.); 0.5-0.8 by token overlap; 0 otherwise. */
  def score(candidate: String, query: String): Double =
    val c = normalize(candidate).split(" ").toVector
    val q = normalize(query).split(" ").toVector
    if c == q then 1.0
    else if c.nonEmpty && q.nonEmpty && c.last == q.last && tokenMatch(c.head, q.head) then 0.9
    else
      val hits = q.count(t => c.exists(tokenMatch(_, t)))
      if hits == 0 then 0.0 else 0.5 + 0.3 * hits.toDouble / math.max(c.size, q.size)

  final case class Match(player: Player, score: Double)

  /** The best-scoring players for a room's label, best first, ties by rank.
    * A D/ST label matches on the team name, at score 1.0. */
  def candidates(players: Vector[Player], label: String, pos: Option[Pos] = None, n: Int = 3): Vector[Match] =
    val pool = pos.fold(players)(p => players.filter(_.pos == p))
    val dst = dstName(label)
    pool
      .map(p => Match(p, if p.pos == Pos.DST then (if dst.contains(p.name) then 1.0 else 0.0) else score(p.name, label)))
      .filter(_.score > 0)
      .sortBy(m => (-m.score, m.player.rank))
      .take(n)

  def best(players: Vector[Player], label: String, pos: Option[Pos] = None): Option[Match] =
    candidates(players, label, pos, 1).headOption

  /** A team given as "3", "Team 3", or a team's name (case-insensitive). */
  def resolveTeam(teamNames: Vector[String], raw: String): Option[Int] =
    val t = raw.trim
    val byIndex = t.toIntOption.filter(i => i >= 1 && i <= teamNames.size).map(_ - 1)
    byIndex.orElse(teamNames.indexWhere(_.equalsIgnoreCase(t)) match
      case -1 => None
      case i => Some(i))
