# gitwrap

**Your year in code.** One command turns your git history into a Spotify-Wrapped-style annual report — numbers, a commit heatmap, streaks, and a personality title. Built to be screenshot and shared.

This repository also includes [41 standalone interactive science experiments](science-collection/README.md), organized into seven themes. Open `science-collection/index.html` locally for the searchable gallery; see the [collection review](science-collection/docs/REVIEW.md) for the improvements and reproducibility limits.

```text
YOUR YEAR IN CODE

███ ███ ███ ███
  █ █ █   █ █
███ █ █ ███ ███
█   █ █ █     █
███ ███ ███ ███

53 commits

◆ 2 · THE NUMBERS

  53  commits
  20  active days
  1  repo touched
  1.1k  lines changed

◆ 3 · WHEN YOU COMMIT

     00       03       06       09       12       15       18       21
Mon    ~   ~           ~ ~ ~ ~   ~ ~ ~
Tue    ~   ~           ~ ~ ~ ~ @ ~ ~ ~
Wed    ~   ~           ~ ~ ~ ~   ~ ~ ~
Thu                    ~ ~ ~ ~   ~ ~ ~
Fri                    ~ ~ ~ ~   ~ ~ ~
Sat                        @ ~               @
Sun                        @ ~               @

      -*@ less → more

◆ 4 · COMMIT PERSONALITY

  latest commit      13:30
  night-owl index    11% (00:00–06:00)
  weekend share      19%
  work-hours share   81% (09:00–18:00)

  ★  ★  ★
  STEADY BUILDER
  ★  ★  ★

◆ 5 · LANGUAGES

  1. Python       ████████████████░░░░░░ 74.0%
  2. Go           ██░░░░░░░░░░░░░░░░░░░░ 9.4%
  3. JavaScript   ██░░░░░░░░░░░░░░░░░░░░ 7.5%
  4. TypeScript   ██░░░░░░░░░░░░░░░░░░░░ 7.0%
  5. Markdown     ░░░░░░░░░░░░░░░░░░░░░░ 1.9%

◆ 6 · STREAKS

  longest streak      8 days
  right now           not on a streak

◆ 7 · HIGHLIGHTS

  busiest day         2025-03-14  6 commits
  most-touched files
    → app.py  ×33
    → ui.tsx  ×5
    → api.go  ×5

◆ 8 · YOUR WORDS

  parser ×37   improve ×20   pipeline ×20
  weekend ×8   streak ×7   day ×7

  ✦ 53 commits · 20 days · 1 repo · 1.1k lines

  Keep shipping. Share your year in code! ✦
```

(In a real terminal it's full color: gradient hero, 256-color heatmap, colored language bars.)

## Titles you can earn

| Title | How |
|---|---|
| The Machine | 200+ active days, or a 30+ day streak |
| Midnight Refactorer | 30%+ of commits between 00:00–06:00 |
| Early Bird | 30%+ of commits between 05:00–09:00 |
| Weekend Warrior | 35%+ of commits on Sat/Sun |
| 9-to-5 Model Citizen | 70%+ in work hours, ≤15% on weekends |
| Steady Builder | everyone else — keep going |

## Install

```bash
pip install gitwrap
# or straight from git:
pip install git+https://github.com/yvichyi/gitwrap
```

Requires Python 3.10+ and `git` on your PATH. **Zero dependencies** — pure standard library, fully offline, 100% read-only (it only ever runs `git log` and `git config`).

## Usage

```bash
gitwrap                     # last year's report (falls back to the most recent year with commits)
gitwrap --year 2026         # a specific year
gitwrap --author a@x.com,b@y.com   # multiple identities (changed jobs? changed emails?)
gitwrap ~/projects          # aggregate every repo under a folder
gitwrap --json              # machine-readable output
gitwrap --ascii             # pure-ASCII glyphs for legacy consoles
gitwrap --no-color          # plain text
```

Notes:

- Commits are attributed to each repo's own `user.email` by default — handy when your repos use different identities.
- Commit-rhythm stats use the committer's **local time** (the offset recorded in each commit), not UTC — so "3am" means 3am where you actually were.
- Language stats come from `--numstat` line counts; extensionless scripts are recognized by their shebang.

## Performance

Measured end-to-end on a synthetic repo of **10,000 commits: 0.23 s** (budget: 3 s). One `git log` call per repo, streaming parse, no temp files.

## Development

```bash
git clone https://github.com/yvichyi/gitwrap
cd gitwrap
python -m unittest discover -s tests -v   # 26 tests, no network needed
```

## License

[MIT](LICENSE)
