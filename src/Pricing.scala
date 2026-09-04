package auction

/** Inflation and the per-player numbers it drives: target, max, and the
  * PlayerView the page renders. Pure: nothing here touches a file, a clock,
  * or a socket. Every function takes the players, the draft state, and the
  * league as plain data and returns plain data. */

object Pricing:
  /** Dollars nobody has spent yet, across the whole room. */
  def moneyLeft(state: DraftState, league: League): Int =
    (0 until league.teams).map(t => league.budget - state.spent(t)).sum

  /** Roster spots nobody has filled yet, across the whole room. */
  def slotsOpen(state: DraftState, league: League): Int =
    (0 until league.teams).map(t => league.rosterSize - state.filled(t)).sum

  /** Sum of effective values of the top `slotsOpen` UNSOLD players by
    * effective value (ties by rank). Every player counts at least $1. */
  def valueLeft(players: Vector[Player], state: DraftState, league: League): Int =
    players
      .filterNot(p => state.isSold(p.id))
      .sortBy(p => (-state.effectiveValue(p), p.rank))
      .take(slotsOpen(state, league))
      .map(p => math.max(1, state.effectiveValue(p)))
      .sum

  /** moneyLeft / valueLeft, 1.0 when valueLeft is 0, clamped to [0.5, 2.0],
    * rounded to two decimals. */
  def inflation(players: Vector[Player], state: DraftState, league: League): Double =
    val value = valueLeft(players, state, league)
    val raw = if value == 0 then 1.0 else moneyLeft(state, league).toDouble / value
    val clamped = math.min(2.0, math.max(0.5, raw))
    math.round(clamped * 100).toDouble / 100

  /** 1.20 for tiers 1-2, 1.10 for tier 3, 1.00 for tiers 4-5, 0.90 otherwise.
    * Raised from 1.10/1.05 after the mock rooms (src/Mock.scala): against
    * nine noisy bidders a cap at value+10% loses nearly every star and
    * leaves the money unspent, which cost more rooms than overpaying did. */
  def tierCap(tier: Int): Double =
    if tier <= 2 then 1.20
    else if tier == 3 then 1.10
    else if tier <= 5 then 1.00
    else 0.90

  /** What the room has actually paid over the sheet so far: the sum of
    * prices divided by the sum of the sold players' effective values, 1.0
    * before the first sale, clamped to [0.5, 2.0], two decimals. Above the
    * tier cap it means the sheet is wrong for THIS room, and a bidder who
    * keeps the sheet's caps ends with money nobody will take. */
  def premium(players: Vector[Player], state: DraftState): Double =
    val byId = players.map(p => p.id -> p).toMap
    val sold = state.picks.flatMap(pick => byId.get(pick.playerId).map(p => (pick.price, state.effectiveValue(p))))
    val value = sold.map(_._2).sum
    val raw = if value == 0 then 1.0 else sold.map(_._1).sum.toDouble / value
    math.round(math.min(2.0, math.max(0.5, raw)) * 100) / 100.0

  /** max(1, round(value * inflation * 0.90)) */
  def target(value: Int, inflation: Double): Int =
    math.max(1, math.round(value * inflation * 0.90).toInt)

  /** max(1, round(value * inflation * tierCap(tier))) */
  def maxBid(value: Int, tier: Int, inflation: Double): Int =
    math.max(1, math.round(value * inflation * tierCap(tier)).toInt)

  /** One PlayerView per player: value = state.effectiveValue(p); target and
    * max from the functions above using inflation(players, state, league);
    * soldTo/price from the pick that sold the player, else None. Assumes at
    * most one pick per player, the same assumption DraftState.spent and
    * DraftState.filled make by summing every pick without deduplication;
    * reconciling a corrected or duplicate sale is roster bookkeeping for
    * Draft.scala, a sibling task, not a display concern here. Sorted by
    * effective value descending, then rank ascending. */
  def playerViews(players: Vector[Player], state: DraftState, league: League): Vector[PlayerView] =
    val infl = inflation(players, state, league)
    players
      .map { p =>
        val value = state.effectiveValue(p)
        val pick = state.picks.find(_.playerId == p.id)
        PlayerView(
          id = p.id,
          rank = p.rank,
          pos = p.pos,
          name = p.name,
          team = p.team,
          tier = p.tier,
          note = p.note,
          value = value,
          target = target(value, infl),
          max = maxBid(value, p.tier, infl),
          soldTo = pick.map(_.team),
          price = pick.map(_.price),
        )
      }
      .sortBy(v => (-v.value, v.rank))
