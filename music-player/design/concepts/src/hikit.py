"""Hi-fi parts for 机, drawn with hirender. Coordinates are logical (phone) pixels; S device pixels each."""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from hirender import Plate, blur, text_width  # noqa: E402,F401

S = 3
ALU = (.80, .775, .735)      # champagne-silver, brushed
ALU_HI = (.97, .95, .91)     # its diamond-cut chamfers
STEEL = (.46, .46, .48)
BLACK = (.045, .045, .05)
INK = (.075, .075, .08)      # silk-screen on silver
INK_SOFT = (.12, .12, .13)
INK_L = (.78, .77, .74)      # silk-screen on black
INK_LS = (.52, .52, .51)
VFD = (.40, 1.0, .82)
VFD_OFF = (.016, .05, .043)
AMBER = (1.0, .58, .16)
RED = (1.0, .2, .1)
GREEN = (.45, 1.0, .45)
CREAM = (.96, .87, .66)


def L(*v):
    return [x * S for x in v]


# ── print ──
def prn(p, x, y, s, size, color=INK, weight="SemiBold", anchor="ls", spacing=0., kind="latin", engrave=0., emis=None):
    w = text_width(s, size, weight, kind, spacing)
    x0 = x - (w / 2 if anchor[0] == "m" else w if anchor[0] == "r" else 0)
    box = L(x0 - 2, y - size * 1.25, x0 + w + 2, y + size * .5)
    m = p.draw(box, lambda d: d.text((x * S, y * S), s, size * S, weight, kind, anchor=anchor, spacing=spacing * S))
    if emis is None:
        p.paint(m, alb=color, metal=0., rough=.55, aniso=0., panelk=0.)
    else:
        p.emis *= (1 - m[..., None])
        p.glow(m, emis)
    if engrave:
        p.H -= engrave * S * m
    return m


def tmask(p, x, y, s, size, weight="SemiBold", anchor="ls", spacing=0., kind="latin"):
    w = text_width(s, size, weight, kind, spacing)
    x0 = x - (w / 2 if anchor[0] == "m" else w if anchor[0] == "r" else 0)
    return p.draw(L(x0 - 2, y - size * 1.3, x0 + w + 2, y + size * .5), lambda d: d.text((x * S, y * S), s, size * S, weight, kind, anchor=anchor, spacing=spacing * S))


def gauss(p, cx, cy, sig, k=3.5):
    out = np.zeros((p.h, p.w), np.float32)
    sl = p._sl(cx - sig * k, cy - sig * k, cx + sig * k, cy + sig * k, 0)
    out[sl] = np.exp(-((p.X[sl] - cx) ** 2 + (p.Y[sl] - cy) ** 2) / (2 * sig * sig))
    return out


def seg(p, x0, y0, x1, y1, r):
    """Capsule distance from a segment."""
    out = np.full((p.h, p.w), 1e4, np.float32)
    sl = p._sl(min(x0, x1) - r, min(y0, y1) - r, max(x0, x1) + r, max(y0, y1) + r, 4)
    px, py = p.X[sl] - x0, p.Y[sl] - y0
    bx, by = x1 - x0, y1 - y0
    h = np.clip((px * bx + py * by) / (bx * bx + by * by + 1e-9), 0, 1)
    out[sl] = np.hypot(px - bx * h, py - by * h) - r
    return out


# ── surfaces ──
def faceplate(p, x0, y0, x1, y1, finish="alu", base=-9., thick=3., r=2.5):
    d = p.rrect(*L(x0, y0, x1, y1), r * S, pad=6 * S)
    m = p.cov(d)
    ring = p.raise_to(d, base * S, thick * S, 1.3 * S, "chamfer")
    p.panel(*L(x0, y0, x1, y1))
    if finish == "alu":
        p.brushed(m, ALU, 0., strength=.05, panelk=1.)
        p.paint(ring, alb=ALU_HI, rough=.07, aniso=0., smod=1., panelk=0.)
    else:
        p.brushed(m, BLACK, 0., strength=.35, rough=.4, panelk=.45)
        p.paint(ring, alb=(.62, .61, .6), rough=.08, aniso=0., smod=1., panelk=0.)
    return (base + thick)  # face height (logical)


def screw(p, cx, cy, r=3.2, rot=.35, finish="steel"):
    cx, cy, r = cx * S, cy * S, r * S
    hole = p.circle(cx, cy, r + .55 * S)
    p.lift(hole, -1.2 * S, .5 * S, "chamfer")
    p.paint(p.cov(hole), alb=(.02, .02, .02), metal=0., rough=.7, aniso=0., panelk=0.)
    d = p.circle(cx, cy, r)
    p.lift(d, 2.4 * S, r * .95, "dome")
    alb = STEEL if finish == "steel" else (.06, .06, .065)
    p.paint(p.cov(d), alb=alb, metal=1., rough=.24, aniso=0., panelk=0., smod=1.)
    hx = p.hexagon(cx, cy, r * .40, rot)
    p.lift(hx, -1.8 * S, .35 * S, "chamfer")
    p.paint(p.cov(hx), alb=(.025, .025, .03), metal=0., rough=.8)


