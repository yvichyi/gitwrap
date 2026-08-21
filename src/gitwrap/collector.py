"""Collect commits from git repositories with a single git log call each."""

from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

GIT_TIMEOUT = 60  # seconds; huge repos can be slow

# Directories we never descend into while searching for repositories.
SKIP_DIRS = {
    ".git", "node_modules", ".venv", "venv", ".tox", ".cache", ".mypy_cache",
    ".pytest_cache", "__pycache__", ".idea", ".vscode", "dist", "build",
    "target", "vendor", "bower_components", "site-packages", "$RECYCLE.BIN",
    "System Volume Information", "AppData",
}

# Extension -> language, 30+ entries to cover the mainstream spectrum.
LANG_BY_EXT = {
    ".py": "Python", ".pyw": "Python",
    ".js": "JavaScript", ".mjs": "JavaScript", ".cjs": "JavaScript",
    ".jsx": "JavaScript", ".ts": "TypeScript", ".tsx": "TypeScript",
    ".java": "Java", ".kt": "Kotlin", ".kts": "Kotlin",
    ".c": "C", ".h": "C",
    ".cpp": "C++", ".cc": "C++", ".cxx": "C++", ".hpp": "C++", ".hh": "C++",
    ".cs": "C#", ".go": "Go", ".rs": "Rust", ".rb": "Ruby",
    ".php": "PHP", ".swift": "Swift", ".scala": "Scala", ".dart": "Dart",
    ".sh": "Shell", ".bash": "Shell", ".zsh": "Shell",
    ".r": "R", ".m": "Objective-C", ".pl": "Perl", ".lua": "Lua",
    ".html": "HTML", ".htm": "HTML", ".css": "CSS", ".scss": "SCSS",
    ".vue": "Vue", ".sql": "SQL", ".md": "Markdown",
    ".yaml": "YAML", ".yml": "YAML", ".json": "JSON", ".xml": "XML",
    ".toml": "TOML",
}

# Shebang interpreter keyword -> language, for extensionless scripts.
LANG_BY_SHEBANG = {
    "python": "Python", "node": "JavaScript", "deno": "TypeScript",
    "bash": "Shell", "sh": "Shell", "zsh": "Shell", "fish": "Shell",
    "ruby": "Ruby", "perl": "Perl", "lua": "Lua", "Rscript": "R",
}

FIELD_SEP = "\x1f"
RECORD_SEP = "\x1e"


@dataclass
class Commit:
    repo: str
    sha: str
    email: str
    dt: datetime           # local wall-clock time as committed (from %cI)
    subject: str
    files: list[tuple[str, int, int]] = field(default_factory=list)  # (path, +, -); -1 = binary

    @property
    def date(self) -> datetime.date:
        return self.dt.date()

    @property
    def weekday(self) -> int:
        return self.dt.weekday()  # Mon=0 .. Sun=6

    @property
    def hour(self) -> int:
        return self.dt.hour


def _run_git(args: list[str], cwd: Path) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            ["git", *args], cwd=str(cwd), capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=GIT_TIMEOUT,
        )
        return proc.returncode, proc.stdout
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 1, str(exc)


def is_repo(path: Path) -> bool:
    return (path / ".git").exists()


def find_repos(roots: list[Path], max_depth: int = 4) -> list[Path]:
    """Resolve each root: itself if a repo, otherwise a recursive search."""
    repos: list[Path] = []
    seen: set[Path] = set()
    for root in roots:
        root = root.resolve()
        if not root.is_dir():
            continue
        if is_repo(root):
            if root not in seen:
                seen.add(root)
                repos.append(root)
            continue
        base_depth = len(root.parts)
        for dirpath, dirnames, _f in os.walk(root, followlinks=False):
            current = Path(dirpath)
            if is_repo(current) and current not in seen:
                seen.add(current)
                repos.append(current)
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")]
            if len(current.parts) - base_depth >= max_depth:
                dirnames[:] = []
    return repos


def default_email(repo: Path) -> str | None:
    rc, out = _run_git(["config", "user.email"], repo)
    email = out.strip() if rc == 0 else ""
    return email.lower() or None


def clean_path(raw: str) -> str:
    """Normalize numstat paths, unwrapping rename notation.

    Handles both "old.txt => new.txt" and "{old => new}/mod.py"; braces are
    not legal in practical paths, so stripping them outright is safe.
    """
    if "=>" in raw:
        raw = raw.split("=>", 1)[1]
    return raw.strip().replace("{", "").replace("}", "").lstrip("/")


def detect_language(path: str, repo: Path) -> str:
    ext = os.path.splitext(path)[1].lower()
    if ext in LANG_BY_EXT:
        return LANG_BY_EXT[ext]
    target = repo / path
    if not ext and target.is_file():
        try:
            first = target.open("rb").readline(128).decode("utf-8", "replace")
        except OSError:
            return "Other"
        if first.startswith("#!"):
            toks = first[2:].strip().replace("/", " ").split()
            for tok in (t for t in toks if t):
                for key, lang in LANG_BY_SHEBANG.items():
                    if key in tok.lower():
                        return lang
    return "Other"


def collect_repo(repo: Path) -> list[Commit]:
    """Fetch every commit on every branch of one repository, numstat included.

    git emits, per commit: a header line built from --format (terminated by
    RECORD_SEP), a blank line, then that commit's numstat lines. We parse
    line-by-line so each numstat attaches to the header above it.
    """
    fmt = f"%H{FIELD_SEP}%ae{FIELD_SEP}%cI{FIELD_SEP}%s{RECORD_SEP}"
    rc, out = _run_git(["log", "--all", "--numstat", f"--format={fmt}"], repo)
    if rc != 0 or not out.strip():
        return []

    commits: list[Commit] = []
    current: Commit | None = None
    # NB: split("\n"), not splitlines() — str.splitlines also treats \x1e as a
    # line boundary and would silently swallow our record separator.
    for line in out.split("\n"):
        if RECORD_SEP in line:
            header = line.split(RECORD_SEP)[0]
            fields = header.split(FIELD_SEP)
            if len(fields) != 4:
                current = None
                continue
            sha, email, c_iso, subject = fields
            try:
                # %cI carries the committer's UTC offset; fromisoformat keeps
                # the wall-clock fields intact — the local time they lived.
                dt = datetime.fromisoformat(c_iso)
            except ValueError:
                current = None
                continue
            current = Commit(repo=repo.name, sha=sha, email=email.lower(),
                             dt=dt, subject=subject)
            commits.append(current)
            continue
        if current is None or not line:
            continue
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        added_s, deleted_s, raw_path = parts
        try:
            added, deleted = int(added_s), int(deleted_s)
        except ValueError:
            added = deleted = -1  # binary file: "-  -  path"
        current.files.append((clean_path(raw_path), added, deleted))
    return commits


def collect(roots: list[Path], max_depth: int = 4) -> tuple[list[Commit], list[Path]]:
    """Gather commits from every repository under the given roots."""
    repos = find_repos(roots, max_depth=max_depth)
    all_commits: list[Commit] = []
    for repo in repos:
        all_commits.extend(collect_repo(repo))
    return all_commits, repos
