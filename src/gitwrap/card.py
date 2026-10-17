"""Render the report as a single self-contained HTML card for sharing.

No external assets: fonts, styles and the heatmap are all inline, so the file
opens offline and survives being emailed or dropped into a chat.
"""

from __future__ import annotations

from html import escape

from .render import WEEKDAYS, _human, _plural
from .stats import Stats

# Hex counterparts of render.LANG_COLORS, tuned to read on light and dark.
LANG_HEX = {
    "Python": "#3572A5", "JavaScript": "#E3B341", "TypeScript": "#3178C6",
    "HTML": "#E34C26", "CSS": "#663399", "SCSS": "#C6538C", "Vue": "#41B883",
    "Java": "#B07219", "Kotlin": "#A97BFF", "C": "#6E7781", "C++": "#F34B7D",
    "C#": "#178600", "Go": "#00ADD8", "Rust": "#DEA584", "Ruby": "#CC342D",
    "PHP": "#4F5D95", "Swift": "#F05138", "Scala": "#DC322F", "Dart": "#00B4AB",
    "Shell": "#89E051", "R": "#198CE7", "Objective-C": "#438EFF",
    "Perl": "#0298C3", "Lua": "#000080", "SQL": "#E38C00",
    "Markdown": "#8B949E", "YAML": "#CB171E", "JSON": "#8BAE4F",
    "XML": "#0060AC", "TOML": "#9C4221", "Other": "#8B949E",
}

HEAT_LEVELS = 5  # level 0 is "no commits"; 1..4 scale with the peak hour