def led(p, cx, cy, r=2.0, color=RED, on=True, k=1.6):
    cx, cy, r = cx * S, cy * S, r * S
    hole = p.circle(cx, cy, r + .5 * S)
    p.lift(hole, -.7 * S, .3 * S, "chamfer")
    p.paint(p.cov(hole), alb=(.015, .015, .015), metal=0., aniso=0., panelk=0., rough=.7)
    d = p.circle(cx, cy, r)
    p.lift(d, 1.3 * S, r, "dome")
    m = p.cov(d)
    c = np.asarray(color, np.float32)
    if on:
        p.paint(m, alb=c * .55, metal=0., rough=.12, gloss=.9, aniso=0., panelk=0.)
        core = gauss(p, cx, cy, r * .55)
        p.glow(np.maximum(core, m * .45), c, k)
    else:
        p.paint(m, alb=c * .09 + .01, metal=0., rough=.12, gloss=.9, aniso=0., panelk=0.)


def well(p, x0, y0, x1, y1, r, floor):
    d = p.rrect(*L(x0, y0, x1, y1), r * S, pad=3 * S)
    c = p.cov(d)
    p.H = p.H * (1 - c) + floor * S * c
    p.paint(c, alb=(.015, .015, .015), metal=0., rough=.7, aniso=0., panelk=0.)


def button(p, x0, y0, x1, y1, face, finish="alu", pressed=False, r=1.6, gap=1.1, travel=4.):
    """A push button standing in its own slot in the panel."""
    well(p, x0 - gap, y0 - gap, x1 + gap, y1 + gap, r + gap, face - travel - 2)
    d = p.rrect(*L(x0, y0, x1, y1), r * S, pad=3 * S)
    top = face + (.6 if pressed else travel - 1.)
    floor = face - travel - 2
    m = p.cov(d)
    ring = p.raise_to(d, floor * S, (top - floor) * S, 1.5 * S, "fillet")
    p.panel(*L(x0, y0, x1, y1))
    if finish == "alu":
        p.brushed(m, ALU, 0., strength=.05, panelk=.9)
        p.paint(ring, alb=ALU_HI, rough=.1, aniso=0., panelk=0.)
    else:
        p.brushed(m, (.07, .07, .075), 0., strength=.3, rough=.38, panelk=.5)
        p.paint(ring, alb=(.35, .35, .36), rough=.12, aniso=0., panelk=0.)
    return top


def knob(p, cx, cy, r, face, angle=-40., finish="alu", height=None, mark="line", grip=True, ticks=None):
    """A machined knob: knurled grip ring, a polished chamfer, a spun cap with an engraved pointer."""
    cx_, cy_, r_ = cx * S, cy * S, r * S
    height = height if height is not None else max(5., r * .42)
    # A dark ring where it stands in the panel.
    hole = p.circle(cx_, cy_, r_ + .9 * S)
    p.lift(hole, -1.4 * S, .6 * S, "chamfer")
    p.paint(p.cov(hole), alb=(.015, .015, .015), metal=0., rough=.7, aniso=0., panelk=0.)
    d = p.circle(cx_, cy_, r_)
    sl = p._sl(cx_ - r_, cy_ - r_, cx_ + r_, cy_ + r_, 4)
    th = np.zeros((p.h, p.w), np.float32)
    rr = np.full((p.h, p.w), 9., np.float32)
    th[sl] = np.arctan2(p.Y[sl] - cy_, p.X[sl] - cx_)
    rr[sl] = np.hypot(p.X[sl] - cx_, p.Y[sl] - cy_) / r_
    base = face - 1.4
    p.raise_to(d, base * S, height * S, 1.4 * S, "fillet")
    m = p.cov(d)
    rc = .80 if grip else .93
    gripm = m * (rr > rc - .02)
    n = max(24, int(2 * math.pi * r / 2.0))
    ridges = (.5 + .5 * np.cos(n * th)) ** 1.2
    if grip:
        p.H += gripm * (ridges - .5) * 1.5 * S
        galb = np.asarray(ALU if finish == "alu" else (.09, .09, .095), np.float32)[None, None] * (.55 + .45 * ridges[..., None])
        p.paint(gripm, alb=galb, metal=1., rough=.3, tan=th + math.pi / 2, aniso=.6, panelk=0., smod=1.)
    else:
        p.spun(gripm, cx_, cy_, ALU if finish == "alu" else (.1, .1, .105))
    # The cap: a chamfer up to a spun face, slightly dished.
    dc = p.circle(cx_, cy_, r_ * rc)
    capm = p.cov(dc)
    ring = p.raise_to(dc, (base + height) * S, 2.2 * S, 1.6 * S, "chamfer")
    dish = np.clip(1 - (rr / rc) ** 2, 0, 1)
    p.H -= capm * dish * min(2.4, r * .06) * S
    capc = ALU if finish == "alu" else (.11, .11, .115)
    p.spun(capm, cx_, cy_, capc, strength=.06, rough=.22)
    p.paint(ring, alb=ALU_HI if finish == "alu" else (.7, .7, .7), rough=.06, aniso=0., panelk=0., metal=1., smod=1.)
    # Pointer.
    a = math.radians(angle - 90)
    if mark == "none":
        return base + height + 2.2
    if mark == "line":
        x0, y0 = cx_ + math.cos(a) * r_ * rc * .42, cy_ + math.sin(a) * r_ * rc * .42
        x1, y1 = cx_ + math.cos(a) * r_ * rc * .86, cy_ + math.sin(a) * r_ * rc * .86
        dl = seg(p, x0, y0, x1, y1, max(.8, r * .045) * S)
    else:
        px, py = cx_ + math.cos(a) * r_ * rc * .7, cy_ + math.sin(a) * r_ * rc * .7
        dl = p.circle(px, py, max(1.2, r * .09) * S)
    ml = p.cov(dl) * capm
    p.H -= ml * 1.1 * S
    p.paint(ml, alb=(.04, .04, .04) if finish == "alu" else (.9, .9, .9), metal=0., rough=.6, aniso=0.)
    return base + height + 2.2


