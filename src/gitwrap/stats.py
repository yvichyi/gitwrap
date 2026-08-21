"""Turn raw commits into the numbers behind the report."""

from __future__ import annotations

import os
import re
from collections import Counter
from dataclasses import dataclass, field
from datetime import date, timedelta
from functools import lru_cache
from pathlib import Path

from .collector import LANG_BY_EXT, Commit, detect_language

# Words that carry no personality; excluded from the vocabulary board.
STOPWORDS = {
    "the", "a", "an", "and", "or", "for", "with", "of", "to", "in", "on",
    "from", "into", "is", "it", "its", "this", "that", "by", "at", "as",
    "fix", "fixes", "fixed", "merge", "merged", "init", "initial", "update",
    "updated", "updates", "add", "added", "adds", "remove", "removed",
    "wip", "refactor", "chore", "misc", "stuff", "changes", "change",
    "branch", "commit", "commits", "version", "release", "docs", "v1", "v2",
}

WORD_RE = re.compile(r"[a-zA-Z][a-zA-Z0-9_\-]+")


@dataclass
class Stats:
    year: int
    commits: list[Commit]
    repos: set[str] = field(default_factory=set)
    active_days: set[date] = field(default_factory=set)
    heatmap: list[list[int]] = field(default_factory=lambda: [[0] * 24 for _ in range(7)])
    languages: Counter = field(default_factory=Counter)      # language -> lines
    files: Counter = field(default_factory=Counter)          # path -> touches
    words: Counter = field(default_factory=Counter)
    lines_changed: int = 0

    @property
    def total_commits(self) -> int:
        return len(self.commits)

    @property
    def latest_commit_time(self) -> str | None:
        if not self.commits:
            return None
        return max(c.dt for c in self.commits).strftime("%H:%M")

    @property
    def night_owl_share(self) -> float:
        return _share(self.commits, lambda c: c.hour < 6)

    @property
    def early_bird_share(self) -> float:
        return _share(self.commits, lambda c: 5 <= c.hour < 9)

    @property
    def work_hours_share(self) -> float:
        return _share(self.commits, lambda c: 9 <= c.hour < 18)

    @property
    def weekend_share(self) -> float:
        return _share(self.commits, lambda c: c.weekday >= 5)

    def busiest_day(self) -> tuple[date, int] | None:
        if not self.commits:
            return None
        per_day = Counter(c.date for c in self.commits)
        return per_day.most_common(1)[0]

    def longest_streak(self) -> int:
        return _streak_len(self.active_days)

    def on_streak_now(self, today: date | None = None) -> bool:
        today = today or date.today()
        return today in self.active_days or (today - timedelta(days=1)) in self.active_days

    def title(self) -> str:
        """The personality award — first matching rule wins."""
        if len(self.active_days) >= 200 or self.longest_streak() >= 30:
            return "The Machine"
        if self.night_owl_share >= 0.30:
            return "Midnight Refactorer"
        if self.early_bird_share >= 0.30:
            return "Early Bird"
        if self.weekend_share >= 0.35:
            return "Weekend Warrior"
        if self.work_hours_share >= 0.70 and self.weekend_share <= 0.15:
            return "9-to-5 Model Citizen"
        return "Steady Builder"


def _share(commits: list[Commit], pred) -> float:
    if not commits:
        return 0.0
    return sum(1 for c in commits if pred(c)) / len(commits)


def _streak_len(days: set[date]) -> int:
    if not days:
        return 0
    ordered = sorted(days)
    best = current = 1
    for prev, cur in zip(ordered, ordered[1:]):
        current = current + 1 if (cur - prev).days == 1 else 1
        best = max(best, current)
    return best


def pick_year(commits: list[Commit], requested: int | None) -> int | None:
    """Default to last year; fall back to the most recent year with data."""
    years = {c.dt.year for c in commits}
    if not years:
        return None
    if requested is not None:
        return requested
    previous = date.today().year - 1
    return previous if previous in years else max(years)


@lru_cache(maxsize=4096)
def _language_of(path: str, repo_key: str, repo_root: Path | None) -> str:
    ext = os.path.splitext(path)[1].lower()
    if ext in LANG_BY_EXT:
        return LANG_BY_EXT[ext]
    if not ext and repo_root is not None:
        return detect_language(path, repo_root)
    return "Other"


def build(commits: list[Commit], year: int, repo_roots: dict[str, Path] | None = None) -> Stats:
    """Aggregate one year of commits into a Stats bundle.

    repo_roots maps repo name -> its Path, used only for shebang lookups on
    extensionless files that still exist in the working tree.
    """
    repo_roots = repo_roots or {}
    st = Stats(year=year, commits=[c for c in commits if c.dt.year == year])
    for c in st.commits:
        st.repos.add(c.repo)
        st.active_days.add(c.date)
        st.heatmap[c.weekday][c.hour] += 1
        root = repo_roots.get(c.repo)
        for path, added, deleted in c.files:
            st.files[path] += 1
            if added < 0 or deleted < 0:  # binary file
                continue
            st.lines_changed += added + deleted
            st.languages[_language_of(path, c.repo, root)] += added + deleted
        for word in WORD_RE.findall(c.subject.lower()):
            if word not in STOPWORDS and len(word) > 1:
                st.words[word] += 1
    return st
