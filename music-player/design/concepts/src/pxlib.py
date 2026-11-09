"""Small pixel-art canvas: palette-locked drawing with stepped glows and ordered dithering."""
import math

import numpy as np
from PIL import Image, ImageDraw


def hx(s):
    return np.array([int(s[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255


class Canvas:
    def __init__(self, w, h, palette, ramps, seed=1):
        self.W, self.H = w, h
        self.C = {k: hx(v) for k, v in palette.items()}
        self.P = np.stack(list(self.C.values()))
        self.ramps = ramps
        self.img = np.zeros((h, w, 3), np.float32)
        self.lock = np.zeros((h, w), bool)
        self.rng = np.random.default_rng(seed)
        self.YY, self.XX = np.mgrid[0:h, 0:w].astype(np.float32)

    def c(self, k):
        return self.C[k] if isinstance(k, str) else k

    @staticmethod
    def lum(c):
        return float(c @ np.array([.3, .59, .11], np.float32))

    def px(self, x, y, k, hard=True):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.W and 0 <= y < self.H:
            self.img[y, x] = self.c(k)
            if hard:
                self.lock[y, x] = True

    def rect(self, x0, y0, x1, y1, k, hard=True):
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        if x1 < x0 or y1 < y0:
            return
        sl = (slice(max(y0, 0), min(y1 + 1, self.H)), slice(max(x0, 0), min(x1 + 1, self.W)))
        self.img[sl] = self.c(k)
        if hard:
            self.lock[sl] = True

    def line(self, x0, y0, x1, y1, k, hard=True):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(n, 1)
            self.px(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, k, hard)

    def poly(self, pts, k, hard=True):
        m = Image.new("L", (self.W, self.H), 0)
        ImageDraw.Draw(m).polygon([(float(x), float(y)) for x, y in pts], fill=255)
        a = np.array(m) > 127
        self.img[a] = self.c(k)
        if hard:
            self.lock[a] = True

    def disc(self, x, y, r, k, hard=True):
        m = (self.XX - x) ** 2 + (self.YY - y) ** 2 <= r * r
        self.img[m] = self.c(k)
        if hard:
            self.lock[m] = True

    def ellipse(self, x, y, rx, ry, k, hard=True):
        m = ((self.XX - x) / rx) ** 2 + ((self.YY - y) / ry) ** 2 <= 1
        self.img[m] = self.c(k)
        if hard:
            self.lock[m] = True

    def checker(self, x, y):
        return (int(x) + int(y)) % 2 == 0

    def dither_rect(self, x0, y0, x1, y1, ka, kb, frac):
        """Fill with colour B, then colour A on a Bayer pattern covering `frac` of the pixels."""
        B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                if 0 <= x < self.W and 0 <= y < self.H:
                    self.img[y, x] = self.c(ka) if B4[y % 4, x % 4] < frac else self.c(kb)

    def glow(self, x, y, ramp, r, start=1, clip=None, sy=1.15):
        cols = [self.c(k) for k in self.ramps[ramp]][start:]
        for yy in range(int(y - r - 1), int(y + r + 2)):
            for xx in range(int(x - r - 1), int(x + r + 2)):
                if not (0 <= xx < self.W and 0 <= yy < self.H):
                    continue
                if clip is not None and not clip[yy, xx]:
                    continue
                d = math.hypot(xx - x, (yy - y) * sy)
                if d > r:
                    continue
                lvl = int(d / r * len(cols))
                if lvl >= len(cols) or (lvl == len(cols) - 1 and not self.checker(xx, yy)):
                    continue
                col = cols[lvl]
                if self.lum(col) > self.lum(self.img[yy, xx]) and not self.lock[yy, xx]:
                    self.img[yy, xx] = col

    def save(self, path1x, path3x, spread=.03, scale=3):
        B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - .5
        thr = np.tile(B4, (self.H // 4 + 1, self.W // 4 + 1))[:self.H, :self.W]
        src = np.clip(self.img + thr[..., None] * spread * (~self.lock)[..., None], 0, 1)
        d = ((src[:, :, None, :] - self.P[None, None]) ** 2 * np.array([.3, .59, .11])).sum(-1)
        out = Image.fromarray((self.P[d.argmin(-1)] * 255).astype(np.uint8))
        out.save(path1x)
        out.resize((self.W * scale, self.H * scale), Image.NEAREST).save(path3x)
        return out


def quantize_image(path, size, palette, out, scale=3, spread=.07, gain=1.1):
    """A cover locked to a theme palette at low resolution, the way the app would show real album art."""
    P = np.stack([hx(v) for v in palette.values()])
    a = np.clip(np.array(Image.open(path).convert("RGB").resize((size, size), Image.LANCZOS), np.float32) / 255 * gain, 0, 1)
    B4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - .5
    thr = np.tile(B4, (size // 4 + 1, size // 4 + 1))[:size, :size]
    src = np.clip(a + thr[..., None] * spread, 0, 1)
    d = ((src[:, :, None, :] - P[None, None]) ** 2 * np.array([.3, .59, .11])).sum(-1)
    im = Image.fromarray((P[d.argmin(-1)] * 255).astype(np.uint8))
    im.resize((size * scale, size * scale), Image.NEAREST).save(out)


FONT = "/home/user/gitwrap/music-player/app/src/main/res/font/fusion_pixel.ttf"


def text_mask(s, size=12):
    from PIL import ImageFont
    f = ImageFont.truetype(FONT, size)
    l, t, r, b = f.getbbox(s)
    m = Image.new("1", (r + 2, size + 4), 0)
    d = ImageDraw.Draw(m)
    d.fontmode = "1"
    d.text((0, 0), s, font=f, fill=1)
    return np.array(m, bool)


def draw_text(cv, x, y, s, k, size=12, shadow=None):
    m = text_mask(s, size)
    ys, xs = np.nonzero(m)
    if shadow is not None:
        for yy, xx in zip(ys, xs):
            cv.px(x + xx + 1, y + yy + 1, shadow)
    for yy, xx in zip(ys, xs):
        cv.px(x + xx, y + yy, k)
    return m.shape[1]
