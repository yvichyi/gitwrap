"""游 · full-screen 1-bit game, no console around it. Two colours only: ink and paper, greys by dithering.
home: HUD, a world map where every entry is a level, a dialogue box, an item bar, save files for playlists, a menu.
play: a side-scrolling stage; the cat runs towards the flag as the song plays, the moon is the album art.
land: the stage, wide, for the landscape player.

Usage: python3 -I pxgame2.py <out dir> <home|play|land>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pxlib import Canvas, draw_text  # noqa: E402

OUT, MODE = sys.argv[1], sys.argv[2]
W, H = (422, 195) if MODE == "land" else (195, 422)
cv = Canvas(W, H, dict(i="#1c1b18", l="#e8e5d8"), {}, seed=2)
rng = cv.rng
cv.rect(0, 0, W - 1, H - 1, "l")
B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16

F35 = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"], "2": ["###", "..#", "###", "#..", "###"],
    "3": ["###", "..#", ".##", "..#", "###"], "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."], "8": ["###", "#.#", "###", "#.#", "###"],
    "9": ["###", "#.#", "###", "..#", "###"], "-": ["...", "...", "###", "...", "..."], ":": ["...", ".#.", "...", ".#.", "..."],
    "W": ["#.#", "#.#", "#.#", "###", "#.#"], "O": ["###", "#.#", "#.#", "#.#", "###"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "L": ["#..", "#..", "#..", "#..", "###"], "D": ["##.", "#.#", "#.#", "#.#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "M": ["#.#", "###", "###", "#.#", "#.#"], "E": ["###", "#..", "##.", "#..", "###"],
    "x": ["...", "#.#", ".#.", "#.#", "..."], " ": ["...", "...", "...", "...", "..."], "P": ["###", "#.#", "###", "#..", "#.."],
    "S": ["###", "#..", "###", "..#", "###"], "A": [".#.", "#.#", "###", "#.#", "#.#"], "G": ["###", "#..", "#.#", "#.#", "###"],
    "C": ["###", "#..", "#..", "#..", "###"], "N": ["##.", "#.#", "#.#", "#.#", "#.#"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "F": ["###", "#..", "##.", "#..", "#.."], "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "U": ["#.#", "#.#", "#.#", "#.#", "###"],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "H": ["#.#", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    ".": ["...", "...", "...", "...", ".#."], "/": ["..#", "..#", ".#.", "#..", "#.."], "%": ["#.#", "..#", ".#.", "#..", "#.#"],
}


def tiny(x, y, s, k="i"):
    for ch in s:
        for j, row in enumerate(F35.get(ch, F35[" "])):
            for i, c in enumerate(row):
                if c == "#":
                    cv.px(x + i, y + j, k)
        x += 4
    return x


def sprite(x, y, rows, k="i", bg=None):
    for j, row in enumerate(rows):
        for i, c in enumerate(row):
            if c == "#":
                cv.px(x + i, y + j, k)
            elif c == "o" and bg:
                cv.px(x + i, y + j, bg)


def frame(x0, y0, x1, y1, title=None):
    """A game window: paper inside, a double ink border with notched corners."""
    cv.rect(x0, y0, x1, y1, "i")
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, "l")
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, "i")
    cv.rect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, "l")
    for (cx, cy) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.px(cx, cy, "l")
    if title:
        w = len(title) * 4 + 3
        cv.rect(x0 + 6, y0, x0 + 6 + w, y0 + 6, "i")
        tiny(x0 + 8, y0 + 1, title, "l")


HEART = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
NOTE = ["..###", "..#.#", "..#..", "###..", "###.."]
CAT_SIT = ["...#.....#...", "..###...###..", "..#########..", ".##.##.##.##.", ".###########.", "..#########.#", ".##########.#", ".###########.", "#############", "#.##.....##.#"]
CAT_RUN = ["....#.....#......", "...###...###.....", "...#########.....", "..##.##.##.##....", "..###########....", ".#############..#", "################.", ".#############...", "..###......###...", ".##..........##.."]


def dither_rect(x0, y0, x1, y1, f):
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            if 0 <= x < W and 0 <= y < H and not cv.lock[y, x]:
                cv.img[y, x] = cv.C["i"] if B4[y % 4, x % 4] < f else cv.C["l"]


def hud(y, left, right):
    cv.rect(0, y, W - 1, y + 10, "i")
    x = tiny(4, y + 3, left, "l")
    for k in range(3):
        sprite(x + 6 + k * 9, y + 2, HEART, "l")
    tiny(W - 4 - len(right) * 4, y + 3, right, "l")


def world_map(X0, Y0, X1, Y1):
    w, h = X1 - X0, Y1 - Y0
    for y in range(Y0, Y1):
        for x in range(X0, X1):
            if (y % 7 == 0 and (x + (y // 7) * 3) % 9 in (0, 1)) or (y % 7 == 1 and (x + (y // 7) * 3) % 9 == 2):
                cv.px(x, y, "i", hard=False)
    cx, cy = X0 + w * .5, Y0 + h * .52
    land = np.zeros((H, W), bool)
    for y in range(Y0 + 3, Y1 - 3):
        for x in range(X0 + 3, X1 - 3):
            a = math.atan2(y - cy, (x - cx) * (h / w))
            r = .43 * h + .05 * h * math.sin(a * 3 + 1) + .035 * h * math.sin(a * 5 + 2) + .02 * h * math.sin(a * 9)
            if math.hypot((x - cx) * (h / w), y - cy) < r:
                land[y, x] = True
    for y in range(Y0, Y1):
        for x in range(X0, X1):
            if land[y, x]:
                edge = not (land[y - 1, x] and land[y + 1, x] and land[y, x - 1] and land[y, x + 1])
                cv.px(x, y, "i" if edge else "l")
                if not edge and not land[y + 2, x] and (x + y) % 2 == 0:
                    cv.px(x, y, "i")     # a dithered beach below the shore
    for i in range(int(w * h / 90)):
        x, y = rng.integers(X0 + 6, X1 - 6), rng.integers(Y0 + 6, Y1 - 6)
        if land[y, x] and land[y + 3, x] and land[y - 3, x] and land[y, x + 3]:
            cv.px(x, y, "i"); cv.px(x + 2, y, "i"); cv.px(x + 1, y - 1, "i")
    P = lambda fx, fy: (int(X0 + fx * w), int(Y0 + fy * h))
    nodes = {"daily": P(.26, .3), "fm": P(.74, .24), "like": P(.82, .6), "chart": P(.6, .84), "recent": P(.22, .76), "dl": P(.5, .52)}
    for a, b in [("daily", "dl"), ("dl", "fm"), ("fm", "like"), ("like", "chart"), ("chart", "recent"), ("recent", "daily"), ("dl", "chart")]:
        (x0, y0), (x1, y1) = nodes[a], nodes[b]
        n = int(max(abs(x1 - x0), abs(y1 - y0)))
        for t in range(0, n, 3):
            cv.px(x0 + (x1 - x0) * t / n, y0 + (y1 - y0) * t / n, "i")
    TREE = ["..#..", ".###.", "#####", ".###.", "#####", "..#.."]
    for k in range(int(w * h / 1400)):
        x, y = rng.integers(X0 + 8, X1 - 8), rng.integers(Y0 + 8, Y1 - 12)
        if land[y + 7, x + 2] and land[y, x] and all(math.hypot(x - nx, y - ny) > 16 for nx, ny in nodes.values()):
            sprite(x, y, TREE)
    CASTLE = ["#.#.#......#.#.#", "#####......#####", ".###...##...###.", ".###..####..###.", ".##############.", ".###.##..##.###.", ".###.#oooo#.###.", ".###.#oooo#.###.", "################"]
    TOWER = ["...#...", "..#.#..", "...#...", "..###..", "..#.#..", ".#...#.", ".#.#.#.", "#.#.#.#", "#.#.#.#", "#######"]
    HOUSE = [".....##.....", "....####....", "...######...", "..########..", ".##########.", "############", ".#oo####oo#.", ".#oo####oo#.", ".##########."]
    MOUNT = [".....##.....", "....#..#....", "...#....#...", "..#..##..#..", ".#..#..#..#.", "#..#....#..#", "############"]
    CLOCK = ["..###..", ".#.#.#.", "#..#..#", "#..##.#", ".#...#.", "..###..", "..#.#..", "..#.#..", ".#####."]
    CAVE = ["...######...", "..#......#..", ".#..####..#.", "#..#oooo#..#", "#.#oooooo#.#", "############"]
    for key, spr in [("daily", CASTLE), ("fm", TOWER), ("like", HOUSE), ("chart", MOUNT), ("recent", CLOCK), ("dl", CAVE)]:
        x, y = nodes[key]
        sw, sh = len(spr[0]), len(spr)
        cv.rect(x - sw // 2 - 1, y - sh - 1, x + sw // 2 + 1, y + 1, "l")
        sprite(x - sw // 2, y - sh, spr, bg="l")
    fx, fy = nodes["fm"]
    for r in (5, 8):
        for a in np.linspace(-.9, .9, 7):
            cv.px(fx + r * math.sin(a), fy - 11 - r * math.cos(a) * .8, "i")
    sprite(nodes["like"][0] - 3, nodes["like"][1] - 18, HEART)
    mx, my = nodes["chart"]
    cv.rect(mx, my - 12, mx, my - 6, "i"); cv.rect(mx + 1, my - 12, mx + 4, my - 10, "i")
    for i, key in enumerate(["daily", "fm", "like", "chart", "recent", "dl"]):
        x, y = nodes[key]
        cv.rect(x - 7, y + 4, x + 5, y + 10, "i")
        tiny(x - 6, y + 5, f"1-{i + 1}", "l")
    d = nodes["daily"]
    sprite(d[0] + 10, d[1] - 10, CAT_SIT, bg="l")
    sprite(d[0] - 2, d[1] - 22, ["#####", ".###.", "..#.."])
    sprite(X0 + 8, Y1 - 14, ["...#....", "..##....", ".###....", "####....", "...#....", "########", ".######."])
    # Clouds drifting over the sea.
    for (x, y) in [(X0 + 20, Y0 + 10), (X1 - 50, Y1 - 24)]:
        sprite(x, y, ["...####.....", ".########...", "############", ".##########."], k="l")
        sprite(x, y - 1, ["...####.....", ".#....###...", "#..........#", ".##########."])


def stage(X0, Y0, X1, Y1, prog=.26):
    w, h = X1 - X0, Y1 - Y0
    ground = Y1 - max(16, int(h * .12))
    for y in range(Y0, ground):
        t = (y - Y0) / (ground - Y0)
        for x in range(X0, X1):
            if B4[y % 4, x % 4] < .04 + t * .24:
                cv.px(x, y, "i", hard=False)
    # The moon: the album art dithered to one bit inside a disc, with a halo ring.
    art = np.array(Image.open(f"{OUT}/covers/c1.png").convert("L").resize((80, 80), Image.LANCZOS), np.float32) / 255
    mr = int(min(w, h) * .17)
    mx, my = X1 - mr - int(w * .08), Y0 + mr + int(h * .1)
    for y in range(-mr - 4, mr + 5):
        for x in range(-mr - 4, mr + 5):
            d = math.hypot(x, y)
            px_, py_ = mx + x, my + y
            if d <= mr:
                v = art[min(79, int((y + mr) / (2 * mr) * 79)), min(79, int((x + mr) / (2 * mr) * 79))]
                cv.px(px_, py_, "l" if .2 + v * .95 > B4[py_ % 4, px_ % 4] else "i")
            elif d <= mr + 1.5:
                cv.px(px_, py_, "i")
            elif d <= mr + 3:
                cv.px(px_, py_, "l")
            elif d <= mr + 4.2 and (px_ + py_) % 2 == 0:
                cv.px(px_, py_, "l")
    for k in range(int(w * h / 900)):
        x, y = rng.integers(X0 + 4, X1 - 4), rng.integers(Y0 + 4, int(Y0 + (ground - Y0) * .45))
        if math.hypot(x - mx, y - my) > mr + 8:
            cv.px(x, y, "l"); cv.px(x - 1, y, "i"); cv.px(x + 1, y, "i"); cv.px(x, y - 1, "i"); cv.px(x, y + 1, "i"); cv.px(x, y, "l")
    for (cx_, cy_) in [(X0 + w * .12, Y0 + h * .22), (X0 + w * .45, Y0 + h * .14)]:
        sprite(int(cx_), int(cy_), ["....#####.....", "..##.....###..", ".#..........#.", "##############"], k="l")
        sprite(int(cx_), int(cy_), ["....#####.....", "..#.......#.#.", ".#............", "##............"])
    far = ground - (ground - Y0) * .3
    mid = ground - (ground - Y0) * .14
    for x in range(X0, X1):
        h1 = far + 12 * math.sin(x * .045 + 1) + 7 * math.sin(x * .12)
        for y in range(int(h1), ground):
            cv.px(x, y, "i", hard=False)
        cv.px(x, int(h1), "l")
        h2 = mid + 6 * math.sin(x * .08 + 3)
        for y in range(int(h2), ground):
            cv.px(x, y, "l" if (x + y) % 3 == 0 else "i")
        cv.px(x, int(h2), "l"); cv.px(x, int(h2) + 1, "i")
    for k in range(int(w / 64) + 1):
        lx = X0 + 18 + k * 64
        if lx > X1 - 6:
            break
        cv.rect(lx, ground - 22, lx, ground, "l"); cv.rect(lx - 2, ground - 24, lx + 2, ground - 22, "l"); cv.px(lx, ground - 21, "i")
        for r in (4, 7):
            for a in np.linspace(0, 2 * math.pi, 16):
                cv.px(lx + r * math.cos(a), ground - 23 + r * math.sin(a) * .7, "l")
    cv.rect(X0, ground, X1 - 1, Y1 - 1, "i")
    for y in range(ground + 1, Y1, 6):
        cv.line(X0, y, X1 - 1, y, "l")
        off = 0 if (y // 6) % 2 else 6
        for x in range(X0 + off, X1, 12):
            cv.line(x, y, x, min(y + 5, Y1 - 1), "l")
    bx = int(X0 + w * .34)
    for k in range(2):
        x0_ = bx + k * 12
        cv.rect(x0_, ground - 36, x0_ + 10, ground - 26, "i"); cv.rect(x0_ + 1, ground - 35, x0_ + 9, ground - 27, "l")
        sprite(x0_ + 3, ground - 34, NOTE)
    for k in range(7):
        nx, ny = int(X0 + w * .52) + k * 9, ground - 22 - int(12 * math.sin(k / 6 * math.pi))
        cv.rect(nx - 1, ny - 1, nx + 5, ny + 5, "l")
        sprite(nx, ny, NOTE)
    catx = int(X0 + w * .16)
    cv.rect(catx - 1, ground - 12, catx + 17, ground - 1, "l")
    sprite(catx, ground - 11, CAT_RUN, bg="l")
    for (dx, dy) in [(-4, -3), (-7, -5), (-9, -2)]:
        cv.px(catx + dx, ground + dy, "l")
    cv.rect(X1 - 6, ground - 34, X1 - 6, ground - 1, "l"); cv.rect(X1 - 13, ground - 34, X1 - 7, ground - 28, "l")
    return ground


def strip(y, X0, X1, prog):
    for x in range(X0, X1, 2):
        cv.px(x, y, "i")
    p = int(X0 + (X1 - X0) * prog)
    cv.rect(p - 1, y - 2, p + 1, y + 2, "i")
    cv.rect(X1 - 1, y - 3, X1, y + 3, "i"); cv.rect(X1 - 5, y - 3, X1 - 2, y - 1, "i")


def button(x0, y0, x1, y1, glyph, big=False, on=False):
    cv.rect(x0, y0, x1, y1, "i")
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, "i" if on else "l")
    cv.rect(x0 + 1, y1 - 2, x1 - 1, y1 - 1, "i")
    gw, gh = len(glyph[0]), len(glyph)
    sprite((x0 + x1) // 2 - gw // 2, (y0 + y1) // 2 - gh // 2 - 1, glyph, k="l" if on else "i")


G = dict(prev=["#....#", "#...##", "#..###", "#...##", "#....#"], next=["#....#", "##...#", "###..#", "##...#", "#....#"],
         pause=["##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##"], rep=[".####.", "#....#", "#.....", ".....#", "#....#", ".####."],
         list=["######", "......", "######", "......", "####.."], heart=HEART)

if MODE == "home":
    cv.line(0, 22, W - 1, 22, "i")
    hud(24, "WORLD 1", "x3420 LV08")
    frame(0, 37, W - 1, 222)
    world_map(4, 41, W - 4, 218)
    # Dialogue box for the level under the cat.
    frame(0, 226, W - 1, 282, "1-1")
    draw_text(cv, 10, 235, "每日推荐", "i")
    draw_text(cv, 10, 251, "30 首 · 今晚的关卡", "i")
    tiny(10, 270, "TRK 30 . 1:52:10", "i")
    cv.rect(W - 50, 262, W - 9, 276, "i")
    draw_text(cv, W - 46, 263, "开始", "l"); sprite(W - 18, 265, ["#...", "##..", "###.", "##..", "#..."], k="l")
    # Item bar: five slots.
    frame(0, 286, W - 1, 338, "ITEMS")
    ICONS = [("FM", ["#......", ".#.....", "..#####", ".#....#", ".#.##.#", ".#####."]), ("喜欢", HEART), ("排行", ["......#", "...#..#", "...#..#", "#..#..#", "#..#..#", "#######"]),
             ("最近", [".#####.", "#..#..#", "#..#..#", "#..##.#", "#.....#", ".#####."]), ("下载", ["...#...", "...#...", ".#####.", "..###..", "...#...", "#######"])]
    sw = (W - 16) // 5
    for k, (label, ic) in enumerate(ICONS):
        x0 = 8 + k * sw
        cv.rect(x0, 294, x0 + sw - 4, 332, "i"); cv.rect(x0 + 1, 295, x0 + sw - 5, 331, "l" if k else "i")
        for j, row in enumerate(ic):
            for i, c in enumerate(row):
                if c == "#":
                    cv.rect(x0 + (sw - 4) // 2 - 7 + i * 2, 298 + j * 2, x0 + (sw - 4) // 2 - 6 + i * 2, 299 + j * 2, "l" if k == 0 else "i")
        draw_text(cv, x0 + (sw - 4) // 2 - (6 if label.isascii() else 12), 314, label, "l" if k == 0 else "i")
    # Save files: playlists.
    frame(0, 342, W - 1, 390, "SAVE")
    for k, (name, meta) in enumerate([("深海", "32 TRK"), ("灯塔", "27 TRK")]):
        y0 = 348 + k * 20
        art = np.array(Image.open(f"{OUT}/covers/c{1 if k == 0 else 4}.png").convert("L").resize((16, 16), Image.LANCZOS), np.float32) / 241
        for yy in range(16):
            for xx in range(16):
                cv.px(10 + xx, y0 + yy, "l" if .1 + art[yy, xx] * 1.1 > B4[yy % 4, xx % 4] else "i")
        cv.rect(9, y0 - 1, 26, y0 - 1, "i"); cv.rect(9, y0 + 16, 26, y0 + 16, "i")
        tiny(32, y0 + 1, f"FILE {k + 1}", "i")
        draw_text(cv, 62, y0 + 1, name, "i")
        tiny(W - 40, y0 + 5, meta, "i")
    # Menu bar.
    cv.rect(0, 394, W - 1, 416, "i")
    sprite(14, 397, ["#...", "##..", "###.", "##..", "#..."], k="l")
    draw_text(cv, 20, 395, "首页", "l"); draw_text(cv, 84, 395, "搜索", "l"); draw_text(cv, 148, 395, "我的", "l")
    cv.save(f"{OUT}/g2_home_1x.png", f"{OUT}/g2_home.png", scale=2)
elif MODE == "play":
    cv.line(0, 22, W - 1, 22, "i")
    hud(24, "3-30", "TIME 058")
    tiny(70, 27, "SCORE 003580", "l")
    strip(42, 6, W - 7, .26)
    frame(0, 48, W - 1, 290)
    stage(4, 52, W - 4, 286)
    frame(0, 294, W - 1, 340)
    draw_text(cv, 10, 302, "只剩你的歌还亮着", "i")
    draw_text(cv, 10, 320, "雨落在霓虹的背面", "i")
    for x in range(10, 110):
        if x % 2:
            pass
    sprite(W - 16, 330, ["#####", ".###.", "..#.."])
    cv.rect(0, 344, W - 1, 368, "i")
    draw_text(cv, 8, 350, "月光下的鲸", "l")
    tiny(W - 64, 354, "3-30 . 4:05", "l")
    xs = [6, 42, 76, 128, 162]
    for k, (g, wdt) in enumerate([("rep", 28), ("prev", 28), ("pause", 44), ("next", 28), ("list", 28)]):
        x0 = [6, 40, 74, 124, 160][k]
        button(x0, 374 if g != "pause" else 372, x0 + wdt, 402 if g != "pause" else 406, G[g], on=(g == "pause"))
    cv.save(f"{OUT}/g2_play_1x.png", f"{OUT}/g2_play.png", scale=2)
else:
    M = 16   # keep clear of the rounded corners
    cv.rect(0, 0, W - 1, 10, "i")
    x = tiny(M + 4, 3, "3-30", "l")
    for k in range(3):
        sprite(x + 6 + k * 9, 2, HEART, "l")
    tiny(W - M - 4 - len("SCORE 003580  TIME 058") * 4, 3, "SCORE 003580  TIME 058", "l")
    strip(15, M + 6, W - M - 7, .26)
    frame(M - 4, 20, W - M + 3, 150)
    stage(M, 24, W - M, 146)
    frame(M - 4, 154, 296, H - 6)
    draw_text(cv, M + 6, 161, "只剩你的歌还亮着", "i")
    draw_text(cv, M + 6, 175, "雨落在霓虹的背面", "i")
    sprite(284, 182, ["#####", ".###.", "..#.."])
    for k, (g, wdt) in enumerate([("prev", 26), ("pause", 38), ("next", 26)]):
        x0 = [304, 334, 376][k]
        button(x0, 158 if g != "pause" else 155, x0 + wdt, 184 if g != "pause" else 187, G[g], on=(g == "pause"))
    cv.save(f"{OUT}/g2_land_1x.png", f"{OUT}/g2_land.png", scale=2)
print("ok", MODE)
