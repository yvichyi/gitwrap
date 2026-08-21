"""Tests for gitwrap — collector, stats, render, and CLI.

Run with:  python -m unittest discover -s tests -v
All timestamps are forged via GIT_COMMITTER_DATE/GIT_AUTHOR_DATE with an
explicit UTC offset, so hour/weekday logic is deterministic regardless of
the machine's timezone.
"""

from __future__ import annotations

import io
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from gitwrap.cli import main  # noqa: E402
from gitwrap.collector import clean_path, collect_repo  # noqa: E402
from gitwrap.render import render  # noqa: E402
from gitwrap.stats import STOPWORDS, build, pick_year  # noqa: E402

EMAIL = "me@example.com"


def git(*args: str, cwd: Path, env: dict | None = None) -> None:
    proc = subprocess.run(
        ["git", *args], cwd=str(cwd), capture_output=True, text=True,
        encoding="utf-8", errors="replace",
        env={**os.environ, **(env or {})},
    )
    if proc.returncode != 0:
        raise AssertionError(f"git {' '.join(args)} failed:\n{proc.stderr}")


class Factory(unittest.TestCase):
    """Scenario factory: fake repositories with scripted commit histories."""

    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="gitwrap-test-"))
        self._seq = 0
        self.addCleanup(lambda: __import__("shutil").rmtree(self.tmp, ignore_errors=True))

    def make_repo(self, name: str, email: str = EMAIL) -> Path:
        repo = self.tmp / name
        repo.mkdir(parents=True)
        git("init", "-q", "-b", "main", str(repo), cwd=self.tmp)
        git("config", "user.email", email, cwd=repo)
        git("config", "user.name", "Test", cwd=repo)
        return repo

    def commit_at(self, repo: Path, when: str, fname: str = "f.py",
                  subject: str = "work", lines: int = 3) -> None:
        """Commit at an explicit local time, e.g. '2025-03-15T02:30:00+08:00'.

        Content carries a per-call sequence number so repeated commits to the
        same file always have something new to stage.
        """
        self._seq += 1
        (repo / fname).write_text(
            "\n".join(f"line {self._seq}.{i}" for i in range(lines)) + "\n",
            encoding="utf-8",
        )
        git("add", "-A", cwd=repo)
        env = {"GIT_COMMITTER_DATE": when, "GIT_AUTHOR_DATE": when}
        git("commit", "-qm", subject, cwd=repo, env=env)

    def build_stats(self, repo: Path, year: int):
        commits = collect_repo(repo)
        return build(commits, year, {repo.name: repo})

    def run_cli(self, *argv: str, repo: str = "cli-repo") -> tuple[int, str]:
        buf_out, buf_err = io.StringIO(), io.StringIO()
        with redirect_stdout(buf_out), redirect_stderr(buf_err):
            code = main([str(self.tmp / repo), *argv])
        return code, buf_out.getvalue() + buf_err.getvalue()


class TestCollector(Factory):
    def test_commits_parsed_with_files(self) -> None:
        repo = self.make_repo("r1")
        self.commit_at(repo, "2025-03-15T10:00:00+08:00", "a.py", "add parser", lines=5)
        commits = collect_repo(repo)
        self.assertEqual(len(commits), 1)
        self.assertEqual(commits[0].email, EMAIL)
        self.assertEqual(commits[0].hour, 10)
        self.assertEqual(commits[0].files, [("a.py", 5, 0)])

    def test_binary_and_rename_paths(self) -> None:
        repo = self.make_repo("r2")
        (repo / "img.png").write_bytes(b"\x00\x01\x02\x03pngdata")
        git("add", "-A", cwd=repo)
        git("commit", "-qm", "add image", cwd=repo,
            env={"GIT_COMMITTER_DATE": "2025-03-15T10:00:00+08:00",
                 "GIT_AUTHOR_DATE": "2025-03-15T10:00:00+08:00"})
        # git marks this png as binary: "-  -  img.png"
        commits = collect_repo(repo)
        self.assertEqual(commits[0].files[0][1], -1)
        self.assertEqual(clean_path("old/{lib => src}/mod.py"), "src/mod.py")

    def test_empty_repo_yields_nothing(self) -> None:
        repo = self.make_repo("empty")
        self.assertEqual(collect_repo(repo), [])