def ticks(p, cx, cy, r0, r1, a0, a1, n, color=INK, width=.7, major=None, dots=False):
    def fn(d):
        for i in range(n):
            a = math.radians(a0 + (a1 - a0) * i / (n - 1) - 90)
            big = major and i % major == 0
            rr1 = r1 + (1.6 if big else 0)
            if dots:
                rr = (width * (1.5 if big else 1)) * S
                x, y = (cx + math.cos(a) * r1) * S, (cy + math.sin(a) * r1) * S
                d.ellipse([x - rr, y - rr, x + rr, y + rr])
            else:
                d.line([((cx + math.cos(a) * r0) * S, (cy + math.sin(a) * r0) * S), ((cx + math.cos(a) * rr1) * S, (cy + math.sin(a) * rr1) * S)], width=width * S * (1.3 if big else 1))
    rmax = r1 + 3
    m = p.draw(L(cx - rmax, cy - rmax, cx + rmax, cy + rmax), fn)
    p.paint(m, alb=color, metal=0., rough=.55, aniso=0., panelk=0.)
    return m


def window(p, x0, y0, x1, y1, face, r=2.5, depth=2.6, bezel="alu", glass=(.006, .008, .008)):
    """A sunken black-glass window with a polished bezel; returns the glass mask."""
    d = p.rrect(*L(x0, y0, x1, y1), r * S, pad=4 * S)
    ring = p.raise_to(d, (face - depth) * S, 0, 1, "chamfer")
    # Bezel: a polished chamfer sloping down to the glass.
    t = np.clip(-d / (1.6 * S), 0, 1)
    c = p.cov(d)
    p.H = p.H * (1 - c) + (face * S - depth * S * t) * c
    bz = c * (t < .999)
    inner = c * (t >= .999)
    p.paint(bz, alb=ALU_HI if bezel == "alu" else (.5, .5, .52), metal=1., rough=.08, aniso=0., panelk=0., smod=1.)
    p.paint(inner, alb=glass, metal=0., rough=.05, aniso=0., gloss=1., panelk=0.)
    return inner


def mesh(p, m, period=1.4, k=.3):
    """The fine grid in front of a fluorescent display."""
    g = ((np.mod(p.X, period * S) < .55 * S) | (np.mod(p.Y + p.X * .0, period * S * 1.6) < .5 * S)).astype(np.float32)
    p.emis *= (1 - k * g * m)[..., None]


SEGS = {"0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc", "5": "afgcd", "6": "afgedc", "7": "abc", "8": "abcdefg",
        "9": "abcdfg", "-": "g", " ": ""}


