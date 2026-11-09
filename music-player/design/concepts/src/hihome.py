"""机 · home: a hi-fi stack in a walnut cabinet, rendered with real materials."""
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hikit import *  # noqa: E402,F401,F403
from hikit import toggle, cabinet  # noqa: E402
import hikit as K  # noqa: E402

C3 = os.path.dirname(os.path.abspath(__file__)) + "/"
W, HT = 390, 844
t0 = time.time()
p = Plate(W * S, HT * S, seed=7)

cabinet(p, W, HT)
X0, X1 = 14, W - 14

# ════ tuner ════
y0, y1 = 53, 262
f = faceplate(p, X0, y0, X1, y1, "alu")
for sx, sy in ((X0 + 7, y0 + 7), (X1 - 7, y0 + 7), (X0 + 7, y1 - 7), (X1 - 7, y1 - 7)):
    screw(p, sx, sy, 2.9)
prn(p, X0 + 15, y0 + 21, "LANYIN", 12.5, INK, "ExtraBoldItalic", engrave=.25)
prn(p, X0 + 78, y0 + 20, "ST-18 · FM/AM STEREO TUNER", 5.6, INK_SOFT, "SemiBold", spacing=1.3)
prn(p, X1 - 36, y0 + 20, "POWER", 5.2, INK_SOFT, "SemiBold", "rs", 1.2)
led(p, X1 - 70, y0 + 18, 1.6, RED, True)
knob(p, X1 - 22, y0 + 17, 6.2, f, 0, "alu", height=3., mark="none", grip=False)
# Display.
wx0, wy0, wx1, wy1 = X0 + 12, y0 + 31, X1 - 12, y0 + 105
g = window(p, wx0, wy0, wx1, wy1, f, r=2.5, depth=2.8)
vtext(p, wx0 + 10, wy0 + 16, "FM", 7.5, VFD, "Bold")
vtext(p, wx0 + 10, wy0 + 27, "AM", 7.5, VFD_OFF, "Bold")
seg7(p, wx0 + 30, wy0 + 11, 40, "107.7", pitch=27)
vtext(p, wx0 + 150, wy0 + 50, "MHz", 7, VFD, "SemiBold")
dots(p, wx0 + 182, wy0 + 24, "私人 FM", VFD)
dots(p, wx0 + 182, wy0 + 41, "随机电台·今晚", VFD, .85)
for i, (lab, on) in enumerate((("STEREO", 1), ("TUNED", 1), ("MONO", 0), ("AUTO", 0))):
    vtext(p, wx0 + 10 + i * 36, wy0 + 66, lab, 5.6, VFD if on else VFD_OFF, "Bold", spacing=.6)
vtext(p, wx0 + 168, wy0 + 66, "P2", 6.5, VFD, "Bold")
for i in range(6):
    hb = 2 + i * 1.6
    m_ = p.draw(L(wx1 - 46 + i * 6, wy0 + 66 - hb, wx1 - 42 + i * 6, wy0 + 67), lambda d, i=i, hb=hb: d.rect(L(wx1 - 46 + i * 6, wy0 + 66 - hb, wx1 - 42 + i * 6, wy0 + 66.5)))
    p.glow(m_, VFD if i < 5 else VFD_OFF)
vtext(p, wx1 - 50, wy0 + 66, "SIG", 5, VFD, "Bold", "rs")
mesh(p, g)
# Dial strip: a lamp-lit scale with a red pointer.
dx0, dy0, dx1, dy1 = X0 + 12, y0 + 111, X1 - 12, y0 + 132
gd = window(p, dx0, dy0, dx1, dy1, f, r=1.5, depth=2.2, glass=(.02, .016, .012))
lampd = np.clip(gauss(p, (dx0 + 60) * S, (dy1 + 4) * S, 50 * S) + gauss(p, (dx1 - 60) * S, (dy1 + 4) * S, 50 * S), 0, 1)
p.glow(gd * (.05 + .1 * lampd), (1., .7, .35))


