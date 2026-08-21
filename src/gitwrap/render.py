"""Render the nine-section annual report. Built to be screenshot-worthy."""

from __future__ import annotations

from datetime import date

from .stats import Stats

RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"


def fg(n: int, text: str) -> str:
    return f"\033[38;5;{n}m{text}{RESET}"


def bg(n: int, text: str) -> str:
    return f"\033[48;5;{n}m{text}{RESET}"


def bold(text: str) -> str:
    return f"{BOLD}{text}{RESET}"


# Wrapped-style gradient: magenta -> orange -> yellow.
GRADIENT = [213, 207, 203, 209, 214, 220]


def gradient_text(text: str) -> str:
    out = []
    for i, ch in enumerate(text):
        out.append(fg(GRADIENT[i % len(GRADIENT)], ch) if ch != " " else ch)
    return "".join(out)


# 3x5 digit glyphs for the hero number.
DIGITS = {
    "0": ("███", "█ █", "█ █", "█ █", "███"),
    "1": ("  █", "  █", "  █", "  █", "  █"),
    "2": ("███", "  █", "███", "█  ", "███"),
    "3": ("███", "  █", "███", "  █", "███"),
    "4": ("█ █", "█ █", "███", "  █", "  █"),
    "5": ("███", "█  ", "███", "  █", "███"),
    "6": ("███", "█  ", "███", "█ █", "███"),
    "7": ("███", "  █", "  █", "  █", "  █"),
    "8": ("███", "█ █", "███", "█ █", "███"),
    "9": ("███", "█ █", "███", "  █", "███"),
}

LANG_COLORS = {
    "Python": 214, "JavaScript": 220, "TypeScript": 33, "HTML": 203,
    "CSS": 39, "SCSS": 207, "Vue": 48, "Java": 166, "Kotlin": 99,
    "C": 81, "C++": 75, "C#": 114, "Go": 37, "Rust": 180, "Ruby": 196,
    "PHP": 135, "Swift": 202, "Scala": 74, "Dart": 44, "Shell": 46,
    "R": 68, "Objective-C": 51, "Perl": 160, "Lua": 22, "SQL": 110,
    "Markdown": 245, "YAML": 178, "JSON": 149, "XML": 144, "TOML": 245,
    "Other": 240,
}

WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

# Heat color scale, cold -> hot (256-color indexes).
HEAT_COLORS = [234, 236, 60, 97, 133, 169, 205, 212, 219, 226]
HEAT_ASCII = [" ", "·", ":", "-", "~", "+", "*", "#", "%", "@"]


class Sym:
    """Unicode glyphs with an ASCII fallback set."""

    def __init__(self, ascii_only: bool):
        if ascii_only:
            self.block, self.light, self.star = "#", "-", "*"
            self.diamond, self.spark, self.arrow = "<>", "*", "->"
            self.fire, self.dot, self.times, self.dash = "+", ".", "x", "-"
            self.digits = {k: tuple(r.replace("█", "#") for r in v) for k, v in DIGITS.items()}
        else:
            self.block, self.light, self.star = "█", "░", "★"
            self.diamond, self.spark, self.arrow = "◆", "✦", "→"
            self.fire, self.dot, self.times, self.dash = "🔥", "·", "×", "–"
            self.digits = DIGITS


def big_number(n: int, s: Sym, colorize: bool) -> str:
    """Render a number in 3x5 block digits, one color per digit."""
    text = str(n)
    rows = [""] * 5
    for i, ch in enumerate(text):
        glyph = s.digits[ch]
        tone = fg(GRADIENT[i % len(GRADIENT)], "") if colorize else ""
        for r in range(5):
            cell = glyph[r]
            if colorize:
                cell = f"\033[38;5;{GRADIENT[i % len(GRADIENT)]}m{cell}{RESET}"
            rows[r] += cell + " "
    return "\n".join(rows)


