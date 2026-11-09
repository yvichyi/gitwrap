"""游 · 1-bit handheld screens. home: an overworld map where every entry is a level. play: a side-scrolling stage,
the cat runs towards the flag as the song plays, the moon is the album art dithered to one bit.

Usage: python3 -I pxgame.py <out dir> <home|play>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pxlib import Canvas, draw_text  # noqa: E402

OUT, MODE = sys.argv[1], sys.argv[2]
W, H = 168, 148
cv = Canvas(W, H, dict(i="#1d1d1b", l="#b9b6ac"), {}, seed=2)
rng = cv.rng
cv.rect(0, 0, W - 1, H - 1, "l")

F35 = {  # a 3x5 font for the HUD
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"], "2": ["###", "..#", "###", "#..", "###"],
    "3": ["###", "..#", ".##", "..#", "###"], "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."], "8": ["###", "#.#", "###", "#.#", "###"],
    "9": ["###", "#.#", "###", "..#", "###"], "-": ["...", "...", "###", "...", "..."], ":": ["...", ".#.", "...", ".#.", "..."],
    "W": ["#.#", "#.#", "#.#", "###", "#.#"], "O": ["###", "#.#", "#.#", "#.#", "###"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "L": ["#..", "#..", "#..", "#..", "###"], "D": ["##.", "#.#", "#.#", "#.#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "M": ["#.#", "###", "###", "#.#", "#.#"], "E": ["###", "#..", "##.", "#..", "###"],
    "x": ["...", "#.#", ".#.", "#.#", "..."], " ": ["...", "...", "...", "...", "..."], "P": ["###", "#.#", "###", "#..", "#.."],
    "S": ["###", "#..", "###", "..#", "###"], "A": [".#.", "#.#", "###", "#.#", "#.#"], "G": ["###", "#..", "#.#", "#.#", "###"],
    "C": ["###", "#..", "#..", "#..", "###"], "N": ["##.", "#.#", "#.#", "#.#", "#.#"],
}


def tiny(x, y, s, k="i"):
    for ch in s:
        for j, row in enumerate(F35.get(ch, F35[" "])):
            for i, c in enumerate(row):
                if c == "#":
                    cv.px(x + i, y + j, k)
        x += 4
    return x


def sprite(x, y, rows, k="i", bg=None, flip=False):
    for j, row in enumerate(rows):
        for i, c in enumerate(row[::-1] if flip else row):
            if c == "#":
                cv.px(x + i, y + j, k)
            elif c == "o" and bg:
                cv.px(x + i, y + j, bg)


HEART = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
CAT_SIT = ["..#...#..", ".##..##..", ".#######.", "#.#.#.##.", "#######..", ".######.#", ".######.#", "########.", "#.#..#.#."]
CAT_RUN = ["...#...#...", "..##..##...", "..#######..", ".#.#.#.##..", ".#######...", "#########.#", ".########.#", "..#.....#..", ".#.......#."]


def heart(x, y):
    sprite(x, y, HEART)


if MODE == "home":
    # HUD: world, lives, notes collected.
    cv.rect(0, 0, W - 1, 8, "i")
    tiny(3, 2, "WORLD 1", "l")
    for k in range(3):
        sprite(60 + k * 9, 1, HEART, "l")
    tiny(100, 2, "x3420", "l")
    cv.px(96, 3, "l"); cv.px(97, 2, "l"); cv.px(97, 3, "l"); cv.px(97, 4, "l"); cv.px(96, 5, "l"); cv.px(95, 5, "l"); cv.px(96, 6, "l")
    # Sea: rows of little waves.
    for y in range(10, H):
        for x in range(W):
            if (y % 6 == 0 and (x + (y // 6) * 3) % 8 in (0, 1)) or (y % 6 == 1 and (x + (y // 6) * 3) % 8 in (2,)):
                cv.px(x, y, "i")
    # Land: an island traced by a wobbly outline, with dithered shore.
    cx, cy = 84, 76
    land = np.zeros((H, W), bool)
    for y in range(12, H - 4):
        for x in range(4, W - 4):
            a = math.atan2(y - cy, (x - cx) * .72)
            r = 56 + 7 * math.sin(a * 3 + 1) + 5 * math.sin(a * 5 + 2) + 3 * math.sin(a * 9)
            if math.hypot((x - cx) * .72, y - cy) < r * .92:
                land[y, x] = True
    for y in range(H):
        for x in range(W):
            if land[y, x]:
                cv.px(x, y, "l")
                edge = not (land[max(y - 1, 0), x] and land[min(y + 1, H - 1), x] and land[y, max(x - 1, 0)] and land[y, min(x + 1, W - 1)])
                if edge:
                    cv.px(x, y, "i")
    # Grass tufts.
    for i in range(70):
        x, y = rng.integers(8, W - 8), rng.integers(16, H - 8)
        if land[y, x] and land[y + 2, x] and land[y - 2, x]:
            cv.px(x, y, "i"); cv.px(x + 2, y, "i"); cv.px(x + 1, y - 1, "i")
    # Paths between the levels: dotted.
    nodes = {"daily": (44, 40), "fm": (122, 34), "like": (138, 84), "chart": (100, 118), "recent": (40, 112), "dl": (78, 74)}
    for a, b in [("daily", "dl"), ("dl", "fm"), ("fm", "like"), ("like", "chart"), ("chart", "recent"), ("recent", "daily"), ("dl", "chart")]:
        (x0, y0), (x1, y1) = nodes[a], nodes[b]
        n = int(max(abs(x1 - x0), abs(y1 - y0)))
        for t in range(0, n, 3):
            f = t / n
            cv.px(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, "i")
    # Trees.
    TREE = ["..#..", ".###.", "#####", ".###.", "#####", "..#.."]
    for (x, y) in [(18, 60), (24, 64), (60, 24), (66, 28), (150, 60), (112, 96), (64, 128), (20, 90), (140, 108), (90, 34)]:
        if land[y + 5, x + 2]:
            sprite(x, y, TREE)
    # Levels.
    CASTLE = ["#.#.#....#.#.#", "#####....#####", ".###......###.", ".###.#..#.###.", ".############.", ".###.####.###.", ".###.#oo#.###.", ".###.#oo#.###.", "##############"]
    TOWER = ["...#...", "..#.#..", "...#...", "..###..", "..#.#..", ".#...#.", ".#.#.#.", "#.#.#.#", "#######"]
    HOUSE = ["....##....", "...####...", "..######..", ".########.", "##########", ".#oo##oo#.", ".#oo##oo#.", ".########."]
    MOUNT = ["....#|....", "....##....", "...#..#...", "..#....#..", ".#..##..#.", "#..#..#..#", "##########"]
    CLOCK = ["..###..", ".#.#.#.", ".#.##..", ".#...#.", "..###..", "..#.#..", "..#.#..", ".#####."]
    CAVE = ["..######..", ".#......#.", "#..####..#", "#.#oooo#.#", "#.#oooo#.#", "##########"]
    for key, spr in [("daily", CASTLE), ("fm", TOWER), ("like", HOUSE), ("chart", MOUNT), ("recent", CLOCK), ("dl", CAVE)]:
        x, y = nodes[key]
        w, h = len(spr[0]), len(spr)
        cv.rect(x - w // 2 - 1, y - h - 1, x + w // 2 + 1, y + 1, "l")
        sprite(x - w // 2, y - h, [r.replace("|", "#") for r in spr], bg="l")
        cv.rect(x - 4, y + 2, x + 4, y + 3, "i")
    # Radio waves from the FM tower, a heart over the house, a flag on the mountain.
    fx, fy = nodes["fm"]
    for r in (5, 8):
        for a in np.linspace(-.9, .9, 7):
            cv.px(fx + r * math.sin(a), fy - 9 - r * math.cos(a) * .8, "i")
    heart(nodes["like"][0] - 3, nodes["like"][1] - 16)
    mx, my = nodes["chart"]
    cv.rect(mx, my - 12, mx + 4, my - 10, "i")
    # The cat stands on the castle, a cursor bouncing over it.
    sprite(nodes["daily"][0] + 8, nodes["daily"][1] - 8, CAT_SIT, bg="l")
    sprite(nodes["daily"][0] - 2, nodes["daily"][1] - 20, ["#####", ".###.", "..#.."])
    # Level numbers.
    for i, key in enumerate(["daily", "fm", "like", "chart", "recent", "dl"]):
        x, y = nodes[key]
        cv.rect(x - 7, y + 5, x + 5, y + 11, "i")
        tiny(x - 6, y + 6, f"1-{i + 1}", "l")
    # Ship in the sea.
    sprite(10, 128, ["...#....", "..##....", ".###....", "####....", "...#....", "########", ".######."])
    cv.save(f"{OUT}/game_home_1x.png", f"{OUT}/game_home.png", scale=2)
else:
    # HUD and a strip map of the song: the cat at 26 %, the flag at the end.
    cv.rect(0, 0, W - 1, 8, "i")
    tiny(3, 2, "3-30", "l")
    for k in range(3):
        sprite(26 + k * 9, 1, HEART, "l")
    tiny(60, 2, "SCORE 003580", "l")
    tiny(118, 2, "TIME 058", "l")
    for x in range(6, W - 6, 2):
        cv.px(x, 13, "i")
    cv.rect(int(6 + (W - 12) * .26) - 1, 11, int(6 + (W - 12) * .26) + 1, 15, "i")
    cv.rect(W - 8, 9, W - 7, 16, "i"); cv.rect(W - 6, 9, W - 3, 11, "i")
    # Sky: denser dither towards the horizon.
    B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16
    for y in range(18, 96):
        t = (y - 18) / 78
        for x in range(W):
            if B4[y % 4, x % 4] < .06 + t * .22:
                cv.px(x, y, "i", hard=False)
    # The moon is the album art, dithered to one bit inside a disc.
    art = np.array(Image.open(f"{OUT}/covers/c1.png").convert("L").resize((40, 40), Image.LANCZOS), np.float32) / 255
    mx, my, mr = 136, 40, 18
    for y in range(-mr - 2, mr + 3):
        for x in range(-mr - 2, mr + 3):
            d = math.hypot(x, y)
            if d <= mr:
                v = art[min(39, y + 20), min(39, x + 20)]
                v = .25 + v * .9
                cv.px(mx + x, my + y, "l" if v > B4[(my + y) % 4, (mx + x) % 4] else "i")
            elif d <= mr + 1.5:
                cv.px(mx + x, my + y, "i")
            elif d <= mr + 3:
                cv.px(mx + x, my + y, "l")
    # Stars.
    for (x, y) in [(20, 24), (44, 30), (70, 22), (96, 34), (110, 24), (30, 48), (84, 50)]:
        cv.px(x, y, "l"); cv.px(x - 1, y, "i"); cv.px(x + 1, y, "i"); cv.px(x, y - 1, "i"); cv.px(x, y + 1, "i")
        cv.px(x, y, "l")
    # Far mountains: solid with a light ridge line; mid hills: hatched.
    for x in range(W):
        h1 = 70 + 10 * math.sin(x * .05 + 1) + 6 * math.sin(x * .13)
        for y in range(int(h1), 104):
            cv.px(x, y, "i", hard=False)
        cv.px(x, int(h1), "l")
        h2 = 92 + 6 * math.sin(x * .09 + 3)
        for y in range(int(h2), 112):
            cv.px(x, y, "l" if (x + y) % 3 == 0 else "i")
        cv.px(x, int(h2), "l"); cv.px(x, int(h2) + 1, "i")
    # Street lamps along the way (this is a night stage), and the ground: bricks.
    for lx in (14, 82, 150):
        cv.rect(lx, 96, lx, 116, "l"); cv.rect(lx - 2, 94, lx + 2, 96, "l"); cv.px(lx, 97, "i")
        for r in (4, 6):
            for a in np.linspace(0, 2 * math.pi, 14):
                cv.px(lx + r * math.cos(a), 95 + r * math.sin(a) * .7, "l")
    cv.rect(0, 116, W - 1, 147, "i")
    for y in range(117, 148, 6):
        cv.line(0, y, W - 1, y, "l")
        off = 0 if (y // 6) % 2 else 6
        for x in range(off, W, 12):
            cv.line(x, y, x, y + 5, "l")
    # Note blocks and notes to collect, floating in an arc.
    for (bx, by) in [(60, 86), (72, 86)]:
        cv.rect(bx, by, bx + 10, by + 10, "i"); cv.rect(bx + 1, by + 1, bx + 9, by + 9, "l")
        sprite(bx + 3, by + 2, ["..##", "..#.", "..#.", "###.", "##.."])
    for k in range(6):
        nx, ny = 92 + k * 9, 92 - int(10 * math.sin(k / 5 * math.pi))
        sprite(nx, ny, ["..##", "..#.", "###.", "##.."], bg="l")
    # The cat, mid-stride, with dust behind.
    cv.rect(38, 103, 50, 115, "l")
    sprite(39, 106, CAT_RUN, bg="l")
    for (dx, dy) in [(34, 112), (31, 110), (29, 113)]:
        cv.px(dx, dy, "l")
    # The flag far away at the end of the level.
    cv.rect(162, 84, 162, 116, "l"); cv.rect(156, 84, 161, 89, "l")
    # Dialogue box for the lyric.
    cv.rect(4, 120, W - 5, 145, "i"); cv.rect(5, 121, W - 6, 144, "l"); cv.rect(7, 123, W - 8, 142, "l")
    cv.line(6, 122, W - 7, 122, "i"); cv.line(6, 143, W - 7, 143, "i"); cv.line(6, 122, 6, 143, "i"); cv.line(W - 7, 122, W - 7, 143, "i")
    draw_text(cv, 12, 126, "只剩你的歌还亮着", "i")
    sprite(W - 16, 138, ["#####", ".###.", "..#.."])
    cv.save(f"{OUT}/game_play_1x.png", f"{OUT}/game_play.png", scale=2)
print("ok", MODE)
