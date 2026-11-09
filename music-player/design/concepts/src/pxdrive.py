"""霓 · 夜行, as pixel art. Draws the whole phone screen at 130 x 282 logical pixels: a wide windshield across
the top (rainy street at night), the car's interior below it. Soft light is painted in float and then locked to a
fixed palette with 4x4 ordered dithering, so every glow becomes stepped bands of real palette colours.

Usage: python3 -I pxdrive.py <out dir> <home|play>
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

OUT, MODE = sys.argv[1], sys.argv[2]
W, H = 130, 282
rng = np.random.default_rng(4 if MODE == "home" else 9)


def hx(s):
    return np.array([int(s[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255


PAL = [hx(c) for c in [
    "#05060a", "#0a0d14", "#10141e", "#171d2b", "#20283a", "#2b354b", "#3a4660", "#4f5d78", "#6d7e9a", "#9fb2c8", "#d8e4ee",
    "#1a1210", "#2a1a14", "#4e2c1a", "#80461f", "#b8682a", "#e89a3e", "#ffc874", "#fff0c8",
    "#3a0e12", "#7a1a1e", "#d0302c", "#ff6a4a",
    "#0c2e33", "#16595a", "#2a9a8e", "#6fe0c8",
]]
P = np.stack(PAL)
C = {k: hx(v) for k, v in dict(
    k0="#05060a", k1="#0a0d14", k2="#10141e", k3="#171d2b", k4="#20283a", k5="#2b354b", k6="#3a4660", k7="#4f5d78", k8="#6d7e9a", k9="#9fb2c8", wh="#d8e4ee",
    a0="#2a1a14", a1="#4e2c1a", a2="#80461f", a3="#b8682a", a4="#e89a3e", a5="#ffc874", a6="#fff0c8",
    r0="#3a0e12", r1="#7a1a1e", r2="#d0302c", r3="#ff6a4a", t0="#0c2e33", t1="#16595a", t2="#2a9a8e", t3="#6fe0c8").items()}

img = np.zeros((H, W, 3), np.float32)
lock = np.zeros((H, W), bool)          # pixels drawn as exact palette colours: never dithered
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def poly(pts, col, hard=True, alpha=1.0):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    a = np.array(m, np.float32)[..., None] / 255 * alpha
    img[:] = img * (1 - a) + col * a
    if hard:
        lock[a[..., 0] > .5] = alpha >= 1


def rect(x0, y0, x1, y1, col, hard=True):
    x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
    if x1 < x0 or y1 < y0:
        return
    img[max(y0, 0):min(y1 + 1, H), max(x0, 0):min(x1 + 1, W)] = col
    if hard:
        lock[max(y0, 0):min(y1 + 1, H), max(x0, 0):min(x1 + 1, W)] = True


def px(x, y, col, hard=True):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < W and 0 <= y < H:
        img[y, x] = col
        if hard:
            lock[y, x] = True


def line(x0, y0, x1, y1, col, hard=True):
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / max(n, 1)
        px(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, col, hard)


def glow(x, y, r, col, k=1.0, region=None):
    d2 = ((XX - x) ** 2 + ((YY - y) * 1.0) ** 2) / (r * r)
    g = np.exp(-d2 * 2.2) * k
    if region is not None:
        g = g * region
    img[:] = img + col[None, None, :] * g[..., None] * (~lock)[..., None]


def disc(x, y, r, col):
    m = (XX - x) ** 2 + (YY - y) ** 2 <= r * r
    img[m] = col
    lock[m] = True


# ─────────────────────────── the street behind the glass ───────────────────────────
GT, GB = 8, 74
VX, HZ = 64.0, 44.0
glass = (YY >= GT) & (YY <= GB)
RAMP = dict(amber=["a6", "a5", "a4", "a3", "a2", "a1", "a0"], red=["r3", "r2", "r1", "r0"], teal=["t3", "t2", "t1", "t0"], white=["wh", "k9", "k8", "k7", "k6"])
LUM = lambda c: float(c @ np.array([.3, .59, .11], np.float32))


def chk(x, y):
    return (int(x) + int(y)) % 2 == 0


def P2(xn, yn, z):
    return VX + (xn - VX) / z, HZ + (yn - HZ) / z


def pglow(x, y, ramp, r, start=1):
    """Pixel-art glow: rings of the colour ramp, the outermost ring checkered; never darkens anything."""
    cols = [C[k] for k in RAMP[ramp]][start:]
    R = r
    for yy in range(int(y - R - 1), int(y + R + 2)):
        for xx in range(int(x - R - 1), int(x + R + 2)):
            if not (0 <= xx < W and GT <= yy <= GB):
                continue
            d = math.hypot(xx - x, (yy - y) * 1.15)
            if d > R:
                continue
            lvl = int(d / R * len(cols))
            if lvl >= len(cols):
                continue
            if lvl == len(cols) - 1 and not chk(xx, yy):
                continue
            c = cols[lvl]
            if LUM(c) > LUM(img[yy, xx]) and not lock[yy, xx]:
                img[yy, xx] = c


def band(y0, y1, cA, cB):
    """Rows y0..y1 in colour B, with a one-row checkered seam into colour A above."""
    for y in range(y0, y1 + 1):
        for x in range(W):
            img[y, x] = C[cB]
    for x in range(W):
        if chk(x, y0):
            img[y0, x] = C[cA]


VX, HZ = 64.0, 47.0
for (y0, y1, ca, cb) in [(8, 14, "k1", "k1"), (15, 21, "k1", "k2"), (22, 28, "k2", "k3"), (29, 35, "k3", "k4"), (36, 41, "k4", "k5"), (42, 47, "k5", "a1")]:
    band(y0, y1, ca, cb)
for x in range(W):
    if x % 3 == 0:
        img[44, x] = C["k5"]
for (cy, x0, x1) in [(13, 18, 58), (19, 66, 116), (24, 22, 50), (29, 78, 110), (33, 30, 60)]:
    for x in range(x0, x1):
        wob = int(math.sin(x * .45 + cy) * 1.2)
        img[cy + wob, x] = C["k3"] if cy < 22 else C["k4"]
        if (x + cy) % 3:
            img[cy + wob + 1, x] = C["a0"] if cy > 20 else C["k2"]

lights_on_road = []


def tower(x0, x1, top, body, win, lit, crown=None, red=False, edge=None):
    for y in range(top, int(HZ) + 1):
        for x in range(x0, x1 + 1):
            img[y, x] = C[body]
    if edge:
        for y in range(top, int(HZ) + 1):
            img[y, x0] = C[edge]
    for y in range(top + 3, int(HZ) - 1, 2):
        for x in range(x0 + 1, x1, 2):
            if rng.random() < lit:
                img[y, x] = C[win] if rng.random() < .8 else C["k6"]
    if crown:
        for x in range(x0, x1 + 1):
            if x % 2 == 0:
                img[top, x] = C[crown]
        img[top + 1, x0] = C[crown]; img[top + 1, x1] = C[crown]
    if red:
        cx = (x0 + x1) // 2
        for y in range(top - 4, top):
            img[y, cx] = C[body]
        img[top - 5, cx] = C["r3"]
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            img[top - 5 + dy, cx + dx] = C["r1"]


# Far skyline: low, grey-blue, glittering.
x = 14
while x < W - 14:
    w, h = int(rng.integers(3, 8)), int(rng.integers(3, 12))
    tower(x, x + w, int(HZ - h), "k4", "a2", .3)
    x += w + int(rng.integers(0, 2))
# Mid towers, darker, taller, with crowns and lights on top.
for (x0, w, top, crown, red, edge) in [(18, 8, 22, None, True, "k4"), (29, 6, 30, None, False, None), (38, 10, 16, "a3", False, "k4"),
                                       (52, 5, 27, None, True, None), (73, 9, 14, "t2", True, "k4"), (85, 6, 26, None, False, None),
                                       (94, 9, 20, "a3", False, "k4"), (106, 6, 30, None, False, None)]:
    tower(x0, x0 + w, top, "k1", "a3", .24, crown, red, "k3" if edge else "k2")
    for k in range(3):
        lights_on_road.append((x0 + rng.uniform(1, w - 1), HZ + 1, "amber", 3, .45))
# A vertical neon sign on one tower: two characters in teal.
SX = 99
rect(SX - 1, 22, SX + 3, 37, C["k0"])
pglow(SX + 1, 29, "teal", 7, start=2)
YE = ["#.#", "###", ".#.", "#.#", "...", "###", "#.#", "###", "#.#", "#.#"]
for j, row in enumerate(YE):
    for i, ch in enumerate(row):
        if ch == "#":
            px(SX + i, 23 + j + (1 if j > 4 else 0), C["t3"])
lights_on_road.append((SX + 1, HZ + 1, "teal", 1, .6))

# The expressway: deck, concrete barriers both sides with amber reflectors, a dashed lane line.
def P2(xn, yn, z):
    return VX + (xn - VX) / z, HZ + (yn - HZ) / z


poly([P2(-40, 74, 1), P2(-40, 74, 60), P2(168, 74, 60), P2(168, 74, 1)], C["k1"], hard=False)
for xn, side in ((-6, -1), (134, 1)):
    for z in np.linspace(1, 60, 400):
        x0_, y0_ = P2(xn, 74, z)
        x1_, y1_ = P2(xn, 74 - 10, z)
        for yy in range(int(round(y1_)), int(round(y0_)) + 1):
            if GT <= yy <= GB and 0 <= int(x0_) < W:
                img[yy, int(round(x0_))] = C["k5"] if yy == int(round(y1_)) else C["k3"]
    for z in [1.3, 1.8, 2.5, 3.5, 5, 7, 10, 14]:
        rx, ry = P2(xn, 74 - 6, z)
        px(rx, ry, C["a4"] if z < 4 else C["a3"])
zz = 1.1
while zz < 30:
    line(*P2(64, 74, zz), *P2(64, 74, zz * 1.14), C["k6"] if zz < 4 else C["k4"], hard=False)
    zz *= 1.38
for xn in (8, 120):
    line(*P2(xn, 74, 1), *P2(xn, 74, 40), C["k4"], hard=False)

# Lamp posts on the left barrier, sodium heads leaning over the road.
for z in [1.15, 1.7, 2.6, 4.0, 6.0, 9.0, 13.0]:
    base, top, head = P2(-6, 64, z), P2(-6, -50, z), P2(20, -48, z)
    top = (top[0], max(top[1], GT))
    if z < 7:
        line(*base, *top, C["k2"] if z < 2 else C["k3"], hard=False)
        line(*top, *head, C["k2"] if z < 2 else C["k3"], hard=False)
    pglow(head[0], head[1], "amber", 1.5 + 6 / z, start=1)
    px(*head, C["a6"])
    lights_on_road.append((head[0], P2(20, 74, z)[1], "amber", 1, .8 * min(1, 2.4 / z)))

# An overhead sign gantry far ahead: 澜音 straight on.
gz = 4.2
g0, g1 = P2(-6, -6, gz), P2(134, -6, gz)
line(*P2(-6, 64, gz), *g0, C["k3"], hard=False); line(*P2(134, 64, gz), *g1, C["k3"], hard=False)
line(*g0, *g1, C["k3"], hard=False)
gx0, gy0 = int(VX + 6), int(g0[1]) + 1
rect(gx0, gy0, gx0 + 17, gy0 + 7, C["t1"]); rect(gx0, gy0, gx0 + 17, gy0, C["t2"])
for (dx, dy) in [(2, 2), (2, 3), (2, 4), (2, 5), (1, 3), (3, 3)]:
    px(gx0 + dx, gy0 + dy, C["wh"])
for i, (dx, dy) in enumerate([(6, 2), (7, 2), (8, 2), (6, 4), (8, 4), (7, 5), (10, 2), (12, 2), (11, 3), (11, 4), (10, 5), (12, 5), (14, 2), (15, 3), (14, 4), (15, 5)]):
    px(gx0 + dx, gy0 + dy, C["k9"])

CAR = ["..##########..",
       ".#3333333333#.",
       "#344444444443#",
       "##############",
       "#rR########Rr#",
       "#22222pp22222#",
       "##############",
       ".00........00."]


def car(cx, yb, big, braking):
    if big:
        h = len(CAR)
        x0 = int(cx - len(CAR[0]) / 2)
        for j, row in enumerate(CAR):
            for i, ch in enumerate(row):
                c = {"#": "k0", "3": "k3", "4": "k4", "2": "k2", "p": "k7", "0": "k0", "r": "r2", "R": "r3" if braking else "r2"}.get(ch)
                if c:
                    px(x0 + i, yb - h + j, C[c])
        ly = yb - h + 4
        for xx in (x0 + 1.5, x0 + len(CAR[0]) - 2.5):
            pglow(xx, ly, "red", 4.5 if braking else 3.5, start=1)
            lights_on_road.append((xx, yb + 1, "red", 1, .95))
        if braking:
            px(cx - 1, yb - h + 1, C["r3"]); px(cx, yb - h + 1, C["r3"])
    else:
        x0 = int(cx - 3)
        rect(x0, yb - 3, x0 + 5, yb - 1, C["k0"]); rect(x0 + 1, yb - 4, x0 + 4, yb - 4, C["k0"])
        px(x0, yb - 2, C["r2"]); px(x0 + 5, yb - 2, C["r2"])
        pglow(x0, yb - 2, "red", 2.2, 1); pglow(x0 + 5, yb - 2, "red", 2.2, 1)
        lights_on_road.append((x0 + 2.5, yb, "red", 1, .6))


car(78, 62 if MODE == "home" else 59, True, MODE == "home")
car(56, 53, False, False)

# Wet deck: every light drags a stepped streak of its ramp towards us.
for (lx, ly, ramp, start, k) in lights_on_road:
    if ly < HZ or ly > GB:
        continue
    cols = RAMP[ramp][start + 1:]
    L = int((5 + (ly - HZ) * 1.1) * k) + 2
    for i in range(L):
        yy = int(ly + 1 + i)
        if yy > GB:
            break
        xx = int(round(lx))
        lvl = min(len(cols) - 1, int(i / max(L, 1) * len(cols)))
        if (yy % 3 == 1 and i > 2) or not (0 <= xx < W) or lock[yy, xx]:
            continue
        c = C[cols[lvl]]
        if LUM(c) > LUM(img[yy, xx]):
            img[yy, xx] = c

# Rain: sparse slanted streaks.
for i in range(70):
    x0, y0 = int(rng.uniform(0, W)), int(rng.uniform(GT, GB - 3))
    for k in range(3):
        xx, yy = x0 - (k // 2), y0 + k
        if 0 <= xx < W and not lock[yy, xx]:
            c = C["k5"] if LUM(img[yy, xx]) < .1 else C["k7"]
            if LUM(c) > LUM(img[yy, xx]):
                img[yy, xx] = c

# Drops on the glass.
for i in range(46):
    x0, y0 = int(rng.uniform(18, W - 18)), int(rng.uniform(GT + 3, GB - 4))
    if MODE == "play" and (x0 - 30) ** 2 + (y0 - 80) ** 2 < 70 ** 2 and x0 < 72 and rng.random() < .8:
        continue
    under = LUM(img[y0, x0])
    hi = C["k9"] if under > .2 else C["k7"] if under > .06 else C["k5"]
    px(x0, y0, hi, hard=False); px(x0 + 1, y0, C["k4"] if under < .1 else C["k6"], hard=False)
    px(x0, y0 + 1, C["k0"], hard=False); px(x0 + 1, y0 + 1, C["k0"], hard=False)

# ─────────────────────────── the car ───────────────────────────
# Wipers.
if MODE == "home":
    line(18, 72, 62, 66, C["k0"]); line(18, 71, 62, 65, C["k3"])
    line(66, 72, 108, 67, C["k0"]); line(66, 71, 108, 66, C["k3"])
else:
    line(30, 74, 70, 30, C["k0"]); line(29, 74, 69, 30, C["k3"])
    line(70, 73, 112, 67, C["k0"]); line(70, 72, 112, 66, C["k3"])

# Roof lining with an overhead console and sun visors; a photo tucked into the driver's visor.
for x in range(W):
    edge = 8 + ((x - 65) / 65) ** 2 * 4
    for y in range(0, int(edge) + 1):
        rect(x, y, x, y, C["k2"] if (x + y * 3) % 7 else C["k3"])
rect(52, 0, 78, 4, C["k1"]); rect(53, 1, 77, 3, C["k3"])
px(57, 2, C["a4"]); px(73, 2, C["k6"]); px(65, 2, C["r1"])
rect(14, 5, 44, 11, C["k3"]); line(14, 11, 44, 11, C["k1"]); line(15, 5, 43, 5, C["k5"])
rect(20, 6, 27, 11, C["wh"]); rect(21, 7, 26, 10, C["k6"]); px(23, 9, C["a4"]); px(24, 8, C["a3"])   # the photo
rect(86, 5, 116, 11, C["k3"]); line(86, 11, 116, 11, C["k1"]); line(87, 5, 115, 5, C["k5"])

# A-pillars, with the edge catching street light.
poly([(0, 6), (10, 6), (18, GB + 2), (0, GB + 2)], C["k1"])
poly([(W, 6), (W - 10, 6), (W - 18, GB + 2), (W, GB + 2)], C["k1"])
line(10, 7, 18, GB + 1, C["k5"]); line(W - 11, 7, W - 19, GB + 1, C["a1"])
line(4, 20, 8, 60, C["k2"]); line(W - 5, 20, W - 9, 60, C["k2"])

# Rear-view mirror: a sliver of the road behind, headlights following.
rect(64, 4, 66, 9, C["k1"])
rect(48, 9, 82, 18, C["k0"]); rect(49, 10, 81, 17, C["k3"])
rect(50, 11, 80, 16, C["k2"]); rect(50, 14, 80, 16, C["k1"])
for xx in (59, 69):
    px(xx, 14, C["wh"]); px(xx + 1, 14, C["k9"])
px(52, 12, C["a2"]); px(77, 12, C["a3"])
line(49, 18, 81, 18, C["k5"])
# The little tree air freshener, hung from the mirror on a string.
line(74, 19, 75, 24, C["k6"])
for r, (y, w) in enumerate([(25, 1), (26, 2), (27, 3), (28, 2), (29, 3), (30, 4), (31, 2)]):
    rect(75 - w, y, 75 + w, y, C["t1"] if r % 2 else C["t2"])
px(74, 26, C["t3"]); px(76, 29, C["t3"]); rect(75, 32, 75, 33, C["a1"])

# Dashboard top with defrost slots, a rim of amber light and a cat that rides along.
for x in range(W):
    top = GB - 1 + int(((x - 65) / 65) ** 2 * 3)
    rect(x, top, x, 92, C["k2"])
    px(x, top, C["k5"] if x % 9 else C["a1"])
for x in range(24, 50, 2):
    px(x, GB + 3, C["k0"]); px(x, GB + 4, C["k0"])
for x in range(82, 108, 2):
    px(x, GB + 3, C["k0"]); px(x, GB + 4, C["k0"])
# The cat: sitting, seen from behind, ears up, watching the road.
cat = ["..#....#..", ".###..###.", ".########.", "##########", "##########", ".########.", ".########.", "##########", "###########", "############"]
cx0, cy0 = 102, GB - 9
for j, row in enumerate(cat):
    for i, ch in enumerate(row):
        if ch == "#":
            px(cx0 + i, cy0 + j, C["k0"])
px(cx0 + 2, cy0 + 2, C["k3"]); px(cx0 + 7, cy0 + 2, C["k3"])
for k in range(5):
    px(cx0 + 11 + (k > 2), cy0 + 9 - k, C["k0"])
line(cx0 + 1, cy0 + 1, cx0 + 3, cy0, C["a1"])

# Light through the wet glass throws drop shadows on the dash.
for i in range(70):
    x0, y0 = rng.uniform(4, W - 4), rng.uniform(GB + 6, 92)
    if 30 < x0 < 100 and rng.random() < .4:
        continue
    px(x0, y0, C["k3"] if rng.random() < .7 else C["k4"])
    if rng.random() < .3:
        px(x0, y0 + 1, C["k1"])

# Dash body below.
for y in range(93, H):
    t = (y - 93) / (H - 93)
    for x in range(W):
        img[y, x] = C["k1"] * (1 - t * .5) + C["k0"] * t * .5
        lock[y, x] = False

# Side vents.
for (x0, x1) in ((0, 8), (W - 9, W - 1)):
    rect(x0, 104, x1, 132, C["k0"])
    for y in range(106, 131, 3):
        line(x0 + 1, y, x1 - 1, y, C["k4"]); line(x0 + 1, y + 1, x1 - 1, y + 1, C["k2"])
    line(x0, 103, x1, 103, C["k5"])

# The centre screen: bezel only, the page draws inside it.
SX0, SY0, SX1, SY1 = 11, 96, W - 12, 202
rect(SX0 - 2, SY0 - 2, SX1 + 2, SY1 + 2, C["k0"])
line(SX0 - 2, SY0 - 3, SX1 + 2, SY0 - 3, C["k5"])
rect(SX0, SY0, SX1, SY1, C["k1"])
px(SX1 - 2, SY1 + 1, C["t2"])

# The head unit: an old radio-cassette with an LCD, two knobs, six preset buttons and a tape slot.
HX0, HY0, HX1, HY1 = 13, 208, W - 14, 248
rect(HX0 - 1, HY0 - 1, HX1 + 1, HY1 + 1, C["k0"])
rect(HX0, HY0, HX1, HY1, C["k4"])
line(HX0, HY0, HX1, HY0, C["k6"]); line(HX0, HY1, HX1, HY1, C["k2"])
for x in range(HX0 + 2, HX1 - 1, 3):
    px(x, HY0 + 2, C["k5"])
rect(36, 212, 93, 225, C["k0"]); rect(37, 213, 92, 224, C["t0"])
for (kx, ky) in ((24, 219), (105, 219)):
    disc(kx, ky, 6.2, C["k1"]); disc(kx, ky, 5.2, C["k5"]); disc(kx, ky, 3.6, C["k6"])
    for a in range(0, 360, 30):
        px(kx + 5 * math.cos(math.radians(a)), ky + 5 * math.sin(math.radians(a)), C["k3"])
    px(kx - 1, ky - 2, C["k8"]); line(kx, ky - 4, kx, ky - 2, C["a4"])
rect(40, 228, 89, 229, C["k0"]); line(40, 230, 89, 230, C["k6"])
for i in range(6):
    bx = 17 + i * 16
    rect(bx, 234, bx + 13, 244, C["k2"]); rect(bx, 234, bx + 13, 243, C["k5"]); line(bx, 234, bx + 13, 234, C["k7"])
    if (MODE == "home" and i == 0) or (MODE == "play" and i == 3):
        px(bx + 6, 236, C["a5"]); px(bx + 7, 236, C["a4"])

# Console: hazard switch, the gear lever, a coffee going cold in the cup holder.
poly([(61, 258), (65, 252), (69, 258)], C["r2"]); poly([(63, 257), (65, 254), (67, 257)], C["r1"])
rect(58, 250, 72, 259, C["k0"]) if False else None
disc(36, 266, 7.5, C["k0"]); disc(36, 264, 6, C["k4"]); disc(35, 262, 2.5, C["k6"]); px(34, 261, C["k8"])
for y in range(271, H):
    rect(30 - (y - 271) // 2, y, 42 + (y - 271) // 2, y, C["k2"] if y % 2 else C["k1"])
rect(84, 262, 110, H - 1, C["k0"]); disc(97, 270, 10, C["k0"])
rect(90, 258, 104, 276, C["wh"]); rect(90, 264, 104, 270, C["a2"]); line(90, 264, 104, 264, C["a1"])
rect(89, 256, 105, 258, C["k9"]); line(89, 256, 105, 256, C["wh"])
px(96, 257, C["k6"])
for k, (sx, sy) in enumerate([(97, 252), (96, 249), (98, 246), (97, 243), (96, 240)]):
    px(sx, sy, C["k6"] if k < 2 else C["k5"], hard=False)

# ─────────────────────────── lock to the palette ───────────────────────────
B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - .5
thr = np.tile(B4, (H // 4 + 1, W // 4 + 1))[:H, :W]
src = np.clip(img + thr[..., None] * .03 * (~lock)[..., None], 0, 1)
d = ((src[:, :, None, :] - P[None, None, :, :]) ** 2 * np.array([.3, .59, .11])).sum(-1)
out = P[d.argmin(-1)]
im = Image.fromarray((out * 255).astype(np.uint8))
im.save(f"{OUT}/px_{MODE}_1x.png")
im.resize((W * 3, H * 3), Image.NEAREST).save(f"{OUT}/px_{MODE}.png")
print("ok", MODE)
