#!/usr/bin/env python3
"""Generates the launcher's wallpapers and the Wishy theme's artwork.

Everything is drawn from scratch with numpy: soft colour blooms over a gradient, a faint light sweep, a vignette
and a little film grain (the grain stops banding on 8-bit TV panels). Nothing here is traced from, or based on,
anyone else's picture, so the output is covered by the project licence (GPL-3.0-only).

Run from the repository root:   python3 tools/gen-wallpapers.py
Needs:                          pip install pillow numpy

Writes
  app/src/main/res/drawable-nodpi/wp_<id>_dark.webp / wp_<id>_light.webp   the wallpaper catalog (see art/Wallpapers.kt)
"""
import os
import sys

import numpy as np
from PIL import Image

W, H = 1600, 900          # decoded at <= 1280 px wide by the launcher; 16:9
QUALITY = 98
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def grid(w=W, h=H):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x / (w - 1), y / (h - 1)          # 0..1 each


def bloom(x, y, cx, cy, rx, ry, rot=0.0):
    """Soft gaussian blob, centre/radii in 0..1 screen units (aspect corrected so 'rx' is a share of the width)."""
    dx = (x - cx) * (W / H)
    dy = (y - cy)
    c, s = np.cos(rot), np.sin(rot)
    u = dx * c + dy * s
    v = -dx * s + dy * c
    return np.exp(-((u / (rx * (W / H))) ** 2 + (v / ry) ** 2))


def render(spec, dark, seed, w=W, h=H, grain=1.0):
    rng = np.random.default_rng(seed)
    x, y = grid(w, h)
    top, bottom = (hexrgb(spec["base_dark"][0]), hexrgb(spec["base_dark"][1])) if dark \
        else (hexrgb(spec["base_light"][0]), hexrgb(spec["base_light"][1]))
    t = (0.65 * y + 0.35 * x)[..., None]
    img = top * (1 - t) + bottom * t
    for (cx, cy, rx, ry, rot, col_d, col_l, k_d, k_l) in spec["blooms"]:
        a = (bloom(x, y, cx, cy, rx, ry, rot) * (k_d if dark else k_l))[..., None]
        col = hexrgb(col_d if dark else col_l)
        img = img * (1 - a) + col * a                      # alpha-over: smooth, never clips
    # a faint diagonal light sweep, like a soft ribbon of light
    sx, sy, sw, sk = spec.get("sweep", (0.55, 0.30, 0.10, 0.07))
    d = ((x - sx) * 0.8 + (y - sy) * -0.6)
    sweep = (np.exp(-((d / sw) ** 2)) * sk)[..., None]
    img = img * (1 - sweep) + (np.ones(3, np.float32) if dark else np.ones(3, np.float32)) * sweep
    # vignette: deepen the edges on dark, gently lift the centre on light
    r = np.sqrt(((x - 0.5) * 1.1) ** 2 + ((y - 0.5) * 1.0) ** 2)[..., None]
    if dark:
        img = img * (1 - 0.34 * np.clip(r - 0.28, 0, 1) ** 1.4)
    else:
        img = img * (1 - 0.06 * np.clip(r - 0.35, 0, 1)) + 0.02 * (1 - np.clip(r, 0, 1))
    img = np.clip(img, 0, 1) * 255.0
    img += rng.normal(0.0, grain, img.shape[:2])[..., None]   # grain against banding
    return Image.fromarray(np.clip(np.rint(img), 0, 255).astype(np.uint8), "RGB")


