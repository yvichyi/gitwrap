"""机 · landscape player: one wide cassette deck in the walnut cabinet."""
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hikit import *  # noqa: E402,F401,F403
from hikit import drums, piano_key, toggle  # noqa: E402

C3 = os.path.dirname(os.path.abspath(__file__)) + "/"
W, HT = 844, 390
t0 = time.time()
p = Plate(W * S, HT * S, seed=13)
# Cabinet: wide stiles left and right (they carry the rounded corners), thin rails.
p.H[:] = 0
wood(p, 0, 0, W, HT, angle=0.)
opening = p.rrect(*L(34, 12, W - 34, HT - 12), 2.5 * S)
p.lift(opening, -16 * S, 2.2 * S, "fillet")
p.paint(p.cov(opening), alb=(.018, .016, .014), metal=0., rough=.8, gloss=0., aniso=0., panelk=0.)
for xa, xb, xc, xd in ((0, 34, 34, 0), (W, W - 34, W - 34, W)):
    st = p.draw(L(min(xa, xb), 0, max(xa, xb), HT), lambda d, xa=xa, xb=xb: d.polygon([(xa * S, 0), (xb * S, 12 * S), (xb * S, (HT - 12) * S), (xa * S, HT * S)]))
    p.wood(st, angle=1., scale=1.)
X0, X1, Y0, Y1 = 37, W - 37, 15, HT - 15
f = faceplate(p, X0, Y0, X1, Y1, "alu")
for sx, sy in ((X0 + 7, Y0 + 7), (X1 - 7, Y0 + 7), (X0 + 7, Y1 - 7), (X1 - 7, Y1 - 7)):
    screw(p, sx, sy, 2.9)
prn(p, X0 + 16, Y0 + 21, "LANYIN", 12.5, INK, "ExtraBoldItalic", engrave=.25)
prn(p, X0 + 80, Y0 + 20, "TC-18W · STEREO CASSETTE DECK", 5.6, INK_SOFT, "SemiBold", spacing=1.3)
bx0 = X1 - 132
fr = p.draw(L(bx0, Y0 + 11, bx0 + 36, Y0 + 23), lambda d: (d.d.rounded_rectangle([0, 0, 36 * S * 4 - 1, 12 * S * 4 - 1], radius=2 * S * 4, outline=255, width=int(.5 * S * 4))))
p.paint(fr, alb=INK, metal=0., aniso=0., panelk=0.)
prn(p, bx0 + 18, Y0 + 19.6, "NR · B·C", 5.4, INK, "Bold", "ms", .4)
prn(p, X1 - 36, Y0 + 20, "POWER", 5.2, INK_SOFT, "SemiBold", "rs", 1.2)
led(p, X1 - 70 + 4, Y0 + 18, 1.5, RED, True)
knob(p, X1 - 22, Y0 + 17, 6.2, f, 0, "alu", height=3., mark="none", grip=False)

# ── left: door, counter, keys ──
dx0, dy0, dx1, dy1 = X0 + 12, Y0 + 30, X0 + 336, Y0 + 246
door = window(p, dx0, dy0, dx1, dy1, f, r=3, depth=12, glass=(.02, .016, .014))
p.paint(door, gloss=0.)
cw = 292
cx0 = (dx0 + dx1) / 2 - cw / 2
cy1 = cassette(p, cx0, dy0 + 12, cw, f - 11.5, "雨停以后", .26, artist="北岸乐队")
hx = (dx0 + dx1) / 2
d = p.rrect(*L(hx - 13, cy1 + 4, hx + 13, cy1 + 14), 2 * S)
p.raise_to(d, (f - 11) * S, 3 * S, 1 * S, "fillet")
p.brushed(p.cov(d), (.72, .72, .73), math.pi / 2, panelk=0.)
for sx in (hx - 46, hx + 46):
    dd = p.circle(sx * S, (cy1 + 9) * S, 5.5 * S)
    p.raise_to(dd, (f - 11) * S, 3 * S, 1.6 * S, "fillet")
    p.paint(p.cov(dd), alb=(.03, .03, .03), metal=0., rough=.5, aniso=0.)
