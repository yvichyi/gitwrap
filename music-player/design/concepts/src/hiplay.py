"""机 · player: a cassette deck in the walnut cabinet, rendered with real materials."""
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hikit import *  # noqa: E402,F401,F403
from hikit import cabinet, drums, piano_key, toggle  # noqa: E402

C3 = os.path.dirname(os.path.abspath(__file__)) + "/"
W, HT = 390, 844
t0 = time.time()
p = Plate(W * S, HT * S, seed=11)
cabinet(p, W, HT)
X0, X1 = 14, W - 14
y0, y1 = 53, 786
f = faceplate(p, X0, y0, X1, y1, "alu")
for sx, sy in ((X0 + 7, y0 + 7), (X1 - 7, y0 + 7), (X0 + 7, y1 - 7), (X1 - 7, y1 - 7)):
    screw(p, sx, sy, 2.9)
prn(p, X0 + 15, y0 + 21, "LANYIN", 12.5, INK, "ExtraBoldItalic", engrave=.25)
prn(p, X0 + 78, y0 + 20, "TC-18 · STEREO CASSETTE DECK", 5.6, INK_SOFT, "SemiBold", spacing=1.3)
# A printed Dolby-style badge, our own: "NR · B·C".
bx0 = X1 - 82
fr = p.draw(L(bx0, y0 + 11, bx0 + 36, y0 + 23), lambda d: (d.d.rounded_rectangle([0, 0, 36 * S * 4 - 1, 12 * S * 4 - 1], radius=2 * S * 4, outline=255, width=int(.5 * S * 4))))
p.paint(fr, alb=INK, metal=0., aniso=0., panelk=0.)
prn(p, bx0 + 18, y0 + 19.6, "NR · B·C", 5.4, INK, "Bold", "ms", .4)
led(p, X1 - 36, y0 + 17, 1.5, RED, True)
knob(p, X1 - 22, y0 + 17, 6.2, f, 0, "alu", height=3., mark="none", grip=False)

# ── cassette well behind a smoked door ──
dx0, dy0, dx1, dy1 = X0 + 12, y0 + 30, X1 - 12, y0 + 260
door = window(p, dx0, dy0, dx1, dy1, f, r=3, depth=12, glass=(.02, .016, .014))
p.paint(door, gloss=0.)
cw = 300
cx0 = (dx0 + dx1) / 2 - cw / 2
cy0 = dy0 + 14
cy1 = cassette(p, cx0, cy0, cw, f - 11.5, "雨停以后", .26, artist="北岸乐队")
# Heads and pinch roller under the cassette.
hx = (dx0 + dx1) / 2
d = p.rrect(*L(hx - 13, cy1 + 4, hx + 13, cy1 + 16), 2 * S)
p.raise_to(d, (f - 11) * S, 3 * S, 1 * S, "fillet")
p.brushed(p.cov(d), (.72, .72, .73), math.pi / 2, panelk=0.)
for sx in (hx - 46, hx + 46):
    dd = p.circle(sx * S, (cy1 + 10) * S, 6 * S)
    p.raise_to(dd, (f - 11) * S, 3 * S, 1.6 * S, "fillet")
    p.paint(p.cov(dd), alb=(.03, .03, .03), metal=0., rough=.5, aniso=0.)
    dc = p.circle(sx * S, (cy1 + 10) * S, 1.6 * S)
    p.raise_to(dc, (f - 8) * S, 1.5 * S, .5 * S, "fillet")
    p.paint(p.cov(dc), alb=(.8, .8, .8), metal=1., rough=.15)
# Smoke over everything in the well, and a warm lamp from above.
smoke = door
p.alb *= (1 - .38 * smoke)[..., None]
lamp = np.clip(gauss(p, ((dx0 + dx1) / 2) * S, (dy0 - 6) * S, 120 * S), 0, 1)
p.glow(smoke * lamp * .12, (1., .78, .5))
p.emis += (smoke * lamp * .22)[..., None] * p.alb
p.paint(smoke, gloss=.9)
prn(p, dx1 - 8, dy0 + 11, "A ▸", 6, (.55, .52, .48), "Bold", "rs")

