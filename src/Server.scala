package auction

import upickle.default.*

/** The View assembler: everything GET /api/view returns, built from the pure
  * phase-1 modules. Pure: takes plain data, returns a plain View. */
object Views:
  /** Assemble the View the page renders. players = Pricing.playerViews;
    * teams = Draft.teamViews; (plan, planRemaining) = Draft.planView with
    * Plan.default; nominations = Nominate.suggest(players, teams,
    * state.myTeam, plan); inflation/moneyLeft/valueLeft from Pricing;
    * lastPick = the pick with the highest seq. */
  def assemble(players: Vector[Player], state: DraftState, league: League): View =
    val playerViews = Pricing.playerViews(players, state, league)
    val teamViews = Draft.teamViews(state, league, players)
    val (plan, planRemaining) = Draft.planView(state, league, players, Plan.default)
    val nominations = Nominate.suggest(playerViews, teamViews, state.myTeam, plan)
    View(
      inflation = Pricing.inflation(players, state, league),
      premium = Pricing.premium(players, state),
      moneyLeft = Pricing.moneyLeft(state, league),
      valueLeft = Pricing.valueLeft(players, state, league),
      myTeam = state.myTeam,
      players = playerViews,
      teams = teamViews,
      plan = plan,
      planRemaining = planRemaining,
      nominations = nominations,
      lastPick = state.picks.maxByOption(_.seq),
    )

/** Everything about a running server that can change: the config read once
  * at startup (the player pool, the league, where to persist, which port)
  * and the draft state itself. A mutating route never edits a field; it
  * builds a whole new Snapshot and publishes it in one assignment. */
final case class Snapshot(players: Vector[Player], league: League, statePath: String, port: Int, draft: DraftState)

/** The Cask edge. The only module in this repo allowed to touch a file, a
  * clock, or a socket; every other module is pure data in, data out. */
