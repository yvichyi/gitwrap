"""Photocopy halftones of the covers for 酸: black dots on lime (htk), lime dots on black (htl), black on paper (htp).
Usage: python3 -I halftone.py <covers dir>"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

D = sys.argv[1]
LIME, INK, PAPER = (214, 255, 30), (11, 11, 11), (236, 234, 226)


def halftone(src, out, fg, bg, light, size=360, cell=6, angle=22, gain=1.0):
    a = np.array(Image.open(src).convert("L").resize((size, size), Image.LANCZOS), np.float32) / 255
    a = np.clip((a - a.min()) / (np.ptp(a) + 1e-6) * gain, 0, 1)
    o = Image.new("RGB", (size * 2, size * 2), bg)
    d = ImageDraw.Draw(o)
    th = math.radians(angle)
    for gy in range(-size, size * 2, cell):
        for gx in range(-size, size * 2, cell):
            x, y = gx * math.cos(th) - gy * math.sin(th), gx * math.sin(th) + gy * math.cos(th)
            if 0 <= x < size and 0 <= y < size:
                v = a[int(y), int(x)] if light else 1 - a[int(y), int(x)]
                r = cell * .62 * math.sqrt(v)
                if r > .3:
                    d.ellipse(((x - r) * 2, (y - r) * 2, (x + r) * 2, (y + r) * 2), fill=fg)
    o.resize((size, size), Image.LANCZOS).save(out)


for n in range(6):
    halftone(f"{D}/c{n}.png", f"{D}/htk{n}.png", INK, LIME, False)
    halftone(f"{D}/c{n}.png", f"{D}/htl{n}.png", LIME, INK, True, gain=1.4)
    halftone(f"{D}/c{n}.png", f"{D}/htp{n}.png", INK, PAPER, False)