smoke = door
p.alb *= (1 - .38 * smoke)[..., None]
lamp = np.clip(gauss(p, ((dx0 + dx1) / 2) * S, (dy0 - 6) * S, 120 * S), 0, 1)
p.glow(smoke * lamp * .12, (1., .78, .5))
p.emis += (smoke * lamp * .22)[..., None] * p.alb
p.paint(smoke, gloss=.9)
ry = Y0 + 254
button(p, X0 + 12, ry, X0 + 48, ry + 13, f, "alu", r=1.4, travel=3)
ei = p.draw(L(X0 + 12, ry, X0 + 48, ry + 13), lambda d: (d.polygon([L(X0 + 30, ry + 3), L(X0 + 34.5, ry + 7.5), L(X0 + 25.5, ry + 7.5)]), d.rect(L(X0 + 25.5, ry + 8.7, X0 + 34.5, ry + 10.1))))
p.paint(ei, alb=INK, metal=0., aniso=0.)
prn(p, X0 + 54, ry + 9, "EJECT", 5.3, INK_SOFT, "SemiBold", spacing=1.1)
prn(p, X0 + 252, ry + 9, "COUNTER", 5.3, INK_SOFT, "SemiBold", "rs", 1.1)
drums(p, X0 + 260, ry - 1, "0058", f, cw=8.5, ch=13)
keys = [("rec", "REC"), ("rew", "REW"), ("play", "PLAY"), ("ff", "F.FWD"), ("stop", "STOP"), ("pause", "PAUSE")]
kx0, kx1 = X0 + 12, X0 + 336
gap = 5.5
kw = (kx1 - kx0 - gap * 5) / 6
ky0, ky1 = Y0 + 286, Y0 + 336
for i, (ic, lab) in enumerate(keys):
    x0 = kx0 + i * (kw + gap)
    pressed = ic == "play"
    led(p, x0 + kw - 7, ky0 + 7, 1.3, GREEN if pressed else (RED if ic == "rec" else AMBER), pressed, 1.4)
    piano_key(p, x0, ky0, x0 + kw, ky1, f, pressed)
    cx, cy = x0 + kw / 2, ky0 + 20

    def icon(d, ic=ic, cx=cx, cy=cy):
        if ic == "rec":
            d.ellipse(L(cx - 4, cy - 4, cx + 4, cy + 4))
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
    prn(p, cx, ky1 + 11, lab, 5, INK if pressed else INK_SOFT, "Bold" if pressed else "SemiBold", "ms", 1.)

# ── right: meters, display, tape scale, controls ──
RX0, RX1 = X0 + 350, X1 - 12
mid = (RX0 + RX1) / 2
vu_meter(p, RX0, Y0 + 30, mid - 4, Y0 + 112, f, .6, "LEFT · CH 1")
vu_meter(p, mid + 4, Y0 + 30, RX1, Y0 + 112, f, .68, "RIGHT · CH 2")
vx0, vy0, vx1, vy1 = RX0, Y0 + 120, RX1, Y0 + 226
g = window(p, vx0, vy0, vx1, vy1, f, r=2.5, depth=2.8)
dots(p, vx0 + 10, vy0 + 17, "雨停以后", VFD)
dots(p, vx0 + 64, vy0 + 17, "北岸乐队·夜航", VFD, .55)
vtext(p, vx1 - 10, vy0 + 16, "TRACK 03/30", 5.6, VFD, "Bold", "rs", .5)
dots(p, vx0 + 10, vy0 + 39, "风把街灯吹得很慢", VFD, .28)
dots(p, vx0 + 10, vy0 + 69, "只剩你的歌还亮着", VFD, 1.05, px=24)
dots(p, vx0 + 10, vy0 + 87, "雨落在霓虹的背面", VFD, .32)
xx = vx0 + 10
for lab, on in (("PLAY ▶", 1), ("CrO2", 1), ("NR B", 1), ("REPEAT", 0)):
    vtext(p, xx, vy0 + 100, lab, 5.4, VFD if on else VFD_OFF, "Bold", spacing=.5)
    xx += text_width(lab, 5.4, "Bold", spacing=.5) + 12