def header(num: int, title: str, s: Sym, colorize: bool) -> str:
    deco = fg(213, s.diamond) if colorize else s.diamond
    label = fg(213, title.upper()) if colorize else title.upper()
    return f"{deco} {num} {s.dot} {label}"


# ------------------------------------------------------------- sections ---

def sec_hero(st: Stats, s: Sym, colorize: bool) -> list[str]:
    title = "YOUR YEAR IN CODE"
    lines = ["", gradient_text(title) if colorize else title, ""]
    lines.append(big_number(st.year, s, colorize))
    label = f"{st.total_commits} commits" if st.total_commits != 1 else "1 commit"
    lines.append("")
    lines.append(gradient_text(label) if colorize else label)
    return lines + [""]


def _plural(n: int, word: str) -> str:
    return word if n == 1 else word + "s"


def sec_numbers(st: Stats, s: Sym, colorize: bool) -> list[str]:
    def cell(value: str, label: str) -> str:
        v = fg(220, value) if colorize else value
        l = fg(245, label) if colorize else label
        return f"  {v}  {l}"

    lines = [header(2, "the numbers", s, colorize), ""]
    lines.append(cell(str(st.total_commits), _plural(st.total_commits, "commit")))
    lines.append(cell(str(len(st.active_days)),
                      f"{_plural(len(st.active_days), 'active day')}"))
    lines.append(cell(str(len(st.repos)), f"{_plural(len(st.repos), 'repo')} touched"))
    lines.append(cell(_human(st.lines_changed), f"{_plural(st.lines_changed, 'line')} changed"))
    return lines + [""]


