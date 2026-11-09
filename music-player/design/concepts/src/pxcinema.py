"""影 · 老电影院, pixel art. home: the cinema's front on a clear night, marquee bulbs, letterboard, poster boxes,
ticket booth, a red carpet. play: inside the auditorium from the back rows, the projector beam over our heads.

Usage: python3 -I pxcinema.py <out dir> <home|play>
"""
import math
import sys

import numpy as np

import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pxlib import Canvas, draw_text  # noqa: E402

OUT, MODE = sys.argv[1], sys.argv[2]
W, H = 130, 282
PAL = dict(
    k0="#07050a", k1="#120c14", k2="#1d1420", k3="#2a1d2c", k4="#3b2a3c", k5="#54405a",
    n0="#0e1024", n1="#1a1e3a", n2="#2a3058", n3="#3e4878",
    m0="#2e0f16", m1="#4f1822", m2="#7a2230", m3="#a8323c", m4="#d0544e",
    g0="#3a2610", g1="#6b4518", g2="#a8742a", g3="#e0ac4a", g4="#ffe08a", g5="#fff6d8",
    c0="#b3a07c", c1="#e8dcc0",
    s0="#4a5470", s1="#8e98b4", s2="#cfd6e6",
    e0="#0f3a26", e1="#1e7a4a", e2="#6ee59a",
)
RAMPS = dict(gold=["g5", "g4", "g3", "g2", "g1", "g0"], red=["m4", "m3", "m2", "m1", "m0"], cool=["s2", "s1", "s0", "n2", "n1"], green=["e2", "e1", "e0"])
cv = Canvas(W, H, PAL, RAMPS, seed=3 if MODE == "home" else 5)
rng = cv.rng
C = cv.C


