# gitwrap

**你的编码年度报告。** 一条命令，把 git 历史变成 Spotify Wrapped 风格的年度总结——数字、提交热力图、连击纪录、专属称号。为截图分享而生。

仓库中还收录了按八个主题整理的 [57 件单文件互动科学作品](science-collection/README.md)。在本地打开 `science-collection/index.html` 可搜索和浏览展厅。另收录一部[互动叙事](interactive-stories/README.md)；[整理与优化报告](science-collection/docs/REVIEW.md) 记录了改进内容和科学结果的复现边界。

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

（真实终端里是全彩的：渐变开场大字、256 色热力图、彩色语言条。）

## 你能获得哪些称号

| 称号 | 条件 |
|---|---|
| The Machine | 200+ 活跃天，或 30+ 天连击 |
| Midnight Refactorer | 30%+ 提交在 00:00–06:00 |
| Early Bird | 30%+ 提交在 05:00–09:00 |
| Weekend Warrior | 35%+ 提交在周六日 |
| 9-to-5 Model Citizen | 70%+ 在工作时段，周末 ≤15% |
| Steady Builder | 其他所有人——继续加油 |

## 安装

```bash
pip install gitwrap
# 或直接从 GitHub 安装：
pip install git+https://github.com/yvichyi/gitwrap
```

需要 Python 3.10+ 和 PATH 里的 `git`。**零依赖**——纯标准库，完全离线，100% 只读（只运行 `git log` 和 `git config`，绝不写仓库）。

## 使用

```bash
gitwrap                     # 上一年的报告（无数据时回落到最近有提交的年份）
gitwrap --year 2026         # 指定年份
gitwrap --author a@x.com,b@y.com   # 多身份（换过公司/改过邮箱？）
gitwrap ~/projects          # 聚合目录下所有仓库
gitwrap --json              # 机器可读输出
gitwrap --ascii             # 纯 ASCII 符号，兼容老终端
gitwrap --no-color          # 无色纯文本
```

说明：

- 默认按每个仓库自己的 `user.email` 归属提交——多个仓库用不同身份时特别好用
- 作息统计使用提交者的**本地时间**（提交里记录的时区偏移），不是 UTC——"凌晨 3 点"就是你当时所在地的凌晨 3 点
- 语言统计来自 `--numstat` 行数；无扩展名脚本按 shebang 识别

## 性能

在 10,000 条提交的合成仓库上实测端到端 **0.23 秒**（预算 3 秒）。每个仓库一次 `git log` 调用，流式解析，无临时文件。

## 开发

```bash
git clone https://github.com/yvichyi/gitwrap
cd gitwrap
python -m unittest discover -s tests -v   # 26 个测试，无需联网
```

## 许可

[MIT](LICENSE)