def seg7(p, x, y, h, text, on=VFD, off=VFD_OFF, w=None, t=None, sl=.13, pitch=None, k=1.0):
    """Seven-segment digits (a '.' attaches to the previous digit) as fluorescent segments."""
    w = w or h * .52
    t = t or h * .11
    pitch = pitch or w + h * .16
    cells, xx = [], x
    for ch in text:
        if ch == ".":
            cells[-1][2] = True
            continue
        cells.append([xx, ch, False])
        xx += pitch
    g = t * .16

    def polys(cx, ch):
        def sk(px, py):
            return ((px + sl * (y + h - py)) * S, py * S)

        def hz(yy):
            return [sk(cx + g + t * .5, yy), sk(cx + g + t, yy - t * .5), sk(cx + w - g - t, yy - t * .5), sk(cx + w - g - t * .5, yy),
                    sk(cx + w - g - t, yy + t * .5), sk(cx + g + t, yy + t * .5)]

        def vt(xx_, a, b):
            return [sk(xx_, a + g + t * .5), sk(xx_ + t * .5, a + g + t), sk(xx_ + t * .5, b - g - t), sk(xx_, b - g - t * .5),
                    sk(xx_ - t * .5, b - g - t), sk(xx_ - t * .5, a + g + t)]
        return {"a": hz(y + t * .5), "g": hz(y + h / 2), "d": hz(y + h - t * .5),
                "f": vt(cx + t * .5, y, y + h / 2), "b": vt(cx + w - t * .5, y, y + h / 2),
                "e": vt(cx + t * .5, y + h / 2, y + h), "c": vt(cx + w - t * .5, y + h / 2, y + h)}

    def draw(lit):
        def fn(d):
            for cx, ch, dot in cells:
                ps = polys(cx, ch)
                for k_, poly in ps.items():
                    if (k_ in SEGS.get(ch, "")) == lit:
                        d.d.polygon([((px - d.ox) * d.ss, (py - d.oy) * d.ss) for px, py in poly], fill=255)
                dx, dy = (cx + w + h * .05) * S, (y + h - t * .5) * S
                if dot == lit or (not lit and not dot):
                    if dot == lit:
                        rr = t * .55 * S
                        d.d.ellipse([(dx - rr - d.ox) * d.ss, (dy - rr - d.oy) * d.ss, (dx + rr - d.ox) * d.ss, (dy + rr - d.oy) * d.ss], fill=255)
        return p.draw(L(x - 2, y - 2, xx + h * .3, y + h + 2), fn)
    mon, moff = draw(True), draw(False)
    p.glow(moff, off, 1.)
    p.glow(mon, on, k)
    return xx


def vtext(p, x, y, s, size, color=VFD, weight="SemiBold", anchor="ls", spacing=0., kind="latin", k=1.):
    m = tmask(p, x, y, s, size, weight, anchor, spacing, kind)
    p.glow(m, color, k)
    return m


def dots(p, x, y, s, color=VFD, k=1., off=None, anchor="ls", px=12):
    """Dot-matrix lettering in Fusion Pixel: each font pixel becomes a round phosphor dot."""
    m = tmask(p, x, y, s, px, "Regular", anchor, 0., "pixel")
    cell = S * px / 12
    gx = np.mod(p.X - x * S, cell) / cell - .5
    gy = np.mod(p.Y - (y * S), cell) / cell - .5
    dotm = np.clip(1.6 - np.hypot(gx, gy) * 3.2, 0, 1)
    mm = np.clip(m * 1.6, 0, 1) * dotm
    p.glow(mm, color, k)
    return mm


def wood(p, x0, y0, x1, y1, angle=0., scale=1.):
    d = p.rrect(*L(x0, y0, x1, y1), .1, pad=2)
    p.wood(p.cov(d), angle=angle, scale=scale * S / 3)


