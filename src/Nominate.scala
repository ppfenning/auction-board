package auction

/** Ranked nomination advice. Pure: consumes only the View types Server.scala
  * already built (PlayerView, TeamView, PlanView, Nomination, Pos) and
  * returns new values. Nothing here touches a file, a clock, or a socket. */
object Nominate:
  private val baitPositions: Set[Pos] = Set(Pos.QB, Pos.DST, Pos.K)

  /** The position a STARTER slot name maps to, or None for FLEX, bench, or
    * any slot outside the nine starter names. A value, not a MatchError: an
    * unrecognised slot is a fact `needs` can filter out, not a crash. */
  private def slotPos(slot: String): Option[Pos] = slot match
    case "QB" => Some(Pos.QB)
    case "RB1" | "RB2" => Some(Pos.RB)
    case "WR1" | "WR2" | "WR3" => Some(Pos.WR)
    case "TE" => Some(Pos.TE)
    case "DST" => Some(Pos.DST)
    case "K" => Some(Pos.K)
    case _ => None

  /** The article that reads naturally before a position's initialism. */
  private def article(pos: Pos): String = if pos == Pos.RB then "an" else "a"

  /** Positions my roster still needs a STARTER for: for each plan line whose
    * `name` is None and whose slot is QB, RB1, RB2, WR1, WR2, WR3, TE, DST
    * or K, the position the slot name starts with (RB1 -> RB, TE -> TE,
    * DST -> DST). FLEX and bench lines never count. */
  def needs(plan: Vector[PlanView]): Set[Pos] =
    plan.filter(_.name.isEmpty).flatMap(l => slotPos(l.slot)).toSet

  /** Rival teams (index != myTeam) with slotsOpen > 0 and maxBid >= price. */
  def canAfford(teams: Vector[TeamView], myTeam: Int, price: Int): Int =
    teams.count(t => t.index != myTeam && t.slotsOpen > 0 && t.maxBid >= price)

  /** Score one available player. Higher is a better nomination for ME. */
  def score(p: PlayerView, teams: Vector[TeamView], myTeam: Int, needs: Set[Pos]): (Int, String) =
    val afford = canAfford(teams, myTeam, p.value)
    val art = article(p.pos)
    if p.value >= 20 && !needs.contains(p.pos) && afford >= 2 then
      (30 + p.value, s"Drain: $afford rivals can pay $$${p.value}, you don't need $art ${p.pos}")
    // An AVOID tag is a decision already made: whether or not the position is
    // still open, this is a player to let a rival own, at any point in the draft.
    else if p.note.toUpperCase.contains("AVOID") && p.value >= 8 then
      (28 + p.value, s"Avoid: $afford rivals can pay $$${p.value}, ${p.note}")
    else if p.value >= 15 && p.note.nonEmpty && !needs.contains(p.pos) then
      (25 + p.value, s"Risk: $afford rivals can pay $$${p.value}, ${p.note}")
    // A $20+ QB is bait even with the QB slot open: the plan never pays that.
    else if baitPositions.contains(p.pos) && (p.value >= 20 || (p.value >= 6 && !needs.contains(p.pos))) then
      (20 + p.value, s"Bait: $afford rivals can pay $$${p.value}, " + (if needs.contains(p.pos) then s"you are not paying $$${p.value} for $art ${p.pos}" else s"you don't need $art ${p.pos}"))
    else if needs.contains(p.pos) && afford <= 2 && p.value >= 8 then
      (15 + p.value + 5 * (2 - afford), s"Steal: $afford rivals can pay $$${p.value}, you need $art ${p.pos}")
    else
      (p.value / 4, s"Filler: $afford rivals can pay $$${p.value}")

  /** Top `n` AVAILABLE players (soldTo.isEmpty) by score descending, ties by
    * value descending then rank ascending, as Nomination(playerId, name, pos,
    * value, reason). */
  def suggest(players: Vector[PlayerView], teams: Vector[TeamView], myTeam: Int, plan: Vector[PlanView], n: Int = 8): Vector[Nomination] =
    val need = needs(plan)
    players
      .filter(_.soldTo.isEmpty)
      .map(p => (p, score(p, teams, myTeam, need)))
      .sortBy { case (p, (s, _)) => (-s, -p.value, p.rank) }
      .take(n)
      .map { case (p, (_, reason)) => Nomination(p.id, p.name, p.pos, p.value, reason) }