object Server extends cask.MainRoutes:
  // The edge's one mutable cell. Every route binds it to a local val once,
  // so the whole handler sees one consistent Snapshot. Every mutating route
  // goes through mutate(), which holds a single lock across the read of
  // `current`, the phase-1 call, the persist, and the write to `current`,
  // so two concurrent mutations can never both start from the same draft.
  @volatile var current: Snapshot =
    Snapshot(Vector.empty, League.espn10, "draft.json", 8080, DraftState.fresh(League.espn10))

  override def port: Int = current.port
  override def host: String = "0.0.0.0"

  /** Write `state` to a freshly created temp file beside `path`, then rename
    * it over `path`. `Files.createTempFile` hands back a name unique to this
    * call, so two concurrent saves never race for the same temp file; the
    * rename is atomic, so a crash mid-write never leaves a half-written
    * draft.json, and a rename over an existing file replaces it whole. */
  def persist(path: String, state: DraftState): Unit =
    val target = java.nio.file.Paths.get(path).toAbsolutePath
    val dir = target.getParent
    val tmp = java.nio.file.Files.createTempFile(dir, target.getFileName.toString, ".tmp")
    java.nio.file.Files.writeString(tmp, write(state, indent = 2))
    java.nio.file.Files.move(
      tmp,
      target,
      java.nio.file.StandardCopyOption.REPLACE_EXISTING,
      java.nio.file.StandardCopyOption.ATOMIC_MOVE,
    )

  /** The state at `path`, or None if there is nothing there yet. */
  def load(path: String): Option[DraftState] =
    val p = java.nio.file.Paths.get(path)
    if java.nio.file.Files.exists(p) then Some(read[DraftState](java.nio.file.Files.readString(p)))
    else None

  /** Run one mutating route. Holds a single lock across the read of
    * `current`, the call to `transition`, the persist, and the write back
    * to `current`. Two concurrent mutating requests serialize under this
    * lock, so the second always transitions from the first's result rather
    * than from a draft that is already stale. A Left never touches disk and
    * becomes a 400 with {"error": reason}; a Right persists and answers
    * with the new View. */
  private def mutate(transition: Snapshot => Either[String, DraftState]): cask.Response[ujson.Value] = synchronized {
    transition(current) match
      case Left(reason) => cask.Response(ujson.Obj("error" -> reason), statusCode = 400)
      case Right(newDraft) =>
        val next = current.copy(draft = newDraft)
        persist(next.statePath, next.draft)
        current = next
        cask.Response(writeJs(Views.assemble(next.players, next.draft, next.league)))
  }

  /** The Option[Int] a `value` field of `n` or `null` decodes to, the shape
    * POST /api/override specifies. upickle's derived Reader[Option[Int]]
    * would expect a zero or one element JSON array instead of a bare number
    * or null, so the route reads the raw field itself and this decodes it. */
  def parseOverrideValue(value: ujson.Value): Option[Int] =
    value match
      case ujson.Null => None
      case ujson.Num(n) => Some(n.toInt)
      case other => throw new IllegalArgumentException(s"value must be a number or null, got: $other")

  @cask.get("/")
  def index(): cask.Response[String] =
    cask.Response(Page.shell, headers = Seq("Content-Type" -> "text/html; charset=utf-8"))

  @cask.staticFiles("/static")
  def files() = "static"

  @cask.get("/strategy")
  def strategy(): cask.Response[String] =
    val path = java.nio.file.Paths.get("docs/strategy.md")
    if java.nio.file.Files.exists(path) then
      cask.Response(java.nio.file.Files.readString(path), headers = Seq("Content-Type" -> "text/plain; charset=utf-8"))
    else cask.Response("", statusCode = 404)

  @cask.get("/api/view")
  def view(): ujson.Value =
    val snap = current
    writeJs(Views.assemble(snap.players, snap.draft, snap.league))

  @cask.postJson("/api/sell")
  def sell(playerId: String, team: Int, price: Int): cask.Response[ujson.Value] =
    mutate(snap => Draft.sell(snap.draft, snap.league, snap.players, playerId, team, price))

  @cask.post("/api/undo")
  def undo(): cask.Response[ujson.Value] =
    mutate(snap => Right(Draft.undo(snap.draft)))

  @cask.postJson("/api/override")
  def setOverride(playerId: String, value: ujson.Value): cask.Response[ujson.Value] =
    mutate(snap => Right(Draft.setOverride(snap.draft, playerId, parseOverrideValue(value))))

  @cask.postJson("/api/me")
  def me(team: Int): cask.Response[ujson.Value] =
    mutate(snap => Draft.setMyTeam(snap.draft, snap.league, team))

  @cask.postJson("/api/teams")
  def teamsRoute(names: Vector[String]): cask.Response[ujson.Value] =
    mutate(snap => Draft.rename(snap.draft, snap.league, names))

  @cask.post("/api/reset")
  def reset(): cask.Response[ujson.Value] =
    mutate(snap => Right(DraftState.fresh(snap.league, snap.draft.myTeam)))

  initialize()

  /** Parse --port/--state/--data (each requires a value; anything unmatched,
    * including a flag with no value trailing at the end of args, is a fatal
    * startup error rather than a silently dropped flag), load the CSV and
    * the state file, then hand off to Cask's own main to start Undertow on
    * the overridden port/host. */
  override def main(args: Array[String]): Unit =
    def parse(remaining: List[String], port: Int, statePath: String, dataPath: String): (Int, String, String) =
      remaining match
        case "--port" :: v :: rest => parse(rest, v.toInt, statePath, dataPath)
        case "--state" :: v :: rest => parse(rest, port, v, dataPath)
        case "--data" :: v :: rest => parse(rest, port, statePath, v)
        case Nil => (port, statePath, dataPath)
        case bad :: _ => throw new IllegalArgumentException(s"bad or incomplete argument: $bad")
    val (parsedPort, parsedStatePath, dataPath) = parse(args.toList, 8080, "draft.json", "data/players.csv")
    val players = Csv.players(java.nio.file.Files.readString(java.nio.file.Paths.get(dataPath)))
    val league = League.espn10
    val draft = load(parsedStatePath).getOrElse(DraftState.fresh(league))
    current = Snapshot(players, league, parsedStatePath, parsedPort, draft)
    super.main(args)
