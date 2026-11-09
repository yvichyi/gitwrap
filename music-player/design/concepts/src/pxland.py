"""霓 · 夜行, the landscape player: 282 x 130 pixels. A wide windshield over the expressway, the dash below with the
instrument cluster (progress and volume dials), the centre screen and the radio-cassette head unit.

Usage: python3 -I pxland.py <out dir>
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pxlib import Canvas  # noqa: E402

OUT = sys.argv[1]
W, H = 282, 130
PAL = dict(k0="#05060a", k1="#0a0d14", k2="#10141e", k3="#171d2b", k4="#20283a", k5="#2b354b", k6="#3a4660", k7="#4f5d78", k8="#6d7e9a", k9="#9fb2c8", wh="#d8e4ee",
           a0="#2a1a14", a1="#4e2c1a", a2="#80461f", a3="#b8682a", a4="#e89a3e", a5="#ffc874", a6="#fff0c8",
           r0="#3a0e12", r1="#7a1a1e", r2="#d0302c", r3="#ff6a4a", t0="#0c2e33", t1="#16595a", t2="#2a9a8e", t3="#6fe0c8")
RAMPS = dict(amber=["a6", "a5", "a4", "a3", "a2", "a1", "a0"], red=["r3", "r2", "r1", "r0"], teal=["t3", "t2", "t1", "t0"], white=["wh", "k9", "k8", "k7", "k6"])
cv = Canvas(W, H, PAL, RAMPS, seed=12)
rng = cv.rng
GT, GB, VX, HZ = 6, 84, 141.0, 50.0
glass = (cv.YY >= GT) & (cv.YY <= GB)
lights = []


def P2(xn, yn, z):
    return VX + (xn - VX) / z, HZ + (yn - HZ) / z


# Sky bands with checkered seams, torn cloud lit from underneath.
bands = [(GT, 14, "k1"), (15, 22, "k2"), (23, 31, "k3"), (32, 40, "k4"), (41, 46, "k5"), (47, 50, "a1")]
prev = None
for (y0, y1, k) in bands:
    cv.rect(0, y0, W - 1, y1, k, hard=False)
    if prev:
        for x in range(0, W, 2):
            cv.px(x + (y0 % 2), y0, prev, hard=False)
    prev = k
for (cy, x0, x1) in [(12, 30, 120), (18, 150, 260), (25, 40, 100), (30, 180, 240), (36, 70, 130)]:
    for x in range(x0, x1):
        wob = int(math.sin(x * .3 + cy) * 1.2)
        cv.px(x, cy + wob, "k3" if cy < 22 else "k4", hard=False)
        if (x + cy) % 3:
            cv.px(x, cy + wob + 1, "a0" if cy > 20 else "k2", hard=False)


def tower(x0, x1, top, body, win, lit, crown=None, red=False, edge=None):
    cv.rect(x0, top, x1, HZ, body, hard=False)
    if edge:
        cv.rect(x0, top, x0, HZ, edge, hard=False)
    for y in range(top + 3, int(HZ) - 1, 2):
        for x in range(x0 + 1, x1, 2):
            if rng.random() < lit:
                cv.px(x, y, win if rng.random() < .8 else "k6", hard=False)
    if crown:
        for x in range(x0, x1 + 1, 2):
            cv.px(x, top, crown, hard=False)
    if red:
        cx = (x0 + x1) // 2
        cv.rect(cx, top - 4, cx, top - 1, body, hard=False)
        cv.px(cx, top - 5, "r3", hard=False)
        for dx, dy in ((-1, 0), (1, 0), (0, -1)):
            cv.px(cx + dx, top - 5 + dy, "r1", hard=False)


x = 20
while x < W - 20:
    w, h = int(rng.integers(3, 9)), int(rng.integers(3, 13))
    tower(x, x + w, int(HZ - h), "k4", "a2", .3)
    x += w + int(rng.integers(0, 2))
for (x0, w, top, crown, red, edge) in [(30, 9, 24, None, True, 1), (44, 6, 32, None, False, 0), (56, 11, 16, "a3", False, 1), (74, 6, 28, None, True, 0),
                                       (90, 8, 22, None, False, 1), (160, 10, 14, "t2", True, 1), (176, 7, 28, None, False, 0), (188, 9, 20, "a3", False, 1),
                                       (204, 6, 30, None, True, 0), (216, 10, 18, None, False, 1), (234, 7, 26, None, False, 0)]:
    tower(x0, x0 + w, top, "k1", "a3", .24, crown, red, "k3" if edge else None)
# A vertical neon sign on one tower.
SX = 222
cv.rect(SX - 1, 22, SX + 3, 38, "k0")
cv.glow(SX + 1, 30, "teal", 8, start=2)
for j, row in enumerate(["#.#", "###", ".#.", "#.#", "...", "###", "#.#", "###", "#.#", "#.#"]):
    for i, ch in enumerate(row):
        if ch == "#":
            cv.px(SX + i, 23 + j + (1 if j > 4 else 0), "t3")
lights.append((SX + 1, HZ + 1, "teal", 1, .6))
# The expressway deck, barriers with reflectors, the lane line, lamp posts on the left.
cv.poly([P2(-60, GB, 1), P2(-60, GB, 60), P2(342, GB, 60), P2(342, GB, 1)], "k1", hard=False)
for xn in (14, 268):
    for z in np.linspace(1, 60, 500):
        x0_, y0_ = P2(xn, GB, z)
        x1_, y1_ = P2(xn, GB - 12, z)
        for yy in range(int(round(y1_)), int(round(y0_)) + 1):
            if GT <= yy <= GB and 0 <= int(round(x0_)) < W:
                cv.px(x0_, yy, "k5" if yy == int(round(y1_)) else "k3", hard=False)
    for z in [1.3, 1.8, 2.5, 3.5, 5, 7, 10, 14]:
        rx, ry = P2(xn, GB - 7, z)
        cv.px(rx, ry, "a4" if z < 4 else "a3", hard=False)
zz = 1.1
while zz < 30:
    cv.line(*P2(141, GB, zz), *P2(141, GB, zz * 1.14), "k6" if zz < 4 else "k4", hard=False)
    zz *= 1.38
for z in [1.15, 1.7, 2.6, 4.0, 6.0, 9.0, 13.0]:
    base, top, head = P2(14, GB - 12, z), P2(14, -60, z), P2(60, -58, z)
    top = (top[0], max(top[1], GT))
    if z < 7:
        cv.line(*base, *top, "k2" if z < 2 else "k3", hard=False)
        cv.line(*top, *head, "k2" if z < 2 else "k3", hard=False)
    cv.glow(head[0], head[1], "amber", 1.5 + 7 / z, start=1)
    cv.px(*head, "a6")
    lights.append((head[0], P2(60, GB, z)[1], "amber", 1, .8 * min(1, 2.4 / z)))
# The overhead gantry: 澜音 straight on, 3 km.
gz = 4.4
g0, g1 = P2(14, -10, gz), P2(268, -10, gz)
cv.line(*P2(14, GB - 12, gz), *g0, "k3", hard=False); cv.line(*P2(268, GB - 12, gz), *g1, "k3", hard=False); cv.line(*g0, *g1, "k3", hard=False)
gx0, gy0 = int(VX + 4), int(g0[1]) + 1
cv.rect(gx0, gy0, gx0 + 22, gy0 + 8, "t1"); cv.rect(gx0, gy0, gx0 + 22, gy0, "t2")
for (dx, dy) in [(2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (1, 3), (3, 3)]:
    cv.px(gx0 + dx, gy0 + dy, "wh")
for (dx, dy) in [(6, 2), (7, 2), (8, 2), (6, 4), (8, 4), (7, 6), (10, 2), (12, 2), (11, 3), (11, 5), (10, 6), (12, 6), (15, 2), (16, 3), (15, 5), (16, 6), (19, 3), (19, 5), (18, 6), (20, 6)]:
    cv.px(gx0 + dx, gy0 + dy, "k9")
CAR = ["..##########..", ".#3333333333#.", "#344444444443#", "##############", "#rR########Rr#", "#22222pp22222#", "##############", ".00........00."]
CMAP = {"#": "k0", "3": "k3", "4": "k4", "2": "k2", "p": "k7", "0": "k0", "r": "r2", "R": "r2"}


def car(cx, yb):
    h = len(CAR)
    x0 = int(cx - len(CAR[0]) / 2)
    for j, row in enumerate(CAR):
        for i, ch in enumerate(row):
            if ch in CMAP:
                cv.px(x0 + i, yb - h + j, CMAP[ch])
    for xx in (x0 + 1.5, x0 + len(CAR[0]) - 2.5):
        cv.glow(xx, yb - h + 4, "red", 3.5, start=1)
        lights.append((xx, yb + 1, "red", 1, .95))


car(160, 70)
x0 = 118
cv.rect(x0, 56, x0 + 5, 58, "k0"); cv.rect(x0 + 1, 55, x0 + 4, 55, "k0"); cv.px(x0, 57, "r2"); cv.px(x0 + 5, 57, "r2")
cv.glow(x0, 57, "red", 2.2, 1); cv.glow(x0 + 5, 57, "red", 2.2, 1); lights.append((x0 + 2.5, 59, "red", 1, .6))
for xx in (P2(70, 76, 9)[0], P2(90, 76, 9)[0]):
    cv.glow(xx, P2(70, 76, 9)[1], "white", 2.5, 1); cv.px(xx, P2(70, 76, 9)[1], "wh")
# Wet deck.
for (lx, ly, ramp, start, k) in lights:
    if ly < HZ or ly > GB:
        continue
    cols = RAMPS[ramp][start + 1:]
    L = int((5 + (ly - HZ) * 1.1) * k) + 2
    for i in range(L):
        yy = int(ly + 1 + i)
        if yy > GB:
            break
        xx = int(round(lx))
        lvl = min(len(cols) - 1, int(i / max(L, 1) * len(cols)))
        if (yy % 3 == 1 and i > 2) or not (0 <= xx < W) or cv.lock[yy, xx]:
            continue
        c = cv.C[cols[lvl]]
        if cv.lum(c) > cv.lum(cv.img[yy, xx]):
            cv.img[yy, xx] = c
# Rain, drops.
for i in range(140):
    x0_, y0_ = int(rng.uniform(0, W)), int(rng.uniform(GT, GB - 3))
    for k in range(3):
        xx, yy = x0_ - (k // 2), y0_ + k
        if 0 <= xx < W and not cv.lock[yy, xx]:
            c = cv.C["k5"] if cv.lum(cv.img[yy, xx]) < .1 else cv.C["k7"]
            if cv.lum(c) > cv.lum(cv.img[yy, xx]):
                cv.img[yy, xx] = c
for i in range(80):
    x0_, y0_ = int(rng.uniform(24, W - 24)), int(rng.uniform(GT + 3, GB - 6))
    if (x0_ - 60) ** 2 + (y0_ - 100) ** 2 < 85 ** 2 and rng.random() < .8:
        continue
    under = cv.lum(cv.img[y0_, x0_])
    cv.px(x0_, y0_, "k9" if under > .2 else "k7" if under > .06 else "k5", hard=False)
    cv.px(x0_ + 1, y0_, "k4" if under < .1 else "k6", hard=False)
    cv.px(x0_, y0_ + 1, "k0", hard=False); cv.px(x0_ + 1, y0_ + 1, "k0", hard=False)
# Wipers, mid-sweep.
cv.line(40, 86, 100, 40, "k0"); cv.line(39, 86, 99, 40, "k3")
cv.line(150, 86, 230, 80, "k0"); cv.line(150, 85, 230, 79, "k3")
# Roof, visors, mirror with the freshener, A-pillars.
for x in range(W):
    edge = 4 + ((x - 141) / 141) ** 2 * 4
    for y in range(0, int(edge) + 1):
        cv.px(x, y, "k2" if (x + y * 3) % 7 else "k3")
cv.rect(30, 3, 96, 9, "k3"); cv.line(30, 9, 96, 9, "k1"); cv.line(31, 3, 95, 3, "k5")
cv.rect(186, 3, 252, 9, "k3"); cv.line(186, 9, 252, 9, "k1"); cv.line(187, 3, 251, 3, "k5")
cv.rect(40, 4, 47, 9, "wh"); cv.rect(41, 5, 46, 8, "k6"); cv.px(43, 7, "a4")
cv.rect(140, 2, 142, 6, "k1")
cv.rect(124, 6, 158, 14, "k0"); cv.rect(125, 7, 157, 13, "k3"); cv.rect(126, 8, 156, 12, "k2"); cv.rect(126, 11, 156, 12, "k1")
cv.px(136, 10, "wh"); cv.px(146, 10, "wh"); cv.line(125, 14, 157, 14, "k5")
cv.line(150, 15, 151, 19, "k6")
for r, (y, w) in enumerate([(20, 1), (21, 2), (22, 3), (23, 2), (24, 3), (25, 4), (26, 2)]):
    cv.rect(151 - w, y, 151 + w, y, "t1" if r % 2 else "t2")
cv.poly([(0, 2), (12, 2), (24, GB + 2), (0, GB + 2)], "k1")
cv.poly([(W, 2), (W - 12, 2), (W - 24, GB + 2), (W, GB + 2)], "k1")
cv.line(12, 3, 24, GB + 1, "k5"); cv.line(W - 13, 3, W - 25, GB + 1, "a1")
# Dash.
for x in range(W):
    top = GB - 1 + int(((x - 141) / 141) ** 2 * 3)
    cv.rect(x, top, x, H - 1, "k2")
    cv.px(x, top, "k5" if x % 9 else "a1")
for x in list(range(40, 70, 2)) + list(range(212, 242, 2)):
    cv.px(x, GB + 3, "k0"); cv.px(x, GB + 4, "k0")
for y in range(92, H):
    cv.rect(0, y, W - 1, y, "k1", hard=False)
# Instrument cluster: two dials in a hood. Left = song progress (0-4 min), right = volume.
cv.rect(10, 90, 108, 128, "k0")
cv.line(10, 90, 108, 90, "k5")


def dial(cx, cy, r, frac, ramp_hi):
    cv.disc(cx, cy, r + 1, "k2")
    cv.disc(cx, cy, r, "k0")
    for i in range(0, 41):
        a = math.radians(135 + 270 * i / 40)
        rr = r - (3 if i % 10 == 0 else 1)
        cv.line(cx + rr * math.cos(a), cy + rr * math.sin(a), cx + r * math.cos(a), cy + r * math.sin(a), "k8" if i % 10 == 0 else "k5")
    for i in range(0, int(40 * frac) + 1):
        a = math.radians(135 + 270 * i / 40)
        cv.px(cx + (r - 1) * math.cos(a), cy + (r - 1) * math.sin(a), "a4")
    a = math.radians(135 + 270 * frac)
    cv.line(cx, cy, cx + (r - 3) * math.cos(a), cy + (r - 3) * math.sin(a), "r3")
    cv.disc(cx, cy, 2, "k4")


dial(36, 109, 16, .26, "amber")
dial(82, 109, 16, .58, "amber")
# Centre screen bezel.
cv.rect(112, 89, 202, 128, "k0"); cv.line(112, 88, 202, 88, "k5"); cv.rect(114, 91, 200, 126, "k1")
# Head unit.
cv.rect(206, 90, 274, 128, "k0"); cv.rect(207, 91, 273, 127, "k4"); cv.line(207, 91, 273, 91, "k6")
cv.rect(214, 94, 266, 106, "k0"); cv.rect(215, 95, 265, 105, "t0")
for i in range(6):
    bx = 210 + i * 11
    cv.rect(bx, 112, bx + 9, 121, "k2"); cv.rect(bx, 112, bx + 9, 120, "k5"); cv.line(bx, 112, bx + 9, 112, "k7")
    if i == 1:
        cv.px(bx + 4, 114, "a5"); cv.px(bx + 5, 114, "a4")
cv.rect(222, 108, 258, 109, "k0")
cv.save(f"{OUT}/px_land_1x.png", f"{OUT}/px_land.png")
print("ok land")