class TestYearSelection(Factory):
    def test_year_filter(self) -> None:
        repo = self.make_repo("r")
        self.commit_at(repo, "2024-06-01T10:00:00+00:00", "a.py", "in 2024")
        self.commit_at(repo, "2025-06-01T10:00:00+00:00", "a.py", "in 2025")
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.total_commits, 1)
        self.assertEqual(st.commits[0].subject, "in 2025")

    def test_fallback_to_recent_year(self) -> None:
        repo = self.make_repo("r")
        self.commit_at(repo, "2024-06-01T10:00:00+00:00", "a.py", "only 2024")
        commits = collect_repo(repo)
        self.assertEqual(pick_year(commits, None), 2024)

    def test_requested_year_without_data_rejected(self) -> None:
        repo = self.make_repo("r")
        self.commit_at(repo, "2025-06-01T10:00:00+00:00", "a.py", "x")
        code, out = self.run_cli("--year", "2030", repo="r")
        self.assertEqual(code, 1)
        self.assertIn("no commits in 2030", out)


class TestTitles(Factory):
    def test_midnight_refactorer(self) -> None:
        repo = self.make_repo("night")
        for day in (2, 3, 4):
            self.commit_at(repo, f"2025-03-{day:02d}T02:30:00+08:00", "a.py", "night work")
        self.assertEqual(self.build_stats(repo, 2025).title(), "Midnight Refactorer")

    def test_early_bird(self) -> None:
        repo = self.make_repo("bird")
        for day in (10, 11, 12):
            self.commit_at(repo, f"2025-03-{day:02d}T06:15:00+08:00", "a.py", "morning")
        self.assertEqual(self.build_stats(repo, 2025).title(), "Early Bird")

    def test_weekend_warrior(self) -> None:
        repo = self.make_repo("wknd")
        # 2025-03-08/09 and 2025-03-15/16 are Sat/Sun.
        for day in ("08", "09", "15"):
            self.commit_at(repo, f"2025-03-{day}T11:00:00+08:00", "a.py", "weekend")
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.title(), "Weekend Warrior")

    def test_925_model_citizen(self) -> None:
        repo = self.make_repo("corp")
        # Mon-Fri 2025-03-10..14 at 10:00, plus one stray weekend commit.
        for day in (10, 11, 12, 13, 14, 14, 14):
            self.commit_at(repo, f"2025-03-{day:02d}T10:00:00+08:00", "a.py", "day job")
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.title(), "9-to-5 Model Citizen")

    def test_the_machine_by_streak(self) -> None:
        repo = self.make_repo("bot")
        for day in range(1, 31):  # 30-day streak in March 2025
            self.commit_at(repo, f"2025-03-{day:02d}T12:00:00+08:00", "a.py", "build")
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.title(), "The Machine")


class TestStreaksAndHeatmap(Factory):
    def test_longest_streak(self) -> None:
        repo = self.make_repo("r")
        for day in (1, 2, 3, 4, 5, 10):
            self.commit_at(repo, f"2025-03-{day:02d}T09:00:00+08:00", "a.py", "d")
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.longest_streak(), 5)

    def test_on_streak_now_with_today_commit(self) -> None:
        repo = self.make_repo("r")
        today = date.today().isoformat()
        self.commit_at(repo, f"{today}T09:00:00+08:00", "a.py", "today")
        st = self.build_stats(repo, date.today().year)
        self.assertTrue(st.on_streak_now())

    def test_heatmap_slots(self) -> None:
        repo = self.make_repo("r")
        self.commit_at(repo, "2025-03-15T02:30:00+08:00", "a.py", "sat night")  # Sat 02:30
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.heatmap[5][2], 1)  # Sat row, hour 2
        self.assertEqual(sum(sum(r) for r in st.heatmap), 1)
        self.assertAlmostEqual(st.night_owl_share, 1.0)


class TestLanguagesAndWords(Factory):
    def test_language_ranking_by_lines(self) -> None:
        repo = self.make_repo("r")
        self.commit_at(repo, "2025-03-03T10:00:00+08:00", "big.py", "py work", lines=100)
        self.commit_at(repo, "2025-03-04T10:00:00+08:00", "app.js", "js work", lines=10)
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.languages.most_common(1)[0][0], "Python")
        self.assertEqual(st.languages["Python"], 100)

    def test_shebang_detection(self) -> None:
        repo = self.make_repo("r")
        script = repo / "deploy"
        script.write_text("#!/usr/bin/env bash\necho hi\n", encoding="utf-8")
        git("add", "-A", cwd=repo)
        git("commit", "-qm", "script", cwd=repo,
            env={"GIT_COMMITTER_DATE": "2025-03-05T10:00:00+08:00",
                 "GIT_AUTHOR_DATE": "2025-03-05T10:00:00+08:00"})
        st = self.build_stats(repo, 2025)
        self.assertEqual(st.languages.most_common(1)[0][0], "Shell")

    def test_stopwords_excluded(self) -> None:
        repo = self.make_repo("r")
        self.commit_at(repo, "2025-03-03T10:00:00+08:00", "a.py", "fix the parser")
        self.commit_at(repo, "2025-03-04T10:00:00+08:00", "a.py", "merge init updates")
        st = self.build_stats(repo, 2025)
        self.assertIn("parser", st.words)
        for banned in ("fix", "the", "merge", "init", "updates"):
            self.assertNotIn(banned, st.words)
        self.assertTrue(all(w not in STOPWORDS for w in st.words))


