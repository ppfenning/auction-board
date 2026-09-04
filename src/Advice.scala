package auction

import upickle.default.*

/** "What should I pay for this player right now." The strategy's rules,
  * applied for me: the board MAX at the tier cap or the room premium
  * (whichever is higher), the plan for the best open slot the player fits,
  * the surplus rule, the hard walk-aways, and my own max bid. Pure. */
final case class AdviceView(
    player: PlayerView,
    slot: Option[String],
    planned: Int,
    inflation: Double,
    premium: Double,
    surplus: Int,
    boardMax: Int,
    bid: Int,
    reason: String,
) derives ReadWriter

object Advice:
  /** Walk-aways the sheet's MAX does not know about (docs/strategy.md). */
  val hardCaps: Map[String, Int] = Map(
    "christian-mccaffrey-rb" -> 47, "ashton-jeanty-rb" -> 24, "derrick-henry-rb" -> 22,
    "kenneth-walker-rb" -> 22, "rashee-rice-wr" -> 24, "davante-adams-wr" -> 15,
    "mike-evans-wr" -> 12, "bo-nix-qb" -> 3, "josh-jacobs-rb" -> 3, "josh-allen-qb" -> 12,
  )

  /** Tier 1-2 walk-aways by position: the top of what any one bid may be. */
  val tierCaps: Map[Pos, Int] = Map(Pos.RB -> 68, Pos.WR -> 61, Pos.TE -> 26, Pos.QB -> 12, Pos.DST -> 3, Pos.K -> 1)

  /** How much surplus a bench buy may use. */
  val benchSurplus = 4

  def openSlots(state: DraftState, league: League, players: Vector[Player]): Vector[Slot] =
    league.slots.zip(Draft.roster(state, league, players, state.myTeam)).collect { case (slot, line) if line.playerId.isEmpty => slot }

  /** The slot a player would fill for me: a starter slot first, then FLEX, then bench. */
  def slotFor(open: Vector[Slot], pos: Pos): Option[Slot] =
    open.find(s => s.eligible.contains(pos) && s.name != "FLEX" && !s.name.startsWith("BN"))
      .orElse(open.find(s => s.name == "FLEX" && s.eligible.contains(pos)))
      .orElse(open.find(_.eligible.contains(pos)))

  def compute(players: Vector[Player], state: DraftState, league: League, plan: Vector[PlanLine], p: Player): AdviceView =
    val inflation = Pricing.inflation(players, state, league)
    val premium = Pricing.premium(players, state)
    val value = state.effectiveValue(p)
    val view = Pricing.playerViews(players, state, league).find(_.id == p.id).getOrElse(
      PlayerView(p.id, p.rank, p.pos, p.name, p.team, p.tier, p.note, value, Pricing.target(value, inflation), Pricing.maxBid(value, p.tier, inflation), None, None)
    )
    val slot = slotFor(openSlots(state, league, players), p.pos)
    val isBench = slot.exists(_.name.startsWith("BN"))
    val planned = slot.flatMap(s => plan.find(_.slot == s.name)).map(_.planned).getOrElse(0)
    val (_, planLeft) = Draft.planView(state, league, players, plan)
    val remaining = league.budget - state.spent(state.myTeam)
    val surplus = math.max(0, remaining - planLeft)
    val tierCap = Pricing.tierCap(p.tier)
    val chasing = !isBench && premium > tierCap
    val mult = if chasing then premium else tierCap
    val boardMax = math.max(1, math.round(value * inflation * mult).toInt)
    val myMax = league.maxBid(state.spent(state.myTeam), state.filled(state.myTeam))
    val walk = (if p.tier <= 2 then tierCaps.get(p.pos) else None).toList ++ hardCaps.get(p.id).toList
    val (bid, reason) = slot match
      case None => (0, s"no open slot for ${p.pos}")
      case Some(s) if isBench =>
        val b = List(1 + math.min(surplus, benchSurplus), boardMax, myMax).min
        (b, s"bench (${s.name}): $$1 plus up to $$$benchSurplus of surplus ($$$surplus)")
      case Some(s) if view.soldTo.nonEmpty => (0, "already sold")
      case Some(s) =>
        val planCap = math.max(planned + 3, math.round(planned * 1.2).toInt) + surplus
        val allowed = boardMax + surplus
        val b = (List(allowed, planCap, myMax) ++ walk).min
        val why = Vector(
          s"${s.name} planned $$$planned",
          s"MAX $$$boardMax = $$$value x ${"%.2f".format(inflation)} x ${"%.2f".format(mult)}" + (if chasing then " (room premium)" else ""),
          if surplus > 0 then s"+$$$surplus surplus" else "no surplus",
          walk.headOption.map(w => s"walk-away $$$w").getOrElse(""),
          if b == myMax then "capped by my max bid" else "",
        ).filter(_.nonEmpty).mkString("; ")
        (math.max(0, b), why)
    AdviceView(view, slot.map(_.name), planned, inflation, premium, surplus, boardMax, bid, reason)