CSS = """
:root {
  --bg: #f4f1fa; --card: #ffffff; --ink: #1b1530; --muted: #6b6383;
  --line: #e6e1f0; --accent: #d6266f; --accent-2: #f2711c; --accent-3: #e8a20c;
  --heat-0: #eeeaf5; --heat-1: #f6c6d9; --heat-2: #ee8db3; --heat-3: #df4f86; --heat-4: #b3124f;
  --track: #eeeaf5;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #0d0a16; --card: #17121f; --ink: #f5f1ff; --muted: #a49cba;
    --line: #2a2337; --accent: #ff4f9a; --accent-2: #ff8a3d; --accent-3: #ffc53d;
    --heat-0: #221b2e; --heat-1: #4a2440; --heat-2: #7f2c5c; --heat-3: #c43d7c; --heat-4: #ff5fa2;
    --track: #2a2337;
  }
}
:root[data-theme="dark"] {
  --bg: #0d0a16; --card: #17121f; --ink: #f5f1ff; --muted: #a49cba;
  --line: #2a2337; --accent: #ff4f9a; --accent-2: #ff8a3d; --accent-3: #ffc53d;
  --heat-0: #221b2e; --heat-1: #4a2440; --heat-2: #7f2c5c; --heat-3: #c43d7c; --heat-4: #ff5fa2;
  --track: #2a2337;
}
* { box-sizing: border-box; }
html, body { margin: 0; }
body {
  background: var(--bg); color: var(--ink);
  font: 15px/1.5 ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto,
        "PingFang SC", "Noto Sans CJK SC", sans-serif;
  padding: 32px 16px 48px;
}
.card {
  max-width: 560px; margin: 0 auto; background: var(--card);
  border: 1px solid var(--line); border-radius: 24px; overflow: hidden;
}
.hero {
  padding: 32px 28px 28px; color: #fff;
  background: linear-gradient(135deg, #c2185b 0%, #e8484f 45%, #f39c12 100%);
}
.eyebrow { font-size: 12px; letter-spacing: .16em; text-transform: uppercase; opacity: .85; margin: 0; }
.year { font-size: clamp(64px, 20vw, 112px); font-weight: 800; line-height: 1; margin: 8px 0 4px;
        letter-spacing: -.04em; font-variant-numeric: tabular-nums; }
.hero-sub { font-size: 18px; font-weight: 600; margin: 0; }
.title-badge {
  display: inline-block; margin-top: 20px; padding: 6px 14px; border-radius: 999px;
  background: rgba(255,255,255,.18); border: 1px solid rgba(255,255,255,.35);
  font-weight: 700; letter-spacing: .06em; text-transform: uppercase; font-size: 13px;
}
section { padding: 24px 28px; border-top: 1px solid var(--line); }
section:first-of-type { border-top: 0; }
h2 { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted);
     margin: 0 0 14px; font-weight: 700; }
.numbers { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.num b { display: block; font-size: 30px; font-weight: 800; line-height: 1.1;
         font-variant-numeric: tabular-nums; }
.num span { color: var(--muted); font-size: 13px; }
.heat { display: grid; grid-template-columns: 30px repeat(24, 1fr); gap: 2px; align-items: center; }
.heat .d { font-size: 10px; color: var(--muted); }
.heat .h { font-size: 9px; color: var(--muted); text-align: left; grid-column: span 3; }
.heat i { display: block; aspect-ratio: 1; border-radius: 2px; background: var(--heat-0); }
.heat i.l1 { background: var(--heat-1); } .heat i.l2 { background: var(--heat-2); }
.heat i.l3 { background: var(--heat-3); } .heat i.l4 { background: var(--heat-4); }
.legend { display: flex; align-items: center; gap: 4px; margin-top: 10px; font-size: 11px; color: var(--muted); }
.legend i { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.rows { display: grid; gap: 8px; margin: 0; }
.rows div { display: flex; justify-content: space-between; gap: 12px; }
.rows dt { color: var(--muted); } .rows dd { margin: 0; font-weight: 600; text-align: right;
                                             font-variant-numeric: tabular-nums; }
.lang { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; margin-bottom: 12px; }
.lang:last-child { margin-bottom: 0; }
.lang .name { font-weight: 600; } .lang .pct { color: var(--muted); font-variant-numeric: tabular-nums; }
.bar { grid-column: 1 / -1; height: 8px; border-radius: 999px; background: var(--track); overflow: hidden; }
.bar span { display: block; height: 100%; border-radius: 999px; }
.files { list-style: none; padding: 0; margin: 12px 0 0; display: grid; gap: 6px; }
.files li { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.files code { font: 13px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              overflow-wrap: anywhere; }
.files span { color: var(--muted); white-space: nowrap; }
.words { display: flex; flex-wrap: wrap; gap: 8px; }
.words span { padding: 4px 12px; border-radius: 999px; border: 1px solid var(--line); font-weight: 600; }
.words small { color: var(--muted); font-weight: 400; margin-left: 4px; }
.words .w0 { color: var(--accent); } .words .w1 { color: var(--accent-2); } .words .w2 { color: var(--accent-3); }
.empty { color: var(--muted); margin: 0; }
footer { padding: 20px 28px 28px; border-top: 1px solid var(--line); color: var(--muted); font-size: 13px; }
footer strong { color: var(--ink); }
@media (max-width: 420px) {
  section, .hero, footer { padding-left: 20px; padding-right: 20px; }
  .heat { grid-template-columns: 26px repeat(24, 1fr); gap: 1px; }
}
@media print { body { background: #fff; padding: 0; } .card { border: 0; } }
"""


def _level(count: int, peak: int) -> int:
    if count <= 0 or peak <= 0:
        return 0
    return min(HEAT_LEVELS - 1, 1 + int((count - 1) / peak * (HEAT_LEVELS - 1)))


def _hero(st: Stats) -> str:
    n = st.total_commits
    return (
        '<header class="hero">'
        '<p class="eyebrow">Your year in code</p>'
        f'<div class="year">{st.year}</div>'
        f'<p class="hero-sub">{n} {_plural(n, "commit")}</p>'
        f'<span class="title-badge">★ {escape(st.title())}</span>'
        '</header>'
    )


def _numbers(st: Stats) -> str:
    days, repos = len(st.active_days), len(st.repos)
    cells = [
        (str(st.total_commits), _plural(st.total_commits, "commit")),
        (str(days), _plural(days, "active day")),
        (str(repos), f"{_plural(repos, 'repo')} touched"),
        (_human(st.lines_changed), f"{_plural(st.lines_changed, 'line')} changed"),
    ]
    body = "".join(f'<div class="num"><b>{v}</b><span>{label}</span></div>' for v, label in cells)
    return f'<section><h2>The numbers</h2><div class="numbers">{body}</div></section>'


