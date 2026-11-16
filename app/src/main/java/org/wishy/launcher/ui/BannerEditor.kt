package org.wishy.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wishy.launcher.R
import org.wishy.launcher.art.AppArt
import org.wishy.launcher.art.ArtDiscovery
import org.wishy.launcher.art.ArtLibrary
import org.wishy.launcher.art.ArtMeta
import org.wishy.launcher.art.BannerRenderer
import org.wishy.launcher.art.FetchedLogo
import org.wishy.launcher.art.LogoImage
import org.wishy.launcher.data.AppEntry
import org.wishy.theme.core.ArtColor
import org.wishy.theme.core.ArtMatcher
import org.wishy.theme.core.ArtOrigin
import org.wishy.theme.core.ArtPresets
import org.wishy.theme.core.AutoDesign
import org.wishy.theme.core.BackgroundSpec
import org.wishy.theme.core.BannerLayout
import org.wishy.theme.core.BannerSpec
import org.wishy.theme.core.BgKind
import org.wishy.theme.core.FitMode
import org.wishy.theme.core.GenericIcons
import org.wishy.theme.core.MatchApp
import org.wishy.theme.core.SvgSanitizer
import org.wishy.theme.core.ThemeLimits

/** A logo the person can pick, with everything needed to draw it, save it and credit it. */
internal class EditorLogo(
    val key: String,
    val title: String,
    val image: LogoImage,
    /** The original file, kept next to the banner. SVG text or PNG bytes. */
    val bytes: ByteArray?,
    val ext: String?,
    val origin: ArtOrigin,
    val source: String,
    val author: String,
    val license: String,
    val sourceUrl: String?,
    val brandHex: String?,
    val dominant: Int?,
    val thumb: Bitmap,
)

private val THUMB_SPEC = BannerSpec(background = BackgroundSpec(BgKind.SOLID, "#E2E8F0", "#E2E8F0"), tint = "#111827")

internal fun makeEditorLogo(
    key: String, title: String, image: LogoImage, bytes: ByteArray?, ext: String?, origin: ArtOrigin,
    source: String, author: String, license: String, sourceUrl: String?, brandHex: String?,
): EditorLogo = EditorLogo(
    key, title, image, bytes, ext, origin, source, author, license, sourceUrl, brandHex,
    BannerRenderer.dominantColor(image),
    BannerRenderer.render(THUMB_SPEC, image, null, 320, 180),
)

internal fun editorLogoFrom(f: FetchedLogo): EditorLogo = makeEditorLogo(
    key = f.logo.provider + "|" + f.logo.url, title = f.logo.title, image = LogoImage.svg(f.info), bytes = f.bytes, ext = "svg",
    origin = ArtOrigin.COMMUNITY, source = f.logo.provider, author = f.logo.author, license = f.logo.license,
    sourceUrl = f.logo.sourcePage, brandHex = f.logo.brandHex,
)

internal fun editorLogoFromIcon(context: Context, pkg: String, title: String): EditorLogo? {
    val icon = AppArt.icon(context, pkg) ?: return null
    return makeEditorLogo("app-icon", title, LogoImage.raster(icon), BannerRenderer.png(icon), "png", ArtOrigin.GENERATED, "app-icon", "", "", null, null)
}

internal fun editorLogoFromGeneric(g: GenericIcons.Icon): EditorLogo {
    val bytes = g.svg.toByteArray()
    return makeEditorLogo("generic-${g.id}", g.name, LogoImage.svg(SvgSanitizer.inspect(bytes)), bytes, "svg", ArtOrigin.GENERATED, "wishy-generic", "Wishy Launcher contributors", "GPL-3.0-only", null, null)
}

/** Reads a logo the person chose from a file: SVG (checked) or a picture (kept as PNG). Throws with a readable message. */
internal fun readLogoFile(context: Context, uri: Uri, title: String): EditorLogo {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024); var total = 0L
        while (true) {
            val n = input.read(buf); if (n < 0) break
            total += n
            if (total > ThemeLimits.MAX_IMAGE_BYTES) throw IllegalArgumentException("The file is larger than 5 MB.")
            out.write(buf, 0, n)
        }
        out.toByteArray()
    } ?: throw IllegalArgumentException("Could not open the file.")
    val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8)
    if (head.contains("<svg", ignoreCase = true) || head.trimStart().startsWith("<?xml")) {
        val info = SvgSanitizer.inspect(bytes)
        return makeEditorLogo("file|" + System.nanoTime(), title, LogoImage.svg(info), bytes, "svg", ArtOrigin.USER, "file", "", "", null, null)
    }
    val bmp = BannerRenderer.decodeLimited(bytes, 2048) ?: throw IllegalArgumentException("It is not an SVG or a picture.")
    return makeEditorLogo("file|" + System.nanoTime(), title, LogoImage.raster(bmp), BannerRenderer.png(bmp), "png", ArtOrigin.USER, "file", "", "", null, null)
}