def bulbs(x0, y0, x1, y1, phase=0):
    """A row or column of marquee bulbs: every other one lit, as if the chase has just moved on."""
    n = int(max(abs(x1 - x0), abs(y1 - y0)))
    for i in range(0, n + 1, 2):
        t = i / max(n, 1)
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        lit = ((i // 2) + phase) % 3 != 0
        cv.px(x, y, "g5" if lit else "g1")



def paste_cover(src, x0, y0, size, gain=1.15):
    """Album art locked to this palette at pixel scale, pasted into the picture."""
    from PIL import Image
    a = np.clip(np.array(Image.open(src).convert("RGB").resize((size, size), Image.LANCZOS), np.float32) / 255 * gain, 0, 1)
    B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - .5
    thr = np.tile(B4, (size // 4 + 1, size // 4 + 1))[:size, :size]
    src_ = np.clip(a + thr[..., None] * .08, 0, 1)
    d = ((src_[:, :, None, :] - cv.P[None, None]) ** 2 * np.array([.3, .59, .11])).sum(-1)
    q = cv.P[d.argmin(-1)]
    cv.img[y0:y0 + size, x0:x0 + size] = q
    cv.lock[y0:y0 + size, x0:x0 + size] = True


if MODE == "home":
    # ── night sky ──
    for y in range(0, 70):
        k = "n0" if y < 20 else "n1" if y < 46 else "n2"
        cv.rect(0, y, W - 1, y, k, hard=False)
        if y in (20, 46):
            for x in range(0, W, 2):
                cv.px(x + (y // 2) % 2, y, "n0" if y == 20 else "n1", hard=False)
    for i in range(26):
        x, y = rng.integers(0, W), rng.integers(0, 40)
        cv.px(x, y, "s2" if rng.random() < .3 else "s0", hard=False)
    # A thin moon.
    cv.disc(18, 14, 5, "c1"); cv.disc(20, 13, 5, "n0")
    # ── the building: an art deco front, stepped up to a central tower ──
    cv.rect(2, 26, W - 3, 240, "k3")
    cv.rect(38, 12, 92, 26, "k3")
    cv.rect(46, 4, 84, 12, "k3")
    for (x0, y0, x1) in [(2, 26, W - 3), (38, 12, 92), (46, 4, 84)]:
        cv.line(x0, y0, x1, y0, "k5"); cv.line(x0, y0 + 1, x1, y0 + 1, "g1")
    # A sunburst crown on the tower.
    for a in range(-80, 81, 20):
        r = math.radians(a - 90)
        cv.line(65, 12, 65 + 14 * math.cos(r), 12 + 14 * math.sin(r) * .55, "g2", hard=False)
    cv.disc(65, 12, 3, "g3"); cv.px(65, 11, "g5")
    # Pilasters and fluting.
    for x in (6, 30, 99, 123):
        cv.rect(x, 28, x + 2, 238, "k4"); cv.line(x, 28, x, 238, "k5"); cv.line(x + 2, 28, x + 2, 238, "k2")
    for x in range(10, 28, 4):
        cv.line(x, 34, x, 90, "k2")
    for x in range(103, 121, 4):
        cv.line(x, 34, x, 90, "k2")
    # Upper windows, a few lit warm behind blinds.
    for (x0, y0) in [(11, 40), (19, 40), (11, 60), (19, 60), (104, 40), (112, 40), (104, 60), (112, 60)]:
        cv.rect(x0, y0, x0 + 5, y0 + 10, "k1")
        if rng.random() < .5:
            cv.rect(x0 + 1, y0 + 1, x0 + 4, y0 + 9, "g1")
            for yy in range(y0 + 2, y0 + 9, 2):
                cv.line(x0 + 1, yy, x0 + 4, yy, "g2")
    # ── the vertical sign: 澜 音 影 院 in bulbs ──
    sx0, sx1, sy0, sy1 = 54, 76, 16, 92
    cv.rect(sx0 - 1, sy0 - 1, sx1 + 1, sy1, "k0")
    cv.rect(sx0, sy0, sx1, sy1, "m1")
    cv.rect(sx0 + 2, sy0 + 2, sx1 - 2, sy1 - 2, "m2")
    bulbs(sx0, sy0, sx1, sy0, 0); bulbs(sx0, sy0, sx0, sy1, 1); bulbs(sx1, sy0, sx1, sy1, 2)
    for i, ch in enumerate("澜音影院"):
        draw_text(cv, sx0 + 5, sy0 + 4 + i * 18, ch, "g4", shadow="m0")
    cv.glow(65, 54, "red", 22, start=2, clip=cv.lock == False)  # noqa: E712
    # ── the marquee canopy ──
    cv.poly([(0, 96), (W - 1, 96), (W - 1, 134), (0, 134)], "k1")
    cv.rect(0, 94, W - 1, 96, "g1")
    bulbs(0, 95, W - 1, 95, 1)
    cv.rect(4, 99, W - 5, 128, "g1"); cv.rect(5, 100, W - 6, 127, "c1")
    for x in range(6, W - 6, 1):          # the letterboard's ribbed felt shows between rows
        if x % 2 == 0:
            cv.px(x, 113, "c0"); cv.px(x, 126, "c0")
    draw_text(cv, 9, 100, "今日放映", "m2")
    draw_text(cv, 61, 100, "每日推荐", "k1")
    cv.px(56, 107, "k1")
    draw_text(cv, 9, 114, "19:30 FM 21:00 榜单", "k1")
    bulbs(0, 132, W - 1, 132, 2)
    cv.rect(0, 133, W - 1, 135, "k0")
    for x in range(8, W, 12):     # down-lights under the canopy
        cv.px(x, 136, "g4")
    # ── the entrance ──
    cv.rect(2, 136, W - 3, 240, "k2")
    # Light pouring down from the canopy onto the wall.
    for x in range(8, W, 12):
        for y in range(137, 150):
            if (y - 137) < 10 - abs(0) and cv.checker(x + y, y) and not cv.lock[y, x]:
                cv.px(x, y, "g0", hard=False)
    # Poster boxes with bulbs, left and right; the page puts the posters in them.
    for (x0, x1) in ((6, 34), (96, 124)):
        cv.rect(x0 - 2, 142, x1 + 2, 204, "g1")
        cv.rect(x0 - 1, 143, x1 + 1, 203, "g2")
        cv.rect(x0, 144, x1, 202, "k0")
        bulbs(x0 - 2, 141, x1 + 2, 141, 0); bulbs(x0 - 2, 205, x1 + 2, 205, 1)
        cv.glow((x0 + x1) / 2, 173, "gold", 22, start=4)
        paste_cover(f"{OUT}/covers/c{1 if x0 < 50 else 4}.png", x0 + 1, 146, 27)
        cv.rect(x0 + 1, 174, x1 - 1, 174, "g1")
    # Doors with warm glass and brass bars.
    for (x0, x1) in ((38, 50), (80, 92)):
        cv.rect(x0 - 1, 150, x1 + 1, 236, "g1")
        cv.rect(x0, 151, x1, 236, "g2")
        cv.rect(x0 + 1, 152, x1 - 1, 200, "g3"); cv.rect(x0 + 1, 202, x1 - 1, 235, "g2")
        cv.line(x0 + 1, 176, x1 - 1, 176, "g1")
        for yy in range(154, 200, 3):
            cv.px(x0 + 2, yy, "g4")
        cv.rect(x0 + (x1 - x0) // 2, 151, x0 + (x1 - x0) // 2, 236, "g1")
        cv.rect(x0 + (x1 - x0) // 2 - 2, 186, x0 + (x1 - x0) // 2 + 2, 186, "g4")
    # The ticket booth between the doors: an arched window, the cashier reading under a lamp.
    cv.rect(53, 150, 77, 236, "m1")
    cv.rect(52, 148, 78, 150, "g2"); cv.rect(51, 147, 79, 147, "g3")
    cv.rect(56, 166, 74, 186, "g2")
    cv.rect(57, 167, 73, 185, "g4")
    for x in range(56, 75):
        for y in range(150, 161):
            if (x - 65) ** 2 + (y - 161) ** 2 > 81 and y < 161:
                pass
    cv.disc(65, 174, 3, "k2"); cv.rect(61, 178, 69, 185, "k2"); cv.rect(60, 181, 70, 185, "k2")
    cv.px(70, 170, "g5"); cv.line(69, 168, 71, 168, "g2")
    cv.rect(58, 182, 72, 185, "g1")
    cv.rect(55, 150, 75, 163, "k0"); cv.line(55, 163, 75, 163, "g2")
    draw_text(cv, 59, 151, "售票", "g4")
    cv.rect(56, 186, 74, 189, "g2"); cv.rect(60, 189, 70, 189, "k0")
    for y in range(194, 236, 6):
        cv.line(54, y, 76, y, "m0")
    # Tiled base along the wall.
    for y in range(206, 240):
        for x in list(range(4, 36)) + list(range(94, 126)):
            k = "m1" if ((x // 4) + (y // 4)) % 2 else "m0"
            cv.px(x, y, k, hard=False)
    cv.line(2, 206, 36, 206, "g1"); cv.line(94, 206, W - 3, 206, "g1")
    # ── the pavement, a red carpet, brass posts and velvet rope ──
    for y in range(240, H):
        for x in range(W):
            k = "k1" if (x // 8 + y // 6) % 2 else "k2"
            cv.px(x, y, k, hard=False)
    cv.poly([(42, 240), (88, 240), (100, H), (30, H)], "m2")
    cv.line(42, 240, 30, H, "g2"); cv.line(88, 240, 100, H, "g2")
    for y in range(244, H, 6):
        cv.line(42 - (y - 240) * 12 / 42 + 3, y, 88 + (y - 240) * 12 / 42 - 3, y, "m1")
    for (x, y) in ((28, 246), (102, 246)):
        cv.rect(x - 1, y - 12, x + 1, y, "g2"); cv.px(x, y - 13, "g4"); cv.rect(x - 2, y, x + 2, y + 1, "g1")
    for (xa, xb) in ((28, 4), (102, 126)):
        for i in range(0, 25):
            t = i / 24
            cv.px(xa + (xb - xa) * t, 236 + math.sin(t * math.pi) * 5, "m3")
    # Reflections of the marquee on the pavement.
    for x in range(4, W - 4, 3):
        for y in range(242, 262):
            if cv.checker(x, y) and rng.random() < .25 and not cv.lock[y, x]:
                cv.px(x, y, "g0", hard=False)
    cv.save(f"{OUT}/cin_home_1x.png", f"{OUT}/cin_home.png")
else:
    # ── the auditorium ──
    cv.rect(0, 0, W - 1, H - 1, "k1")
    for y in range(0, 22):
        cv.rect(0, y, W - 1, y, "k1" if y > 4 else "k0")
    # Coffered ceiling with dim sconces.
    for x in range(0, W, 16):
        cv.line(x, 0, x + 4, 18, "k2")
    for x in (12, 118):
        cv.rect(x - 2, 40, x + 2, 46, "g1"); cv.px(x, 41, "g3"); cv.glow(x, 42, "gold", 7, start=3)
    # The proscenium, the screen and the velvet curtains.
    cv.rect(16, 20, 114, 92, "g1")
    cv.rect(17, 21, 113, 91, "k0")
    for x in range(18, 113, 4):
        cv.px(x, 20, "g3")
    SX0, SY0, SX1, SY1 = 22, 26, 108, 86
    cv.rect(SX0, SY0, SX1, SY1, "s0")
    cv.rect(SX0 + 1, SY0 + 1, SX1 - 1, SY1 - 1, "k0")
    for side in (-1, 1):
        x0 = 0 if side < 0 else 112
        for x in range(18):
            for y in range(18, 150):
                fold = (x + (y // 40)) % 6
                k = "m3" if fold in (1, 2) else "m2" if fold in (0, 3) else "m1"
                if side < 0 and x > 13 - (y - 18) * .02:
                    k = "m1"
                xx = x0 + x
                if side > 0 and x < 4 + (y - 18) * .02:
                    k = "m1"
                cv.px(xx, y, k)
        # Gold tie-back and tassel.
        tx = 10 if side < 0 else 120
        cv.rect(tx - 4, 104, tx + 4, 106, "g3"); cv.px(tx, 107, "g4"); cv.rect(tx - 1, 108, tx + 1, 114, "g2")
    # The pelmet across the top.
    cv.rect(0, 16, W - 1, 22, "m2")
    for x in range(0, W, 6):
        cv.rect(x, 22, x + 3, 24, "m2"); cv.px(x + 1, 25, "g3")
    cv.line(0, 16, W - 1, 16, "g3")
    paste_cover(f"{OUT}/covers/c3.png", 43, 28, 44, gain=1.25)
    # The projector beam, from the booth behind us: a dusty cone over everything dark, motes over the picture.
    for y in range(0, SY1):
        t = (y + 30) / (SY1 + 30)
        xl, xr = 65 + (SX0 - 65) * t, 65 + (SX1 - 65) * t
        for x in range(int(xl), int(xr) + 1):
            if not (0 <= x < W):
                continue
            here = cv.img[y, x]
            if cv.lum(here) < .05 and (x + 2 * y) % 4 == 0 and y % 2 == 0:
                cv.img[y, x] = C["n1"]
    for i in range(70):
        y = rng.uniform(2, SY1 - 2); t = (y + 30) / (SY1 + 30)
        x = rng.uniform(65 + (SX0 - 65) * t, 65 + (SX1 - 65) * t)
        cv.img[int(y), int(x)] = C["s2"] if rng.random() < .25 else C["s1"] if rng.random() < .6 else C["s0"]
    # Side walls: an exit sign glowing green, little lamps marking the steps down the aisle.
    cv.rect(0, 150, 18, 200, "k2"); cv.rect(112, 150, W - 1, 200, "k2")
    cv.rect(3, 128, 15, 134, "e0"); cv.rect(4, 129, 14, 133, "e1")
    for (x, y) in [(5, 130), (5, 131), (5, 132), (6, 130), (6, 132), (8, 130), (8, 132), (9, 131), (10, 130), (10, 132), (12, 130), (12, 131), (12, 132), (13, 130), (14, 130)]:
        cv.px(x, y, "e2")
    cv.glow(9, 131, "green", 10, start=2)
    # Rows of seats, lit along their tops by the screen.
    rows = [(100, 9, 6), (116, 10, 7), (134, 11, 8), (154, 13, 9), (178, 15, 11)]
    for r, (y, sw, sh) in enumerate(rows):
        x = -((r * 5) % sw)
        while x < W:
            cv.rect(x + 1, y, x + sw - 2, y + sh + 6, "k2")
            cv.rect(x + 2, y - 1, x + sw - 3, y - 1, "k2")
            cv.line(x + 2, y - 1, x + sw - 3, y - 1, "s0" if r < 3 else "k4")
            cv.px(x + 1, y, "k4")
            x += sw
    # People: a couple leaning together, someone alone, a tall one with a hat.
    def head(x, y, r=3, hat=False):
        cv.disc(x, y, r, "k0"); cv.rect(x - r - 1, y + r - 1, x + r + 1, y + r + 4, "k0")
        cv.line(x - r + 1, y - r, x + r - 1, y - r, "s0")
        if hat:
            cv.rect(x - r - 1, y - r - 1, x + r + 1, y - r, "k0"); cv.rect(x - r + 1, y - r - 3, x + r - 1, y - r - 1, "k0")
    head(46, 108); head(52, 109)
    head(88, 128, 4)
    head(28, 148, 4, hat=True)
    head(104, 108, 3)
    # Our row: armrests and a striped popcorn bucket.
    cv.rect(0, 206, W - 1, H - 1, "k0")
    cv.rect(0, 200, 10, 210, "k2"); cv.rect(W - 11, 200, W - 1, 210, "k2")
    cv.poly([(108, 244), (126, 244), (124, 270), (110, 270)], "c1")
    for x in range(108, 127, 4):
        cv.poly([(x, 244), (x + 2, 244), (x + 1.5, 270), (x + .5, 270)], "m3")
    for i in range(14):
        cv.disc(109 + i * 1.3, 242 - (i % 3), 2, "g4" if i % 2 else "c1")
    cv.save(f"{OUT}/cin_play_1x.png", f"{OUT}/cin_play.png")
print("ok", MODE)