def vu_meter(p, x0, y0, x1, y1, face, val=.6, label="LEFT", lamp=1.):
    """A backlit VU meter behind glass: cream scale, red zone, black needle with its shadow, a shroud."""
    inner = window(p, x0, y0, x1, y1, face, r=2., depth=3.2)
    m = inner
    w_, h_ = x1 - x0, y1 - y0
    cx, cy = (x0 + x1) / 2, y1 + h_ * .38
    R = h_ * 1.02
    # Paper, lit from below by two little lamps.
    lampg = (gauss(p, (cx - w_ * .28) * S, (y1 - 2) * S, h_ * .55 * S) + gauss(p, (cx + w_ * .28) * S, (y1 - 2) * S, h_ * .55 * S)) * .55
    lit = np.clip(lampg, 0, 1)
    vy = np.clip((p.Y / S - y0) / h_, 0, 1)
    p.paint(m, alb=np.asarray((.93, .78, .50), np.float32)[None, None] * (.42 + .38 * vy[..., None] + .3 * lit[..., None]), metal=0., rough=.8, gloss=1.)
    p.glow(m * (.04 + .5 * lit) * lamp, (1., .66, .3))
    a0, a1 = -50, 50
    vals = [(-20, "20"), (-10, "10"), (-7, "7"), (-5, "5"), (-3, "3"), (-2, ""), (-1, ""), (0, "0"), (1, ""), (2, ""), (3, "3")]

    def ang(db):
        # VU is close to linear in voltage: map -20..+3 dB to the arc.
        v = 10 ** (db / 20)
        return a0 + (a1 - a0) * (v - .1) / (1.413 - .1)

    def scale(d):
        d.arc([(cx - R) * S, (cy - R) * S, (cx + R) * S, (cy + R) * S], 270 + a0, 270 + ang(0), width=.5 * S)
        for db, lab in vals:
            a = math.radians(ang(db) - 90)
            l2 = 4.5 if lab else 3
            d.line([((cx + math.cos(a) * R) * S, (cy + math.sin(a) * R) * S), ((cx + math.cos(a) * (R + l2)) * S, (cy + math.sin(a) * (R + l2)) * S)], width=.55 * S)
            if lab and db < 0:
                d.text(((cx + math.cos(a) * (R + 9)) * S, (cy + math.sin(a) * (R + 9)) * S), lab, 5.6 * S, "SemiBold", anchor="mm")
        for i in range(0, 11):
            a = math.radians(a0 + (a1 - a0) * i / 10 * .86 - 90)
            d.line([((cx + math.cos(a) * (R - 6)) * S, (cy + math.sin(a) * (R - 6)) * S), ((cx + math.cos(a) * (R - 8)) * S, (cy + math.sin(a) * (R - 8)) * S)], width=.4 * S)
        d.text((cx * S, (cy - R * .38) * S), "VU", 10 * S, "Bold", anchor="mm")
        d.text(((x0 + 5) * S, (y0 + 9) * S), "-", 6 * S, "SemiBold", anchor="lm")
        d.text(((x1 - 5) * S, (y0 + 9) * S), "+", 6 * S, "SemiBold", anchor="rm")
        d.text(((x0 + 6) * S, (y1 - 5) * S), "LANYIN", 3.8 * S, "Bold", anchor="ls", spacing=.6 * S)

    def red(d):
        d.arc([(cx - R - 1.5) * S, (cy - R - 1.5) * S, (cx + R + 1.5) * S, (cy + R + 1.5) * S], 270 + ang(0), 270 + a1, width=3.2 * S)
        a = math.radians(ang(3) - 90)
        d.text(((cx + math.cos(a) * (R + 9)) * S, (cy + math.sin(a) * (R + 9)) * S), "+3", 5.6 * S, "SemiBold", anchor="mm")
        a = math.radians(ang(0) - 90)
        d.text(((cx + math.cos(a) * (R + 9)) * S, (cy + math.sin(a) * (R + 9)) * S), "0", 5.6 * S, "SemiBold", anchor="mm")
    ms = p.draw(L(x0, y0, x1, y1), scale) * m
    mr = p.draw(L(x0, y0, x1, y1), red) * m
    for mk, colr in ((ms, (.09, .08, .07)), (mr, (.78, .14, .08))):
        p.paint(mk, alb=colr)
        p.emis *= (1 - .85 * mk[..., None])
    # Needle with a shadow on the paper.
    a = math.radians(a0 + (a1 - a0) * val - 90)
    tipx, tipy = cx + math.cos(a) * (R + 3), cy + math.sin(a) * (R + 3)
    dn = seg(p, cx * S, cy * S, tipx * S, tipy * S, .42 * S)
    mn = p.cov(dn) * m
    p.H += mn * 3.2 * S
    p.paint(mn, alb=(.03, .03, .03), gloss=0., rough=.5)
    p.emis *= (1 - mn[..., None])
    # Shroud over the pivot.
    sh = p.rrect(*L(cx - w_ * .3, y1 - h_ * .2, cx + w_ * .3, y1 + 4), 3 * S)
    msh = p.cov(sh) * m
    p.H = p.H * (1 - msh) + (face - .8) * S * msh
    p.paint(msh, alb=(.03, .03, .032), metal=0., rough=.35, gloss=.6)
    p.emis *= (1 - msh[..., None])
    mt = tmask(p, cx, y1 - h_ * .07, label, 4.4, "SemiBold", "ms", .9)
    p.paint(mt * msh, alb=(.55, .53, .5), gloss=0.)
    return m