internal fun EditorLogo?.toMeta(spec: BannerSpec): ArtMeta =
    if (this == null) ArtMeta(spec = spec, origin = ArtOrigin.USER, source = "editor")
    else ArtMeta(spec = spec, origin = origin, source = source, author = author, license = license, sourceUrl = sourceUrl, title = title)

/** Draws the final 1280 x 720 banner and saves it with its originals. Blocking: call from a background thread. */
internal fun saveBannerBlocking(context: Context, pkg: String, logo: EditorLogo?, spec: BannerSpec, background: Bitmap?, bgBytes: ByteArray?) {
    val bmp = BannerRenderer.render(spec, logo?.image, background, 1280, 720)
    try {
        ArtLibrary.get(context).save(pkg, bmp, logo.toMeta(spec), logo?.bytes, logo?.ext, bgBytes)
    } finally { bmp.recycle() }
}

private fun autoTint(bg: BackgroundSpec): String {
    if (bg.kind == BgKind.IMAGE || bg.kind == BgKind.TRANSPARENT) return "#FFFFFF"
    val a = ArtColor.parseHex(bg.a) ?: 0xFF000000.toInt()
    val b = ArtColor.parseHex(bg.b) ?: a
    return ArtColor.toHex(ArtColor.contrastOn(ArtColor.mix(a, b, 0.5f)))
}

private fun brushOf(bg: BackgroundSpec): Brush {
    val a = Color(ArtColor.parseHex(bg.a) ?: 0xFF1F2937.toInt())
    val b = Color(ArtColor.parseHex(bg.b) ?: 0xFF111827.toInt())
    return if (bg.kind == BgKind.GRADIENT) Brush.linearGradient(listOf(a, b)) else Brush.linearGradient(listOf(a, a))
}

/** A large focusable tile. The focus ring is thick and white; the chosen tile keeps a coloured ring. */
@Composable
internal fun Tile(modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Wish.SurfaceHigh, focusedContainerColor = Wish.SurfaceHigh),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, Wish.Primary)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Wish.Text)),
        ),
    ) { Box(Modifier.fillMaxSize(), content = content) }
}

@Composable
private fun LogoTile(l: EditorLogo, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val img = remember(l) { l.thumb.asImageBitmap() }
        Tile(modifier.fillMaxWidth().aspectRatio(16f / 9f), selected, onClick) {
            Image(bitmap = img, contentDescription = l.title, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        }
        Text(l.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Wish.TextDim)
    }
}

@Composable
private fun Swatch(bg: BackgroundSpec, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(modifier.fillMaxWidth().aspectRatio(16f / 9f), selected, onClick) {
        Box(Modifier.fillMaxSize().background(brushOf(bg)))
    }
}

/**
 * The banner editor. Three areas: choose a logo, choose a background, adjust (with the preview always on the left).
 * Every control is a big focusable tile or button, nothing needs a pointer, and Back asks before throwing work away.
 */