class TestRender(Factory):
    def make_year_repo(self) -> Path:
        repo = self.make_repo("renderable")
        self.commit_at(repo, "2025-03-15T23:30:00+08:00", "a.py", "parser magic", lines=40)
        self.commit_at(repo, "2025-03-16T23:40:00+08:00", "b.js", "cache invalidation", lines=10)
        return repo

    def test_nine_sections_present(self) -> None:
        repo = self.make_year_repo()
        text = render(self.build_stats(repo, 2025), color=False)
        markers = [
            "YOUR YEAR IN CODE", "the numbers".upper(), "WHEN YOU COMMIT",
            "COMMIT PERSONALITY", "LANGUAGES", "STREAKS", "HIGHLIGHTS",
            "YOUR WORDS", "Share your year in code",
        ]
        for m in markers:
            self.assertIn(m, text, f"missing section marker: {m}")

    def test_ascii_mode_is_pure_ascii(self) -> None:
        repo = self.make_year_repo()
        text = render(self.build_stats(repo, 2025), color=False, ascii_only=True)
        text.encode("ascii")  # raises if any non-ascii glyph survived

    def test_no_color_has_no_ansi_codes(self) -> None:
        repo = self.make_year_repo()
        text = render(self.build_stats(repo, 2025), color=False)
        self.assertNotIn("\033[", text)


class TestCLI(Factory):

    def test_report_runs(self) -> None:
        repo = self.make_repo("cli-repo")
        self.commit_at(repo, "2025-05-05T11:00:00+08:00", "a.py", "hello world")
        code, out = self.run_cli("--year", "2025")
        self.assertEqual(code, 0)
        self.assertIn("YOUR YEAR IN CODE", out)

    def test_json_roundtrip(self) -> None:
        repo = self.make_repo("cli-repo")
        self.commit_at(repo, "2025-05-05T11:00:00+08:00", "a.py", "hello parser")
        code, out = self.run_cli("--year", "2025", "--json")
        self.assertEqual(code, 0)
        data = json.loads(out)
        for key in ("year", "total_commits", "active_days", "repos",
                    "lines_changed", "title", "longest_streak_days",
                    "top_languages", "top_words", "busiest_day"):
            self.assertIn(key, data)
        self.assertEqual(data["total_commits"], 1)

    def test_author_filter_multiple_emails(self) -> None:
        repo = self.make_repo("cli-repo", email="a@x.com")
        self.commit_at(repo, "2025-05-05T11:00:00+08:00", "a.py", "mine")
        git("-c", "user.email=other@y.com", "commit", "-qm", "theirs",
            "--allow-empty", cwd=repo,
            env={"GIT_COMMITTER_DATE": "2025-05-06T11:00:00+08:00",
                 "GIT_AUTHOR_DATE": "2025-05-06T11:00:00+08:00"})
        code, out = self.run_cli("--year", "2025", "--author", "a@x.com", "--json")
        data = json.loads(out)
        self.assertEqual(data["total_commits"], 1)

    def test_author_no_match(self) -> None:
        repo = self.make_repo("cli-repo")
        self.commit_at(repo, "2025-05-05T11:00:00+08:00", "a.py", "x")
        code, out = self.run_cli("--year", "2025", "--author", "nobody@z.io")
        self.assertEqual(code, 1)
        self.assertIn("no commits found", out)

    def test_multi_repo_aggregation(self) -> None:
        self.make_repo("proj-a")
        self.make_repo("proj-b")
        for name in ("proj-a", "proj-b"):
            repo = self.tmp / name
            self.commit_at(repo, "2025-05-05T11:00:00+08:00", "a.py", "work")
        buf = io.StringIO()
        with redirect_stdout(buf):
            code = main([str(self.tmp), "--year", "2025", "--json"])
        self.assertEqual(code, 0)
        data = json.loads(buf.getvalue())
        self.assertEqual(data["repos"], ["proj-a", "proj-b"])
        self.assertEqual(data["total_commits"], 2)

    def test_empty_repo_cli(self) -> None:
        self.make_repo("cli-repo")  # valid repo, zero commits
        code, out = self.run_cli("--year", "2025")
        self.assertEqual(code, 1)


if __name__ == "__main__":
    unittest.main()
