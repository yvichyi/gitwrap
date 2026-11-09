"""Locks covers to the 霓 palette at 32x32 with 4x4 ordered dithering, then scales them x3.
Usage: python3 -I pxcovers.py <covers dir>"""
import sys

import numpy as np
from PIL import Image

D = sys.argv[1]
pal = ["#05060a", "#0a0d14", "#10141e", "#171d2b", "#20283a", "#2b354b", "#3a4660", "#4f5d78", "#6d7e9a", "#9fb2c8", "#d8e4ee",
       "#1a1210", "#2a1a14", "#4e2c1a", "#80461f", "#b8682a", "#e89a3e", "#ffc874", "#fff0c8", "#3a0e12", "#7a1a1e", "#d0302c", "#ff6a4a",
       "#0c2e33", "#16595a", "#2a9a8e", "#6fe0c8"]
P = np.array([[int(c[i:i + 2], 16) for i in (1, 3, 5)] for c in pal], np.float32) / 255
B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - .5
for n in range(6):
    a = np.clip(np.array(Image.open(f"{D}/c{n}.png").convert("RGB").resize((32, 32), Image.LANCZOS), np.float32) / 255 * 1.1, 0, 1)
    src = np.clip(a + np.tile(B4, (8, 8))[..., None] * .07, 0, 1)
    d = ((src[:, :, None, :] - P[None, None]) ** 2 * np.array([.3, .59, .11])).sum(-1)
    Image.fromarray((P[d.argmin(-1)] * 255).astype(np.uint8)).resize((96, 96), Image.NEAREST).save(f"{D}/p{n}.png")
