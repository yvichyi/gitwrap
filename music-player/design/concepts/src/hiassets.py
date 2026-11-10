"""Renders 机's hardware for the app with hirender: knobs in champagne aluminium and in black
anodising (no pointer: the app draws it, since the light on a turned part doesn't turn with it),
and a walnut tile for the cabinet.

Usage: python3 -I hiassets.py <res/drawable-nodpi dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import hikit  # noqa: E402
from hikit import Plate, knob  # noqa: E402

OUT = sys.argv[1]
S = hikit.S


def knob_png(name, r, finish, grip=True, height=None):
    side = int((r * 2 + 4) * S)
    p = Plate(side, side, seed=5)
    c = side / 2 / S
    p.H[:] = 0
    # A flat panel under it, so the shading has something to sit on; cut away below.
    p.paint(np.ones((side, side), np.float32), alb=(.5, .5, .5), metal=0., rough=.8, aniso=0., panelk=0.)
    knob(p, c, c, r, 0., 0., finish, height=height, mark="none", grip=grip)
    col = p.shade(S=S)
    rgb = (np.clip(col, 0, 1) ** (1 / 1.06) * 255 + .5).astype(np.uint8)
    yy, xx = np.mgrid[0:side, 0:side]
    d = np.hypot(xx - side / 2 + .5, yy - side / 2 + .5) - r * S
    alpha = (np.clip(.5 - d, 0, 1) * 255).astype(np.uint8)
    Image.fromarray(np.dstack([rgb, alpha]), "RGBA").save(os.path.join(OUT, name + ".png"), optimize=True)
    print(name, side)


def walnut(name, w, h):
    p = Plate(w, h, seed=3)
    m = np.ones((h, w), np.float32)
    p.wood(m, angle=1., scale=1.)
    col = p.shade(S=S)
    rgb = (np.clip(col, 0, 1) ** (1 / 1.06) * 255 + .5).astype(np.uint8)
    Image.fromarray(rgb, "RGB").save(os.path.join(OUT, name + ".jpg"), quality=86, optimize=True)
    print(name, w, h)


knob_png("hifi_knob_big", 38, "alu")
knob_png("hifi_knob_small", 14, "alu")
knob_png("hifi_knob_big_black", 38, "black")
knob_png("hifi_knob_small_black", 14, "black")
walnut("hifi_walnut", 240, 900)
