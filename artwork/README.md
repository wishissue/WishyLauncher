# App banner artwork

Put 16:9 banners here, one file per Android package name, for example `com.example.app.png`
(320x180 up to 1920x1080, PNG, at most 2 MB). Then add a line to `credits.json`:

```json
{ "com.example.app": { "author": "Your name", "license": "CC0-1.0", "source": "https://...", "title": "Example banner" } }
```

`python3 tools/build_repo.py` (or the GitHub Action) copies the pictures into `repo/artwork/` and writes the index
with their SHA-256. The launcher downloads a banner only for apps the person has installed, and it replaces the
app's own banner, so only add a picture that is better than the one the app ships.

Do not add logos, key art or screenshots you do not have the right to redistribute. This folder is intentionally
empty until real artwork is ready.