@Composable
internal fun BannerEditorDialog(
    app: AppEntry,
    initial: Cand?,
    netOk: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { ArtLibrary.get(ctx) }
    val discovery = remember { ArtDiscovery.get(ctx) }
    val pkg = app.pkg

    var logo by remember { mutableStateOf<EditorLogo?>(null) }
    var spec by remember { mutableStateOf(initial?.spec ?: BannerSpec()) }
    var bgImage by remember { mutableStateOf<Bitmap?>(null) }
    var bgBytes by remember { mutableStateOf<ByteArray?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var bgTouched by remember { mutableStateOf(initial != null) }
    var logoChosen by remember { mutableStateOf(initial != null) }
    var dirty by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showHex by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var yours by remember { mutableStateOf<EditorLogo?>(null) }
    val found = remember { mutableStateListOf<EditorLogo>() }
    val fromApp = remember { mutableStateListOf<EditorLogo>() }
    val searchRes = remember { mutableStateListOf<EditorLogo>() }
    val generics = remember { mutableStateListOf<EditorLogo>() }
    val catalogCache = remember { arrayOfNulls<ArtDiscovery.Catalogs>(1) }

    val mono = logo?.image?.mono == true

    fun choose(l: EditorLogo, byPerson: Boolean) {
        logo = l
        if (byPerson) { logoChosen = true; dirty = true }
        spec = if (!bgTouched) AutoDesign.forLogo(pkg, l.image.mono, l.brandHex, l.dominant)
        else spec.copy(scale = 1f, dx = 0f, dy = 0f, tint = if (l.image.mono) (spec.tint ?: autoTint(spec.background)) else null)
    }

    fun setBackground(bg: BackgroundSpec, tint: String? = null) {
        bgTouched = true; dirty = true
        spec = spec.copy(background = bg, tint = if (mono) (tint ?: autoTint(bg)) else spec.tint)
    }

    // First, what is already on the device (fast), then what the catalogs know about this app.
    LaunchedEffect(pkg) {
        class Loaded(val gens: List<EditorLogo>, val icon: EditorLogo?, val saved: EditorLogo?, val meta: ArtMeta?, val bgBytes: ByteArray?, val bg: Bitmap?)
        val loaded = withContext(Dispatchers.IO) {
            val gens = GenericIcons.all.mapNotNull { runCatching { editorLogoFromGeneric(it) }.getOrNull() }
            val icon = editorLogoFromIcon(ctx, pkg, ctx.getString(R.string.ed_app_icon))
            val stored = library.logo(pkg)
            val meta = library.meta(pkg)
            val savedLogo = if (initial == null && stored != null) runCatching {
                if (stored.ext == "svg") makeEditorLogo("saved", meta?.title?.ifBlank { null } ?: app.label, LogoImage.svg(SvgSanitizer.inspect(stored.bytes)), stored.bytes, "svg",
                    meta?.origin ?: ArtOrigin.USER, meta?.source ?: "", meta?.author ?: "", meta?.license ?: "", meta?.sourceUrl, null)
                else BannerRenderer.decodeLimited(stored.bytes, 2048)?.let {
                    makeEditorLogo("saved", app.label, LogoImage.raster(it), stored.bytes, "png", meta?.origin ?: ArtOrigin.USER, meta?.source ?: "", meta?.author ?: "", meta?.license ?: "", meta?.sourceUrl, null)
                }
            }.getOrNull() else null
            val bgB = if (initial == null) library.backgroundBytes(pkg) else null
            Loaded(gens, icon, savedLogo, meta, bgB, bgB?.let { BannerRenderer.decodeLimited(it, 1920) })
        }
        generics.addAll(loaded.gens)
        loaded.icon?.let { fromApp.add(it) }
        when {
            initial?.logo != null -> { yours = initial.logo; logo = initial.logo; spec = initial.spec }
            loaded.saved != null -> {
                yours = loaded.saved; logo = loaded.saved
                loaded.meta?.let { spec = it.spec }
                if (loaded.bg != null) { bgImage = loaded.bg; bgBytes = loaded.bgBytes }
                logoChosen = true; bgTouched = true
            }
            else -> {
                val first = loaded.icon ?: generics.firstOrNull { it.key == "generic-" + GenericIcons.forCategory(app.autoCategory).id }
                if (first != null) choose(first, false)
            }
        }
        // Then look for real logos. This downloads the catalogs as whole files; nothing about this app is sent.
        val matched = withContext(Dispatchers.IO) {
            val cats = discovery.loadCatalogs(netOk).also { catalogCache[0] = it }
            val m = ArtMatcher.match(listOf(MatchApp(pkg, app.label, app.autoCategory)), cats.logos)[pkg].orEmpty()
            m.mapNotNull { x -> discovery.fetchLogo(x.logo, netOk)?.let { runCatching { editorLogoFrom(it) }.getOrNull() } }
        }
        found.addAll(matched)
        if (!logoChosen && matched.isNotEmpty()) choose(matched[0], false)
    }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { readLogoFile(ctx, uri, ctx.getString(R.string.ed_sec_yours)) } }
            r.onSuccess { yours = it; choose(it, true); status = "" }
                .onFailure { status = ctx.getString(R.string.ed_bad_file, it.message ?: "") }
        }
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val o = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var t = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            t += n; if (t > ThemeLimits.MAX_IMAGE_BYTES) throw IllegalArgumentException("The picture is larger than 5 MB.")
                            o.write(buf, 0, n)
                        }
                        o.toByteArray()
                    } ?: throw IllegalArgumentException("Could not open the file.")
                    val bmp = BannerRenderer.decodeLimited(bytes, 1920) ?: throw IllegalArgumentException("It is not a picture.")
                    bytes to bmp
                }
            }
            r.onSuccess { (b, bmp) ->
                bgBytes = b; bgImage = bmp
                setBackground(BackgroundSpec(BgKind.IMAGE, image = "background.img"), "#FFFFFF")
                status = ""
            }.onFailure { status = ctx.getString(R.string.ed_bad_file, it.message ?: "") }
        }
    }

    val preview by produceState<Bitmap?>(null, logo, spec, bgImage) {
        value = withContext(Dispatchers.Default) { BannerRenderer.render(spec, logo?.image, bgImage, 640, 360) }
    }

    fun save() {
        if (saving) return
        if (logo == null && spec.background.kind != BgKind.IMAGE) { status = ctx.getString(R.string.ed_need_logo); tab = 0; return }
        saving = true
        val l = logo; val s = spec; val bi = bgImage; val bb = if (s.background.kind == BgKind.IMAGE) bgBytes else null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { saveBannerBlocking(ctx, pkg, l, s, bi, bb) } }
            saving = false
            r.onSuccess { onSaved(); onDismiss() }
                .onFailure { status = ctx.getString(R.string.art_save_failed, it.message ?: "") }
        }
    }

    fun close() { if (dirty && !saving) showDiscard = true else onDismiss() }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        }
        Row(Modifier.fillMaxSize().background(Wish.Bg).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // ----- left: preview and the two main buttons -----
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ed_title, app.label), color = Wish.Text, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Wish.Surface)) {
                    val p = preview
                    if (p != null) Image(bitmap = p.asImageBitmap(), contentDescription = stringResource(R.string.ed_preview), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (status.isNotEmpty()) Text(status, color = Wish.TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val doneFocus = initialFocus()
                    WishButton(stringResource(R.string.ed_done), modifier = Modifier.focusRequester(doneFocus)) { save() }
                    WishButton(stringResource(R.string.ed_auto), filled = false) {
                        logo?.let { l ->
                            bgTouched = false; dirty = true
                            spec = AutoDesign.forLogo(pkg, l.image.mono, l.brandHex, l.dominant)
                        }
                    }
                }
            }
            // ----- right: tabs and their content -----
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WishButton(stringResource(R.string.ed_tab_logo), compact = true, filled = tab == 0) { tab = 0 }
                    WishButton(stringResource(R.string.ed_tab_bg), compact = true, filled = tab == 1) { tab = 1 }
                    WishButton(stringResource(R.string.ed_tab_adjust), compact = true, filled = tab == 2) { tab = 2 }
                }
                when (tab) {
                    0 -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WishButton(stringResource(R.string.ed_import_logo), compact = true, filled = false) {
                                    logoPicker.launch(arrayOf("image/svg+xml", "image/png", "image/jpeg", "image/webp", "text/xml"))
                                }
                                WishButton(stringResource(R.string.ed_search), compact = true, filled = false) { showSearch = true }
                            }
                        }
                        val sections = listOf(
                            R.string.ed_sec_yours to listOfNotNull(yours),
                            R.string.ed_sec_found to found.toList(),
                            R.string.ed_sec_search to searchRes.toList(),
                            R.string.ed_sec_app to fromApp.toList(),
                            R.string.ed_sec_generic to generics.toList(),
                        )
                        for ((label, list) in sections) {
                            if (list.isEmpty()) continue
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(label)) }
                            items(list, key = { label.toString() + "/" + it.key }) { l ->
                                LogoTile(l, selected = logo?.key == l.key) { choose(l, true) }
                            }
                        }
                    }
                    1 -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.ed_bg_looks)) }
                        items(ArtPresets.all, key = { "look-" + it.id }) { p ->
                            Swatch(p.background, selected = spec.background == p.background) { setBackground(p.background, p.tint) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.ed_bg_colors)) }
                        items(ArtPresets.solidColors, key = { "solid-$it" }) { c ->
                            val bg = BackgroundSpec(BgKind.SOLID, c, c)
                            Swatch(bg, selected = spec.background == bg) { setBackground(bg) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WishButton(stringResource(R.string.ed_bg_custom), compact = true, filled = false) { showHex = true }
                                WishButton(stringResource(R.string.ed_bg_picture), compact = true, filled = false) {
                                    bgPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            WishButton(stringResource(R.string.ed_bg_clear), compact = true, filled = false) {
                                setBackground(BackgroundSpec(BgKind.TRANSPARENT), "#FFFFFF")
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val aspect = logo?.image?.aspect ?: 1f
                        fun change(f: (BannerSpec) -> BannerSpec) { spec = f(spec); dirty = true }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WishButton(stringResource(R.string.ed_bigger), compact = true, filled = false) {
                                change {
                                    val max = if (it.mode == FitMode.FIT) BannerLayout.maxFitScale(1280f, 720f, aspect) else BannerLayout.MAX_SCALE
                                    it.copy(scale = (it.scale * 1.1f).coerceAtMost(max))
                                }
                            }
                            WishButton(stringResource(R.string.ed_smaller), compact = true, filled = false) {
                                change { it.copy(scale = (it.scale / 1.1f).coerceAtLeast(if (it.mode == FitMode.FIT) BannerLayout.MIN_SCALE else 1f)) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WishButton(stringResource(R.string.ed_left), compact = true, filled = false) { change { it.copy(dx = (it.dx - 0.02f).coerceAtLeast(-0.5f)) } }
                            WishButton(stringResource(R.string.ed_right), compact = true, filled = false) { change { it.copy(dx = (it.dx + 0.02f).coerceAtMost(0.5f)) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WishButton(stringResource(R.string.ed_up), compact = true, filled = false) { change { it.copy(dy = (it.dy - 0.02f).coerceAtLeast(-0.5f)) } }
                            WishButton(stringResource(R.string.ed_down), compact = true, filled = false) { change { it.copy(dy = (it.dy + 0.02f).coerceAtMost(0.5f)) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val modeName = stringResource(if (spec.mode == FitMode.FIT) R.string.ed_fit else R.string.ed_fill)
                            WishButton(stringResource(R.string.ed_fit_mode, modeName), compact = true, filled = false) {
                                change { it.copy(mode = if (it.mode == FitMode.FIT) FitMode.COVER else FitMode.FIT, scale = 1f, dx = 0f, dy = 0f) }
                            }
                            WishButton(stringResource(R.string.ed_reset), compact = true, filled = false) { change { it.copy(scale = 1f, dx = 0f, dy = 0f) } }
                        }
                        if (mono) {
                            val names = listOf(
                                "#FFFFFF" to stringResource(R.string.ed_color_white),
                                "#111827" to stringResource(R.string.ed_color_dark),
                                null to stringResource(R.string.ed_color_asis),
                            )
                            val idx = names.indexOfFirst { it.first.equals(spec.tint, ignoreCase = true) }.let { if (it < 0) 2 else it }
                            WishButton(stringResource(R.string.ed_color, names[idx].second), compact = true, filled = false) {
                                change { it.copy(tint = names[(idx + 1) % names.size].first) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHex) {
        TextEntryDialog(
            title = stringResource(R.string.ed_hex_title), hint = stringResource(R.string.ed_hex_hint),
            initial = spec.background.a,
            onSave = { t ->
                val c = ArtColor.parseHex(t)
                if (c == null) status = ctx.getString(R.string.ed_hex_bad)
                else { val h = ArtColor.toHex(c); setBackground(BackgroundSpec(BgKind.SOLID, h, h)); status = "" }
                showHex = false
            },
            onDismiss = { showHex = false },
        )
    }
    if (showSearch) {
        TextEntryDialog(
            title = stringResource(R.string.ed_search_title), hint = stringResource(R.string.ed_search_hint), initial = "",
            onSave = { q ->
                showSearch = false
                if (q.isBlank()) return@TextEntryDialog
                status = ctx.getString(R.string.ed_searching)
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        val cats = catalogCache[0] ?: discovery.loadCatalogs(netOk).also { catalogCache[0] = it }
                        discovery.search(q, cats, netOk).mapNotNull { cl -> discovery.fetchLogo(cl, netOk)?.let { runCatching { editorLogoFrom(it) }.getOrNull() } }
                    }
                    searchRes.clear(); searchRes.addAll(res)
                    status = if (res.isEmpty()) ctx.getString(R.string.ed_no_results) else ""
                    tab = 0
                }
            },
            onDismiss = { showSearch = false },
        )
    }
    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.ed_discard_title), body = stringResource(R.string.ed_discard_body),
            confirm = stringResource(R.string.ed_discard),
            onConfirm = { showDiscard = false; onDismiss() },
            onDismiss = { showDiscard = false },
        )
    }
}