# (cx, cy, rx, ry, rot, colour on dark, colour on light, strength dark, strength light)
CATALOG = {
    # Brand blue, the same blue as the Wishy Browser: #0166FF, with the lighter #66A3FF for dark mode.
    "wishy": dict(
        base_dark=("#05070D", "#0A1533"), base_light=("#EEF5FF", "#D6E6FF"),
        blooms=[
            (0.92, 1.02, 0.55, 0.62, 0.0, "#0166FF", "#66A3FF", 0.95, 0.80),
            (0.62, 0.95, 0.40, 0.40, 0.4, "#0A3FBF", "#9CC4FF", 0.65, 0.65),
            (1.00, 0.22, 0.28, 0.35, 0.0, "#2B8CFF", "#BFE0FF", 0.34, 0.60),
            (0.18, 1.05, 0.30, 0.28, 0.0, "#00A3FF", "#CFE9FF", 0.22, 0.55),
            (0.02, 0.04, 0.35, 0.30, 0.0, "#123A8F", "#FFFFFF", 0.22, 0.65),
        ],
        sweep=(0.62, 0.35, 0.09, 0.06),
    ),
    "aurora": dict(
        base_dark=("#040B0F", "#07202A"), base_light=("#EAFBF6", "#D2EEF6"),
        blooms=[
            (0.85, 1.00, 0.50, 0.55, 0.0, "#12B89A", "#6FE3CB", 0.85, 0.75),
            (0.55, 0.90, 0.38, 0.35, 0.5, "#1F6FE0", "#8FC1FF", 0.60, 0.60),
            (1.00, 0.20, 0.25, 0.32, 0.0, "#34D399", "#B6F3DF", 0.30, 0.55),
            (0.08, 0.02, 0.35, 0.28, 0.0, "#0B3C5D", "#FFFFFF", 0.28, 0.60),
        ],
    ),
    "dusk": dict(
        base_dark=("#07060F", "#140B2E"), base_light=("#F3EFFF", "#E2D8FF"),
        blooms=[
            (0.90, 1.02, 0.52, 0.60, 0.0, "#6D4BFF", "#B3A1FF", 0.90, 0.78),
            (0.58, 0.92, 0.36, 0.36, 0.4, "#B03AE8", "#E0A8FF", 0.58, 0.60),
            (1.02, 0.25, 0.26, 0.32, 0.0, "#FF6BB5", "#FFC1E0", 0.26, 0.55),
            (0.05, 0.03, 0.34, 0.28, 0.0, "#2A1A66", "#FFFFFF", 0.30, 0.62),
        ],
    ),
    "ember": dict(
        base_dark=("#0D0605", "#2B0E08"), base_light=("#FFF3EA", "#FFE0CC"),
        blooms=[
            (0.90, 1.02, 0.52, 0.60, 0.0, "#FF5A36", "#FF9C7A", 0.88, 0.75),
            (0.58, 0.92, 0.36, 0.34, 0.4, "#E03A1E", "#FFB892", 0.60, 0.60),
            (1.02, 0.22, 0.26, 0.32, 0.0, "#FFB020", "#FFE0A6", 0.30, 0.58),
            (0.05, 0.03, 0.34, 0.28, 0.0, "#4A1608", "#FFFFFF", 0.30, 0.62),
        ],
    ),
    "orchid": dict(
        base_dark=("#0C050E", "#2A0B2E"), base_light=("#FFF0FB", "#F8D9F2"),
        blooms=[
            (0.90, 1.02, 0.52, 0.60, 0.0, "#D946EF", "#F1A1FA", 0.86, 0.75),
            (0.58, 0.92, 0.36, 0.34, 0.4, "#8B2BE2", "#CFA3FF", 0.60, 0.60),
            (1.02, 0.22, 0.26, 0.32, 0.0, "#FF5FA2", "#FFC3DC", 0.28, 0.56),
            (0.05, 0.03, 0.34, 0.28, 0.0, "#46124F", "#FFFFFF", 0.30, 0.62),
        ],
    ),
    "ocean": dict(
        base_dark=("#03090F", "#06223A"), base_light=("#E8F8FF", "#CBEAFB"),
        blooms=[
            (0.90, 1.02, 0.52, 0.60, 0.0, "#00A9D6", "#6FD6F2", 0.88, 0.76),
            (0.58, 0.92, 0.36, 0.34, 0.4, "#0A5FB5", "#86BFF0", 0.62, 0.60),
            (1.02, 0.22, 0.26, 0.32, 0.0, "#22D3EE", "#B4F0FA", 0.30, 0.56),
            (0.05, 0.03, 0.34, 0.28, 0.0, "#0A3558", "#FFFFFF", 0.30, 0.62),
        ],
    ),
    "forest": dict(
        base_dark=("#040A06", "#0A2314"), base_light=("#EEFBEF", "#D5F0DC"),
        blooms=[
            (0.90, 1.02, 0.52, 0.60, 0.0, "#22B45A", "#7EE0A0", 0.86, 0.74),
            (0.58, 0.92, 0.36, 0.34, 0.4, "#0F8F7A", "#86DCCB", 0.60, 0.58),
            (1.02, 0.22, 0.26, 0.32, 0.0, "#9BD62F", "#DDF5A8", 0.26, 0.55),
            (0.05, 0.03, 0.34, 0.28, 0.0, "#0E3A20", "#FFFFFF", 0.30, 0.62),
        ],
    ),
    "mono": dict(
        base_dark=("#07080B", "#16181E"), base_light=("#F4F5F8", "#E0E3EA"),
        blooms=[
            (0.90, 1.02, 0.52, 0.60, 0.0, "#7B8496", "#B6BDCB", 0.75, 0.70),
            (0.58, 0.92, 0.36, 0.34, 0.4, "#4A5163", "#C9CFDB", 0.55, 0.58),
            (1.02, 0.22, 0.26, 0.32, 0.0, "#A9B4CC", "#E7ECF6", 0.22, 0.55),
            (0.05, 0.03, 0.34, 0.28, 0.0, "#22252E", "#FFFFFF", 0.30, 0.62),
        ],
    ),
}
ORDER = ["wishy"]    # only the brand wallpaper is generated now; the others are GNOME Backgrounds photos/renders, see ARTWORK_ATTRIBUTION.md