def _human(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 10_000:
        return f"{n / 1000:.0f}k"
    if n >= 1000:
        return f"{n / 1000:.1f}k"
    return str(n)


def sec_heatmap(st: Stats, s: Sym, colorize: bool) -> list[str]:
    peak = max(max(row) for row in st.heatmap) or 1
    levels = len(HEAT_COLORS) - 1

    def cell(count: int) -> str:
        idx = int(count / peak * levels)
        if colorize:
            return bg(HEAT_COLORS[idx], "  ")
        return f"{HEAT_ASCII[idx]} "

    lines = [header(3, "when you commit", s, colorize), ""]
    ruler = "     "
    for hour in range(24):
        ruler += f"{hour:02d} " if hour % 3 == 0 else "   "
    lines.append(fg(245, ruler) if colorize else ruler)
    for wd, row in enumerate(st.heatmap):
        label = fg(245, WEEKDAYS[wd]) if colorize else WEEKDAYS[wd]
        lines.append(f"{label}  " + "".join(cell(c) for c in row))
    low = bg(HEAT_COLORS[0], "  ") + bg(HEAT_COLORS[4], "  ") + bg(HEAT_COLORS[7], "  ") + bg(HEAT_COLORS[-1], "  ") if colorize else "".join(HEAT_ASCII[::3])
    lines.append("")
    lines.append(f"     {low} less {s.arrow} more")
    return lines + [""]


def sec_portrait(st: Stats, s: Sym, colorize: bool) -> list[str]:
    lines = [header(4, "commit personality", s, colorize), ""]
    latest = st.latest_commit_time or "--:--"
    rows = [
        ("latest commit", latest),
        ("night-owl index", f"{st.night_owl_share:.0%} (00:00{s.dash}06:00)"),
        ("weekend share", f"{st.weekend_share:.0%}"),
        ("work-hours share", f"{st.work_hours_share:.0%} (09:00{s.dash}18:00)"),
    ]
    for label, value in rows:
        l = fg(245, f"{label:<18}") if colorize else f"{label:<18}"
        lines.append(f"  {l} {value}")
    lines.append("")
    stars = f"{s.star}  {s.star}  {s.star}" if not colorize else "  ".join(fg(c, s.star) for c in (220, 214, 209))
    lines.append(f"  {stars}")
    title = st.title().upper()
    lines.append(f"  \033[1m\033[38;5;213m{title}{RESET}" if colorize else f"  {title}")
    lines.append(f"  {stars}")
    return lines + [""]


def sec_languages(st: Stats, s: Sym, colorize: bool) -> list[str]:
    lines = [header(5, "languages", s, colorize), ""]
    total_lines = sum(st.languages.values()) or 1
    for rank, (lang, count) in enumerate(st.languages.most_common(5), start=1):
        share = count / total_lines
        bar_width = 22
        filled = round(share * bar_width)
        color = LANG_COLORS.get(lang, 240)
        if colorize:
            bar = fg(color, s.block * filled) + fg(238, s.light * (bar_width - filled))
        else:
            bar = s.block * filled + s.light * (bar_width - filled)
        name = fg(color, f"{lang:<12}") if colorize else f"{lang:<12}"
        lines.append(f"  {rank}. {name} {bar} {share:.1%}")
    return lines + [""]


def sec_streak(st: Stats, s: Sym, colorize: bool) -> list[str]:
    lines = [header(6, "streaks", s, colorize), ""]
    longest = st.longest_streak()
    v = fg(220, str(longest)) if colorize else str(longest)
    lines.append(f"  longest streak      {v} days")
    now = st.on_streak_now()
    mark = (fg(46, f"{s.fire} on streak right now") if colorize
            else "+ on streak right now") if now else "not on a streak"
    lines.append(f"  right now           {mark}")
    return lines + [""]


def sec_highlights(st: Stats, s: Sym, colorize: bool) -> list[str]:
    lines = [header(7, "highlights", s, colorize), ""]
    busiest = st.busiest_day()
    if busiest:
        day, count = busiest
        v = fg(220, f"{count} {_plural(count, 'commit')}") if colorize else f"{count} {_plural(count, 'commit')}"
        lines.append(f"  busiest day         {day.isoformat()}  {v}")
    lines.append("  most-touched files")
    for path, touches in st.files.most_common(3):
        p = fg(39, path) if colorize else path
        lines.append(f"    {s.arrow if not colorize else fg(39, s.arrow)} {p}  {s.times}{touches}")
    return lines + [""]


def sec_words(st: Stats, s: Sym, colorize: bool) -> list[str]:
    lines = [header(8, "your words", s, colorize), ""]
    words = st.words.most_common(10)
    if not words:
        lines.append("  (no notable words this year)")
        return lines + [""]
    cells = []
    for i, (word, count) in enumerate(words):
        tone = GRADIENT[i % len(GRADIENT)]
        w = fg(tone, f"{word}") if colorize else word
        cells.append(f"{w} {s.times}{count}")
    for i in range(0, len(cells), 3):
        lines.append("  " + "   ".join(cells[i:i + 3]))
    return lines + [""]


def sec_closing(st: Stats, s: Sym, colorize: bool) -> list[str]:
    spark = fg(220, s.spark) if colorize else s.spark
    summary = (f"{st.total_commits} {_plural(st.total_commits, 'commit')} {s.dot} "
               f"{len(st.active_days)} {_plural(len(st.active_days), 'day')} {s.dot} "
               f"{len(st.repos)} {_plural(len(st.repos), 'repo')} {s.dot} "
               f"{_human(st.lines_changed)} {_plural(st.lines_changed, 'line')}")
    lines = [
        f"  {spark} {summary}",
        "",
        f"  Keep shipping. Share your year in code! {spark}",
        "",
    ]
    return lines


def render(st: Stats, color: bool = True, ascii_only: bool = False) -> str:
    s = Sym(ascii_only)
    sections = [
        sec_hero, sec_numbers, sec_heatmap, sec_portrait,
        sec_languages, sec_streak, sec_highlights, sec_words, sec_closing,
    ]
    out: list[str] = []
    for sec in sections:
        out.extend(sec(st, s, color))
    return "\n".join(out)