def disc(p, cx, cy, r, img, face, lift=1.2, smoke=1.):
    """A compact disc, label side up, printed with a cover; clear hub ring and centre hole."""
    cx_, cy_, r_ = cx * S, cy * S, r * S
    d = p.circle(cx_, cy_, r_)
    m = p.cov(d)
    p.H = p.H * (1 - m) + (face + lift) * S * m
    rr = np.hypot(p.X - cx_, p.Y - cy_) / r_
    th = np.arctan2(p.Y - cy_, p.X - cx_)
    # Cover printed over the label area.
    size = int(2 * r_)
    im = Image.open(img).convert("RGB").resize((size, size), Image.LANCZOS)
    arr = np.zeros((p.h, p.w, 3), np.float32)
    x0, y0 = int(cx_ - r_), int(cy_ - r_)
    ys, xs = slice(max(0, y0), min(p.h, y0 + size)), slice(max(0, x0), min(p.w, x0 + size))
    src = np.asarray(im, np.float32) / 255
    arr[ys, xs] = src[ys.start - y0:ys.stop - y0, xs.start - x0:xs.stop - x0]
    arr = arr ** 1.15 * smoke
    label = m * (rr > .33) * (rr < .965)
    p.paint(label, alb=arr, metal=0., rough=.3, gloss=.55, aniso=0., panelk=0.)
    # Silver rim and the iridescent edge where the data layer shows.
    rim = m * (rr >= .965)
    hue = (th / (2 * math.pi) * 2 + rr * 3) % 1
    irid = np.stack([.65 + .3 * np.cos(2 * math.pi * hue), .65 + .3 * np.cos(2 * math.pi * (hue - .33)), .65 + .3 * np.cos(2 * math.pi * (hue - .66))], -1) * smoke
    p.paint(rim, alb=irid, metal=1., rough=.15, aniso=0., panelk=0.)
    hub = m * (rr <= .33) * (rr > .13)
    p.paint(hub, alb=np.asarray((.3, .32, .33), np.float32) * smoke, metal=.3, rough=.12, gloss=.8, aniso=0., panelk=0.)
    ring = m * (np.abs(rr - .24) < .025)
    p.H += ring * .5 * S
    hole = m * (rr <= .13)
    p.H -= hole * 4 * S
    p.paint(hole, alb=(.01, .01, .01), metal=0., gloss=0.)
    return m