def save(img, path, **kw):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, **kw)
    return os.path.getsize(path)


def placeholder_cover(w=640, h=360):
    """The Wishy theme's cover for apps with no artwork: brand-blue bloom with two soft rings."""
    spec = dict(
        base_dark=("#0A2A8F", "#0143C7"), base_light=("#0A2A8F", "#0143C7"),
        blooms=[
            (0.12, 0.05, 0.40, 0.40, 0.0, "#0166FF", "#0166FF", 0.55, 0.55),
            (0.95, 0.15, 0.30, 0.45, 0.0, "#66A3FF", "#66A3FF", 0.95, 0.95),
            (0.20, 1.05, 0.40, 0.35, 0.0, "#001F66", "#001F66", 0.60, 0.60),
        ],
        sweep=(0.55, 0.40, 0.08, 0.05),
    )
    img = render(spec, True, 7, w, h, grain=0.4)
    a = np.asarray(img).astype(np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy = 0.80 * w, 0.18 * h
    r = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    for radius, width, alpha in ((0.34 * h, 2.0, 0.22), (0.52 * h, 2.0, 0.12)):
        ring = np.exp(-(((r - radius) / width) ** 2)) * alpha
        a = a * (1 - ring[..., None]) + 255.0 * ring[..., None]
    return Image.fromarray(np.clip(np.rint(a), 0, 255).astype(np.uint8), "RGB")


def main():
    total = 0
    res = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
    for i, wid in enumerate(ORDER):
        for dark in (True, False):
            tone = "dark" if dark else "light"
            n = save(render(CATALOG[wid], dark, 100 + i), os.path.join(res, f"wp_{wid}_{tone}.webp"),
                     format="WEBP", quality=QUALITY, method=6)
            total += n
            print(f"wp_{wid}_{tone}.webp  {n / 1024:.0f} KB")
    print(f"total {total / 1024:.0f} KB")


if __name__ == "__main__":
    sys.exit(main())
