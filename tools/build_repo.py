#!/usr/bin/env python3
"""Builds the `repo/` folder the launcher downloads from.

    python3 tools/build_repo.py            # build / refresh repo/
    python3 tools/build_repo.py --check    # build, then fail if repo/ differs from what is committed

Inputs
    themes/<id>/                 theme source folders (theme.json + assets/)
    artwork/<package>.png        16:9 banners for apps (320x180 up to 1920x1080, max 2 MB)
    artwork/credits.json         {"<package>": {"author": "...", "license": "...", "source": "...", "title": "..."}}

Outputs (all deterministic, so unchanged inputs produce byte-identical files and no git churn)
    repo/index.json              theme list with SHA-256 of every zip
    repo/themes/<id>.zip
    repo/previews/<id>.png       copied from themes/<id>/preview.png when present
    repo/artwork/index.json      app banner list with SHA-256 of every picture
    repo/artwork/<package>.png

The rules mirrored here come from themecore (ThemeLimits, RepoChecker). The launcher checks them again on the
device, so this script is a convenience for authors, not the security boundary.
"""
import hashlib
import json
import os
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEMES = ROOT / "themes"
ART_SRC = ROOT / "artwork"
OUT = ROOT / "repo"

ALLOWED_EXT = {"png", "jpg", "jpeg", "webp", "ttf", "otf"}
SPECIAL = {"theme.json", "CREDITS.md"}
MAX_ZIP = 20 * 1024 * 1024
MAX_FILES = 200
MAX_IMG = 5 * 1024 * 1024
MAX_FONT = 2 * 1024 * 1024
MAX_TOTAL = 40 * 1024 * 1024
MAX_ART_BYTES = 2 * 1024 * 1024
ART_MIN = (320, 180)
ART_MAX = (1920, 1080)
FIXED_TIME = (2026, 1, 1, 0, 0, 0)  # constant timestamp so zips are reproducible


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def image_size(path: Path):
    try:
        from PIL import Image  # type: ignore
        with Image.open(path) as im:
            return im.size
    except ImportError:
        with open(path, "rb") as f:
            head = f.read(24)
        if head[:8] == b"\x89PNG\r\n\x1a\n":
            return struct.unpack(">II", head[16:24])
        raise SystemExit(f"{path}: install Pillow (pip install pillow) to read non-PNG images")


def fail(msg: str):
    print(f"error: {msg}", file=sys.stderr)
    raise SystemExit(1)