vtext(p, vx1 - 10, vy0 + 100, "00:58 / 03:44", 6, VFD, "Bold", "rs", .4)
# A small spectrum on the right of the display.
import random
random.seed(4)
for i in range(16):
    hgt = 3 + 26 * abs(math.sin(i * .55 + .6)) * (1 - i / 22) + random.random() * 6
    bx = vx1 - 128 + i * 7.4
    for j in range(int(hgt // 3.2)):
        yy = vy0 + 88 - j * 3.2
        m_ = p.draw(L(bx, yy - 2.2, bx + 5.6, yy), lambda d, bx=bx, yy=yy: d.rect(L(bx, yy - 2.2, bx + 5.6, yy)))
        p.glow(m_, VFD, .8 if j < 7 else .5)
mesh(p, g)
tx0, ty0, tx1, ty1 = RX0, Y0 + 233, RX1, Y0 + 252
gd = window(p, tx0, ty0, tx1, ty1, f, r=1.5, depth=2.2, glass=(.02, .016, .012))
lampd = np.clip(gauss(p, (tx0 + 80) * S, (ty1 + 4) * S, 70 * S) + gauss(p, (tx1 - 80) * S, (ty1 + 4) * S, 70 * S), 0, 1)
p.glow(gd * (.05 + .1 * lampd), (1., .7, .35))
total, pos = 224, 58


def tscale(d):
    for s_ in range(0, total + 1, 5):
        x = tx0 + 10 + (tx1 - tx0 - 20) * s_ / total
        big = s_ % 60 == 0
        d.line([(x * S, (ty1 - 3) * S), (x * S, (ty1 - (7.5 if big else 5 if s_ % 15 == 0 else 3.8)) * S)], width=(.6 if big else .35) * S)
        if big:
            d.text(((x + 1.5) * S, (ty0 + 7.6) * S), "%d'" % (s_ // 60), 5.4 * S, "SemiBold", anchor="lm")
    x = tx0 + 10 + (tx1 - tx0 - 20)
    d.text(((x - 1.5) * S, (ty0 + 7.6) * S), "3'44\"", 5.4 * S, "SemiBold", anchor="rm")


p.glow(p.draw(L(tx0, ty0, tx1, ty1), tscale) * gd, (1., .86, .62), .95)
px_ = tx0 + 10 + (tx1 - tx0 - 20) * pos / total
p.glow(p.draw(L(tx0, ty0, tx1, ty1), lambda d: d.rect(L(tx0 + 10, ty1 - 2.2, px_, ty1 - 1.4))) * gd, (1., .3, .12), 1.2)
p.glow(p.draw(L(px_ - 2, ty0, px_ + 2, ty1), lambda d: d.rect(L(px_ - .6, ty0 + 1.5, px_ + .6, ty1 - 1.5))) * gd, (1., .25, .1), 1.8)
cy = Y0 + 296
ticks(p, RX0 + 26, cy, 0, 26, -140, 140, 11, INK, .45, major=5, dots=True)
knob(p, RX0 + 26, cy, 20, f, 40, "alu")
prn(p, RX0 + 26, Y1 - 10, "OUTPUT", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
for tx, lab, a, b, up in ((RX0 + 80, "NR", "B", "OFF", True), (RX0 + 110, "MPX", "ON", "OFF", False)):
    prn(p, tx, cy - 22, lab, 5.3, INK_SOFT, "SemiBold", "ms", 1.)
    prn(p, tx, cy - 15.5, a, 4.3, INK_SOFT, "SemiBold", "ms", .6)
    toggle(p, tx, cy, f, up)
    prn(p, tx, cy + 18, b, 4.3, INK_SOFT, "SemiBold", "ms", .6)
memo = [("收藏", "MEMO"), ("歌单", "LIST"), ("评论", "TALK"), ("更多", "MENU")]
for i, (cn, en) in enumerate(memo):
    mx = RX0 + 150 + i * 64
    knob(p, mx, cy, 9, f, 0, "alu", height=3.5, mark="none", grip=False)
    if i == 0:
        led(p, mx, cy - 15, 1.3, RED, True, 1.5)
    prn(p, mx + 14, cy - 1, cn, 7, INK, "Bold")
    prn(p, mx + 14, cy + 7.5, en, 4.4, INK_SOFT, "SemiBold", spacing=1.)
jx = RX1 - 12
d = p.circle(jx * S, cy * S, 6.5 * S)
p.raise_to(d, (f - .5) * S, 1.5 * S, 1 * S, "chamfer")
p.spun(p.cov(d), jx * S, cy * S, (.8, .79, .77))
d = p.circle(jx * S, cy * S, 2.6 * S)
p.lift(d, -4 * S, .6 * S, "chamfer")
p.paint(p.cov(d), alb=(.01, .01, .01), metal=0.)
prn(p, jx, Y1 - 10, "PHONES", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
prn(p, RX0 + 150 + 96 + 14, Y1 - 10, "AUTO REVERSE · 3 HEAD · DUAL CAPSTAN", 5.0, INK_SOFT, "SemiBold", "ms", 1.)

print("built", round(time.time() - t0, 1))
col = p.shade(S=S)
print("shaded", round(time.time() - t0, 1))
p.save(C3 + "hi_land.png", col)
