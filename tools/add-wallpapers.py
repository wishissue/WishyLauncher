#!/usr/bin/env python3
"""Adds additional procedural wallpapers to the wallpapers/ folder and credits.json."""
import os
import json
import numpy as np
from PIL import Image

W, H = 1600, 900
TW, TH = 480, 270
QUALITY = 98
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WP_DIR = os.path.join(ROOT, "wallpapers")

NEW_WALLPAPERS = {
    "neon": {
        "name": "Neon Pulse",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#030712", "#0F172A"),
        "base_light": ("#F0FDF4", "#DCFCE7"),
        "blooms": [
            (0.85, 0.95, 0.48, 0.55, 0.0, "#06B6D4", "#0284C7", 0.90, 0.78),
            (0.50, 0.85, 0.35, 0.35, 0.4, "#EC4899", "#DB2777", 0.65, 0.60),
            (0.95, 0.15, 0.28, 0.32, 0.0, "#8B5CF6", "#7C3AED", 0.40, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#3B82F6", "#60A5FA", 0.30, 0.50),
        ],
        "sweep": (0.60, 0.30, 0.10, 0.07),
    },
    "solar": {
        "name": "Solar Flare",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#180E02", "#381B04"),
        "base_light": ("#FFFBEB", "#FEF3C7"),
        "blooms": [
            (0.88, 0.98, 0.50, 0.55, 0.0, "#F59E0B", "#D97706", 0.92, 0.80),
            (0.55, 0.88, 0.36, 0.35, 0.3, "#EA580C", "#C2410C", 0.65, 0.60),
            (0.98, 0.18, 0.26, 0.30, 0.0, "#DC2626", "#B91C1C", 0.35, 0.55),
            (0.10, 0.92, 0.32, 0.28, 0.0, "#FBBF24", "#F59E0B", 0.30, 0.50),
        ],
        "sweep": (0.58, 0.32, 0.09, 0.06),
    },
    "midnight": {
        "name": "Midnight Glow",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#060315", "#140D36"),
        "base_light": ("#F5F3FF", "#EDE9FE"),
        "blooms": [
            (0.85, 0.95, 0.50, 0.55, 0.0, "#6366F1", "#4F46E5", 0.90, 0.78),
            (0.52, 0.86, 0.36, 0.35, 0.5, "#8B5CF6", "#7C3AED", 0.65, 0.60),
            (0.95, 0.15, 0.26, 0.32, 0.0, "#3B82F6", "#2563EB", 0.38, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#A78BFA", "#8B5CF6", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "frost": {
        "name": "Frost Glacial",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#020817", "#0B192C"),
        "base_light": ("#F0F9FF", "#E0F2FE"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#38BDF8", "#0284C7", 0.90, 0.78),
            (0.54, 0.87, 0.36, 0.35, 0.4, "#2DD4BF", "#14B8A6", 0.62, 0.58),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#0EA5E9", "#0284C7", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#6EE7B7", "#34D399", 0.30, 0.50),
        ],
        "sweep": (0.58, 0.32, 0.09, 0.06),
    },
    "velvet": {
        "name": "Velvet Crimson",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#140408", "#2E0A16"),
        "base_light": ("#FFF1F2", "#FFE4E6"),
        "blooms": [
            (0.87, 0.97, 0.50, 0.55, 0.0, "#F43F5E", "#E11D48", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#FB7185", "#F43F5E", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#9F1239", "#881337", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#FDA4af", "#FB7185", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "canyon": {
        "name": "Canyon Earth",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#120804", "#2E1408"),
        "base_light": ("#FFF7ED", "#FFEDD5"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#C2410C", "#9A3412", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#D97706", "#B45309", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#FB923C", "#F97316", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#FDE68A", "#FCD34D", 0.30, 0.50),
        ],
        "sweep": (0.58, 0.32, 0.09, 0.06),
    },
    "nebula": {
        "name": "Deep Nebula",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#080212", "#18062B"),
        "base_light": ("#FAF5FF", "#F3E8FF"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#A855F7", "#9333EA", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#EC4899", "#DB2777", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#22D3EE", "#06B6D4", 0.38, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#C084FC", "#A855F7", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "aurora_borealis": {
        "name": "Aurora Glow",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#020A05", "#062314"),
        "base_light": ("#ECFDF5", "#D1FAE5"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#10B981", "#059669", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#06B6D4", "#0891B2", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#34D399", "#10B981", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#A7F3D0", "#6EE7B7", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "sakura": {
        "name": "Sakura Bloom",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#12040A", "#2E0A1A"),
        "base_light": ("#FDF2F8", "#FCE7F3"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#F472B6", "#EC4899", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#FB7185", "#F43F5E", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#E879F9", "#D946EF", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#FBCFE8", "#F472B6", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "emerald": {
        "name": "Emerald City",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#020E08", "#082F1C"),
        "base_light": ("#F0FDF4", "#DCFCE7"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#059669", "#047857", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#10B981", "#059669", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#34D399", "#10B981", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#A7F3D0", "#6EE7B7", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "sunset": {
        "name": "Sunset Horizon",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#120414", "#2E0A28"),
        "base_light": ("#FAF5FF", "#F3E8FF"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#9333EA", "#7E22CE", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#EA580C", "#C2410C", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#DB2777", "#BE185D", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#FDBA74", "#FB923C", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "abyss": {
        "name": "Deep Abyss",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#020617", "#0F172A"),
        "base_light": ("#F8FAFC", "#F1F5F9"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#2563EB", "#1D4ED8", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#0284C7", "#0369A1", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#3B82F6", "#2563EB", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#7DD3FC", "#38BDF8", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "amethyst": {
        "name": "Amethyst Haze",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#0A0214", "#200638"),
        "base_light": ("#FDF4FF", "#FAE8FF"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#C084FC", "#A855F7", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#E879F9", "#D946EF", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#9333EA", "#7E22CE", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#F5D0FE", "#F0ABFC", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "topaz": {
        "name": "Golden Topaz",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#120A02", "#2E1C06"),
        "base_light": ("#FEFCE8", "#FEF08A"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#EAB308", "#CA8A04", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#F59E0B", "#D97706", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#FACC15", "#EAB308", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#FEF08A", "#FEF08A", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
    "matrix": {
        "name": "Cyber Matrix",
        "author": "Wishy Launcher contributors",
        "license": "GPL-3.0-only",
        "source": "https://github.com/wishissue/WishyLauncher",
        "tone": "pair",
        "base_dark": ("#020804", "#06200F"),
        "base_light": ("#ECFDF5", "#D1FAE5"),
        "blooms": [
            (0.86, 0.96, 0.50, 0.55, 0.0, "#22C55E", "#16A34A", 0.90, 0.78),
            (0.53, 0.86, 0.36, 0.35, 0.4, "#10B981", "#059669", 0.65, 0.60),
            (0.96, 0.16, 0.26, 0.32, 0.0, "#4ADE80", "#22C55E", 0.35, 0.55),
            (0.12, 0.90, 0.30, 0.28, 0.0, "#BBF7D0", "#86EFAC", 0.30, 0.50),
        ],
        "sweep": (0.55, 0.30, 0.10, 0.06),
    },
}


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x / (w - 1), y / (h - 1)


def bloom(x, y, cx, cy, rx, ry, rot=0.0, w=W, h=H):
    dx = (x - cx) * (w / h)
    dy = (y - cy)
    c, s = np.cos(rot), np.sin(rot)
    u = dx * c + dy * s
    v = -dx * s + dy * c
    return np.exp(-((u / (rx * (w / h))) ** 2 + (v / ry) ** 2))


def render(spec, dark, seed, w=W, h=H, grain=1.0):
    rng = np.random.default_rng(seed)
    x, y = grid(w, h)
    top, bottom = (hexrgb(spec["base_dark"][0]), hexrgb(spec["base_dark"][1])) if dark \
        else (hexrgb(spec["base_light"][0]), hexrgb(spec["base_light"][1]))
    t = (0.65 * y + 0.35 * x)[..., None]
    img = top * (1 - t) + bottom * t
    for (cx, cy, rx, ry, rot, col_d, col_l, k_d, k_l) in spec["blooms"]:
        a = (bloom(x, y, cx, cy, rx, ry, rot, w, h) * (k_d if dark else k_l))[..., None]
        col = hexrgb(col_d if dark else col_l)
        img = img * (1 - a) + col * a
    sx, sy, sw, sk = spec.get("sweep", (0.55, 0.30, 0.10, 0.07))
    d = ((x - sx) * 0.8 + (y - sy) * -0.6)
    sweep = (np.exp(-((d / sw) ** 2)) * sk)[..., None]
    img = img * (1 - sweep) + np.ones(3, np.float32) * sweep
    r = np.sqrt(((x - 0.5) * 1.1) ** 2 + ((y - 0.5) * 1.0) ** 2)[..., None]
    if dark:
        img = img * (1 - 0.34 * np.clip(r - 0.28, 0, 1) ** 1.4)
    else:
        img = img * (1 - 0.06 * np.clip(r - 0.35, 0, 1)) + 0.02 * (1 - np.clip(r, 0, 1))
    img = np.clip(img, 0, 1) * 255.0
    img += rng.normal(0.0, grain, img.shape[:2])[..., None]
    return Image.fromarray(np.clip(np.rint(img), 0, 255).astype(np.uint8), "RGB")


def main():
    os.makedirs(WP_DIR, exist_ok=True)
    for i, (wid, spec) in enumerate(NEW_WALLPAPERS.items()):
        # render dark
        im_dark = render(spec, True, 300 + i)
        dark_path = os.path.join(WP_DIR, f"{wid}-dark.webp")
        im_dark.save(dark_path, format="WEBP", quality=QUALITY, method=6)

        # render light
        im_light = render(spec, False, 300 + i)
        light_path = os.path.join(WP_DIR, f"{wid}-light.webp")
        im_light.save(light_path, format="WEBP", quality=QUALITY, method=6)

        # render thumb (480x270)
        im_thumb = render(spec, True, 300 + i, w=TW, h=TH, grain=0.5)
        thumb_path = os.path.join(WP_DIR, f"{wid}-thumb.webp")
        im_thumb.save(thumb_path, format="WEBP", quality=QUALITY, method=6)
        print(f"Generated wallpaper {wid}: dark, light, thumb")

    # Update credits.json
    cpath = os.path.join(WP_DIR, "credits.json")
    credits = {}
    if os.path.isfile(cpath):
        with open(cpath, "r", encoding="utf-8") as f:
            credits = json.load(f)

    for wid, spec in NEW_WALLPAPERS.items():
        credits[wid] = {
            "name": spec["name"],
            "author": spec["author"],
            "license": spec["license"],
            "source": spec["source"],
            "tone": spec["tone"],
        }

    with open(cpath, "w", encoding="utf-8") as f:
        json.dump(credits, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("Updated wallpapers/credits.json successfully.")


if __name__ == "__main__":
    main()
