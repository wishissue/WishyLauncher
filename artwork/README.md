# App banner artwork

Put 16:9 banners here, one file per Android package name, for example `com.example.app.png`
(320x180 up to 1920x1080, PNG, at most 2 MB). Then add a line to `credits.json`:

```json
{ "com.example.app": { "author": "Your name", "license": "CC0-1.0", "source": "https://...", "title": "Example banner" } }
```

`python3 tools/build_repo.py` (or the GitHub Action) copies the pictures into `repo/artwork/` and writes the index
with their SHA-256. The launcher downloads a banner only for apps the person has installed, and it replaces the
app's own banner, so only add a picture that is better than the one the app ships.

## Several versions of one app's banner

To let people choose between banners for the same app, put them in a folder named after the package, one file per
version, with a short lowercase id as the file name (`a-z`, `0-9`, `_`, at most 32 characters, at most 12 per app):

```
artwork/com.example.app/dark.png
artwork/com.example.app/key_art.png
```

Credits for each go in `credits.json` under `"<package>/<id>"`, and `name` is what the picker shows (at most 40 characters):

```json
{ "com.example.app/dark": { "name": "Dark logo", "author": "Your name", "license": "CC0-1.0", "source": "https://..." } }
```

The build writes them to `repo/artwork/variants.json`. In Settings > Artwork, tapping an app shows every version with a
preview. People can also add their own source: any https address ending in `variants.json` in this same format.

Do not add logos, key art or screenshots you do not have the right to redistribute. The launcher only downloads what
the index lists, and the index only lists what is in this folder, so what you add here is what every user receives.
This folder is intentionally empty until real artwork is ready.
