package auction

import upickle.default.*

/** The domain, the JSON contract, and the CSV reader. Pure: nothing here
  * touches a file, a clock, or a socket. Every other module takes these
  * types as arguments and returns new values; the Cask edge in Server.scala
  * is the only place that reads or writes anything. */

enum Pos derives ReadWriter:
  case QB, RB, WR, TE, DST, K

object Pos:
  def parse(s: String): Option[Pos] = values.find(_.toString == s.trim.toUpperCase)

/** One row of data/players.csv. `value` is this league's base value (10-team,
  * half-PPR, no TE slot, $200); `tier` is positional; `note` is an injury or
  * situation flag from the research pass. */
final case class Player(
    id: String,
    rank: Int,
    pos: Pos,
    name: String,
    team: String,
    value: Int,
    tier: Int,
    note: String,
) derives ReadWriter

/** A lineup slot and which positions may fill it. Bench slots accept anything. */
final case class Slot(name: String, eligible: Set[Pos]) derives ReadWriter

final case class League(teams: Int, budget: Int, slots: Vector[Slot]) derives ReadWriter:
  def rosterSize: Int = slots.size
  def totalMoney: Int = teams * budget

object League:
  /** ESPN, 10 teams, $200: QB, 2 RB, 3 WR, FLEX (RB/WR/TE), D/ST, K, 7 bench. */
  val espn10: League =
    val bench = Vector.tabulate(7)(i => Slot(s"BN${i + 1}", Pos.values.toSet))
    League(
      teams = 10,
      budget = 200,
      slots = Vector(
        Slot("QB", Set(Pos.QB)),
        Slot("RB1", Set(Pos.RB)),
        Slot("RB2", Set(Pos.RB)),
        Slot("WR1", Set(Pos.WR)),
        Slot("WR2", Set(Pos.WR)),
        Slot("WR3", Set(Pos.WR)),
        Slot("FLEX", Set(Pos.RB, Pos.WR, Pos.TE)),
        Slot("DST", Set(Pos.DST)),
        Slot("K", Set(Pos.K)),
      ) ++ bench,
    )

/** One sale. `seq` is the order it happened in, starting at 1. */
final case class Pick(playerId: String, team: Int, price: Int, seq: Int) derives ReadWriter

/** Everything the draft has decided so far. Teams are indexed 0..teams-1;
  * `myTeam` is the index this board is drafting for. `overrides` replaces a
  * player's base value when the room prices a player differently than the
  * sheet and the user wants the sheet to follow. */
final case class DraftState(
    teamNames: Vector[String],
    myTeam: Int,
    picks: Vector[Pick],
    overrides: Map[String, Int],
) derives ReadWriter:
  def nextSeq: Int = picks.map(_.seq).maxOption.getOrElse(0) + 1
  def isSold(playerId: String): Boolean = picks.exists(_.playerId == playerId)
  def spent(team: Int): Int = picks.filter(_.team == team).map(_.price).sum

object DraftState:
  def fresh(league: League, myTeam: Int = 0): DraftState =
    DraftState(
      teamNames = Vector.tabulate(league.teams)(i => s"Team ${i + 1}"),
      myTeam = myTeam,
      picks = Vector.empty,
      overrides = Map.empty,
    )

/** My spending plan, one line per slot in League.espn10 order. Sums to $200. */
final case class PlanLine(slot: String, planned: Int) derives ReadWriter

object Plan:
  val default: Vector[PlanLine] = Vector(
    PlanLine("QB", 10),
    PlanLine("RB1", 48),
    PlanLine("RB2", 30),
    PlanLine("WR1", 34),
    PlanLine("WR2", 22),
    PlanLine("WR3", 13),
    PlanLine("FLEX", 12),
    PlanLine("DST", 2),
    PlanLine("K", 1),
    PlanLine("BN1", 8),
    PlanLine("BN2", 6),
    PlanLine("BN3", 4),
    PlanLine("BN4", 3),
    PlanLine("BN5", 3),
    PlanLine("BN6", 2),
    PlanLine("BN7", 2),
  )

// ── The JSON the page renders: GET /api/view returns exactly one View ────────

/** A player as the board shows it. `value` includes any override; `target` is
  * where I want to win them; `max` is the walk-away price after inflation. */
final case class PlayerView(
    id: String,
    rank: Int,
    pos: Pos,
    name: String,
    team: String,
    tier: Int,
    note: String,
    value: Int,
    target: Int,
    max: Int,
    soldTo: Option[Int],
    price: Option[Int],
) derives ReadWriter

final case class RosterLine(slot: String, playerId: Option[String], name: Option[String], pos: Option[Pos], price: Option[Int]) derives ReadWriter

final case class TeamView(
    index: Int,
    name: String,
    spent: Int,
    remaining: Int,
    slotsOpen: Int,
    maxBid: Int,
    perSlot: Double,
    roster: Vector[RosterLine],
) derives ReadWriter

final case class PlanView(slot: String, planned: Int, actual: Option[Int], name: Option[String]) derives ReadWriter

final case class Nomination(playerId: String, name: String, pos: Pos, value: Int, reason: String) derives ReadWriter

final case class View(
    inflation: Double,
    moneyLeft: Int,
    valueLeft: Int,
    myTeam: Int,
    players: Vector[PlayerView],
    teams: Vector[TeamView],
    plan: Vector[PlanView],
    planRemaining: Int,
    nominations: Vector[Nomination],
    lastPick: Option[Pick],
) derives ReadWriter

// ── CSV ──────────────────────────────────────────────────────────────────────

object Csv:
  /** Parse data/players.csv text. Header: rank,pos,player,team,value,tier,ppr12,note.
    * A quoted field may contain commas. Rows that do not parse are dropped, not
    * raised: a bad row in a 217-row sheet is a row to fix, not a reason for
    * the board to refuse to start. */
  def players(text: String): Vector[Player] =
    val lines = text.linesIterator.toVector.filter(_.trim.nonEmpty)
    lines.drop(1).flatMap(row)

  def row(line: String): Option[Player] =
    val f = fields(line)
    if f.size < 8 then None
    else
      for
        rank <- f(0).toIntOption
        pos <- Pos.parse(f(1))
        value <- f(4).toIntOption
        tier <- f(5).toIntOption
      yield Player(id = slug(f(2), f(1)), rank = rank, pos = pos, name = f(2), team = f(3), value = value, tier = tier, note = f(7))

  def slug(name: String, pos: String): String =
    name.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripSuffix("-") + "-" + pos.toLowerCase

  /** Split one CSV line honouring double quotes. */
  def fields(line: String): Vector[String] =
    val (acc, cur, _) = line.foldLeft((Vector.empty[String], new StringBuilder, false)) {
      case ((acc, cur, inQ), '"') => (acc, cur, !inQ)
      case ((acc, cur, false), ',') => (acc :+ cur.result(), new StringBuilder, false)
      case ((acc, cur, inQ), c) => (acc, cur.append(c), inQ)
    }
    acc :+ cur.result()