# ── under the door: eject, counter ──
ry = y0 + 268
button(p, X0 + 14, ry, X0 + 52, ry + 14, f, "alu", r=1.4, travel=3)
ei = p.draw(L(X0 + 14, ry, X0 + 52, ry + 14), lambda d: (d.polygon([L(X0 + 33, ry + 3.4), L(X0 + 37.5, ry + 8), L(X0 + 28.5, ry + 8)]), d.rect(L(X0 + 28.5, ry + 9.2, X0 + 37.5, ry + 10.6))))
p.paint(ei, alb=INK, metal=0., aniso=0.)
prn(p, X0 + 58, ry + 9.5, "EJECT", 5.3, INK_SOFT, "SemiBold", spacing=1.1)
prn(p, X0 + 116, ry + 9.5, "SIDE A · 30 MIN", 5.3, INK_SOFT, "SemiBold", spacing=1.1)
prn(p, X1 - 104, ry + 9.5, "COUNTER", 5.3, INK_SOFT, "SemiBold", "rs", 1.1)
xe = drums(p, X1 - 96, ry - 1, "0058", f, cw=8.5, ch=13)
button(p, X1 - 25, ry + 1, X1 - 13, ry + 12, f, "black", r=1.2, travel=2.5)
prn(p, X1 - 19, ry + 22, "RESET", 4.3, INK_SOFT, "SemiBold", "ms", .6)

# ── fluorescent display ──
vx0, vy0, vx1, vy1 = X0 + 12, y0 + 294, X1 - 12, y0 + 432
g = window(p, vx0, vy0, vx1, vy1, f, r=2.5, depth=2.8)
dots(p, vx0 + 9, vy0 + 17, "雨停以后", VFD)
dots(p, vx0 + 63, vy0 + 17, "北岸乐队·夜航", VFD, .55)
vtext(p, vx1 - 9, vy0 + 16, "TRACK 03/30", 5.6, VFD, "Bold", "rs", .5)
ORANGE = (1., .42, .16)
segs, sx0, sx1 = 30, vx0 + 20, vx1 - 10
sw = (sx1 - sx0) / segs
for row, (lab, n, peak) in enumerate((("L", 21, 25), ("R", 19, 23))):
    yy = vy0 + 25 + row * 10
    vtext(p, vx0 + 9, yy + 6, lab, 6.2, VFD, "Bold")
    for i in range(segs):
        on = i < n or i == peak
        colr = ORANGE if i >= 24 else VFD
        m_ = p.draw(L(sx0 + i * sw, yy, sx0 + i * sw + sw - 1.4, yy + 6.5), lambda d, i=i, yy=yy: d.rect(L(sx0 + i * sw, yy, sx0 + i * sw + sw - 1.4, yy + 6.5)))
        p.glow(m_, colr if on else VFD_OFF, 1. if on else 1.)
for db, i in (("-30", 0), ("-20", 6), ("-10", 12), ("-5", 17), ("0", 23), ("+3", 26), ("+6", 29)):
    vtext(p, sx0 + i * sw + sw / 2, vy0 + 52, db, 4.8, ORANGE if i >= 23 else VFD, "SemiBold", "ms")
dots(p, vx0 + 9, vy0 + 72, "风把街灯吹得很慢", VFD, .28)
dots(p, vx0 + 9, vy0 + 102, "只剩你的歌还亮着", VFD, 1.05, px=24)
dots(p, vx0 + 9, vy0 + 120, "雨落在霓虹的背面", VFD, .32)
st = [("PLAY ▶", 1), ("CrO2", 1), ("NR B", 1), ("REPEAT", 0)]
xx = vx0 + 9
for lab, on in st:
    vtext(p, xx, vy0 + 133, lab, 5.4, VFD if on else VFD_OFF, "Bold", spacing=.5)
    xx += text_width(lab, 5.4, "Bold", spacing=.5) + 12
vtext(p, vx1 - 9, vy0 + 133, "00:58 / 03:44", 6, VFD, "Bold", "rs", .4)
mesh(p, g)

