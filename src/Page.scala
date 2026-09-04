package auction

import scalatags.Text.all.*
import scalatags.Text.tags2.{title as titleTag, nav}

/** The HTML shell. Pure: a function of nothing, returning a string. The page
  * is rendered ONCE; every number on it comes from `static/app.js` calling
  * GET /api/view and filling the containers by id. Keep the ids stable; the
  * script is written against them. */
object Page:
  val css: String = """
    :root{--bg:#0f1115;--panel:#181b22;--line:#2a2f3a;--fg:#e6e8ee;--dim:#8b93a7;--acc:#5ad1a5;--warn:#f0b35c;--bad:#ef6f6c;--me:#4f8cff}
    *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.4 system-ui,sans-serif}
    header{display:flex;gap:16px;align-items:center;padding:8px 14px;border-bottom:1px solid var(--line);position:sticky;top:0;background:var(--bg);z-index:2}
    header .stat{display:flex;flex-direction:column;font-size:12px;color:var(--dim)}header .stat b{font-size:18px;color:var(--fg)}
    nav button{background:none;border:1px solid var(--line);color:var(--fg);padding:6px 12px;margin-right:4px;border-radius:6px;cursor:pointer}
    nav button.active{border-color:var(--acc);color:var(--acc)}
    main{padding:12px 14px}section[hidden]{display:none}
    table{width:100%;border-collapse:collapse}th,td{padding:4px 6px;border-bottom:1px solid var(--line);text-align:left;white-space:nowrap}
    th{color:var(--dim);font-weight:600;cursor:pointer;position:sticky;top:52px;background:var(--bg)}
    tr.sold td{color:var(--dim);text-decoration:line-through}tr.mine td{color:var(--me);text-decoration:none}
    tr.t1 td.tier,tr.t2 td.tier{color:var(--acc)}td.note{white-space:normal;max-width:360px;color:var(--warn);font-size:12px}
    .controls{display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap}.controls input,.controls select{background:var(--panel);color:var(--fg);border:1px solid var(--line);padding:6px 8px;border-radius:6px}
    .sell{display:inline-flex;gap:4px}.sell input{width:52px;background:var(--panel);color:var(--fg);border:1px solid var(--line);padding:2px 4px;border-radius:4px}
    button.act{background:var(--acc);color:#000;border:0;padding:3px 8px;border-radius:4px;cursor:pointer}button.act.warn{background:var(--warn)}button.act.bad{background:var(--bad)}
    .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:10px}.card{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:10px}
    .card h3{margin:0 0 6px;font-size:14px}.card.me{border-color:var(--me)}.over{color:var(--bad)}.under{color:var(--acc)}
    .prose{max-width:900px}.prose table{width:auto}.prose h2{margin-top:22px}.prose code{background:var(--panel);padding:1px 4px;border-radius:3px}
    .nom{padding:8px;border-bottom:1px solid var(--line)}.nom b{font-size:15px}.nom .why{color:var(--dim)}
  """

  def shell: String =
    "<!doctype html>" + html(
      head(
        meta(charset := "utf-8"),
        meta(name := "viewport", content := "width=device-width, initial-scale=1"),
        titleTag("Auction Board"),
        tag("style")(raw(css)),
      ),
      body(
        header(
          div(cls := "stat")("inflation", b(id := "st-inflation")("1.00")),
          div(cls := "stat")("my $", b(id := "st-remaining")("200")),
          div(cls := "stat")("my max bid", b(id := "st-maxbid")("185")),
          div(cls := "stat")("plan left", b(id := "st-plan")("200")),
          div(cls := "stat")("open slots", b(id := "st-slots")("16")),
          div(cls := "stat")("room $ left", b(id := "st-room")("2000")),
          nav(
            button(cls := "active", attr("data-tab") := "board")("Board"),
            button(attr("data-tab") := "teams")("Teams"),
            button(attr("data-tab") := "plan")("My Plan"),
            button(attr("data-tab") := "nominate")("Nominate"),
            button(attr("data-tab") := "strategy")("Strategy"),
          ),
          button(id := "undo", cls := "act warn")("Undo last"),
        ),
        tag("main")(
          tag("section")(id := "tab-board")(
            div(cls := "controls")(
              input(id := "search", placeholder := "type a name…", autofocus),
              select(id := "pos-filter")(
                option(value := "")("All"),
                Pos.values.toSeq.map(p => option(value := p.toString)(p.toString)),
              ),
              label(input(`type` := "checkbox", id := "hide-sold"), " hide sold"),
            ),
            table(id := "board")(
              thead(tr(th("#"), th("Pos"), th("Player"), th("Team"), th("Tier"), th("Value"), th("Target"), th("MAX"), th("Sold"), th("Note"))),
              tbody(id := "board-body"),
            ),
          ),
          tag("section")(id := "tab-teams", attr("hidden") := "hidden")(
            div(cls := "controls")(
              label("I am ", select(id := "my-team")),
              button(id := "rename", cls := "act")("Rename teams"),
              button(id := "reset", cls := "act bad")("Reset draft"),
            ),
            div(id := "teams", cls := "grid"),
          ),
          tag("section")(id := "tab-plan", attr("hidden") := "hidden")(
            table(id := "plan")(thead(tr(th("Slot"), th("Planned"), th("Actual"), th("Player"), th("Δ"))), tbody(id := "plan-body")),
          ),
          tag("section")(id := "tab-nominate", attr("hidden") := "hidden")(div(id := "nominations")),
          tag("section")(id := "tab-strategy", attr("hidden") := "hidden", cls := "prose")(div(id := "strategy")),
        ),
        script(src := "/static/app.js"),
      ),
    ).render