def _heatmap(st: Stats) -> str:
    peak = max(max(row) for row in st.heatmap)
    parts = ['<span></span>']
    parts += [f'<span class="h">{h:02d}</span>' for h in range(0, 24, 3)]
    for wd, row in enumerate(st.heatmap):
        parts.append(f'<span class="d">{WEEKDAYS[wd]}</span>')
        for hour, count in enumerate(row):
            tip = f"{WEEKDAYS[wd]} {hour:02d}:00 · {count} {_plural(count, 'commit')}"
            parts.append(f'<i class="l{_level(count, peak)}" title="{tip}"></i>')
    legend = "".join(f'<i style="background:var(--heat-{i})"></i>' for i in range(HEAT_LEVELS))
    return (
        '<section><h2>When you commit</h2>'
        f'<div class="heat" role="img" aria-label="Commits by weekday and hour">{"".join(parts)}</div>'
        f'<div class="legend">less {legend} more</div></section>'
    )


def _portrait(st: Stats) -> str:
    rows = [
        ("Latest commit", st.latest_commit_time or "--:--"),
        ("Night-owl index (00–06)", f"{st.night_owl_share:.0%}"),
        ("Weekend share", f"{st.weekend_share:.0%}"),
        ("Work-hours share (09–18)", f"{st.work_hours_share:.0%}"),
        ("Longest streak", f"{st.longest_streak()} {_plural(st.longest_streak(), 'day')}"),
        ("Right now", "🔥 on a streak" if st.on_streak_now() else "not on a streak"),
    ]
    body = "".join(f"<div><dt>{k}</dt><dd>{v}</dd></div>" for k, v in rows)
    return f'<section><h2>Commit personality</h2><dl class="rows">{body}</dl></section>'


def _languages(st: Stats) -> str:
    top = st.languages.most_common(5)
    if not top:
        return '<section><h2>Languages</h2><p class="empty">No line changes this year.</p></section>'
    total = sum(st.languages.values()) or 1
    body = []
    for lang, count in top:
        share = count / total
        color = LANG_HEX.get(lang, LANG_HEX["Other"])
        body.append(
            f'<div class="lang"><span class="name">{escape(lang)}</span>'
            f'<span class="pct">{share:.1%}</span>'
            f'<div class="bar"><span style="width:{share * 100:.1f}%;background:{color}"></span></div></div>'
        )
    return f'<section><h2>Languages</h2>{"".join(body)}</section>'


def _highlights(st: Stats) -> str:
    rows = ""
    busiest = st.busiest_day()
    if busiest:
        day, count = busiest
        rows = (f'<dl class="rows"><div><dt>Busiest day</dt>'
                f'<dd>{day.isoformat()} · {count} {_plural(count, "commit")}</dd></div></dl>')
    files = "".join(
        f"<li><code>{escape(path)}</code><span>×{touches}</span></li>"
        for path, touches in st.files.most_common(3)
    )
    files = f'<ul class="files">{files}</ul>' if files else ""
    return f"<section><h2>Highlights</h2>{rows}{files}</section>"


def _words(st: Stats) -> str:
    words = st.words.most_common(10)
    if not words:
        return '<section><h2>Your words</h2><p class="empty">No notable words this year.</p></section>'
    chips = "".join(
        f'<span class="w{i % 3}">{escape(w)}<small>×{n}</small></span>'
        for i, (w, n) in enumerate(words)
    )
    return f'<section><h2>Your words</h2><div class="words">{chips}</div></section>'


def _footer(st: Stats) -> str:
    days, repos = len(st.active_days), len(st.repos)
    summary = (f"{st.total_commits} {_plural(st.total_commits, 'commit')} · "
               f"{days} {_plural(days, 'day')} · {repos} {_plural(repos, 'repo')} · "
               f"{_human(st.lines_changed)} {_plural(st.lines_changed, 'line')}")
    return (f"<footer><strong>✦ {summary}</strong><br>"
            "Keep shipping. Made with gitwrap.</footer>")


def render_html(st: Stats) -> str:
    """Return a complete, standalone HTML document for one year's stats."""
    body = "".join([
        _hero(st), _numbers(st), _heatmap(st), _portrait(st),
        _languages(st), _highlights(st), _words(st), _footer(st),
    ])
    return (
        "<!doctype html>\n"
        '<html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        f"<title>Year in Code {st.year}</title>"
        f"<style>{CSS}</style></head>"
        f'<body><main class="card">{body}</main></body></html>\n'
    )