def theme_files(folder: Path):
    files = sorted(p for p in folder.rglob("*") if p.is_file())
    out = []
    for p in files:
        rel = p.relative_to(folder).as_posix()
        if rel.startswith(".") or "/." in rel:
            continue
        ext = p.suffix.lower().lstrip(".")
        if rel not in SPECIAL and ext not in ALLOWED_EXT:
            fail(f"{folder.name}: '{rel}' has a file type themes may not contain")
        if any(c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-/" for c in rel):
            fail(f"{folder.name}: '{rel}' has characters themes may not use in paths")
        size = p.stat().st_size
        if ext in {"png", "jpg", "jpeg", "webp"} and size > MAX_IMG:
            fail(f"{folder.name}: '{rel}' is over 5 MB")
        if ext in {"ttf", "otf"} and size > MAX_FONT:
            fail(f"{folder.name}: '{rel}' is over 2 MB")
        out.append((rel, p))
    if len(out) > MAX_FILES:
        fail(f"{folder.name}: more than {MAX_FILES} files")
    if sum(p.stat().st_size for _, p in out) > MAX_TOTAL:
        fail(f"{folder.name}: unpacked size is over 40 MB")
    return out


def build_zip(folder: Path, dest: Path):
    dest.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel, p in theme_files(folder):
            info = zipfile.ZipInfo(rel, FIXED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, p.read_bytes())
    if dest.stat().st_size > MAX_ZIP:
        fail(f"{folder.name}: zip is over 20 MB")


def validate_theme_json(folder: Path, schema):
    data = json.loads((folder / "theme.json").read_text(encoding="utf-8"))
    if data.get("id") != folder.name:
        fail(f"{folder.name}: theme.json id is '{data.get('id')}', it must equal the folder name")
    if schema is not None:
        import jsonschema  # type: ignore
        try:
            jsonschema.Draft202012Validator(schema).validate(data)
        except jsonschema.ValidationError as e:
            where = "/".join(str(x) for x in e.absolute_path)
            fail(f"{folder.name}: theme.json breaks the schema at '{where}': {e.message}")
    # every file the theme points at must exist
    def walk(o):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in {"file", "image", "source"} and isinstance(v, str) and v.startswith("assets/"):
                    if not (folder / v).is_file():
                        fail(f"{folder.name}: theme.json refers to missing file '{v}'")
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(data)
    return data


def write_json(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def build_themes():
    schema = None
    schema_path = ROOT / "schema" / "wishy-theme.schema.v1.json"
    try:
        import jsonschema  # type: ignore  # noqa: F401
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
    except ImportError:
        print("note: jsonschema not installed, skipping schema validation (pip install jsonschema)")
    entries = []
    for folder in sorted(p for p in THEMES.iterdir() if p.is_dir()):
        if not (folder / "theme.json").is_file():
            continue
        data = validate_theme_json(folder, schema)
        zip_path = OUT / "themes" / f"{folder.name}.zip"
        build_zip(folder, zip_path)
        entry = {
            "id": data["id"],
            "name": data["name"],
            "version": data["version"],
            "author": data["author"],
            "description": data.get("description", ""),
            "license": data["license"],
            "schemaVersion": data["schemaVersion"],
            "minLauncherVersion": data.get("minLauncherVersion", 1),
            "downloadUrl": f"themes/{folder.name}.zip",
            "sha256": sha256(zip_path),
            "sizeBytes": zip_path.stat().st_size,
        }
        preview = folder / "preview.png"
        if preview.is_file():
            dest = OUT / "previews" / f"{folder.name}.png"
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(preview.read_bytes())
            entry["previewUrl"] = f"previews/{folder.name}.png"
        entries.append(entry)
        print(f"theme {folder.name}: {entry['sizeBytes']} bytes")
    write_json(OUT / "index.json", {"schemaVersion": 1, "name": "Wishy Launcher official themes", "themes": entries})


def build_artwork():
    credits = {}
    cpath = ART_SRC / "credits.json"
    if cpath.is_file():
        credits = json.loads(cpath.read_text(encoding="utf-8"))
    apps = {}
    if ART_SRC.is_dir():
        for p in sorted(ART_SRC.glob("*.png")):
            pkg = p.stem
            c = credits.get(pkg)
            if not c or not c.get("author") or not c.get("license"):
                fail(f"artwork/{p.name}: add author and license for '{pkg}' to artwork/credits.json")
            w, h = image_size(p)
            if not (ART_MIN[0] <= w <= ART_MAX[0] and ART_MIN[1] <= h <= ART_MAX[1]):
                fail(f"artwork/{p.name}: {w}x{h} is outside 320x180 .. 1920x1080")
            if abs(w / h - 16 / 9) > 16 / 9 * 0.03:
                fail(f"artwork/{p.name}: {w}x{h} is not 16:9")
            if p.stat().st_size > MAX_ART_BYTES:
                fail(f"artwork/{p.name}: over 2 MB")
            dest = OUT / "artwork" / p.name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(p.read_bytes())
            e = {
                "url": p.name,
                "sha256": sha256(dest),
                "sizeBytes": dest.stat().st_size,
                "width": w,
                "height": h,
                "author": c["author"],
                "license": c["license"],
            }
            if c.get("source"):
                e["source"] = c["source"]
            if c.get("title"):
                e["title"] = c["title"]
            apps[pkg] = e
    print(f"artwork: {len(apps)} pictures")
    write_json(OUT / "artwork" / "index.json", {"schemaVersion": 1, "apps": apps})


def main():
    import shutil
    if OUT.exists():
        shutil.rmtree(OUT)
    build_themes()
    build_artwork()
    if "--check" in sys.argv:
        import subprocess
        r = subprocess.run(["git", "status", "--porcelain", "--", "repo"], cwd=ROOT, capture_output=True, text=True)
        if r.stdout.strip():
            fail("repo/ is out of date. Run: python3 tools/build_repo.py and commit the result.\n" + r.stdout)
    print("repo/ is up to date")


if __name__ == "__main__":
    main()