def cassette(p, x0, y0, w, face, title="雨停以后", prog=.3, side="A", detail=1., shell=(.07, .06, .055), hand=True, artist=""):
    """A compact cassette seen from the front: smoked shell, paper label with a handwritten title,
    reels whose tape packs follow the progress, screws, the head-access trapezoid."""
    h = w * .63
    x1, y1 = x0 + w, y0 + h
    u = w / 100.
    d = p.rrect(*L(x0, y0, x1, y1), 3 * u * S, pad=4 * S)
    m = p.cov(d)
    ring = p.raise_to(d, face * S, 2.2 * u * S, 1.2 * u * S, "fillet")
    p.paint(m, alb=shell, metal=0., rough=.28, gloss=.55, aniso=0., panelk=0.)
    # Ribs along the top edge.
    rib = p.draw(L(x0, y0, x1, y1), lambda dd: [dd.line([((x0 + 6 * u + i * 2.2 * u) * S, (y0 + 2 * u) * S), ((x0 + 6 * u + i * 2.2 * u) * S, (y0 + 5 * u) * S)], width=.5 * u * S) for i in range(40)])
    p.H += rib * .4 * u * S
    # Label.
    lx0, ly0, lx1, ly1 = x0 + 7 * u, y0 + 6.5 * u, x1 - 7 * u, y0 + 44 * u
    dl = p.rrect(*L(lx0, ly0, lx1, ly1), 1.6 * u * S)
    ml = p.cov(dl)
    paper = np.asarray((.93, .89, .80), np.float32)[None, None] * (1 - .022 * p.noise(2.5 * S, 2)[..., None])
    p.paint(ml, alb=paper, gloss=0., rough=.9)
    p.H += ml * .3 * S
    band = p.cov(p.rrect(*L(lx0, ly0, lx1, ly0 + 7.5 * u), 1.6 * u * S)) * ml
    p.paint(band, alb=(.80, .25, .14))
    mt = tmask(p, lx0 + 3 * u, ly0 + 5.7 * u, side, 5.6 * u, "Bold")
    p.paint(mt * band, alb=(.97, .93, .86))
    mt = tmask(p, lx1 - 3 * u, ly0 + 5.5 * u, "CrO2 · 60 MIN", 3.3 * u, "SemiBold", "rs", .3 * u)
    p.paint(mt * band, alb=(.97, .93, .86))
    # Ruled lines and the handwritten title.
    rules = p.draw(L(lx0, ly0, lx1, ly1), lambda dd: [dd.line([((lx0 + 3 * u) * S, (ly0 + (15.5 + 6.2 * i) * u) * S), ((lx1 - 3 * u) * S, (ly0 + (15.5 + 6.2 * i) * u) * S)], width=.22 * u * S) for i in range(1)])
    p.paint(rules * ml, alb=(.62, .70, .80))
    if hand:
        mh = tmask(p, lx0 + 4 * u, ly0 + 15 * u, title, 8.2 * u, "Regular", "ls", .5 * u, "hand")
        p.paint(mh * ml, alb=(.12, .14, .26))
        if artist:
            ma = tmask(p, lx1 - 4 * u, ly0 + 14.6 * u, artist, 4.3 * u, "Regular", "rs", .2 * u, "hand")
            p.paint(ma * ml, alb=(.12, .14, .26))
    # The window onto the reels.
    wx0, wy0, wx1, wy1 = x0 + 25 * u, y0 + 23 * u, x1 - 25 * u, y0 + 39 * u
    dw = p.rrect(*L(wx0, wy0, wx1, wy1), 3 * u * S)
    mw = p.cov(dw)
    p.H -= mw * 1.2 * S
    p.paint(mw, alb=(.02, .018, .016), gloss=.7, rough=.1)
    for i, (rx, pack) in enumerate(((x0 + 33 * u, 1 - prog), (x1 - 33 * u, prog))):
        ry = (wy0 + wy1) / 2
        rp = 5.2 * u + 9.5 * u * pack
        dp = p.circle(rx * S, ry * S, rp * S)
        mp = p.cov(dp) * mw
        p.paint(mp, alb=(.16, .09, .055), gloss=.25, rough=.3)
        rings = np.sin(np.hypot(p.X - rx * S, p.Y - ry * S) * 2.1) * .5 + .5
        p.alb *= (1 - .18 * (mp * rings))[..., None]
        dh = p.circle(rx * S, ry * S, 4.9 * u * S)
        mh_ = p.cov(dh)
        p.H += mh_ * .8 * S
        p.paint(mh_, alb=(.92, .91, .88), gloss=.3, rough=.4)
        dt = p.circle(rx * S, ry * S, 3.0 * u * S)
        p.paint(p.cov(dt), alb=(.05, .05, .05))
        teeth = p.draw(L(rx - 5 * u, ry - 5 * u, rx + 5 * u, ry + 5 * u),
                       lambda dd: [dd.line([((rx + math.cos(a) * 1.2 * u) * S, (ry + math.sin(a) * 1.2 * u) * S), ((rx + math.cos(a) * 3.0 * u) * S, (ry + math.sin(a) * 3.0 * u) * S)], width=.9 * u * S)
                                   for a in [k * math.pi / 3 + i * .4 + prog * 7 for k in range(6)]])
        p.paint(teeth * p.cov(dt), alb=(.9, .89, .86))
    # Window frame print.
    frame = p.draw(L(wx0 - 2, wy0 - 2, wx1 + 2, wy1 + 2), lambda dd: dd.rect([(wx0 - .6 * u) * S, (wy0 - .6 * u) * S, (wx1 + .6 * u) * S, (wy1 + .6 * u) * S], r=3.4 * u * S))
    p.paint(frame * ml * (1 - mw), alb=(.12, .11, .1))
    # Trapezoid at the bottom.
    tz = p.draw(L(x0, y0 + 46 * u, x1, y1), lambda dd: dd.polygon([((x0 + 18 * u) * S, (y0 + 49 * u) * S), ((x1 - 18 * u) * S, (y0 + 49 * u) * S), ((x1 - 13 * u) * S, (y1 + .5) * S), ((x0 + 13 * u) * S, (y1 + .5) * S)]))
    p.H += tz * 1.2 * u * S
    p.paint(tz * m, alb=np.asarray(shell) * 1.25 + .01)
    for hx in (x0 + 31 * u, x1 - 31 * u, x0 + 40 * u, x1 - 40 * u):
        dh = p.circle(hx * S, (y0 + 56.5 * u) * S, (1.7 if hx in (x0 + 31 * u, x1 - 31 * u) else 1.1) * u * S)
        p.H -= p.cov(dh) * 1.5 * S
        p.paint(p.cov(dh), alb=(.01, .01, .01), gloss=0.)
    if detail:
        for sx, sy in ((x0 + 4 * u, y0 + 4 * u), (x1 - 4 * u, y0 + 4 * u), (x0 + 4 * u, y1 - 4 * u), (x1 - 4 * u, y1 - 4 * u), ((x0 + x1) / 2, y1 - 4 * u)):
            ds = p.circle(sx * S, sy * S, 1.4 * u * S)
            p.lift(ds, .5 * S, .8 * u * S, "dome")
            p.paint(p.cov(ds), alb=(.5, .5, .5), metal=1., rough=.3)
    return y1


