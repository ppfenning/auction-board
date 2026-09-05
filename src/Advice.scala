package auction

import upickle.default.*

/** "What should I pay for this player right now." The strategy's rules,
  * applied for me: the board MAX at the tier cap, or at the room premium
  * when the room is hot; plus my surplus for a starter; never above the
  * walk-away for the player's tier and position, a named hard cap, or my
  * own max bid. The plan for the slot is reported, never enforced: the
  * strategy calls it a floor for starters, not a ceiling. Pure. */
final case class AdviceView(
    player: PlayerView,
    slot: Option[String],
    planned: Int,
    inflation: Double,
    premium: Double,
    surplus: Int,
    boardMax: Int,
    walkAway: Int,
    bid: Int,
    reason: String,
) derives ReadWriter

object Advice:
  /** Named walk-aways (docs/strategy.md, "Cut lines on price"). */
  val hardCaps: Map[String, Int] = Map(
    "christian-mccaffrey-rb" -> 47, "jonathan-taylor-rb" -> 50, "ja-marr-chase-wr" -> 60,
    "jaxon-smith-njigba-wr" -> 56, "amon-ra-st-brown-wr" -> 54,
    "ashton-jeanty-rb" -> 24, "derrick-henry-rb" -> 22, "kenneth-walker-rb" -> 22,
    "rashee-rice-wr" -> 24, "davante-adams-wr" -> 15, "mike-evans-wr" -> 12,
    "bo-nix-qb" -> 3, "josh-jacobs-rb" -> 3, "josh-allen-qb" -> 12,
    "colston-loveland-te" -> 14, "tyler-warren-te" -> 11, "harold-fannin-jr-te" -> 11, "kyle-pitts-te" -> 9,
  )

  /** Tier 1-2 walk-aways by position. */
  val tierCaps: Map[Pos, Int] = Map(Pos.RB -> 68, Pos.WR -> 61, Pos.TE -> 26, Pos.QB -> 12, Pos.DST -> 3, Pos.K -> 1)

  /** The room premium above which starter caps follow the room, not the sheet. */
  val chaseAbove = 1.20

  /** How much surplus a bench buy may use. */
  val benchSurplus = 4

  /** The most any one bid may be for this player, whatever the surplus says:
    * a named cap first; then the position's tier walk-away (tier 1-2), the
    * tier-3 line ($39 for RB and WR, the strategy's "zone where the room
    * overpays most"), "any QB: walk at $12", "any TE beyond the named: $6",
    * D/ST $3, K $1; and for the tiers the strategy leaves unnamed, value
    * plus 30% plus $2, so surplus can lift a bid but not double it. */
  def walkAway(p: Player, value: Int): Int =
    hardCaps.get(p.id).getOrElse {
      p.pos match
        case Pos.QB => tierCaps(Pos.QB)
        case Pos.DST => tierCaps(Pos.DST)
        case Pos.K => tierCaps(Pos.K)
        case Pos.TE => if p.tier <= 2 then tierCaps(Pos.TE) else 6
        case Pos.RB | Pos.WR =>
          if p.tier <= 2 then tierCaps(p.pos)
          else if p.tier == 3 then 39
          else math.round(value * 1.3).toInt + 2
    }

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
    val sold = state.picks.find(_.playerId == p.id)
    val view = PlayerView(
      p.id, p.rank, p.pos, p.name, p.team, p.tier, p.note, value,
      Pricing.target(value, inflation), Pricing.maxBid(value, p.tier, inflation), sold.map(_.team), sold.map(_.price),
    )
    val slot = slotFor(openSlots(state, league, players), p.pos)
    val isBench = slot.exists(_.name.startsWith("BN"))
    val planned = slot.flatMap(s => plan.find(_.slot == s.name)).map(_.planned).getOrElse(0)
    val (_, planLeft) = Draft.planView(state, league, players, plan)
    val remaining = league.budget - state.spent(state.myTeam)
    val surplus = math.max(0, remaining - planLeft)
    val tierCap = Pricing.tierCap(p.tier)
    val chasing = !isBench && premium > chaseAbove && premium > tierCap
    val mult = if chasing then premium else tierCap
    val boardMax = math.max(1, math.round(value * inflation * mult).toInt)
    val myMax = league.maxBid(state.spent(state.myTeam), state.filled(state.myTeam))
    val walk = walkAway(p, value)
    val (bid, reason) = (sold, slot) match
      case (Some(_), _) => (0, "already sold")
      case (_, None) => (0, s"no open slot for ${p.pos}")
      case (_, Some(s)) if isBench =>
        val b = List(1 + math.min(surplus, benchSurplus), boardMax, walk, myMax).min
        (math.max(0, b), s"bench (${s.name}): $$1 plus up to $$$benchSurplus of surplus ($$$surplus), never above MAX $$$boardMax")
      case (_, Some(s)) =>
        val allowed = boardMax + surplus
        val b = List(allowed, walk, myMax).min
        val why = Vector(
          s"${s.name} planned $$$planned",
          s"MAX $$$boardMax = $$$value x ${"%.2f".format(inflation)} x ${"%.2f".format(mult)}" + (if chasing then " (room premium)" else ""),
          if surplus > 0 then s"+$$$surplus surplus" else "no surplus",
          s"walk-away $$$walk",
          if b == myMax && myMax < allowed then "capped by my max bid" else "",
        ).filter(_.nonEmpty).mkString("; ")
        (math.max(0, b), why)
    AdviceView(view, slot.map(_.name), planned, inflation, premium, surplus, boardMax, walk, bid, reason)
