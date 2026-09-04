package auction

import scala.util.Random

/** Mock auctions. Nine rivals with randomised pricing styles bid against a
  * "me" that follows docs/strategy.md to the letter: the board's MAX, the
  * slot plan, and the hard walk-away caps. Every sale goes to the top bidder
  * at one dollar over the runner-up, and a room runs until all 170 spots
  * are filled. Pure except for the seeded Random that is passed in; the
  * only I/O is the `main` that reads the CSV and prints the report.
  *
  *   scala-cli run . --main-class auction.Mock -- --n 300 --seed 1
  */
object Mock:
  val league: League = League.espn10
  val me = 0

  /** A rival's pricing style: how far above or below the sheet it pays, and
    * how much it discounts bench-only buys. */
  final case class Rival(style: Double, benchFactor: Double, noise: Double)

  /** The strategy's hard caps that the sheet's MAX does not know about. */
  val hardCaps: Map[String, Int] = Map(
    "christian-mccaffrey-rb" -> 47, "ashton-jeanty-rb" -> 24, "derrick-henry-rb" -> 22,
    "kenneth-walker-rb" -> 22, "rashee-rice-wr" -> 24, "davante-adams-wr" -> 15,
    "mike-evans-wr" -> 12, "bo-nix-qb" -> 3, "josh-jacobs-rb" -> 3, "josh-allen-qb" -> 12,
  )
  val tier1Cap: Map[Pos, Int] = Map(Pos.RB -> 68, Pos.WR -> 61, Pos.TE -> 26, Pos.QB -> 12, Pos.DST -> 3, Pos.K -> 1)

  /** No real manager drops $184 on one player: a rival pays at most this
    * share of the budget for anyone, and never more than this share of what
    * it has left. */
  val rivalSingleShare = 0.36
  val rivalRemainingShare = 0.65

  /** How "me" bids. `surplus`: when my money exceeds what the plan still
    * needs, the difference is added to the cap of the starter in front of
    * me. `boost`: multiplies the board's MAX for tier 1-2 players. */
  /** `chase`: starter caps rise to the room's premium when it exceeds the
    * tier cap, so a hot room does not leave the money unspent. */
  final case class Policy(name: String, surplus: Boolean, boost: Double, plan: Vector[PlanLine] = Plan.default, chase: Boolean = false)

  /** The bench money moved into the starters: $1 per bench spot, $20 more
    * across RB1, RB2, WR1, WR2, WR3, TE. Sums to 200. */
  val starterHeavy: Vector[PlanLine] = Vector(
    PlanLine("QB", 10), PlanLine("RB1", 52), PlanLine("RB2", 32), PlanLine("WR1", 37), PlanLine("WR2", 24),
    PlanLine("WR3", 14), PlanLine("TE", 10), PlanLine("FLEX", 12), PlanLine("DST", 1), PlanLine("K", 1),
  ) ++ (1 to 7).map(i => PlanLine(s"BN$i", 1))

  /** $186 on starters, $14 on the bench with $6 held for one upside back. */
  val balanced: Vector[PlanLine] = Vector(
    PlanLine("QB", 10), PlanLine("RB1", 50), PlanLine("RB2", 30), PlanLine("WR1", 35), PlanLine("WR2", 22),
    PlanLine("WR3", 15), PlanLine("TE", 10), PlanLine("FLEX", 12), PlanLine("DST", 1), PlanLine("K", 1),
    PlanLine("BN1", 6), PlanLine("BN2", 3),
  ) ++ (3 to 7).map(i => PlanLine(s"BN$i", 1))

  /** The first plan, kept for comparison: $173 on starters, $27 on the bench. */
  val benchHeavy: Vector[PlanLine] = Vector(
    PlanLine("QB", 10), PlanLine("RB1", 46), PlanLine("RB2", 28), PlanLine("WR1", 33), PlanLine("WR2", 21),
    PlanLine("WR3", 12), PlanLine("TE", 9), PlanLine("FLEX", 11), PlanLine("DST", 2), PlanLine("K", 1),
    PlanLine("BN1", 8), PlanLine("BN2", 6), PlanLine("BN3", 4), PlanLine("BN4", 3), PlanLine("BN5", 3),
    PlanLine("BN6", 2), PlanLine("BN7", 1),
  )

  val policies: Vector[Policy] = Vector(
    Policy("bench-heavy plan, no surplus rule, old T1-2 cap", surplus = false, boost = 1.0 / 1.09, plan = benchHeavy),
    Policy("bench-heavy plan + surplus rule", surplus = true, boost = 1.0, plan = benchHeavy),
    Policy("balanced plan ($14 bench) + surplus rule", surplus = true, boost = 1.0, plan = balanced),
    Policy("starter-heavy plan (the default) + surplus rule", surplus = true, boost = 1.0),
    Policy("default + surplus + chase the room premium", surplus = true, boost = 1.0, chase = true),
  )

  /** `dead` holds players nobody in the room could roster when nominated:
    * they leave the pool, as an unwanted player does in a real room. */
  final case class Room(state: DraftState, rivals: Vector[Rival], log: Vector[(String, Int, Int)], dead: Set[String] = Set.empty)

  def openSlots(state: DraftState, players: Vector[Player], team: Int): Vector[Slot] =
    val lines = Draft.roster(state, league, players, team)
    league.slots.zip(lines).collect { case (slot, line) if line.playerId.isEmpty => slot }

  def starterOpen(open: Vector[Slot], pos: Pos): Boolean =
    open.exists(s => s.eligible.contains(pos) && s.name != "FLEX" && !s.name.startsWith("BN"))

  def anyOpen(open: Vector[Slot], pos: Pos): Boolean = open.exists(_.eligible.contains(pos))

  /** What a rival will pay: sheet value times style times inflation, noised,
    * halved-ish when only a bench spot is open, zero when no spot fits. */
  def rivalMax(rival: Rival, p: Player, state: DraftState, players: Vector[Player], team: Int, inflation: Double, rng: Random): Int =
    val open = openSlots(state, players, team)
    if !anyOpen(open, p.pos) then 0
    else
      val slotFactor = if starterOpen(open, p.pos) then 1.0 else if open.exists(_.name == "FLEX") && Set(Pos.RB, Pos.WR, Pos.TE)(p.pos) then 0.85 else rival.benchFactor
      val noise = 1.0 - rival.noise + rng.nextDouble() * 2 * rival.noise
      val raw = math.round(state.effectiveValue(p) * rival.style * inflation * noise * slotFactor).toInt
      val remaining = league.budget - state.spent(team)
      val ceiling = List(
        league.maxBid(state.spent(team), state.filled(team)),
        math.round(league.budget * rivalSingleShare).toInt,
        math.max(1, math.round(remaining * rivalRemainingShare).toInt),
      ).min
      math.min(math.max(raw, 1), ceiling)

  /** What I will pay: never above the board's MAX, never above the hard caps,
    * never above the plan for the best open slot the player fits (plus a
    * little slack), and only $1 for anything I have no slot for. */
  def myMax(p: Player, state: DraftState, players: Vector[Player], inflation: Double, policy: Policy): Int =
    val open = openSlots(state, players, me)
    if !anyOpen(open, p.pos) then 0
    else
      val value = state.effectiveValue(p)
      val boost = if p.tier <= 2 then policy.boost else 1.0
      val premium = if policy.chase then Pricing.premium(players, state) else 0.0
      val mult = math.max(Pricing.tierCap(p.tier) * boost, premium)
      val boardMax = math.max(1, math.round(value * inflation * mult).toInt)
      val slot = open.find(s => s.eligible.contains(p.pos) && !s.name.startsWith("BN") && s.name != "FLEX")
        .orElse(open.find(s => s.name == "FLEX" && s.eligible.contains(p.pos)))
        .orElse(open.find(_.eligible.contains(p.pos)))
      val planned = slot.flatMap(s => policy.plan.find(_.slot == s.name)).map(_.planned).getOrElse(1)
      val isBench = slot.exists(_.name.startsWith("BN"))
      // What the plan still needs versus what I still have: the surplus is
      // money the plan has no home for, and it goes to the next starter.
      val (_, planLeft) = Draft.planView(state, league, players, policy.plan)
      val surplus = math.max(0, (league.budget - state.spent(me)) - planLeft)
      val planCap =
        if isBench then planned + (if policy.surplus then math.min(surplus, 4) else 0)
        else math.max(planned + 3, math.round(planned * 1.2).toInt) + (if policy.surplus then surplus else 0)
      val tierCap = if p.tier <= 2 then math.round(tier1Cap.getOrElse(p.pos, Int.MaxValue).toDouble * boost).toInt else Int.MaxValue
      val cap = List(boardMax, planCap, tierCap, hardCaps.getOrElse(p.id, Int.MaxValue), league.maxBid(state.spent(me), state.filled(me))).min
      math.max(cap, if planned >= 1 then 1 else 0)

  /** One nomination: everyone's max, top bidder wins at runner-up + 1. */
  def auction(room: Room, players: Vector[Player], p: Player, nominator: Int, rng: Random, policy: Policy): Room =
    val inflation = Pricing.inflation(players, room.state, league)
    val bids = (0 until league.teams).map { t =>
      val b = if t == me then myMax(p, room.state, players, inflation, policy) else rivalMax(room.rivals(t - 1), p, room.state, players, t, inflation, rng)
      (t, b)
    }.filter(_._2 >= 1)
    if bids.isEmpty then room.copy(dead = room.dead + p.id)
    else
      val sorted = bids.sortBy { case (t, b) => (-b, if t == nominator then 0 else 1, rng.nextInt(100)) }
      val (winner, top) = sorted.head
      val second = sorted.drop(1).headOption.map(_._2).getOrElse(0)
      val price = math.max(1, math.min(top, second + 1))
      Draft.sell(room.state, league, players, p.id, winner, price) match
        case Right(s) => room.copy(state = s, log = room.log :+ (p.id, winner, price))
        case Left(_) => room

  /** Who a team throws out: rivals mostly the best available, sometimes a
    * random top-30 name; I follow the board's own Nominate tab. */
  def nominate(room: Room, players: Vector[Player], team: Int, rng: Random): Option[Player] =
    val open = openSlots(room.state, players, team)
    val available = players
      .filter(p => !room.state.isSold(p.id) && !room.dead(p.id) && anyOpen(open, p.pos))
      .sortBy(p => (-room.state.effectiveValue(p), p.rank))
    if available.isEmpty then None
    else if team == me then
      val views = Pricing.playerViews(players, room.state, league)
      val teams = Draft.teamViews(room.state, league, players)
      val (plan, _) = Draft.planView(room.state, league, players, Plan.default)
      Nominate.suggest(views, teams, me, plan, 1).headOption.flatMap(n => available.find(_.id == n.playerId)).orElse(available.headOption)
    else if rng.nextDouble() < 0.7 then available.headOption
    else Some(available(rng.nextInt(math.min(30, available.size))))

  def full(state: DraftState, team: Int): Boolean = state.filled(team) >= league.rosterSize

  def runRoom(players: Vector[Player], seed: Long, roomHeat: Double, policy: Policy = policies.head, noise: Double = 0.15): Room =
    val rng = new Random(seed)
    val rivals = Vector.fill(league.teams - 1)(Rival(style = roomHeat + rng.nextGaussian() * 0.08, benchFactor = 0.35 + rng.nextDouble() * 0.3, noise = noise))
    val start = Room(DraftState.fresh(league, me), rivals, Vector.empty)
    Iterator.iterate((start, 0)) { case (room, turn) =>
      val team = turn % league.teams
      if full(room.state, team) then (room, turn + 1)
      else nominate(room, players, team, rng).map(p => (auction(room, players, p, team, rng, policy), turn + 1)).getOrElse((room, turn + 1))
    }.dropWhile { case (room, turn) => turn < league.teams * league.rosterSize * 3 && (0 until league.teams).exists(t => !full(room.state, t)) }
      .next()._1

  /** `rank` is my starting lineup's place among the ten teams by sheet
    * value (1 = best); `rivalMean` is the average rival lineup. */
  final case class Outcome(seed: Long, heat: Double, spent: Int, starterValue: Int, rank: Int, rivalMean: Double, roster: Vector[RosterLine])

  def outcome(players: Vector[Player], seed: Long, heat: Double, policy: Policy, noise: Double): Outcome =
    val room = runRoom(players, seed, heat, policy, noise)
    val byId = players.map(p => p.id -> p).toMap
    def starterValue(team: Int): Int =
      Draft.roster(room.state, league, players, team).filterNot(_.slot.startsWith("BN")).flatMap(_.playerId).map(id => byId(id).value).sum
    val mine = starterValue(me)
    val rivals = (1 until league.teams).map(starterValue)
    Outcome(seed, heat, room.state.spent(me), mine, 1 + rivals.count(_ > mine), rivals.sum.toDouble / rivals.size, Draft.roster(room.state, league, players, me))

  def report(outs: Vector[Outcome], players: Vector[Player]): String =
    val n = outs.size
    def avg(f: Outcome => Double): Double = outs.map(f).sum / n
    val byId = players.map(p => p.id -> p).toMap
    val slotSpend = league.slots.map(s => s.name -> avg(o => o.roster.find(_.slot == s.name).flatMap(_.price).getOrElse(0).toDouble))
    val t1rb = outs.count(_.roster.exists(r => r.slot == "RB1" && r.playerId.exists(id => byId(id).tier == 1)))
    val t2rb = outs.count(_.roster.exists(r => r.slot == "RB1" && r.playerId.exists(id => byId(id).tier <= 2)))
    val t1wr = outs.count(_.roster.exists(r => r.slot == "WR1" && r.playerId.exists(id => byId(id).tier <= 2)))
    val bigTe = outs.count(_.roster.exists(r => r.slot == "TE" && r.price.exists(_ >= 20)))
    val top1 = outs.count(_.rank == 1)
    val top3 = outs.count(_.rank <= 3)
    val topStarters = outs.flatMap(_.roster.filterNot(_.slot.startsWith("BN")).flatMap(_.name)).groupBy(identity).view.mapValues(_.size).toVector.sortBy(-_._2).take(12)
    val unspent = avg(o => (league.budget - o.spent).toDouble)
    val lines = Vector(
      s"rooms: $n; room heat mean ${"%.2f".format(avg(_.heat))}",
      s"my lineup value avg ${"%.1f".format(avg(_.starterValue))} vs rival mean ${"%.1f".format(avg(_.rivalMean))}; my rank avg ${"%.2f".format(avg(_.rank.toDouble))}; #1 in $top1/$n rooms, top 3 in $top3/$n",
      s"spent avg ${"%.1f".format(avg(_.spent))} of 200 (unspent ${"%.1f".format(unspent)})",
      s"RB1 is a tier-1 back in $t1rb/$n rooms, tier-1 or 2 in $t2rb/$n; WR1 tier-1 or 2 in $t1wr/$n; paid 20+ for a TE in $bigTe/$n",
      "avg $ by slot: " + slotSpend.map { case (s, d) => s"$s ${"%.0f".format(d)}" }.mkString(", "),
      "most common starters: " + topStarters.map { case (nm, c) => s"$nm ($c)" }.mkString(", "),
    )
    lines.mkString("\n")

  def show(o: Outcome): String =
    s"seed ${o.seed} heat ${"%.2f".format(o.heat)} spent ${o.spent} lineup value ${o.starterValue} rank ${o.rank} (rival mean ${"%.0f".format(o.rivalMean)})\n" +
      o.roster.map(r => f"  ${r.slot}%-5s ${r.name.getOrElse("—")}%-24s ${r.price.map(p => s"$$$p").getOrElse("")}").mkString("\n")

  def main(args: Array[String]): Unit =
    val n = args.sliding(2).collectFirst { case Array("--n", v) => v.toInt }.getOrElse(200)
    val seed = args.sliding(2).collectFirst { case Array("--seed", v) => v.toLong }.getOrElse(1L)
    val players = Csv.players(java.nio.file.Files.readString(java.nio.file.Paths.get("data/players.csv")))
    val heats = { val rng = new Random(seed); Vector.fill(n)(1.0 + rng.nextGaussian() * 0.08) }
    val results = for
      noise <- Vector(0.15, 0.30)
      policy <- policies
    yield (noise, policy) -> (0 until n).toVector.map(i => outcome(players, seed * 1000 + i, heats(i), policy, noise))
    for ((noise, policy), outs) <- results do
      println(s"\n#### rival noise ±${(noise * 100).toInt}% — ${policy.name}\n" + report(outs, players))
    val ((_, best), outs) = results.maxBy { case (_, outs) => -outs.map(_.rank).sum }
    println(s"\n==== sample rooms under '${best.name}' ====")
    val sorted = outs.sortBy(_.heat)
    println("\n== tight room ==\n" + show(sorted.head))
    println("\n== typical room ==\n" + show(sorted(n / 2)))
    println("\n== hot room ==\n" + show(sorted.last))