def toggle(p, cx, cy, face, up=True, r=5.):
    """A bat-handle toggle: hex nut on a threaded bushing, a chrome lever thrown up or down."""
    cx_, cy_ = cx * S, cy * S
    d = p.circle(cx_, cy_, (r + 1.6) * S)
    p.lift(d, -.8 * S, .5 * S, "chamfer")
    hx = p.hexagon(cx_, cy_, r * S, .52)
    p.raise_to(hx, face * S, 2.6 * S, .9 * S, "chamfer")
    p.paint(p.cov(hx), alb=(.72, .72, .73), metal=1., rough=.18, aniso=0., panelk=0.)
    db = p.circle(cx_, cy_, r * .62 * S)
    p.raise_to(db, (face + 2.6) * S, 1.4 * S, .5 * S, "fillet")
    p.spun(p.cov(db), cx_, cy_, (.8, .8, .8))
    ty = cy - 11 if up else cy + 11
    dl = seg(p, cx_, cy_, cx_, ty * S, 1.25 * S)
    ml = p.cov(dl)
    t = np.clip(np.abs(p.Y - cy_) / (11 * S), 0, 1)
    p.H = p.H * (1 - ml) + ((face + 12 - 4 * t) * S + np.sqrt(np.clip(1 - (dl / (1.25 * S) + 1) ** 2, 0, 1)) * 1.2 * S) * ml
    p.paint(ml, alb=(.86, .86, .87), metal=1., rough=.08, aniso=0., panelk=0.)
    dt = p.circle(cx_, ty * S, 1.9 * S)
    mt = p.cov(dt)
    p.H = np.maximum(p.H, ((face + 8.5) * S + np.sqrt(np.clip(1 - (np.hypot(p.X - cx_, p.Y - ty * S) / (1.9 * S)) ** 2, 0, 1)) * 1.9 * S) * mt)
    p.paint(mt, alb=(.86, .86, .87), metal=1., rough=.08, aniso=0., panelk=0.)


def cabinet(p, W, HT, top=50, bottom=792, side=11):
    """The walnut cabinet: vertical stiles, mitred top and bottom rails, a dark opening."""
    p.H[:] = 0
    wood(p, 0, 0, W, HT, angle=1.)
    opening = p.rrect(*L(side, top, W - side, bottom), 2.5 * S)
    p.lift(opening, -16 * S, 2.2 * S, "fillet")
    p.paint(p.cov(opening), alb=(.018, .016, .014), metal=0., rough=.8, gloss=0., aniso=0., panelk=0.)
    t = p.draw(L(0, 0, W, top), lambda d: d.polygon([(0, 0), (W * S, 0), ((W - side) * S, top * S), (side * S, top * S)]))
    p.wood(t, angle=0., scale=1.)
    b = p.draw(L(0, bottom, W, HT), lambda d: d.polygon([(side * S, bottom * S), ((W - side) * S, bottom * S), (W * S, HT * S), (0, HT * S)]))
    p.wood(b, angle=0., scale=1.)


def drums(p, x, y, digits, face, cw=9., ch=14.):
    """A mechanical tape counter: white figures on black drums behind a little window."""
    n = len(digits)
    w = n * cw + (n - 1) * 1.2
    window(p, x - 2.5, y - 2.5, x + w + 2.5, y + ch + 2.5, face, r=1.2, depth=2.4)
    for i, dg in enumerate(digits):
        x0 = x + i * (cw + 1.2)
        d = p.rrect(*L(x0, y - 1, x0 + cw, y + ch + 1), .3 * S)
        m = p.cov(d)
        cyl = np.sqrt(np.clip(1 - ((p.Y / S - (y + ch / 2)) / (ch * .62)) ** 2, 0, 1))
        p.H = p.H * (1 - m) + ((face - 2.4) * S + cyl * 2.6 * S) * m
        p.paint(m, alb=(.05, .05, .052), metal=0., rough=.35, gloss=.0, aniso=0.)
        mt = tmask(p, x0 + cw / 2, y + ch / 2 + 4.2, dg, 11.5, "SemiBold", "ms")
        p.paint(mt * m, alb=(.93, .92, .9))
        # Half of the next figure peeks in at the top of the last drum.
        if i == n - 1:
            mt2 = tmask(p, x0 + cw / 2, y - 1.5, str((int(dg) + 1) % 10), 11.5, "SemiBold", "ms") * m
            p.paint(mt2, alb=(.93, .92, .9))
    return x + w


def piano_key(p, x0, y0, x1, y1, face, pressed=False):
    """A long transport key with rounded edges and a sloped lip at the bottom."""
    well(p, x0 - 1.3, y0 - 1.3, x1 + 1.3, y1 + 1.3, 2., face - 9)
    d = p.rrect(*L(x0, y0, x1, y1), 1.8 * S)
    m = p.cov(d)
    t = np.clip(-d / (1.4 * S), 0, 1)
    top = face + (1. if pressed else 5.)
    yy = np.clip((p.Y / S - y0) / (y1 - y0), 0, 1)
    lip = np.clip((yy - .8) / .2, 0, 1)
    prof = top - 4.5 * lip ** 1.4 - (1 - np.sqrt(1 - (1 - t) ** 2)) * 3
    p.H = p.H * (1 - m) + prof * S * m
    p.panel(*L(x0, y0, x1, y1))
    p.brushed(m, ALU if not pressed else tuple(c * .84 for c in ALU), 0., strength=.05, panelk=.9 if not pressed else .55)
    p.paint(m * (t < .999), alb=ALU_HI, rough=.1, aniso=0., panelk=0.)
    p.paint(m * (lip > .05), panelk=.25)
    return top
