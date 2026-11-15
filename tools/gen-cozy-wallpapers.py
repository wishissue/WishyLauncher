#!/usr/bin/env python3
"""Draws the two soft wallpapers of the Wishy Cozy theme from scratch (warm colour blooms, light grain).

    pip install pillow numpy
    python3 tools/gen-cozy-wallpapers.py

Writes themes/wishy-cozy/assets/ui/wallpaper-{light,dark}.webp.
"""
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "themes" / "wishy-cozy" / "assets" / "ui"
W, H = 1920, 1080

# (x, y, radius, colour) as fractions of the width; blurred discs laid over a flat base.
LIGHT = ((255, 244, 230), [(0.12, 0.10, 0.34, (255, 205, 160)), (0.85, 0.20, 0.30, (255, 226, 170)),
                           (0.70, 0.95, 0.38, (214, 232, 200)), (0.05, 0.90, 0.28, (255, 190, 170))])
DARK = ((30, 22, 18), [(0.12, 0.10, 0.34, (92, 52, 34)), (0.85, 0.20, 0.30, (84, 64, 30)),
                       (0.70, 0.95, 0.38, (44, 64, 46)), (0.05, 0.90, 0.28, (86, 40, 36))])


def draw(base, blobs, name):
    img = Image.new("RGB", (W, H), base)
    layer = Image.new("RGB", (W, H), base)
    from PIL import ImageDraw
    d = ImageDraw.Draw(layer)
    for x, y, r, col in blobs:
        cx, cy, rad = x * W, y * H, r * W
        d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=col)
    layer = layer.filter(ImageFilter.GaussianBlur(160))
    img = Image.blend(img, layer, 0.9)
    rng = np.random.default_rng(7)
    grain = rng.normal(0, 2.2, (H, W, 1))
    arr = np.clip(np.asarray(img, dtype=np.float32) + grain, 0, 255).astype(np.uint8)
    Image.fromarray(arr).save(OUT / name, "WEBP", quality=80)


OUT.mkdir(parents=True, exist_ok=True)
draw(*LIGHT, "wallpaper-light.webp")
draw(*DARK, "wallpaper-dark.webp")
print("wrote", OUT)
