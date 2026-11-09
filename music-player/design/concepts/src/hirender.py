"""A small 2.5D renderer for hardware faceplates (机).

Every part is a height field with a material: brushed aluminium (anisotropic highlights across its
brushing), spun metal, black anodising, polished diamond-cut chamfers, glossy glass, varnished walnut,
printed paper. Normals come from the heights, so bevels, knurling, engraving and screw heads catch the
light; a studio key light from the top left casts soft shadows and cavities get ambient occlusion.
Flat metal reflects a softbox in its own panel's frame (`panel()`), which gives brushed plates the
vertical light band they have in product photographs. Emissive parts (fluorescent displays, LEDs,
meter lamps) bloom.

All 2D drawing (text, scales, segments, icons) goes through `draw()`, which supersamples a local box.
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONTS = "/usr/share/fonts/opentype/inter/"
import os
C3 = os.path.dirname(os.path.abspath(__file__)) + "/"
CJK = C3 + "fonts/NotoSansSC-%d.otf"
HAND = C3 + "fonts/LongCang.ttf"
PIXEL = "/home/user/gitwrap/music-player/app/src/main/res/font/fusion_pixel.ttf"


def _box(a, r, axis):
    if r < 1:
        return a
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float64)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(a, sigma):
    """Gaussian blur approximated by three box passes per axis (works on 2D and HxWx3)."""
    if sigma <= 0:
        return a.astype(np.float32)
    r = max(1, int(round(math.sqrt(12 * sigma * sigma / 3 + 1) / 2 - .5)))
    out = a.astype(np.float32)
    for _ in range(3):
        out = _box(_box(out, r, 0), r, 1)
    return out


_fcache = {}


def font(size, weight="SemiBold", kind="latin"):
    key = (size, weight, kind)
    if key not in _fcache:
        if kind == "cjk":
            w = {"Regular": 400, "Medium": 500, "SemiBold": 500, "Bold": 700, "ExtraBold": 900, "Black": 900}.get(weight.replace("Italic", ""), 500)
            _fcache[key] = ImageFont.truetype(CJK % w, size)
        elif kind == "hand":
            _fcache[key] = ImageFont.truetype(HAND, size)
        elif kind == "pixel":
            _fcache[key] = ImageFont.truetype(PIXEL, size)
        else:
            _fcache[key] = ImageFont.truetype(FONTS + "Inter-%s.otf" % weight, size)
    return _fcache[key]


def is_cjk(ch):
    return ord(ch) >= 0x2E80


class SD:
    """ImageDraw with coordinates in plate pixels, drawn `ss` times larger into a local box."""

    def __init__(self, im, ox, oy, ss):
        self.d, self.ox, self.oy, self.ss = ImageDraw.Draw(im), ox, oy, ss

    def P(self, pts):
        return [((x - self.ox) * self.ss, (y - self.oy) * self.ss) for x, y in pts]

    def B(self, box):
        x0, y0, x1, y1 = box
        return [(x0 - self.ox) * self.ss, (y0 - self.oy) * self.ss, (x1 - self.ox) * self.ss, (y1 - self.oy) * self.ss]

    def polygon(self, pts, fill=255):
        self.d.polygon(self.P(pts), fill=fill)

    def line(self, pts, width=1., fill=255):
        self.d.line(self.P(pts), fill=fill, width=max(1, int(round(width * self.ss))), joint="curve")

    def ellipse(self, box, fill=255, outline=None, width=1.):
        self.d.ellipse(self.B(box), fill=fill, outline=outline, width=max(1, int(round(width * self.ss))))

    def rect(self, box, fill=255, r=0):
        if r:
            self.d.rounded_rectangle(self.B(box), radius=r * self.ss, fill=fill)
        else:
            self.d.rectangle(self.B(box), fill=fill)

    def arc(self, box, a0, a1, width=1., fill=255):
        self.d.arc(self.B(box), a0, a1, fill=fill, width=max(1, int(round(width * self.ss))))

    def text(self, xy, s, size, weight="SemiBold", kind="latin", fill=255, anchor="ls", spacing=0.):
        """Lettering with per-character fallback: Latin in Inter, Chinese in Noto Sans SC."""
        ss = self.ss
        fonts = []
        for ch in s:
            k = kind
            if kind == "latin" and is_cjk(ch):
                k = "cjk"
            fonts.append(font(int(round(size * ss)), weight, k))
        widths = [f.getlength(ch) for f, ch in zip(fonts, s)]
        total = sum(widths) + spacing * ss * (len(s) - 1)
        x = (xy[0] - self.ox) * ss - (total / 2 if anchor[0] == "m" else total if anchor[0] == "r" else 0)
        y = (xy[1] - self.oy) * ss
        for f, ch, w in zip(fonts, s, widths):
            self.d.text((x, y), ch, font=f, fill=fill, anchor="l" + anchor[1])
            x += w + spacing * ss
        return total / ss


def text_width(s, size, weight="SemiBold", kind="latin", spacing=0.):
    w = 0
    for ch in s:
        k = "cjk" if (kind == "latin" and is_cjk(ch)) else kind
        w += font(int(round(size * 4)), weight, k).getlength(ch) / 4
    return w + spacing * (len(s) - 1)


class Plate:
    def __init__(self, w, h, seed=1):
        self.w, self.h = w, h
        self.Y, self.X = np.mgrid[0:h, 0:w].astype(np.float32)
        self.H = np.zeros((h, w), np.float32)
        self.alb = np.zeros((h, w, 3), np.float32)
        self.metal = np.zeros((h, w), np.float32)
        self.rough = np.full((h, w), .5, np.float32)
        self.tan = np.zeros((h, w), np.float32)
        self.aniso = np.zeros((h, w), np.float32)
        self.gloss = np.zeros((h, w), np.float32)
        self.smod = np.ones((h, w), np.float32)      # brushing streaks in the highlight
        self.emis = np.zeros((h, w, 3), np.float32)
        self.u = self.X / w                          # panel-local coordinates for the softbox
        self.v = self.Y / h
        self.panelk = np.zeros((h, w), np.float32)   # how strongly flat metal shows the softbox
        self.rng = np.random.default_rng(seed)

    # ── shapes (signed distance, computed only near the shape) ──
    def _sl(self, x0, y0, x1, y1, pad):
        a, b = max(0, int(y0 - pad)), min(self.h, int(math.ceil(y1 + pad)))
        c, d = max(0, int(x0 - pad)), min(self.w, int(math.ceil(x1 + pad)))
        return (slice(a, b), slice(c, d))

    def rrect(self, x0, y0, x1, y1, r, pad=40):
        out = np.full((self.h, self.w), 1e4, np.float32)
        sl = self._sl(x0, y0, x1, y1, pad)
        X, Y = self.X[sl], self.Y[sl]
        cx, cy, bx, by = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2
        qx, qy = np.abs(X - cx) - bx + r, np.abs(Y - cy) - by + r
        out[sl] = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r
        return out

    def circle(self, cx, cy, r, pad=40):
        out = np.full((self.h, self.w), 1e4, np.float32)
        sl = self._sl(cx - r, cy - r, cx + r, cy + r, pad)
        out[sl] = np.hypot(self.X[sl] - cx, self.Y[sl] - cy) - r
        return out

    def hexagon(self, cx, cy, r, rot=0.):
        out = np.full((self.h, self.w), 1e4, np.float32)
        sl = self._sl(cx - r * 1.2, cy - r * 1.2, cx + r * 1.2, cy + r * 1.2, 4)
        x, y = self.X[sl] - cx, self.Y[sl] - cy
        c, s = math.cos(rot), math.sin(rot)
        x, y = np.abs(c * x - s * y), np.abs(s * x + c * y)
        kx, ky, kz = -0.866025404, 0.5, 0.577350269
        t = 2 * np.minimum(kx * x + ky * y, 0)
        x, y = x - t * kx, y - t * ky
        x = x - np.clip(x, -kz * r, kz * r)
        y = y - r
        out[sl] = np.hypot(x, y) * np.sign(y)
        return out

    @staticmethod
    def cov(d):
        return np.clip(.5 - d, 0, 1)

    def draw(self, box, fn, ss=4):
        """Run fn(SD) on a supersampled local canvas over box = (x0, y0, x1, y1); returns a 0..1 mask."""
        x0, y0, x1, y1 = [int(v) for v in (math.floor(box[0]), math.floor(box[1]), math.ceil(box[2]), math.ceil(box[3]))]
        x0, y0, x1, y1 = max(0, x0), max(0, y0), min(self.w, x1), min(self.h, y1)
        m = np.zeros((self.h, self.w), np.float32)
        if x1 <= x0 or y1 <= y0:
            return m
        im = Image.new("L", ((x1 - x0) * ss, (y1 - y0) * ss), 0)
        fn(SD(im, x0, y0, ss))
        im = im.resize((x1 - x0, y1 - y0), Image.BOX)
        m[y0:y1, x0:x1] = np.asarray(im, np.float32) / 255
        return m

    # ── materials ──
    def paint(self, m, alb=None, metal=None, rough=None, tan=None, aniso=None, gloss=None, smod=None, panelk=None):
        if alb is not None:
            a = np.asarray(alb, np.float32)
            if a.ndim <= 1:
                a = a[None, None, :]
            self.alb = self.alb * (1 - m[..., None]) + a * m[..., None]
        for name, v in (("metal", metal), ("rough", rough), ("tan", tan), ("aniso", aniso), ("gloss", gloss), ("smod", smod), ("panelk", panelk)):
            if v is not None:
                cur = getattr(self, name)
                setattr(self, name, cur * (1 - m) + np.asarray(v, np.float32) * m)

    def glow(self, m, color, k=1.):
        self.emis += m[..., None] * np.asarray(color, np.float32)[None, None, :] * k

    def panel(self, x0, y0, x1, y1, k=None):
        """Set the softbox frame for a flat panel: its own u/v run 0..1 across it."""
        sl = (slice(max(0, int(y0)), min(self.h, int(y1))), slice(max(0, int(x0)), min(self.w, int(x1))))
        self.u[sl] = (self.X[sl] - x0) / max(1, x1 - x0)
        self.v[sl] = (self.Y[sl] - y0) / max(1, y1 - y0)
        if k is not None:
            self.panelk[sl] = k

    def lift(self, d, h, bw, kind="fillet", set_=False):
        """Raise (h > 0) or sink (h < 0) the area inside d by h, with a bevel bw wide.
        Returns the bevel ring as a mask (for polishing a chamfer)."""
        t = np.clip(-d / bw, 0, 1)
        if kind == "fillet":
            prof = np.sqrt(1 - (1 - t) ** 2)
        elif kind == "dome":
            prof = np.sqrt(np.clip(1 - (1 - t) ** 2, 0, 1)) ** .7
        else:
            prof = t
        c = self.cov(d)
        if set_:
            self.H = self.H * (1 - c) + (h * prof) * c
        else:
            self.H += h * prof * c
        return c * (t < .999)

    def raise_to(self, d, base, h, bw, kind="chamfer"):
        """Set the area inside d to height base + h * profile (replacing what was there)."""
        t = np.clip(-d / bw, 0, 1)
        prof = t if kind == "chamfer" else np.sqrt(1 - (1 - t) ** 2)
        c = self.cov(d)
        self.H = self.H * (1 - c) + (base + h * prof) * c
        return c * (t < .999)

    def noise(self, scale, octaves=3, aspect=(1, 1)):
        """Value noise (fbm) of roughly unit amplitude; scale is the coarsest feature size in px."""
        out = np.zeros((self.h, self.w), np.float32)
        amp = 1
        for o in range(octaves):
            sx = max(2, int(self.w / (scale * aspect[0]) * 2 ** o) + 2)
            sy = max(2, int(self.h / (scale * aspect[1]) * 2 ** o) + 2)
            g = self.rng.normal(0, 1, (sy, sx)).astype(np.float32)
            out += amp * np.asarray(Image.fromarray(g, mode="F").resize((self.w, self.h), Image.BICUBIC), np.float32)
            amp *= .5
        return out / 1.6

    def brushed(self, m, alb, angle=0.0, strength=.06, length=90, aniso=1., rough=.32, panelk=1.):
        """Aluminium brushed along `angle` (0 horizontal): long fine streaks in albedo and highlight."""
        h, w = self.h, self.w
        vert = abs(angle - math.pi / 2) < 1e-3
        H_, W_ = (w, h) if vert else (h, w)
        n = self.rng.normal(0, 1, (H_, max(2, W_ // length))).astype(np.float32)
        streak = np.asarray(Image.fromarray(n, mode="F").resize((W_, H_), Image.BILINEAR), np.float32)
        fine = self.rng.normal(0, 1, (H_, max(2, W_ // 6))).astype(np.float32)
        streak = .6 * streak + .5 * np.asarray(Image.fromarray(fine, mode="F").resize((W_, H_), Image.BILINEAR), np.float32)
        if vert:
            streak = streak.T
        a = np.asarray(alb, np.float32)[None, None, :] * (1 + strength * streak[..., None])
        self.paint(m, alb=a, metal=1., rough=rough, tan=angle, aniso=aniso, smod=np.clip(1 + .32 * streak, .3, 2), panelk=panelk)

    def spun(self, m, cx, cy, alb, strength=.07, rough=.26):
        """A machined (turned) disc: concentric rings, highlights running radially through the centre."""
        r = np.hypot(self.X - cx, self.Y - cy)
        rings = np.interp(r * 2.2, np.arange(8192), self.rng.normal(0, 1, 8192)).astype(np.float32)
        a = np.asarray(alb, np.float32)[None, None, :] * (1 + strength * rings[..., None])
        ang = np.arctan2(self.Y - cy, self.X - cx) + math.pi / 2
        self.paint(m, alb=a, metal=1., rough=rough, tan=ang, aniso=1., smod=np.clip(1 + .6 * rings, .2, 2), panelk=0.)

    def wood(self, m, light=(.31, .175, .095), dark=(.105, .056, .03), angle=0., scale=1.):
        """Walnut: wavy growth lines, pores, varnish."""
        X, Y = (self.Y, self.X) if angle else (self.X, self.Y)
        warp = self.noise(260 * scale, 3, aspect=(4, .5) if not angle else (.5, 4)) * 28 * scale
        fig = np.sin((Y + warp + .002 * X * X / max(1, scale * 300)) / (7.5 * scale)) * .5 + .5
        fig2 = np.sin((Y * 1.7 + warp * 1.3) / (2.6 * scale)) * .5 + .5
        pores = self.noise(5 * scale, 2, aspect=(10, .3) if not angle else (.3, 10))
        t = np.clip(.18 + .38 * fig ** 1.6 + .16 * fig2 + .1 * pores + .12 * self.noise(90 * scale, 2, aspect=(6, .4) if not angle else (.4, 6)), 0, 1)
        lo, hi = np.asarray(dark, np.float32), np.asarray(light, np.float32)
        a = lo[None, None] * (1 - t[..., None]) + hi[None, None] * t[..., None]
        a *= (1 - .25 * np.clip(pores - .9, 0, 1))[..., None]
        self.paint(m, alb=a, metal=0., rough=.38, aniso=0., gloss=.28, panelk=0.)
        self.H += m * pores * .25

    # ── light ──
    def shade(self, key=(-.38, -.62, .69), exposure=1.0, shadow_k=.75, ao_k=1., bloom=1., S=3):
        H = blur(self.H, .6)
        gx = np.zeros_like(H); gy = np.zeros_like(H)
        gx[:, 1:-1] = (H[:, 2:] - H[:, :-2]) / 2
        gy[1:-1] = (H[2:] - H[:-2]) / 2
        N = np.stack([-gx, -gy, np.ones_like(H)], -1)
        N /= np.linalg.norm(N, axis=-1, keepdims=True)
        del gx, gy
        # Soft shadows from the key light: march towards it over the height field.
        L2 = np.array(key[:2]) / np.hypot(*key[:2])
        elev = key[2] / np.hypot(*key[:2])
        span = float(H.max() - np.percentile(H, 5))
        reach = int(min(220, max(20, span / (elev * .9))))
        occ = np.zeros_like(H)
        step = max(1, reach // 60)
        for k in range(1, reach, step):
            dx, dy = int(round(L2[0] * k)), int(round(L2[1] * k))
            sh = np.roll(np.roll(H, -dy, 0), -dx, 1)
            occ = np.maximum(occ, (sh - H - k * elev * .9) / (2.5 + .06 * k))
        shadow = np.clip(blur(np.clip(occ, 0, 1), 1.2 * S), 0, 1)
        del occ
        ao = np.clip(1 - np.clip(blur(H, 2.3 * S) - H, 0, None) * .045 * ao_k - np.clip(blur(H, 6 * S) - H, 0, None) * .018 * ao_k, .35, 1)
        V = np.array([0, 0, 1.], np.float32)
        lights = [(np.array(key), 1.0, (1., .975, .94)), (np.array([.6, -.25, .76]), .28, (.9, .94, 1.)), (np.array([.1, .8, .6]), .1, (1, 1, 1))]
        col = np.zeros((self.h, self.w, 3), np.float32)
        T = np.stack([np.cos(self.tan), np.sin(self.tan), np.zeros_like(self.tan)], -1)
        T = T - N * (N * T).sum(-1, keepdims=True)
        T /= np.linalg.norm(T, axis=-1, keepdims=True) + 1e-6
        B = np.cross(N, T)
        spec_col = (1 - self.metal[..., None]) * .9 + self.metal[..., None] * self.alb * 1.1
        for k, (L, I, lc) in enumerate(lights):
            L = L / np.linalg.norm(L)
            ndl = np.clip((N * L).sum(-1), 0, None)
            Hh = (L + V); Hh = Hh / np.linalg.norm(Hh)
            ndh = np.clip((N * Hh).sum(-1), 1e-3, None)
            p = 2 / np.clip(self.rough, .03, 1) ** 2
            iso = ndh ** p * (p + 2) / 50
            ht, hb = (T * Hh).sum(-1), (B * Hh).sum(-1)
            an = np.exp(-((ht / .07) ** 2 + (hb / .42) ** 2) / ndh ** 2) * 1.2 * self.smod
            spec = iso * (1 - self.aniso) + an * self.aniso
            spec = spec * (.05 + .95 * self.metal) * (1 - .7 * self.gloss) + self.gloss * ndh ** 300 * 3
            vis = 1 - (shadow_k * shadow if k == 0 else .25 * shadow)
            diff = self.alb * ndl[..., None] * (1 - .85 * self.metal[..., None])
            col += (diff + spec_col * spec[..., None]) * I * np.array(lc, np.float32) * vis[..., None]
        # The studio seen in metal and glass: bright ceiling, a softbox top left, dark floor.
        R = np.stack([2 * N[..., 2] * N[..., 0], 2 * N[..., 2] * N[..., 1], 2 * N[..., 2] ** 2 - 1], -1)
        env = (.16 + .5 * np.clip(-R[..., 1] * 1.4 + .1, 0, 1) + .7 * np.exp(-((R[..., 0] + .45) ** 2 + (R[..., 1] + .6) ** 2) / .08)
               + .25 * np.exp(-((R[..., 0] - .7) ** 2 + (R[..., 1] + .2) ** 2) / .1))
        # Flat panels reflect the softbox in their own frame: a soft vertical band, brighter at the top.
        flat = np.clip(N[..., 2], 0, 1) ** 40
        u, v = self.u, self.v
        band = np.exp(-((u - .3 - .12 * v) / .16) ** 2) * (.9 - .45 * v) + .22 * np.exp(-((u - .82) / .1) ** 2) * (1 - v)
        local = (.34 + .34 * (1 - v) ** 1.5 + band * self.smod * 1.15) * flat * self.panelk
        env = env * (1 - flat * self.panelk) + local
        envw = .3 + .7 * np.clip(1 - self.rough * 1.6, 0, 1)
        col += (self.metal * envw * env)[..., None] * self.alb * .95
        fres = .04 + .96 * (1 - np.clip(N[..., 2], 0, 1)) ** 5
        gband = np.exp(-(((self.X - self.Y * .55) / self.w - .32) / .1) ** 2) * .07 + np.exp(-(((self.X - self.Y * .55) / self.w - .5) / .025) ** 2) * .03
        col += (self.gloss * (fres * env * .5 + gband + .02 * (1 - v)))[..., None]
        amb = self.alb * (.2 - .1 * self.metal[..., None])
        col = (col + amb) * ao[..., None] * exposure
        e = self.emis
        if bloom:
            e = e + blur(e, 1.5 * S) * .55 * bloom + blur(e, 6 * S) * .35 * bloom
        col = col + e
        col = col / (1 + col * .12)
        return np.clip(col, 0, 1)

    def save(self, path, col=None, **kw):
        if col is None:
            col = self.shade(**kw)
        Image.fromarray((col ** (1 / 1.06) * 255 + .5).astype(np.uint8)).save(path)
        return col
