package auction

/** Transitions over DraftState, and the views the board reads back out of it.
  * Pure: every function here takes plain data and returns plain data or
  * Either[String, _]; nothing here reads a file, a clock, or a socket. */
object Draft:

  /** Record a sale. Left with a one-line reason when: playerId unknown;
    * already sold; team outside 0 until league.teams; price < 1; team has no
    * open slot (filled >= rosterSize); price > league.maxBid(spent, filled)
    * for that team. Otherwise Right(state with the pick appended at nextSeq). */
  def sell(state: DraftState, league: League, players: Vector[Player], playerId: String, team: Int, price: Int): Either[String, DraftState] =
    for
      _ <- players.find(_.id == playerId).toRight(s"unknown player: $playerId")
      _ <- if state.isSold(playerId) then Left(s"already sold: $playerId") else Right(())
      _ <- if team < 0 || team >= league.teams then Left(s"team out of range: $team") else Right(())
      _ <- if price < 1 then Left(s"price must be at least 1: $price") else Right(())
      filled = state.filled(team)
      _ <- if filled >= league.rosterSize then Left(s"team $team has no open slot") else Right(())
      cap = league.maxBid(state.spent(team), filled)
      _ <- if price > cap then Left(s"price $price exceeds max bid $cap") else Right(())
    yield state.copy(picks = state.picks :+ Pick(playerId, team, price, state.nextSeq))

  /** Drop the pick with the highest seq; a state with no picks is returned unchanged. */
  def undo(state: DraftState): DraftState =
    state.picks.maxByOption(_.seq) match
      case None => state
      case Some(last) => state.copy(picks = state.picks.filterNot(_ == last))

  /** Some(value) sets an override (value >= 1), None removes it. */
  def setOverride(state: DraftState, playerId: String, value: Option[Int]): DraftState =
    value match
      case Some(v) if v >= 1 => state.copy(overrides = state.overrides + (playerId -> v))
      case _ => state.copy(overrides = state.overrides - playerId)

  def setMyTeam(state: DraftState, league: League, team: Int): Either[String, DraftState] =
    if team < 0 || team >= league.teams then Left(s"team out of range: $team")
    else Right(state.copy(myTeam = team))

  /** Names must have exactly league.teams entries, none blank. */
  def rename(state: DraftState, league: League, names: Vector[String]): Either[String, DraftState] =
    if names.size != league.teams then Left(s"expected ${league.teams} names, got ${names.size}")
    else if names.exists(_.trim.isEmpty) then Left("team names may not be blank")
    else Right(state.copy(teamNames = names))

  /** One RosterLine per league slot, in league.slots order. Assign the team's
    * picks to slots: process picks in DESCENDING effective value (ties by
    * seq), each taking the first open slot in league.slots order whose
    * `eligible` contains the player's pos (starters come before FLEX before
    * bench in espn10, so a third RB lands in FLEX and a fourth on the bench;
    * a first TE lands in TE, a second in FLEX if it is still open).
    * Unfilled slots have None in every optional field. */
  def roster(state: DraftState, league: League, players: Vector[Player], team: Int): Vector[RosterLine] =
    val byId = players.map(p => p.id -> p).toMap
    val teamPicks = state.picks
      .filter(_.team == team)
      .flatMap(pick => byId.get(pick.playerId).map(p => (pick, p)))
      .sortBy((pick, p) => (-state.effectiveValue(p), pick.seq))

    val (assigned, _) = teamPicks.foldLeft((Vector.empty[(Int, Pick, Player)], Set.empty[Int])) {
      case ((acc, taken), (pick, p)) =>
        league.slots.zipWithIndex.find((slot, i) => !taken.contains(i) && slot.eligible.contains(p.pos)) match
          case Some((_, i)) => (acc :+ (i, pick, p), taken + i)
          case None => (acc, taken)
    }
    val bySlotIndex = assigned.map((i, pick, p) => i -> (pick, p)).toMap

    league.slots.zipWithIndex.map { (slot, i) =>
      bySlotIndex.get(i) match
        case Some((pick, p)) => RosterLine(slot.name, Some(p.id), Some(p.name), Some(p.pos), Some(pick.price))
        case None => RosterLine(slot.name, None, None, None, None)
    }

  /** spent, remaining = budget - spent, slotsOpen = rosterSize - filled,
    * maxBid = league.maxBid(spent, filled), perSlot = remaining / slotsOpen
    * (0.0 when none open), roster from above, name from state.teamNames. */
  def teamView(state: DraftState, league: League, players: Vector[Player], team: Int): TeamView =
    val spent = state.spent(team)
    val filled = state.filled(team)
    val slotsOpen = league.rosterSize - filled
    val remaining = league.budget - spent
    val perSlot = if slotsOpen <= 0 then 0.0 else remaining.toDouble / slotsOpen
    TeamView(
      index = team,
      // A total lookup, not a raw index: a DraftState whose teamNames is
      // shorter than league.teams (rename never having been called for it)
      // must not throw out of a pure core function. Every other refusal in
      // this module is a Left; an out-of-range team here falls back to the
      // same "Team N" name DraftState.fresh would have given it.
      name = state.teamNames.lift(team).getOrElse(s"Team ${team + 1}"),
      spent = spent,
      remaining = remaining,
      slotsOpen = slotsOpen,
      maxBid = league.maxBid(spent, filled),
      perSlot = perSlot,
      roster = roster(state, league, players, team),
    )

  def teamViews(state: DraftState, league: League, players: Vector[Player]): Vector[TeamView] =
    Vector.tabulate(league.teams)(team => teamView(state, league, players, team))

  /** My team's roster joined to the plan by slot name: actual = the price
    * paid for the player in that slot, name likewise; planRemaining = sum of
    * `planned` over slots with no player. Returns (lines, planRemaining). */
  def planView(state: DraftState, league: League, players: Vector[Player], plan: Vector[PlanLine]): (Vector[PlanView], Int) =
    val myRoster = roster(state, league, players, state.myTeam).map(l => l.slot -> l).toMap
    val lines = plan.map { line =>
      myRoster.get(line.slot) match
        case Some(rosterLine) if rosterLine.playerId.isDefined =>
          PlanView(line.slot, line.planned, rosterLine.price, rosterLine.name)
        case _ => PlanView(line.slot, line.planned, None, None)
    }
    val planRemaining = lines.filter(_.actual.isEmpty).map(_.planned).sum
    (lines, planRemaining)