def dial(d):
    for i in range(41):
        x = dx0 + 12 + (dx1 - dx0 - 24) * i / 40
        big = i % 4 == 0
        d.line([(x * S, (dy1 - 3) * S), (x * S, (dy1 - (8 if big else 5.5)) * S)], width=(.6 if big else .4) * S)
        if big:
            d.text((x * S, (dy0 + 8.5) * S), str(88 + i // 2), 5.6 * S, "SemiBold", anchor="mm")


md = p.draw(L(dx0, dy0, dx1, dy1), dial) * gd
p.glow(md, (1., .86, .62), .95)
px_ = dx0 + 12 + (dx1 - dx0 - 24) * (107.7 - 88) / 20
mp = p.draw(L(px_ - 2, dy0, px_ + 2, dy1), lambda d: d.rect(L(px_ - .6, dy0 + 1.5, px_ + .6, dy1 - 1.5))) * gd
p.glow(mp, (1., .25, .1), 1.8)
# Presets.
names = ["每日推荐", "私人FM", "我喜欢", "排行榜", "最近", "下载"]
for i, nm in enumerate(names):
    bx = X0 + 14 + i * 45
    led(p, bx + 19, y0 + 143, 1.5, GREEN if i == 1 else RED, i == 1)
    top_ = button(p, bx, y0 + 150, bx + 38, y0 + 174, f, "alu", pressed=(i == 1))
    prn(p, bx + 19, y0 + 165.5, str(i + 1), 7.5, INK, "SemiBold", "ms")
    prn(p, bx + 19, y0 + 188, nm, 7, INK, "Medium", "ms")
prn(p, X0 + 15, y1 - 10, "PRESET · MEMORY", 5.3, INK_SOFT, "SemiBold", spacing=1.1)
prn(p, X0 + 150, y1 - 10, "FM · MW · LW", 5.3, INK_SOFT, "SemiBold", spacing=1.1)
kx, ky = X1 - 41, y0 + 165
ticks(p, kx, ky, 0, 31, -150, 150, 31, INK, .45, major=5, dots=True)
knob(p, kx, ky, 26, f, 32, "alu")
prn(p, kx, y1 - 9, "TUNING", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)

# ════ CD changer ════
y0, y1 = 267, 450
f = faceplate(p, X0, y0, X1, y1, "black")
for sx, sy in ((X0 + 7, y0 + 7), (X1 - 7, y0 + 7), (X0 + 7, y1 - 7), (X1 - 7, y1 - 7)):
    screw(p, sx, sy, 2.9, finish="black")
prn(p, X0 + 15, y0 + 21, "LANYIN", 12.5, INK_L, "ExtraBoldItalic")
prn(p, X0 + 78, y0 + 20, "CD-5 · 5 DISC CAROUSEL", 5.6, INK_LS, "SemiBold", spacing=1.3)
prn(p, X1 - 15, y0 + 20, "推荐歌单 · PLAYLISTS", 6, INK_LS, "Medium", "rs", .6)
wx0, wy0, wx1, wy1 = X0 + 12, y0 + 29, X1 - 12, y0 + 120
g = window(p, wx0, wy0, wx1, wy1, f, r=2.5, depth=3.2, bezel="black", glass=(.012, .011, .01))
# The carousel platter behind the discs.
pl = p.draw(L(wx0, wy0, wx1, wy1), lambda d: [d.ellipse(L(wx0 + 34 + i * 67 - 33, wy0 + 46 - 33, wx0 + 34 + i * 67 + 33, wy0 + 46 + 33)) for i in range(5)])
p.paint(pl * g, alb=(.035, .035, .04), gloss=.6)
covers = [C3 + "covers/c%d.png" % i for i in (0, 1, 2, 3, 4)]
for i in range(5):
    cx = wx0 + 34 + i * 67
    sel = i == 1
    disc(p, cx, wy0 + 46 - (3 if sel else 0), 31 if not sel else 32, covers[i], f - 3.2, lift=2.4 if sel else 1., smoke=.95 if sel else .62)
# The selected disc sits under a small reading lamp.
p.glow(g * gauss(p, (wx0 + 101) * S, (wy0 + 40) * S, 40 * S) * .06, (1., .85, .6))
labels = ["深海", "灯塔", "雨窗", "夜路", "晚归"]
for i, nm in enumerate(labels):
    cx = wx0 + 34 + i * 67
    prn(p, cx, y0 + 132, "%d · %s" % (i + 1, nm), 6.3, INK_L if i == 1 else INK_LS, "Medium", "ms")
led(p, wx0 + 101 - 22, y0 + 129.5, 1.3, GREEN, True, 1.3)
# Small display and disc keys.
vx0, vy0, vx1, vy1 = X0 + 12, y0 + 140, X0 + 196, y0 + 171
g2 = window(p, vx0, vy0, vx1, vy1, f, r=2, depth=2.2, bezel="black")
vtext(p, vx0 + 7, vy0 + 13, "DISC", 5.4, VFD, "Bold", spacing=.5)
seg7(p, vx0 + 32, vy0 + 5, 20, "2", pitch=14)
vtext(p, vx0 + 7, vy0 + 25, "48 TRK", 5.4, VFD, "Bold", spacing=.4)
dots(p, vx0 + 54, vy0 + 17, "雾里的森林", VFD)
vtext(p, vx0 + 54, vy0 + 27, "RANDOM", 5, VFD_OFF, "Bold", spacing=.5)
vtext(p, vx0 + 92, vy0 + 27, "REPEAT", 5, VFD, "Bold", spacing=.5)
mesh(p, g2)
for i in range(5):
    bx = X0 + 206 + i * 24
    button(p, bx, y0 + 146, bx + 19, y0 + 159, f, "black", pressed=(i == 1), r=1.2, travel=3)
    prn(p, bx + 9.5, y0 + 169, str(i + 1), 5.2, INK_L if i == 1 else INK_LS, "SemiBold", "ms")
ex = X1 - 34
button(p, ex, y0 + 146, ex + 22, y0 + 159, f, "black", r=1.2, travel=3)
mi = p.draw(L(ex, y0 + 146, ex + 22, y0 + 159), lambda d: (d.polygon([L(ex + 11, y0 + 149.2), L(ex + 15, y0 + 153.6), L(ex + 7, y0 + 153.6)]), d.rect(L(ex + 7, y0 + 155, ex + 15, y0 + 156.4))))
p.paint(mi, alb=(.75, .74, .72), metal=0., rough=.5, aniso=0.)
prn(p, ex + 11, y0 + 169, "OPEN", 4.6, INK_LS, "SemiBold", "ms", .8)

# ════ amplifier ════
y0, y1 = 455, 712
f = faceplate(p, X0, y0, X1, y1, "alu")
for sx, sy in ((X0 + 7, y0 + 7), (X1 - 7, y0 + 7), (X0 + 7, y1 - 7), (X1 - 7, y1 - 7)):
    screw(p, sx, sy, 2.9)
prn(p, X0 + 15, y0 + 21, "LANYIN", 12.5, INK, "ExtraBoldItalic", engrave=.25)
prn(p, X0 + 78, y0 + 20, "A-18 · INTEGRATED AMPLIFIER", 5.6, INK_SOFT, "SemiBold", spacing=1.3)
prn(p, X1 - 15, y0 + 20, "CLASS A · 2 × 50 W", 5.2, INK_SOFT, "SemiBold", "rs", 1.)
vu_meter(p, X0 + 12, y0 + 30, X0 + 178, y0 + 118, f, .58, "LEFT · CH 1")
vu_meter(p, X0 + 184, y0 + 30, X1 - 12, y0 + 118, f, .66, "RIGHT · CH 2")
# Input selector = the three tabs.
ix, iy = X0 + 54, y0 + 190
for lab, a, on in (("首页", -52, True), ("搜索", 0, False), ("我的", 52, False)):
    ar = math.radians(a - 90)
    led(p, ix + math.cos(ar) * 29, iy + math.sin(ar) * 29, 1.3, AMBER, on, 1.5)
    prn(p, ix + math.cos(ar) * 41, iy + math.sin(ar) * 41 + 2.6, lab, 7.4, INK if on else INK_SOFT, "Bold" if on else "Medium", "ms")
knob(p, ix, iy, 21, f, -52, "alu")
LY = y1 - 17
prn(p, ix, LY, "INPUT", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
for kx, lab, ang in ((X0 + 118, "BASS", -20), (X0 + 160, "TREBLE", 25)):
    ticks(p, kx, iy + 8, 0, 17, -135, 135, 11, INK, .45, major=5, dots=True)
    knob(p, kx, iy + 8, 12, f, ang, "alu")
    prn(p, kx, LY, lab, 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
# Loudness and mute toggles over the headphone socket.
for tx, lab, up in ((X0 + 198, "LOUD", True), (X0 + 230, "MUTE", False)):
    prn(p, tx, iy - 27, lab, 5.0, INK_SOFT, "SemiBold", "ms", .9)
    prn(p, tx, iy - 20, "ON", 4.3, INK_SOFT, "SemiBold", "ms", .6)
    toggle(p, tx, iy - 5, f, up)
    prn(p, tx, iy + 12.5, "OFF", 4.3, INK_SOFT, "SemiBold", "ms", .6)
jx, jy = X0 + 214, iy + 26
d = p.circle(jx * S, jy * S, 6.5 * S)
p.raise_to(d, (f - .5) * S, 1.5 * S, 1 * S, "chamfer")
p.spun(p.cov(d), jx * S, jy * S, (.8, .79, .77))
d = p.circle(jx * S, jy * S, 2.6 * S)
p.lift(d, -4 * S, .6 * S, "chamfer")
p.paint(p.cov(d), alb=(.01, .01, .01), metal=0.)
prn(p, jx, LY, "PHONES", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
vx, vy = X1 - 56, iy - 6
ticks(p, vx, vy, 0, 44, -140, 140, 21, INK, .5, major=5, dots=True)
for i, n in enumerate(("0", "2", "4", "6", "8", "10")):
    a = math.radians(-140 + 280 * i / 5 - 90)
    prn(p, vx + math.cos(a) * 51, vy + math.sin(a) * 51 + 2, n, 5.4, INK_SOFT, "SemiBold", "ms")
knob(p, vx, vy, 37, f, 38, "alu")
prn(p, vx, LY, "VOLUME", 5.3, INK_SOFT, "SemiBold", "ms", 1.1)
# A hairline under the meters, as engraved on the panel.
hl = p.draw(L(X0 + 12, y0 + 126, X1 - 12, y0 + 128), lambda d: d.line([(L(X0 + 12, y0 + 127)), (L(X1 - 12, y0 + 127))], width=.35 * S))
p.paint(hl, alb=INK_SOFT, metal=0., aniso=0., panelk=0.)

# ════ cassette strip (now playing) ════
y0, y1 = 717, 786
f = faceplate(p, X0, y0, X1, y1, "black")
cw = 78
cy0 = y0 + 9
well(p, X0 + 9, y0 + 7, X0 + 9 + cw + 6, y1 - 7, 2, f - 5)
cassette(p, X0 + 12, cy0 + 2, cw, f - 4.6, "雨停以后", .32, hand=True)
vx0, vy0, vx1, vy1 = X0 + 100, y0 + 10, X1 - 92, y1 - 10
g3 = window(p, vx0, vy0, vx1, vy1, f, r=2, depth=2.2, bezel="black")
dots(p, vx0 + 7, vy0 + 19, "雨停以后", VFD)
dots(p, vx0 + 7, vy0 + 36, "北岸乐队·A面", VFD, .7)
seg7(p, vx1 - 52, vy0 + 9, 17, "0058", pitch=11.5, w=8.5)
vtext(p, vx1 - 52, vy0 + 40, "PLAY ▶", 5, VFD, "Bold", spacing=.4)
mesh(p, g3)
for i, ic in enumerate(("pause", "next")):
    bx = X1 - 84 + i * 37
    button(p, bx, y0 + 15, bx + 31, y1 - 15, f, "alu", r=1.6)
    cx, cy = bx + 15.5, (y0 + y1) / 2

    def icon(d, ic=ic, cx=cx, cy=cy):
        if ic == "pause":
            d.rect(L(cx - 4.2, cy - 5, cx - 1.4, cy + 5))
            d.rect(L(cx + 1.4, cy - 5, cx + 4.2, cy + 5))
        else:
            d.polygon([L(cx - 5.5, cy - 5), L(cx + .5, cy), L(cx - 5.5, cy + 5)])
            d.polygon([L(cx, cy - 5), L(cx + 6, cy), L(cx, cy + 5)])
    mi = p.draw(L(bx, y0, bx + 31, y1), lambda d, icon=icon: icon(d))
    p.paint(mi, alb=INK, metal=0., rough=.5, aniso=0.)
    p.H -= mi * .4 * S

print("built", round(time.time() - t0, 1))
col = p.shade(S=S)
print("shaded", round(time.time() - t0, 1))
p.save(C3 + "hi_home.png", col)
