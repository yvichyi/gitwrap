"""Approximate pink, sunflower and blue separations of the 暮 poster, each printed alone on paper.
Usage: python3 -I separations.py <covers dir>"""
import sys

import numpy as np
from PIL import Image

D = sys.argv[1]
a = np.array(Image.open(f"{D}/riso_poster.jpg").convert("RGB").resize((260, 254)), np.float32)
R, G, B = a[..., 0], a[..., 1], a[..., 2]
cb = np.clip((242 - R) / 192, 0, 1)
cy = np.clip((221 - B) / 204 - cb * .3, 0, 1)
cp = np.clip((235 - G) / 163 - cb * .6 - cy * .4, 0, 1)
paper = np.array([242, 235, 221], np.float32)
for name, cov, ink in (("sep_pink", cp, (255, 72, 176)), ("sep_yellow", cy, (255, 181, 17)), ("sep_blue", cb, (50, 85, 164))):
    out = paper * (1 - cov[..., None] * (1 - np.array(ink, np.float32) / 255))
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(f"{D}/{name}.jpg", quality=90)
