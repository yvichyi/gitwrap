"""Round two covers.

暮: three-ink risograph prints (fluorescent pink, sunflower, medium blue) on warm paper: halftone screens at
different angles, multiply overprint, uneven ink, slight misregistration.
霓: cyberpunk city on a rainy night: fogged towers, lit windows, neon signs in Chinese, wet street reflections, rain.

Usage: python3 -I covers3.py <covers dir>
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
rng = np.random.default_rng(5)

PAPER = np.array([242, 235, 221], np.float32) / 255
PINK = (255, 72, 176)
YELLOW = (255, 181, 17)
BLUE = (50, 85, 164)


# ───────────────────────── riso ─────────────────────────

def grid(h, w):
    return np.mgrid[0:h, 0:w].astype(np.float32)


def screen(tone, angle, period=7.0):
    """Euclidean halftone dots giving coverage `tone` (array 0..1), antialiased."""
    h, w = tone.shape
    yy, xx = grid(h, w)
    a = math.radians(angle)
    u = xx * math.cos(a) + yy * math.sin(a)
    v = -xx * math.sin(a) + yy * math.cos(a)
    spot = 0.5 + 0.25 * (np.cos(2 * math.pi * u / period) + np.cos(2 * math.pi * v / period))
    edge = spot - (1 - tone)
    return np.clip(edge * 6 + 0.5, 0, 1)


def ink_texture(cov, strength=0.16):
    h, w = cov.shape
    n = rng.normal(0, 1, (h // 8 + 2, w // 8 + 2)).astype(np.float32)
    n = np.array(Image.fromarray(n).resize((w, h), Image.BICUBIC))
    speck = rng.random((h, w)) < 0.015
    out = cov * (1 - strength * 0.5 + strength * 0.5 * np.tanh(n))
    out[speck] *= 0.35
    return np.clip(out, 0, 1)


def mask(h, w, draw_fn, blur=0.6):
    im = Image.new("L", (w, h), 0)
    draw_fn(ImageDraw.Draw(im))
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    return np.array(im, np.float32) / 255


def compose(layers, h, w, grain=6, paper=PAPER):
    out = np.ones((h, w, 3), np.float32) * paper
    # Paper fibres: faint low-frequency mottling.
    mott = rng.normal(0, 1, (h // 24 + 2, w // 24 + 2)).astype(np.float32)
    mott = np.array(Image.fromarray(mott).resize((w, h), Image.BICUBIC))
    out *= (1 - 0.018 * mott)[..., None]
    for ink, cov, (dx, dy) in layers:
        cov = ink_texture(np.roll(cov, (dy, dx), (0, 1)))
        k = np.array(ink, np.float32) / 255
        out *= 1 - cov[..., None] * (1 - k)
    out = out * 255 + rng.normal(0, grain, (h, w))[..., None]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def vtone(h, w, stops):
    ys = np.linspace(0, 1, h)
    col = np.interp(ys, [p for p, _ in stops], [t for _, t in stops]).astype(np.float32)
    return np.repeat(col[:, None], w, 1)


def riso_sea(name, N=640, sun=(0.5, 0.56, 0.17)):
    h = w = N
    hz = 0.62
    sky = vtone(h, w, [(0, 0.22), (hz, 0.9), (hz + 0.001, 0.0), (1, 0.0)])
    blue_sky = vtone(h, w, [(0, 0.72), (0.4, 0.2), (hz, 0.0), (hz + 0.001, 0.0), (1, 0)])
    cx, cy, r = sun[0] * N, sun[1] * N, sun[2] * N
    sun_m = mask(h, w, lambda d: d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255))
    sun_m[int(hz * N):] = 0
    glow = vtone(h, w, [(0, 0), (1, 0)])
    yy, xx = grid(h, w)
    dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / N
    glow = np.clip(0.55 - dist * 1.3, 0, 1)
    glow[int(hz * N):] = 0
    sea_m = mask(h, w, lambda d: d.rectangle((0, hz * N, N, N), fill=255), blur=0)
    refl = mask(h, w, lambda d: [d.rectangle((cx - (0.05 + k * 0.022) * N, (hz + 0.025 + k * 0.038) * N,
                                              cx + (0.05 + k * 0.022) * N, (hz + 0.025 + k * 0.038) * N + 5 - k * 0.3), fill=255)
                                 for k in range(9)])
    island = mask(h, w, lambda d: (d.polygon([(0, hz * N), (0, 0.5 * N), (0.12 * N, 0.47 * N), (0.26 * N, 0.55 * N), (0.36 * N, hz * N)], fill=255),
                                   d.polygon([(0.68 * N, hz * N), (0.82 * N, 0.52 * N), (N, 0.57 * N), (N, hz * N)], fill=255)))
    yellow = np.maximum(screen(glow, 45, 6), sun_m) + refl
    yellow = np.clip(yellow - island, 0, 1)
    pink = np.maximum(screen(sky, 15, 6), sea_m * screen(np.full((h, w), 0.32, np.float32), 15, 6))
    blue = np.maximum(screen(blue_sky, 75, 6), sea_m * np.clip(1 - refl * 0.9, 0, 1) * 0.92)
    blue = np.maximum(blue, island)
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (2, 1)), (BLUE, blue, (-1, 2))], h, w).save(f"{OUT}/{name}.jpg", quality=92)


def riso_peaks(name, N=640):
    h = w = N
    sky_y = vtone(h, w, [(0, 0.15), (0.55, 0.9), (1, 0.9)])
    sky_p = vtone(h, w, [(0, 0.75), (0.5, 0.35), (1, 0.2)])
    cx, cy, r = 0.68 * N, 0.36 * N, 0.13 * N
    sun = mask(h, w, lambda d: d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255))
    blue = np.zeros((h, w), np.float32)
    pink_cut = np.zeros((h, w), np.float32)
    for k, (base, tone) in enumerate([(0.5, 0.28), (0.62, 0.5), (0.75, 0.75), (0.9, 1.0)]):
        pts = [(x, N * base - N * 0.07 * abs(math.sin(x / N * 6.5 + k * 1.9)) - N * 0.045 * math.sin(x / N * 2.6 + k * 2.3)) for x in range(0, N + 10, 10)]
        m = mask(h, w, lambda d: d.polygon(pts + [(N, N), (0, N)], fill=255))
        blue = blue * (1 - m) + m * (screen(np.full((h, w), tone, np.float32), 75, 6) if tone < 1 else 1)
        pink_cut = np.maximum(pink_cut, m * (0.6 - k * 0.15))
    yellow = np.clip(screen(sky_y, 45, 6) * (1 - pink_cut) + sun, 0, 1)
    pink = screen(np.clip(sky_p * (1 - sun) + pink_cut * 0.6, 0, 1), 15, 6)
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (-2, 1)), (BLUE, blue, (1, -1))], h, w).save(f"{OUT}/{name}.jpg", quality=92)


def riso_roofs(name, N=640):
    h = w = N
    sky_p = vtone(h, w, [(0, 0.3), (0.7, 0.85), (1, 0.85)])
    sky_y = vtone(h, w, [(0, 0.0), (0.45, 0.4), (0.75, 1.0), (1, 1)])
    cx, cy, r = 0.36 * N, 0.62 * N, 0.2 * N
    sun = mask(h, w, lambda d: d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255))

    def roofs(d):
        x = -10
        while x < N:
            bw = rng.uniform(50, 120)
            top = N * rng.uniform(0.66, 0.82)
            d.rectangle((x, top, x + bw, N), fill=255)
            if rng.random() < 0.5:
                d.polygon([(x - 4, top), (x + bw / 2, top - rng.uniform(16, 34)), (x + bw + 4, top)], fill=255)
            x += bw - 2
        # Water tower and antenna.
        d.rectangle((0.7 * N, 0.5 * N, 0.82 * N, 0.6 * N), fill=255)
        d.polygon([(0.69 * N, 0.5 * N), (0.76 * N, 0.45 * N), (0.83 * N, 0.5 * N)], fill=255)
        for lx in (0.71, 0.81):
            d.line([(lx * N, 0.6 * N), (lx * N + (0.76 - lx) * N * 0.3, 0.7 * N)], fill=255, width=4)
        d.line([(0.2 * N, 0.3 * N), (0.2 * N, 0.72 * N)], fill=255, width=3)
        for k in range(3):
            d.line([(0.17 * N, (0.34 + k * 0.04) * N), (0.23 * N, (0.34 + k * 0.04) * N)], fill=255, width=3)
        # Wires sagging across the sky, and birds.
        for k in range(3):
            pts = [(x, (0.18 + k * 0.05) * N + 40 * math.sin(x / N * math.pi) ** 2) for x in range(0, N + 8, 8)]
            d.line(pts, fill=255, width=2)
        for bx, by in [(0.55, 0.22), (0.6, 0.18), (0.66, 0.24), (0.86, 0.12)]:
            d.line([(bx * N - 9, by * N - 4), (bx * N, by * N), (bx * N + 9, by * N - 5)], fill=255, width=3)

    sil = mask(h, w, roofs)
    yellow = np.clip(screen(sky_y, 45, 6) + sun, 0, 1) * (1 - sil)
    pink = screen(sky_p, 15, 6) * (1 - sun * 0.7)
    blue = np.maximum(sil, screen(vtone(h, w, [(0, 0.45), (0.5, 0.0), (1, 0)]), 75, 6))
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (2, -1)), (BLUE, blue, (-1, 1))], h, w).save(f"{OUT}/{name}.jpg", quality=92)


def riso_tree(name, N=640):
    h = w = N
    yy, xx = grid(h, w)
    cx, cy = 0.5 * N, 0.5 * N
    d0 = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / N
    rings_y = np.clip(1 - d0 * 1.6, 0, 1)
    rings_p = ((np.floor(d0 * 14) % 2) == 0).astype(np.float32) * np.clip(d0 * 2.2 - 0.4, 0, 1)
    sun = (d0 < 0.21).astype(np.float32)
    hill = mask(h, w, lambda d: d.ellipse((-0.3 * N, 0.7 * N, 1.3 * N, 1.6 * N), fill=255))

    def tree(d):
        d.line([(0.62 * N, 0.76 * N), (0.62 * N, 0.5 * N)], fill=255, width=9)
        for k, (ox, oy, rr) in enumerate([(0.62, 0.42, 0.11), (0.54, 0.48, 0.08), (0.7, 0.47, 0.08), (0.62, 0.34, 0.07)]):
            d.ellipse(((ox - rr) * N, (oy - rr) * N, (ox + rr) * N, (oy + rr) * N), fill=255)
        d.line([(0.3 * N, 0.78 * N), (0.3 * N, 0.73 * N)], fill=255, width=5)
        d.ellipse((0.285 * N, 0.69 * N, 0.315 * N, 0.725 * N), fill=255)

    t = mask(h, w, tree)
    yellow = np.clip(screen(rings_y, 45, 6) + sun, 0, 1) * (1 - hill)
    pink = np.clip(rings_p * 0.9 + screen(np.full((h, w), 0.35, np.float32), 15, 6) * hill, 0, 1)
    blue = np.maximum(t, hill * screen(np.full((h, w), 0.8, np.float32), 75, 6))
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (-2, 2)), (BLUE, blue, (2, 0))], h, w).save(f"{OUT}/{name}.jpg", quality=92)


def riso_duo(src, name, N=640):
    """Any photo as a pink and blue riso duotone, the way the theme would print album art."""
    im = Image.open(f"{OUT}/{src}.jpg").convert("L").resize((N, N))
    L = np.array(im, np.float32) / 255
    L = np.clip((L - L.min()) / (np.ptp(L) + 1e-6), 0, 1)
    blue = screen(np.clip(1.1 - L * 1.35, 0, 1) ** 1.2, 75, 6)
    pink = screen(np.clip(1 - np.abs(L - 0.45) * 1.9, 0, 1) * 0.85, 15, 6)
    yellow = screen(np.clip(L - 0.55, 0, 1) * 1.5, 45, 6)
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (2, 1)), (BLUE, blue, (-1, 1))], N, N).save(f"{OUT}/{name}.jpg", quality=92)


import os
RISO = os.environ.get("RISO", "1") == "1"
if RISO:
    riso_sea("riso_sea")
    riso_peaks("riso_peaks")
    riso_roofs("riso_roofs")
    riso_tree("riso_tree")
    for src in ["forest", "lake", "petals", "figure", "dunes"]:
        riso_duo(src, "riso_" + src)

    # The 暮 home poster: wide, the sea and an island, the sun about to touch the horizon.
    H, W = 760, 780
    hz = 0.66
    sky = vtone(H, W, [(0, 0.12), (hz, 0.92), (hz + 0.001, 0), (1, 0)])
    blue_sky = vtone(H, W, [(0, 0.85), (0.3, 0.42), (0.55, 0.0), (1, 0)])
    cx, cy, r = 0.62 * W, 0.6 * H, 0.15 * W
    sun_m = mask(H, W, lambda d: d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255))
    sun_m[int(hz * H):] = 0
    yy, xx = grid(H, W)
    glow = np.clip(0.6 - np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / W * 1.2, 0, 1)
    glow[int(hz * H):] = 0
    sea = mask(H, W, lambda d: d.rectangle((0, hz * H, W, H), fill=255), blur=0)
    refl = mask(H, W, lambda d: [d.rectangle((cx - (0.04 + k * 0.02) * W, (hz + 0.02 + k * 0.036) * H, cx + (0.04 + k * 0.02) * W, (hz + 0.02 + k * 0.036) * H + 5), fill=255) for k in range(9)])
    isl = mask(H, W, lambda d: (d.polygon([(0, hz * H), (0, 0.5 * H), (0.1 * W, 0.47 * H), (0.17 * W, 0.52 * H), (0.3 * W, 0.56 * H), (0.42 * W, hz * H)], fill=255),
                                d.line([(0.2 * W, 0.53 * H), (0.2 * W, 0.4 * H)], fill=255, width=3),
                                d.rectangle((0.19 * W, 0.38 * H, 0.21 * W, 0.41 * H), fill=255)))
    birds = mask(H, W, lambda d: [d.line([(bx * W - 8, by * H - 4), (bx * W, by * H), (bx * W + 8, by * H - 5)], fill=255, width=3) for bx, by in [(0.3, 0.2), (0.35, 0.16), (0.4, 0.23)]])
    yellow = np.clip(np.maximum(screen(glow, 45, 6), sun_m) + refl - isl, 0, 1)
    pink = np.maximum(screen(sky, 15, 6), sea * screen(np.full((H, W), 0.3, np.float32), 15, 6))
    blue = np.maximum(np.maximum(screen(blue_sky, 75, 6), sea * np.clip(1 - refl, 0, 1) * 0.9), np.maximum(isl, birds))
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (3, 1)), (BLUE, blue, (-1, 2))], H, W).save(f"{OUT}/riso_poster.jpg", quality=92)

    # The player's print: sky and three ridges only. The sun is the album art, laid on top by the page.
    H, W = 760, 700
    sky = vtone(H, W, [(0, 0.1), (0.72, 0.95), (1, 0.95)])
    bs = vtone(H, W, [(0, 0.7), (0.4, 0.0), (1, 0)])
    ys = vtone(H, W, [(0, 0), (0.35, 0.2), (0.75, 1.0), (1, 1)])
    blue = screen(bs, 75, 6)
    pinkx = np.zeros((H, W), np.float32)
    for k, (base, tone) in enumerate([(0.7, 0.35), (0.8, 0.62), (0.9, 1.0)]):
        pts = [(x, H * base - H * 0.05 * abs(math.sin(x / W * 5.5 + k * 2.1)) - H * 0.03 * math.sin(x / W * 2.2 + k)) for x in range(0, W + 10, 10)]
        m = mask(H, W, lambda d: d.polygon(pts + [(W, H), (0, H)], fill=255))
        blue = blue * (1 - m) + m * (screen(np.full((H, W), tone, np.float32), 75, 6) if tone < 1 else 1)
        pinkx = np.maximum(pinkx, m)
    yellow = screen(ys, 45, 6) * (1 - pinkx)
    pink = screen(np.clip(sky * (1 - pinkx * 0.6), 0, 1), 15, 6)
    compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (2, 1)), (BLUE, blue, (-1, 2))], H, W).save(f"{OUT}/riso_stage.jpg", quality=92)
    # The same print split in two so the album-art sun can set between them: the sky on paper, and the ridges
    # alone on white, which the page multiplies over the sun.
    compose([(YELLOW, screen(ys, 45, 6), (0, 0)), (PINK, screen(sky, 15, 6), (2, 1)), (BLUE, screen(bs, 75, 6), (-1, 2))], H, W).save(f"{OUT}/riso_sky.jpg", quality=92)
    ridge_blue = np.zeros((H, W), np.float32)
    for k, (base, tone) in enumerate([(0.7, 0.35), (0.8, 0.62), (0.9, 1.0)]):
        pts = [(x, H * base - H * 0.05 * abs(math.sin(x / W * 5.5 + k * 2.1)) - H * 0.03 * math.sin(x / W * 2.2 + k)) for x in range(0, W + 10, 10)]
        m = mask(H, W, lambda d: d.polygon(pts + [(W, H), (0, H)], fill=255))
        ridge_blue = ridge_blue * (1 - m) + m * (screen(np.full((H, W), tone, np.float32), 75, 6) if tone < 1 else 1)
    ridge_pink = screen(np.full((H, W), 0.55, np.float32), 15, 6) * (pinkx > 0.5)
    ridges = compose([(PINK, ridge_pink, (2, 1)), (BLUE, ridge_blue, (-1, 2))], H, W, grain=3)
    alpha = Image.fromarray((np.clip(pinkx * 1.0, 0, 1) * 255).astype(np.uint8))
    ridges.putalpha(alpha)
    ridges.save(f"{OUT}/riso_ridges.png")

    def riso_sun(src, name, N=640):
        """Album art printed as the sun: highlights in sunflower, midtones in pink, only the deepest shadows in blue."""
        im = Image.open(f"{OUT}/{src}.jpg").convert("L").resize((N, N))
        L = np.array(im, np.float32) / 255
        L = np.clip((L - L.min()) / (np.ptp(L) + 1e-6), 0, 1)
        yellow = np.clip(screen(np.clip(0.35 + L * 0.8, 0, 1), 45, 6), 0, 1)
        pink = screen(np.clip(1 - L * 1.25, 0, 1) * 0.9, 15, 6)
        blue = screen(np.clip(0.55 - L * 1.4, 0, 1), 75, 6)
        compose([(YELLOW, yellow, (0, 0)), (PINK, pink, (2, 1)), (BLUE, blue, (-1, 1))], N, N).save(f"{OUT}/{name}.jpg", quality=92)

    for src in ["figure", "forest", "lake"]:
        riso_sun(src, "sun_" + src)


# ───────────────────────── cyberpunk rain ─────────────────────────

CJK = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
try:
    ImageFont.truetype(CJK, 20)
except OSError:
    import glob
    CJK = (glob.glob("/usr/share/fonts/**/wqy-zenhei*", recursive=True) + [None])[0]

CYAN = np.array([25, 240, 255], np.float32)
MAGENTA = np.array([255, 42, 109], np.float32)
AMBER = np.array([255, 176, 46], np.float32)
VIOLET = np.array([150, 70, 255], np.float32)
SIGNS = ["夜雨", "澜音", "唱片", "电台", "酒", "按摩", "旅馆", "当铺", "拉面", "网吧", "电玩", "冰室"]


def glow_layer(h, w, draw_fn, radius):
    im = Image.new("RGB", (w, h), 0)
    draw_fn(ImageDraw.Draw(im))
    core = np.array(im, np.float32)
    halo = np.array(im.filter(ImageFilter.GaussianBlur(radius)), np.float32)
    wide = np.array(im.filter(ImageFilter.GaussianBlur(radius * 3)), np.float32)
    return core * 0.9 + halo * 1.6 + wide * 0.9


def tower_windows(d, x0, x1, top, bottom, rr, lit=0.1, dim=1.0):
    cols = [CYAN * 0.55, AMBER * 0.75, np.array([210, 225, 235.0]) * 0.8, MAGENTA * 0.5]
    floor = 9
    y = top + 10
    while y < bottom - 8:
        # Whole floors light up together, as offices and flats do.
        if rr.random() < lit * 2.2:
            c = tuple(int(v * dim) for v in cols[int(rr.integers(0, 4))])
            x = x0 + 4
            while x < x1 - 7:
                if rr.random() < 0.75:
                    d.rectangle((x, y, x + 4, y + 3), fill=c)
                x += 8
        y += floor


def cyber_city(name, W, H, seed, horizon=0.72, signs=True, billboard=None, figure=False, alley=False, nsigns=6):
    rr = np.random.default_rng(seed)
    yy, xx = grid(H, W)
    gy = int(H * horizon)
    t = np.clip(yy / gy, 0, 1)
    base = np.zeros((H, W, 3), np.float32)
    # Sky: near black at the top, a fogged teal glow towards the street, lit pink in places by the signs.
    base[..., 0] = 5 + 30 * t ** 2
    base[..., 1] = 10 + 52 * t ** 2
    base[..., 2] = 18 + 66 * t ** 2
    base += MAGENTA * (0.22 * np.exp(-((xx - W * 0.25) / (W * 0.35)) ** 2) * np.exp(-((yy - gy * 0.85) / (H * 0.18)) ** 2))[..., None]
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    rows = [(0.62, 0.35, 0.75, 34, 70, 0.05), (0.35, 0.45, 0.9, 50, 100, 0.08), (0.0, 0.6, 1.05, 90, 170, 0.1)]
    near_edges = []
    for row, (fog, hmin, hmax, wmin, wmax, lit) in enumerate(rows):
        if alley and row == 2:
            break
        col = tuple(int(c) for c in np.array([4, 7, 11]) * (1 - fog) + np.array([34, 58, 72]) * fog)
        x = -30 + rr.uniform(0, 30)
        while x < W:
            bw = rr.uniform(wmin, wmax) * W / 640
            top = gy - rr.uniform(hmin, hmax) * gy
            d.rectangle((x, top, x + bw, gy), fill=col)
            if rr.random() < 0.35:
                d.rectangle((x + bw * 0.2, top - 12, x + bw * 0.45, top), fill=col)
            if rr.random() < 0.45:
                ax = x + bw * rr.uniform(0.3, 0.7)
                d.line([(ax, top), (ax, top - rr.uniform(16, 50))], fill=col, width=2)
            tower_windows(d, x, x + bw, top, gy, rr, lit=lit, dim=0.55 + 0.45 * (1 - fog))
            if row == 2:
                near_edges.append((x, x + bw, top))
            x += bw + rr.uniform(-8, 14)
    arr = np.array(img, np.float32)
    font_cache = {}

    def font(sz):
        if sz not in font_cache:
            font_cache[sz] = ImageFont.truetype(CJK, sz)
        return font_cache[sz]

    def signs_fn(d2):
        # Rim light along one edge of each near tower.
        for x0, x1, top in near_edges:
            d2.line([(x1 - 1, top), (x1 - 1, gy)], fill=(20, 120, 140), width=1)
        if not signs:
            return
        n = nsigns if not alley else 10
        for k in range(n):
            col = [MAGENTA, CYAN, AMBER, MAGENTA, VIOLET, CYAN][k % 6]
            c = tuple(int(v) for v in col)
            word = SIGNS[int(rr.integers(0, len(SIGNS)))]
            sz = int(rr.uniform(24, 40) * W / 640)
            if alley:
                side = k % 2
                x = (rr.uniform(0.03, 0.18) if side == 0 else rr.uniform(0.74, 0.88)) * W
                y = rr.uniform(0.06, 0.6) * H
                sz = int(sz * (1.5 - y / H))
            else:
                x = rr.uniform(0.05, 0.88) * W
                y = rr.uniform(0.3, 0.68) * gy
            bh = sz * len(word) + sz * 0.5
            d2.rectangle((x - 5, y - 5, x + sz + 5, y + bh), outline=c, width=2)
            for i, ch in enumerate(word):
                d2.text((x, y + i * sz + sz * 0.15), ch, font=font(sz), fill=c)
        for k in range(4):
            col = tuple(int(v) for v in [CYAN, MAGENTA, AMBER][k % 3])
            x = rr.uniform(0, 0.8) * W
            y = rr.uniform(0.5, 0.95) * gy
            d2.rectangle((x, y, x + rr.uniform(50, 120) * W / 640, y + 4), fill=col)

    lights = glow_layer(H, W, signs_fn, 3.5 * W / 640)
    arr += lights
    if billboard is not None:
        bx0, by0, bx1, by1 = [int(v) for v in billboard]
        bb = np.zeros((by1 - by0, bx1 - bx0, 3), np.float32)
        gx = np.linspace(0, 1, bx1 - bx0)[None, :, None]
        bb[:] = MAGENTA * (1 - gx) * 0.8 + CYAN * gx * 0.7
        bb[::3] *= 0.55
        arr[by0:by1, bx0:bx1] = arr[by0:by1, bx0:bx1] * 0.15 + bb
        halo = np.zeros((H, W, 3), np.float32)
        halo[by0:by1, bx0:bx1] = bb
        arr += np.array(Image.fromarray(np.clip(halo, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(30 * W / 640)), np.float32)
    # Fog where the towers meet the street.
    arr += np.stack([np.full((H, W), 24.0), np.full((H, W), 44.0), np.full((H, W), 56.0)], -1) * (0.5 * np.exp(-((yy - gy) / (H * 0.05)) ** 2))[..., None]
    # Wet street: the city mirrored and smeared down, so each sign leaves a long streak of colour.
    above = arr[max(0, 2 * gy - H):gy][::-1]
    refl = np.zeros((H - gy, W, 3), np.float32)
    refl[: above.shape[0]] = above
    ri = Image.fromarray(np.clip(refl, 0, 255).astype(np.uint8))
    ri = ri.resize((W // 2, max(1, (H - gy) // 14))).resize((W, H - gy), Image.BICUBIC).filter(ImageFilter.GaussianBlur(2))
    r = np.array(ri, np.float32)
    shift = (np.sin(np.arange(H - gy) * 0.7) * 4 + rr.normal(0, 2, H - gy)).astype(int)
    for i in range(H - gy):
        r[i] = np.roll(r[i], shift[i], 0)
    street = np.full((H - gy, W, 3), [5, 9, 12], np.float32)
    fade = np.linspace(1.1, 0.45, H - gy)[:, None, None]
    arr[gy:] = street + r * fade
    # Puddle highlights and kerb lines.
    pd = np.zeros((H - gy, W), np.float32)
    for k in range(6):
        y0 = rr.uniform(0.05, 0.9) * (H - gy)
        pd[int(y0):int(y0) + 1] += 0.08
    arr[gy:] += (np.array([120, 200, 220], np.float32) * pd[..., None])
    if figure:
        fig = Image.new("L", (W, H), 0)
        fd = ImageDraw.Draw(fig)
        fx, fy = W * 0.5, gy + (H - gy) * 0.55
        uw, uh = 0.22 * W, 0.1 * H
        top = fy - 0.42 * H
        fd.pieslice((fx - uw, top, fx + uw, top + uh * 2), 180, 360, fill=255)
        for k in range(8):
            sx = fx - uw + (k + 0.5) * uw * 2 / 8
            fd.ellipse((sx - uw / 8, top + uh - uh * 0.18, sx + uw / 8, top + uh + uh * 0.18), fill=0)
        fd.line([(fx, top), (fx, top - 0.03 * H)], fill=255, width=3)
        fd.line([(fx, top + uh), (fx, top + uh + 0.12 * H)], fill=255, width=3)
        hx, hy = fx + 0.01 * W, top + uh + 0.04 * H
        fd.ellipse((hx - 0.035 * W, hy, hx + 0.035 * W, hy + 0.075 * H), fill=255)
        fd.polygon([(hx - 0.075 * W, hy + 0.07 * H), (hx + 0.075 * W, hy + 0.07 * H), (hx + 0.1 * W, fy), (hx - 0.1 * W, fy)], fill=255)
        m = np.array(fig.filter(ImageFilter.GaussianBlur(0.8)), np.float32)[..., None] / 255
        arr = arr * (1 - m) + np.array([3, 5, 8], np.float32) * m
        edge = np.array(fig.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(1.2)), np.float32)[..., None] / 255
        arr += MAGENTA * edge * np.clip((xx[..., None] - fx) / W * -6, 0, 1) * 1.4 + CYAN * edge * np.clip((xx[..., None] - fx) / W * 6, 0, 1) * 1.4
        # Its reflection, a dark smear on the wet street.
        fm = np.array(fig, np.float32) / 255
        rf = fm[int(2 * fy - H):int(fy)][::-1] if 2 * fy - H > 0 else fm[:int(fy)][::-1]
        rf = np.array(Image.fromarray((rf * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)), np.float32) / 255
        h2 = min(H - int(fy), rf.shape[0])
        arr[int(fy):int(fy) + h2] *= (1 - 0.7 * rf[:h2])[..., None]
    # Rain: thin slanted streaks, a few bright near ones, rings on the street.
    rain = Image.new("L", (W, H), 0)
    rd = ImageDraw.Draw(rain)
    for k in range(int(700 * W * H / 640 ** 2)):
        x, y = rr.uniform(-40, W), rr.uniform(-40, H)
        ln = rr.uniform(10, 30) * H / 640
        rd.line([(x, y), (x + ln * 0.18, y + ln)], fill=int(rr.uniform(30, 100)), width=1)
    for k in range(int(30 * W * H / 640 ** 2)):
        x, y = rr.uniform(0, W), rr.uniform(0, H)
        ln = rr.uniform(60, 120) * H / 640
        rd.line([(x, y), (x + ln * 0.18, y + ln)], fill=int(rr.uniform(110, 190)), width=2)
    for k in range(int(120 * W * H / 640 ** 2)):
        x, y = rr.uniform(0, W), rr.uniform(gy + 4, H)
        s = 2 + (y - gy) / (H - gy) * 6
        rd.ellipse((x - s, y - s * 0.3, x + s, y + s * 0.3), outline=int(rr.uniform(50, 120)))
    rain_a = np.array(rain.filter(ImageFilter.GaussianBlur(0.5)), np.float32)[..., None] / 255
    arr = arr + (np.array([170, 215, 230], np.float32) - arr * 0.3) * rain_a * 0.5
    arr[..., 0] = np.roll(arr[..., 0], 2, 1)
    arr[..., 2] = np.roll(arr[..., 2], -2, 1)
    arr += rr.normal(0, 3.5, (H, W))[..., None]
    v = 1 - 0.5 * (((xx - W / 2) / W) ** 2 + ((yy - H / 2) / H) ** 2)
    arr *= v[..., None]
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).save(f"{OUT}/{name}.jpg", quality=92)


cyber_city("cp_city", 640, 640, 1)
cyber_city("cp_alley", 640, 640, 2, horizon=0.86, alley=True)
cyber_city("cp_umbrella", 640, 640, 3, horizon=0.7, figure=True)
cyber_city("cp_bill", 640, 640, 4, horizon=0.74, billboard=(140, 120, 500, 330))
# Page backdrops: the home's skyline and the player's street with an empty billboard for the album art.
cyber_city("cp_home_bg", 780, 900, 7, horizon=0.62)
cyber_city("cp_player_bg", 780, 1688, 9, horizon=0.56)
print("ok")
