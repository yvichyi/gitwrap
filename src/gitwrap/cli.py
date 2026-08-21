"""Command-line entry point for gitwrap."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import __version__
from .collector import collect, default_email
from .render import render
from .stats import build, pick_year


def enable_windows_vt() -> None:
    if sys.platform == "win32":
        import ctypes
        kernel32 = ctypes.windll.kernel32  # type: ignore[attr-defined]
        handle = kernel32.GetStdHandle(-11)  # STD_OUTPUT_HANDLE
        mode = ctypes.c_uint32()
        if kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
            kernel32.SetConsoleMode(handle, mode.value | 0x0004)  # ENABLE_VIRTUAL_TERMINAL_PROCESSING


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="gitwrap",
        description="Your year in code — an annual report from your git history.",
    )
    parser.add_argument("--year", type=int, default=None,
                        help="report year (default: last year, "
                             "falling back to the most recent year with commits)")
    parser.add_argument("--author", type=str, default=None, metavar="EMAILS",
                        help="comma-separated author emails (default: each repo's user.email)")
    parser.add_argument(
        "dirs", nargs="*", type=Path, default=[],
        help="repository or directory to scan (positional form of --path)",
    )
    parser.add_argument("--path", action="append", type=Path, default=None, dest="paths",
                        help="repository or directory to scan (repeatable; default: cwd)")
    parser.add_argument("--depth", type=int, default=4,
                        help="max depth when searching directories for repos (default: 4)")
    parser.add_argument("--json", action="store_true", dest="as_json",
                        help="emit machine-readable JSON instead of the report")
    parser.add_argument("--no-color", action="store_true",
                        help="disable colored output")
    parser.add_argument("--ascii", action="store_true",
                        help="ASCII-only glyphs for legacy consoles")
    parser.add_argument("-V", "--version", action="version",
                        version=f"%(prog)s {__version__}")
    return parser


def filter_authors(commits, repo_emails: dict[str, str], wanted: set[str] | None):
    """Keep commits by the wanted identities.

    wanted=None means "each repo's own default email"; commits from repos
    without a configured email are kept as-is.
    """
    kept = []
    for c in commits:
        if wanted is not None:
            if c.email in wanted:
                kept.append(c)
        elif c.email == repo_emails.get(c.repo):
            kept.append(c)
        elif repo_emails.get(c.repo) is None:
            kept.append(c)
    return kept


def stats_to_json(st) -> dict:
    busiest = st.busiest_day()
    return {
        "year": st.year,
        "total_commits": st.total_commits,
        "active_days": len(st.active_days),
        "repos": sorted(st.repos),
        "lines_changed": st.lines_changed,
        "latest_commit_time": st.latest_commit_time,
        "night_owl_share": round(st.night_owl_share, 4),
        "early_bird_share": round(st.early_bird_share, 4),
        "work_hours_share": round(st.work_hours_share, 4),
        "weekend_share": round(st.weekend_share, 4),
        "title": st.title(),
        "longest_streak_days": st.longest_streak(),
        "on_streak_now": st.on_streak_now(),
        "busiest_day": busiest[0].isoformat() if busiest else None,
        "busiest_day_commits": busiest[1] if busiest else 0,
        "top_files": st.files.most_common(3),
        "top_languages": st.languages.most_common(5),
        "top_words": st.words.most_common(10),
    }


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    roots = list(args.dirs) + list(args.paths or [])
    roots = roots or [Path.cwd()]
    missing = [str(p) for p in roots if not p.is_dir()]
    if missing:
        print(f"gitwrap: not a directory: {', '.join(missing)}", file=sys.stderr)
        return 2

    commits, repos = collect(roots, max_depth=args.depth)
    if not repos:
        print("gitwrap: no git repositories found under the given paths.")
        return 1

    wanted = {e.strip().lower() for e in args.author.split(",") if e.strip()} \
        if args.author else None

    repo_emails = {r.name: default_email(r) for r in repos}
    commits = filter_authors(commits, repo_emails, wanted)
    if not commits:
        print("gitwrap: no commits found for the selected author(s).")
        return 1

    year = pick_year(commits, args.year)
    repo_roots = {r.name: r for r in repos}
    st = build(commits, year, repo_roots)
    if not st.commits:
        print(f"gitwrap: no commits in {year}. Try --year "
              f"{max(c.dt.year for c in commits)}.")
        return 1

    if args.as_json:
        json.dump(stats_to_json(st), sys.stdout, indent=2, ensure_ascii=False)
        sys.stdout.write("\n")
        return 0

    color = not args.no_color and sys.stdout.isatty()
    if color:
        enable_windows_vt()
    print(render(st, color=color, ascii_only=args.ascii))
    return 0


if __name__ == "__main__":
    sys.exit(main())