# ── tape position: a lamp-lit scale with a red pointer (the seek bar) ──
tx0, ty0, tx1, ty1 = X0 + 12, y0 + 439, X1 - 12, y0 + 458
gd = window(p, tx0, ty0, tx1, ty1, f, r=1.5, depth=2.2, glass=(.02, .016, .012))
lampd = np.clip(gauss(p, (tx0 + 70) * S, (ty1 + 4) * S, 60 * S) + gauss(p, (tx1 - 70) * S, (ty1 + 4) * S, 60 * S), 0, 1)
p.glow(gd * (.05 + .1 * lampd), (1., .7, .35))
total = 224
pos = 58


def tscale(d):
    for s in range(0, total + 1, 5):
        x = tx0 + 10 + (tx1 - tx0 - 20) * s / total
        big = s % 60 == 0
        d.line([(x * S, (ty1 - 3) * S), (x * S, (ty1 - (7.5 if big else 5 if s % 15 == 0 else 3.8)) * S)], width=(.6 if big else .35) * S)
        if big:
            d.text(((x + 1.5) * S, (ty0 + 7.6) * S), "%d'" % (s // 60), 5.4 * S, "SemiBold", anchor="lm")
    x = tx0 + 10 + (tx1 - tx0 - 20)
    d.text(((x - 1.5) * S, (ty0 + 7.6) * S), "3'44\"", 5.4 * S, "SemiBold", anchor="rm")


md = p.draw(L(tx0, ty0, tx1, ty1), tscale) * gd
p.glow(md, (1., .86, .62), .95)
px_ = tx0 + 10 + (tx1 - tx0 - 20) * pos / total
played = p.draw(L(tx0, ty0, tx1, ty1), lambda d: d.rect(L(tx0 + 10, ty1 - 2.2, px_, ty1 - 1.4))) * gd
p.glow(played, (1., .3, .12), 1.2)
mp = p.draw(L(px_ - 2, ty0, px_ + 2, ty1), lambda d: d.rect(L(px_ - .6, ty0 + 1.5, px_ + .6, ty1 - 1.5))) * gd
p.glow(mp, (1., .25, .1), 1.8)

# ── transport keys ──
keys = [("rec", "REC"), ("rew", "REW"), ("play", "PLAY"), ("ff", "F.FWD"), ("stop", "STOP"), ("pause", "PAUSE")]
kx0, kx1 = X0 + 12, X1 - 12
gap = 5.5
kw = (kx1 - kx0 - gap * 5) / 6
ky0, ky1 = y0 + 474, y0 + 540
for i, (ic, lab) in enumerate(keys):
    x0 = kx0 + i * (kw + gap)
    pressed = ic == "play"
    led(p, x0 + kw / 2, y0 + 467, 1.4, GREEN if pressed else (RED if ic == "rec" else AMBER), pressed, 1.4)
    top = piano_key(p, x0, ky0, x0 + kw, ky1, f, pressed)
    cx, cy = x0 + kw / 2, ky0 + 24

    def icon(d, ic=ic, cx=cx, cy=cy):
        if ic == "rec":
            d.ellipse(L(cx - 4.2, cy - 4.2, cx + 4.2, cy + 4.2))
        elif ic == "rew":
            d.polygon([L(cx + .5, cy - 5), L(cx - 5.5, cy), L(cx + .5, cy + 5)])
            d.polygon([L(cx + 6, cy - 5), L(cx, cy), L(cx + 6, cy + 5)])
        elif ic == "ff":
            d.polygon([L(cx - 6, cy - 5), L(cx, cy), L(cx - 6, cy + 5)])
            d.polygon([L(cx - .5, cy - 5), L(cx + 5.5, cy), L(cx - .5, cy + 5)])
        elif ic == "play":
            d.polygon([L(cx - 3.5, cy - 6), L(cx + 6, cy), L(cx - 3.5, cy + 6)])
        elif ic == "stop":
            d.rect(L(cx - 4.5, cy - 4.5, cx + 4.5, cy + 4.5))
        else:
            d.rect(L(cx - 4.5, cy - 5.5, cx - 1.5, cy + 5.5))
            d.rect(L(cx + 1.5, cy - 5.5, cx + 4.5, cy + 5.5))
    mi = p.draw(L(x0, ky0, x0 + kw, ky1), icon)
    p.paint(mi, alb=(.78, .13, .07) if ic == "rec" else INK, metal=0., rough=.5, aniso=0.)
    prn(p, cx, ky1 + 12, lab, 5, INK if pressed else INK_SOFT, "Bold" if pressed else "SemiBold", "ms", 1.)

# ── level knobs, switches, tape type, phones ──
cy = y0 + 604
for kx, lab, ang in ((X0 + 44, "REC LEVEL", -30), (X0 + 104, "OUTPUT", 40)):
    ticks(p, kx, cy, 0, 27, -140, 140, 11, INK, .45, major=5, dots=True)
    knob(p, kx, cy, 21, f, ang, "alu")
    prn(p, kx, cy + 44, lab, 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
for tx, lab, a, b, up in ((X0 + 158, "NR", "B", "OFF", True), (X0 + 190, "MPX", "ON", "OFF", False)):
    prn(p, tx, cy - 24, lab, 5.3, INK_SOFT, "SemiBold", "ms", 1.)
    prn(p, tx, cy - 17, a, 4.3, INK_SOFT, "SemiBold", "ms", .6)
    toggle(p, tx, cy - 2, f, up)
    prn(p, tx, cy + 16, b, 4.3, INK_SOFT, "SemiBold", "ms", .6)
prn(p, X0 + 174, cy + 44, "FILTER", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
for i, (lab, on) in enumerate((("NORMAL", 0), ("CrO2", 1), ("METAL", 0))):
    yy = cy - 18 + i * 15
    led(p, X0 + 226, yy - 2, 1.4, AMBER, on, 1.4)
    prn(p, X0 + 233, yy, lab, 5, INK if on else INK_SOFT, "Bold" if on else "SemiBold", spacing=.8)
prn(p, X0 + 246, cy + 44, "TAPE", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
jx, jy = X1 - 34, cy
d = p.circle(jx * S, jy * S, 7 * S)
p.raise_to(d, (f - .5) * S, 1.5 * S, 1 * S, "chamfer")
p.spun(p.cov(d), jx * S, jy * S, (.8, .79, .77))
d = p.circle(jx * S, jy * S, 2.8 * S)
p.lift(d, -4 * S, .6 * S, "chamfer")
p.paint(p.cov(d), alb=(.01, .01, .01), metal=0.)
prn(p, jx, cy + 44, "PHONES", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)

# ── memo keys: like, queue, comments, more ──
hl = p.draw(L(X0 + 12, y0 + 666, X1 - 12, y0 + 668), lambda d: d.line([L(X0 + 12, y0 + 667), L(X1 - 12, y0 + 667)], width=.35 * S))
p.paint(hl, alb=INK_SOFT, metal=0., aniso=0., panelk=0.)
memo = [("收藏", "MEMO"), ("歌单", "LIST"), ("评论", "TALK"), ("更多", "MENU")]
for i, (cn, en) in enumerate(memo):
    mx = X0 + 40 + i * 82
    my = y0 + 692
    knob(p, mx, my, 9, f, 0, "alu", height=3.5, mark="none", grip=False)
    if i == 0:
        led(p, mx - 16, my, 1.4, RED, True, 1.5)
    prn(p, mx + 15, my - 1, cn, 7, INK, "Bold")
    prn(p, mx + 15, my + 7.5, en, 4.4, INK_SOFT, "SemiBold", spacing=1.)
prn(p, X0 + 15, y1 - 11, "AUTO REVERSE · 3 HEAD · DUAL CAPSTAN", 5.0, INK_SOFT, "SemiBold", spacing=1.)
prn(p, X1 - 15, y1 - 11, "SERIAL 2026-1108", 5.0, INK_SOFT, "SemiBold", "rs", 1.)

print("built", round(time.time() - t0, 1))
col = p.shade(S=S)
print("shaded", round(time.time() - t0, 1))
p.save(C3 + "hi_play.png", col)
