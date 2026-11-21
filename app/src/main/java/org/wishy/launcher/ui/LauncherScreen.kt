package org.wishy.launcher.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.wishy.launcher.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.wishy.launcher.Actions
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.AppRepository
import org.wishy.launcher.data.ConfigStore
import org.wishy.launcher.data.LAYOUT_DOCK
import org.wishy.launcher.data.LAYOUT_GRID
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.data.NetStatus
import org.wishy.launcher.data.networkStatusFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Icon size steps (card width) — 5 levels for fine control. */
val ICON_SIZES = listOf(120.dp, 150.dp, 190.dp, 230.dp, 270.dp)

/** Allocated ONCE — recomposition never rebuilds the scrim brushes. */
// Full: darkens the whole wallpaper evenly.
private val ScrimFull = Brush.verticalGradient(
    listOf(
        Color.Black.copy(alpha = 0.55f),
        Color.Black.copy(alpha = 0.25f),
        Color.Black.copy(alpha = 0.45f),
    )
)
// Top & bottom: dark bands only where the status bar and dock/labels sit; the
// middle of the wallpaper stays fully clear .
private val ScrimTopBottom = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.6f),
    0.16f to Color.Black.copy(alpha = 0f),
    0.82f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0.5f),
)
// Top only — dark band under the status bar, rest clear.
private val ScrimTop = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.6f),
    0.20f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0f),
)
// Bottom only — dark band under the dock/labels, rest clear.
private val ScrimBottom = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0f),
    0.80f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0.5f),
)
/** Spacing steps between cards — 5 levels. */
val GAP_SIZES = listOf(4.dp, 10.dp, 16.dp, 24.dp, 32.dp)
/** Icon corner roundness steps (index stored in config.cornerRadius). */
val CORNER_RADII = listOf(0.dp, 4.dp, 10.dp, 18.dp, 28.dp)
/** Manual whole-UI scale steps (config.uiScale 1..5). <1 = more compact. */
val UI_SCALES = listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.3f)

/** The effective UI scale: 0 = Auto (compact high-DPI TVs down to ~1200dp of
 *  width so every device reads the same, never enlarging roomy ones); 1..5 pick
 *  a fixed value from [UI_SCALES]. */
fun uiScaleFactor(index: Int, screenWidthDp: Int): Float =
    if (index <= 0) (screenWidthDp / 1200f).coerceIn(0.6f, 1.0f)
    else UI_SCALES[(index - 1).coerceIn(0, UI_SCALES.size - 1)]

/** Global text boost (sp only, not dp): 1 = off. Raise to enlarge every label
 *  without touching icons or layout. */
const val FONT_BOOST = 1f

/** Renders [content] at [scale]× the device density — one knob shrinks/grows
 *  every dp and sp uniformly (icons, text, settings, wizard). Also rescales the
 *  Configuration so width-derived layout (dock/grid columns) stays correct. */
@Composable
fun ScaledUi(scale: Float, content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val scaledCfg = remember(cfg, scale) {
        android.content.res.Configuration(cfg).apply {
            screenWidthDp = (cfg.screenWidthDp / scale).toInt()
            screenHeightDp = (cfg.screenHeightDp / scale).toInt()
            smallestScreenWidthDp = (cfg.smallestScreenWidthDp / scale).toInt()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(base.density * scale, base.fontScale * FONT_BOOST),
        androidx.compose.ui.platform.LocalConfiguration provides scaledCfg,
    ) { content() }
}
// compositionLocalOf (not static): roundness changes at runtime and must
// recompose every reader, including those inside subcompositions like the
// dock's BoxWithConstraints (its 2nd-row peek) — static wouldn't reach them.
val LocalCornerRadius = androidx.compose.runtime.compositionLocalOf { 10.dp }

/**
 * How many full-size cards fit in [available] width, and the spacing to use.
 * Icons NEVER shrink: if the requested gap doesn't fit, the gap is squeezed
 * instead so every icon keeps its exact size.
 */
fun fitRow(available: Dp, cardWidth: Dp, gap: Dp): Pair<Int, Dp> {
    val cols = (((available + gap) / (cardWidth + gap)).toInt()).coerceAtLeast(1)
    val leftover = available - cardWidth * cols
    val used = if (cols > 1 && leftover > 0.dp) {
        val spread = leftover / (cols - 1)
        if (spread < gap) spread else gap
    } else 0.dp
    return cols to used
}

/** Unwrap a (possibly wrapped) Context to its host Activity, or null. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun LauncherApp(rescanTick: Int, homeTick: Int = 0) {
    val context = LocalContext.current
    val store = remember { ConfigStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Wait for the persisted config before drawing anything, so the setup
    // wizard never flashes for users who already completed it.
    val configOrNull by produceState<LauncherConfig?>(initialValue = null) {
        store.flow.collect { value = it }
    }
    // Kick off the app scan on the FIRST composition — concurrent with the
    // DataStore config read, not serialized after it (scan takes no config).
    // Warm relaunches start from the in-memory cache (instant); a fresh scan
    // still runs to pick up changes. Not rendered until past the gates below.
    val appsOrNull by produceState(initialValue = AppRepository.memoryCache, rescanTick) {
        value = withContext(Dispatchers.Default) { AppRepository.scan(context.applicationContext) }
    }
    val config = configOrNull ?: run {
        LoadingScreen()
        return
    }

    val uiScale = uiScaleFactor(
        config.uiScale,
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp,
    )

    // Mirror the chosen language to SharedPreferences (which attachBaseContext
    // reads synchronously) and recreate the activity when it changes, so the new
    // locale takes effect without a restart.
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(config.language) {
        if (org.wishy.launcher.MainActivity.currentLocalePref(context) != config.language) {
            org.wishy.launcher.MainActivity.persistLocale(context, config.language)
            activity?.recreate()
        }
    }
    // Every wallpaper mode draws an opaque image, so keep the window opaque.
    LaunchedEffect(activity) {
        activity?.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
        )
    }

    if (!config.setupDone) {
        // The greeter follows the colour mode too (system by default), so a light TV setup gets the light set.
        val wizardDark = when (config.themeMode) {
            1 -> false
            2 -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        ScaledUi(uiScale) {
            LiteTvTheme(accent = ACCENTS[0], dark = wizardDark) {
                SetupWizard(
                    currentThemeId = config.themeId,
                    onThemeChosen = { id -> scope.launch { store.update { it.copy(themeId = id) } } },
                    onOnlineChosen = { on ->
                        scope.launch { store.update { it.copy(offlineMode = !on, artworkOnline = on) } }
                    },
                    onDone = {
                        scope.launch { store.update { it.copy(setupDone = true) } }
                    },
                    onVpnChosen = { pkg ->
                        scope.launch {
                            store.update {
                                // Picked an app → VPN button opens it; skipped → hide it.
                                if (pkg == null) it.copy(showVpnButton = false, vpnApp = "")
                                else it.copy(showVpnButton = true, vpnApp = pkg)
                            }
                        }
                    },
                )
            }
        }
        return
    }

    // Hold the sober loading screen until the launcher can render COMPLETE —
    // every app scanned and every icon decoded.
    val scannedApps = appsOrNull ?: run {
        LoadingScreen()
        return
    }
    // Artwork the user chose, or downloaded from the official repository, replaces an app's own banner.
    // Decoded off the main thread; until it is ready the app's own banner or icon shows.
    val bannerManager = remember { org.wishy.launcher.art.BannerManager.get(context) }
    var artTick by remember { mutableIntStateOf(0) }
    val apps by produceState(scannedApps, scannedApps, artTick) {
        value = withContext(Dispatchers.IO) { bannerManager.withArtwork(scannedApps) }
    }
    // Optional, opt-in, background: fetch artwork for installed apps at most once a day.
    LaunchedEffect(config.artworkOnline, config.offlineMode, scannedApps) {
        if (!config.artworkOnline || config.offlineMode || !bannerManager.syncDue()) return@LaunchedEffect
        delay(5_000) // never compete with start-up
        val changed = withContext(Dispatchers.IO) {
            runCatching { bannerManager.syncOfficial(scannedApps.map { it.pkg }.toSet()) }.getOrDefault(0)
        }
        if (changed > 0) artTick++
    }
    // On every launch: ask GitHub whether a newer release exists and, if so, tell the person once per version.
    var updateInfo by remember { mutableStateOf<org.wishy.launcher.net.UpdateInfo?>(null) }
    LaunchedEffect(config.offlineMode) {
        if (config.offlineMode) return@LaunchedEffect
        delay(8_000) // never compete with start-up
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { org.wishy.launcher.net.UpdateChecker.check(installed) }
        if (r is org.wishy.launcher.net.UpdateResult.Available &&
            r.info.version != org.wishy.launcher.net.UpdateChecker.dismissedVersion(context)
        ) updateInfo = r.info
    }
    updateInfo?.let { info ->
        ConfirmDialog(
            title = stringResource(R.string.update_dialog_title),
            body = stringResource(R.string.update_dialog_body, info.version),
            confirm = stringResource(if (info.apkUrl.isNotEmpty()) R.string.update_now else R.string.update_open),
            onConfirm = {
                org.wishy.launcher.net.UpdateChecker.dismiss(context, info.version)
                updateInfo = null
                if (info.apkUrl.isEmpty()) {
                    org.wishy.launcher.net.UpdateChecker.openPage(context, info.pageUrl)
                } else {
                    // Download in the background, then hand the file to the system installer (which asks to confirm).
                    android.widget.Toast.makeText(context, R.string.update_downloading_unknown, android.widget.Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val failure = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = org.wishy.launcher.net.UpdateInstaller.download(context, info) { _, _ -> }
                                org.wishy.launcher.net.UpdateInstaller.install(context, f)
                            }.exceptionOrNull()
                        }
                        if (failure != null) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.update_download_failed, failure.message ?: failure.javaClass.simpleName),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            onDismiss = {
                org.wishy.launcher.net.UpdateChecker.dismiss(context, info.version)
                updateInfo = null
            },
        )
    }
    // flowOn(IO): the callbackFlow's initial compute() does ConnectivityManager
    // binder calls — keep them off the main thread on the first real frame.
    val net by remember { networkStatusFlow(context).flowOn(Dispatchers.IO) }
        .collectAsStateWithLifecycle(initialValue = NetStatus())

    // Bump on every ON_RESUME so the clock/date refresh immediately after
    // sleep — they normally only tick on minute boundaries via produceState.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Format with the app's current locale (reflects the language override, and
    // recomposes on change) — not Locale.getDefault(), which the framework can
    // reset, leaving the date stuck in the previous language.
    val locale = androidx.core.os.ConfigurationCompat
        .getLocales(androidx.compose.ui.platform.LocalConfiguration.current)
        .get(0) ?: Locale.getDefault()
    val time by produceState(initialValue = "", config.h24, locale, resumeTick) {
        val fmt = SimpleDateFormat(if (config.h24) "HH:mm" else "h:mm a", locale)
        while (true) {
            value = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val date by produceState(initialValue = "", config.dateFormat, locale, resumeTick) {
        val pattern = org.wishy.launcher.data.DATE_FORMATS
            .getOrElse(config.dateFormat) { "" }
        if (pattern.isEmpty()) { value = ""; return@produceState }
        val fmt = SimpleDateFormat(pattern, locale)
        while (true) {
            value = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    // New installs are auto-added to the FIRST section.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val current = apps.map { it.pkg }.toSet()
        store.update { cfg ->
            if (cfg.knownApps.isEmpty()) cfg.copy(knownApps = current)
            else {
                val added = current - cfg.knownApps
                if (added.isEmpty() && current == cfg.knownApps) cfg
                else {
                    // New installs go to the first real section. "All apps" fills itself, so it is never the target
                    // (it used to be picked here, which filtered out to an empty set and hid new apps from every section).
                    val first = cfg.categories.firstOrNull { it.id != AppRepository.ALL_APPS_ID }?.id
                    cfg.copy(
                        knownApps = current,
                        sections = if (first == null) cfg.sections else cfg.sections + added.associateWith { setOf(first) },
                    )
                }
            }
        }
    }

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var wallpaperVersion by remember { mutableIntStateOf(0) }

    // Drop empty sections: an empty row is dead space AND a focus trap that
    // stops D-pad navigation from reaching the section below it.
    val categorized = remember(apps, config) {
        AppRepository.categorize(apps, config).filter { it.second.isNotEmpty() }
    }
    // The dock's flat app list (all sections concatenated, de-duped). Hoisted &
    // remembered so its identity stays stable across recompositions — otherwise a
    // fresh list on every clock tick blocks DockArea from strong-skipping.
    val dockApps = remember(categorized) {
        categorized.flatMap { it.second }.distinctBy { it.pkg }
    }
    val accent = ACCENTS[config.accent.coerceIn(0, ACCENTS.size - 1)]

    // ----- display dimensions: size + spacing drive everything, columns
    // are always derived from available width (never a fixed count) -----
    val cardWidth: Dp = ICON_SIZES[config.iconScale.coerceIn(0, ICON_SIZES.size - 1)]
    val gap: Dp = GAP_SIZES[config.spacing.coerceIn(0, GAP_SIZES.size - 1)]

    // Columns as laid out on screen (mirrors the padding used by GridSection /
    // DockArea) — move mode needs them to shift an icon up/down by a full row.
    val screenW: Dp = LocalConfiguration.current.screenWidthDp.dp

    var dockExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(config.layout) { if (config.layout != LAYOUT_DOCK) dockExpanded = false }

    // The Home button while Wishy is already showing: close whatever is open and show the plain home screen,
    // like every launcher does. (homeTick only changes when the system delivers a HOME intent.)
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            showSettings = false
            menuFor = null
            dockExpanded = false
        }
    }

    // ----- theme: resolved off the main thread; null theme = classic layouts -----
    val themeStorage = remember { org.wishy.launcher.theme.ThemeStorage.get(context) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val themeDark = when (config.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val themeResult by produceState<org.wishy.launcher.theme.ThemeResult?>(
        initialValue = null, config.themeId, themeDark, config.animations,
    ) {
        val dm = context.resources.displayMetrics
        val ctx = org.wishy.theme.core.LoadContext(
            aspect = dm.widthPixels.toFloat() / dm.heightPixels,
            heightPx = dm.heightPixels,
            colorScheme = if (themeDark) org.wishy.theme.core.ColorSchemeName.DARK else org.wishy.theme.core.ColorSchemeName.LIGHT,
            reducedMotion = config.animations != 0,
        )
        value = withContext(Dispatchers.IO) { themeStorage.resolve(config.themeId, ctx) }
    }
    val loadedTheme = themeResult?.theme
    val themed = loadedTheme != null && config.themeId != org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID
    var themeCols by remember { mutableIntStateOf(1) }

    // ----- wallpaper: decoded off the main thread, downsampled, cached.
    // A ~100px copy is decoded alongside: upscaled by the GPU it looks
    // blurred, and crossfading it in costs one texture blend per frame
    // instead of a full-screen RenderEffect blur (heavy on TV GPUs). -----
    // A wallpaper that ships with the launcher comes in a dark and a light tone. The classic layouts are always dark.
    val bundledWallpaper = org.wishy.launcher.art.Wallpapers.byId(config.wallpaperId)
    val wallpaperDark = config.themeId == org.wishy.launcher.theme.ThemeStorage.CLASSIC_ID || themeDark
    val wallpaperPair by produceState<Pair<ImageBitmap?, ImageBitmap?>>(
        initialValue = null to null, config.useCustomWallpaper, bundledWallpaper?.id, wallpaperDark, wallpaperVersion
    ) {
        value = if (!config.useCustomWallpaper) {
            // No photo of the user's own: the chosen bundled wallpaper, or nothing (theme default / colour gradient).
            if (bundledWallpaper == null) null to null
            else withContext(Dispatchers.IO) {
                runCatching {
                    val sharp = org.wishy.launcher.art.Wallpapers.decodeForDisplay(context, bundledWallpaper, wallpaperDark)
                        ?.also { it.prepareToDraw() }?.asImageBitmap()
                    val blurred = org.wishy.launcher.art.Wallpapers.decode(context, bundledWallpaper, wallpaperDark, maxWidth = 96)
                        ?.also { it.prepareToDraw() }?.asImageBitmap()
                    sharp to blurred
                }.getOrDefault(null to null)
            }
        }
        else withContext(Dispatchers.IO) {
            val f = File(context.filesDir, "wallpaper.jpg")
            if (!f.exists()) null to null
            else runCatching {
                // Decoded for the real screen size in full colour (never smaller than the panel, never above the
                // 4096 px texture limit of TV GPUs). prepareToDraw() moves the texture upload off the first frame.
                val sharp = org.wishy.launcher.art.Wallpapers.decodeFileForDisplay(context, f)
                    ?.also { it.prepareToDraw() }?.asImageBitmap()
                val blurred = decodeDownsampled(f, maxWidth = 96)
                    ?.also { it.prepareToDraw() }?.asImageBitmap()
                sharp to blurred
            }.getOrDefault(null to null)
        }
    }
    val (wallpaperSharp, wallpaperBlurred) = wallpaperPair
    val presetBrush = remember(config.wallpaper) {
        WALLPAPERS[config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)].brush()
    }

    val corner: Dp = CORNER_RADII[config.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)]
    Box(Modifier.fillMaxSize()) {
    ScaledUi(uiScale) {
    androidx.compose.runtime.CompositionLocalProvider(LocalCornerRadius provides corner) {
    LiteTvTheme(accent = accent, dark = wallpaperDark) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // MENU always opens launcher settings — the only entry point
                    // when the user hides the status bar.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                        showSettings = true
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            // One theme environment for the background and the home screen (built inside ScaledUi so
            // its px-to-dp conversion matches the layout).
            val tEnv = if (themed) org.wishy.launcher.theme.rememberThemeEnv(
                loadedTheme!!, themeDark, config.themeScalePct / 100f, config.animations,
                org.wishy.launcher.theme.ThemeTweaks.from(config, ACCENTS),
            ) else null
            // Wallpaper + readability scrim.
            if (themed) {
                // wallpaperSharp is only set when the user picked a photo or a bundled wallpaper; otherwise the theme's own shows.
                org.wishy.launcher.theme.ThemedBackground(tEnv!!, wallpaperSharp)
                val themedDim = config.wallpaperDim.coerceIn(0, 5)
                if (themedDim > 0) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = themedDim * 0.1f)))
                }
            } else if (wallpaperSharp != null) {
                Image(
                    bitmap = wallpaperSharp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Medium = mipmapped bilinear: a picture a little larger than the screen is shrunk cleanly
                    // instead of the aliased, soft result of the default Low setting.
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(presetBrush))
            }
            // Dock-expanded overlay: cheap pseudo-blur (tiny bitmap upscaled)
            // + dim, crossfaded. Works identically on every Android version.
            val overlayAlpha by animateFloatAsState(
                targetValue = if (dockExpanded) 1f else 0f,
                animationSpec = tween(1000),
                label = "dockOverlay",
            )
            if (overlayAlpha > 0.01f) {
                if (wallpaperBlurred != null) {
                    Image(
                        bitmap = wallpaperBlurred,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha },
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
            when (if (themed) 4 else config.scrimMode) {
                4 -> {} // off
                3 -> Box(Modifier.fillMaxSize().background(ScrimFull))
                2 -> Box(Modifier.fillMaxSize().background(ScrimBottom))
                1 -> Box(Modifier.fillMaxSize().background(ScrimTop))
                else -> Box(Modifier.fillMaxSize().background(ScrimTopBottom))
            }

            // Launch entrance: after the boot logo, wait a beat, then the glass
            // panels slide in (status bar from the top, dock from the bottom) and
            // the icons fade in after.
            var introShown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(450); introShown = true }
            val introTop by animateFloatAsState(
                targetValue = if (introShown) 1f else 0f,
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                label = "introTop",
            )

            Column(Modifier.fillMaxSize()) {
                // The status bar sits ABOVE the content, so Up from the top row
                // reaches it — and its height IS the content's top inset (the top
                // padding is a function of the bar's height, nothing hardcoded).
                // When hidden it collapses to an invisible 4dp strip that is still
                // focusable: Up from the top row slides it back into view.
                var statusBarFocused by remember { mutableStateOf(false) }
                val statusBarHeight by animateDpAsState(
                    targetValue = if (config.showStatusBar || statusBarFocused) 80.dp else 4.dp,
                    label = "statusBarHeight",
                )
                if (!(themed && loadedTheme!!.doc.ownsStatusBar)) Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .clipToBounds()
                        .onFocusChanged { statusBarFocused = it.hasFocus }
                        .focusGroup()
                        // Entrance: slide down from the top + fade in.
                        .graphicsLayer {
                            translationY = (introTop - 1f) * size.height
                            alpha = introTop
                        }
                ) {
                    StatusBar(
                        net = net,
                        time = if (themed || !config.showClock) "" else time,
                        date = if (themed) "" else date,
                        showVpn = config.showVpnButton,
                        glass = config.statusBarGlass,
                        onVpnClick = {
                            if (config.vpnApp.isNotEmpty()) Actions.launchApp(context, config.vpnApp)
                            else Actions.openVpnSettings(context)
                        },
                        onNetworkClick = { Actions.openNetworkSettings(context) },
                        onSettingsClick = { showSettings = true },
                    )
                }
                when {
                    themed -> {
                        org.wishy.launcher.theme.ThemedHome(
                            env = tEnv!!,
                            sections = categorized,
                            flatApps = dockApps,
                            config = config,
                            time = if (config.showClock) time else "",
                            date = date,
                            onLaunch = { app -> Actions.launchApp(context, app.pkg) },
                            onMenu = { app -> menuFor = app },
                            onGridColumns = { themeCols = it },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            net = net,
                            onVpn = {
                                if (config.vpnApp.isNotEmpty()) Actions.launchApp(context, config.vpnApp)
                                else Actions.openVpnSettings(context)
                            },
                            onNetwork = { Actions.openNetworkSettings(context) },
                            onSettings = { showSettings = true },
                        )
                    }
                    else -> when (config.layout) {
                    LAYOUT_DOCK -> DockArea(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        apps = dockApps,
                        config = config,
                        accent = accent,
                        cardWidth = cardWidth,
                        gap = gap,
                        intro = introShown,
                        expanded = dockExpanded,
                        onExpandChange = { dockExpanded = it },
                        onLaunch = { app -> Actions.launchApp(context, app.pkg) },
                        onMenu = { app -> menuFor = app },
                    )
                    else -> {
                        // Magnetic vertical scroll: the focused row is pinned to a
                        // fixed pivot just below the status bar, so the row above
                        // slides off leaving ~15% of its card peeking (like the
                        // dock's row-1 peek) and the target row drops into place and
                        // snaps. Same peek at the bottom. The pivot leaves room for
                        // the section label + a 15%-card sliver above the focused row.
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val cardHeightPx = with(density) { (cardWidth * 9f / 16f).toPx() }
                        val peekPx = cardHeightPx * 0.15f
                        val labelPx = with(density) {
                            if (config.showCategoryNames) 34.dp.toPx() else 0f
                        }
                        val pivotPx = peekPx + labelPx
                        val vPivot = remember(pivotPx) {
                            object : androidx.compose.foundation.gestures.BringIntoViewSpec {
                                override fun calculateScrollDistance(
                                    offset: Float,
                                    size: Float,
                                    containerSize: Float,
                                ): Float = offset - pivotPx
                            }
                        }
                        // Rows crossing the top edge fade out (and back in on the way
                        // down) instead of being hard-clipped by an invisible wall.
                        // Kept to the peek sliver so the focused section's label,
                        // which sits just below it, stays fully readable.
                        val fadePx = peekPx
                        // Hoisted out of drawWithContent: rebuilt inside the draw
                        // lambda it re-allocated a Brush (+ shader) every frame.
                        val fadeBrush = remember(fadePx) {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadePx,
                            )
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides vPivot
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Restore the last-focused card when focus returns
                                    // (e.g. coming back down from the status bar).
                                    .focusRestorer()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                                    },
                                contentPadding = PaddingValues(
                                    top = with(density) { pivotPx.toDp() },
                                    bottom = with(density) { peekPx.toDp() } + 16.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                        items(categorized.size, key = { categorized[it].first.id }) { rowIndex ->
                            val (cat, catApps) = categorized[rowIndex]
                            val isGrid = config.layout == LAYOUT_GRID
                            // One-shot launch entrance (tied to introShown, so it never
                            // replays on scroll/navigation): carousel rows slide in from
                            // alternating sides and decelerate; the grid blooms per-icon
                            // (inside GridSection). The label fades in after either.
                            val rowEnter by animateFloatAsState(
                                targetValue = if (introShown) 1f else 0f,
                                animationSpec = tween(500, easing = LinearOutSlowInEasing),
                                label = "rowEnter",
                            )
                            val labelAlpha by animateFloatAsState(
                                targetValue = if (introShown) 1f else 0f,
                                animationSpec = tween(280, delayMillis = 320),
                                label = "labelFade",
                            )
                            val slideDir = if (rowIndex % 2 == 0) 1f else -1f
                            Column(
                                modifier = if (!isGrid) Modifier.graphicsLayer {
                                    translationX = (1f - rowEnter) * slideDir * size.width
                                    alpha = rowEnter
                                } else Modifier
                            ) {
                                if (config.showCategoryNames) {
                                    Text(
                                        // Quiet uppercase divider, not competing with
                                        // the icons below it.
                                        text = cat.name.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.6f),
                                        letterSpacing = 1.5.sp,
                                        // Both grid and carousel start their content at
                                        // the 44dp margin, so the label sits above the
                                        // first icon.
                                        modifier = Modifier
                                            .graphicsLayer { alpha = labelAlpha }
                                            .padding(start = 44.dp, bottom = 10.dp),
                                    )
                                }
                                if (isGrid) {
                                    GridSection(
                                        catApps, config, accent,
                                        cardWidth, gap, introShown,
                                        onLaunch = { app -> Actions.launchApp(context, app.pkg) },
                                        onMenu = { app -> menuFor = app },
                                    )
                                } else {
                                    CarouselSection(
                                        catApps, config, accent,
                                        cardWidth, gap,
                                        onLaunch = { app -> Actions.launchApp(context, app.pkg) },
                                        onMenu = { app -> menuFor = app },
                                    )
                                }
                            }
                        }
                            }
                        }
                    }
                }
                }
            }

            // ----- dialogs -----
            menuFor?.let { app ->
                AppContextMenu(
                    app = app,
                    isHidden = app.pkg in config.hidden,
                    onDismiss = { menuFor = null },
                    onOpen = { menuFor = null; Actions.launchApp(context, app.pkg) },
                    onToggleHide = {
                        menuFor = null
                        scope.launch {
                            store.update {
                                it.copy(
                                    hidden = if (app.pkg in it.hidden) it.hidden - app.pkg
                                    else it.hidden + app.pkg
                                )
                            }
                        }
                    },
                    onAppInfo = { menuFor = null; Actions.openAppInfo(context, app.pkg) },
                    onClose = { menuFor = null; Actions.close(context, app.pkg) },
                    onUninstall = { menuFor = null; Actions.uninstall(context, app.pkg) },
                )
            }

            if (showSettings) {
                SettingsSheet(
                    config = config,
                    apps = apps,
                    store = store,
                    onDismiss = { showSettings = false },
                    onWallpaperChanged = { wallpaperVersion++ },
                    onArtChanged = { artTick++ },
                    // Runs on LauncherApp's scope, which survives the dialog
                    // teardown — otherwise the setupDone write gets cancelled.
                    onRerunWizard = {
                        showSettings = false
                        scope.launch { store.update { it.copy(setupDone = false) } }
                    },
                )
            }
        }
    }
    }
    }
    // Fondu: the boot logo fades out over the freshly-drawn launcher.
    // Wait for a real drawn frame (not just first composition) so the fade
    // begins once the heavy first frame is actually on screen — the breathing
    // logo covers that hitch instead of fading out through it.
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos {}; withFrameNanos {}; booted = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = !booted,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.fadeOut(tween(500)),
        modifier = Modifier.fillMaxSize(),
    ) { LoadingScreen() }
    }
}

/* --------------------- carousel: fixed selection --------------------- */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CarouselSection(
    catApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    cardWidth: Dp,
    gap: Dp,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val state = rememberLazyListState()
    // Pivot scrolling done RIGHT: instead of letting the default
    // bring-into-view scroll minimally and then re-pinning by hand (two
    // fighting animations = the janky feel), we replace the scroll spec so
    // the ONLY scroll that ever happens places the focused card at the
    // fixed pivot (the 44dp start padding) in one smooth motion.
    val padPx = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    val pivotSpec = remember(padPx) {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = offset - padPx
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides pivotSpec
    ) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(catApps.size, key = { catApps[it].pkg }) { i ->
                val app = catApps[i]
                AppCard(
                    app = app,
                    isHidden = app.pkg in config.hidden,
                    accent = accent,
                    cardWidth = cardWidth,
                    showLabel = config.showAppLabels,
                    onLaunch = { onLaunch(app) },
                    onLongPress = { onMenu(app) },
                )
            }
        }
    }
}

/* ------------------- grid: everything on screen ------------------- */

/** Columns are derived from the available width, card size and spacing —
 *  a row can never overflow, so the last icon is never squeezed. */
@Composable
private fun GridSection(
    catApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    cardWidth: Dp,
    gap: Dp,
    intro: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        // fitRow only decides how MANY columns fit; the gap is then expanded so the
        // row fills the width exactly — left margin == right margin (both 44dp) for
        // any icon size / spacing, and every section lines up column-for-column.
        val (cols, _) = fitRow(maxWidth, cardWidth, gap)
        val gapUsed = if (cols > 1) (maxWidth - cardWidth * cols) / (cols - 1) else 0.dp
        val blockWidth = cardWidth * cols + gapUsed * (cols - 1)
        val rows = catApps.chunked(cols)
        // Launch entrance: icons bloom out from the section's centre — the middle
        // tiles pop first (scale + fade), outer ones stagger by distance. One-shot
        // via `intro`; sections composed later (scroll) start settled, no replay.
        val centerR = (rows.size - 1) / 2f
        val centerC = (cols - 1) / 2f
        val maxDist = kotlin.math.hypot(centerC.toDouble(), centerR.toDouble())
            .toFloat().coerceAtLeast(1f)
        Column(
            Modifier.width(blockWidth),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            rows.forEachIndexed { r, rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                    rowApps.forEachIndexed { c, app ->
                        val dist = kotlin.math.hypot((c - centerC).toDouble(), (r - centerR).toDouble()).toFloat()
                        val bloom by animateFloatAsState(
                            targetValue = if (intro) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 300,
                                delayMillis = (dist / maxDist * 240f).toInt(),
                                easing = FastOutSlowInEasing,
                            ),
                            label = "bloom",
                        )
                        AppCard(
                            app = app,
                            isHidden = app.pkg in config.hidden,
                            accent = accent,
                            cardWidth = cardWidth,
                            showLabel = config.showAppLabels,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = bloom
                                    val s = 0.8f + 0.2f * bloom
                                    scaleX = s
                                    scaleY = s
                                },
                            onLaunch = { onLaunch(app) },
                            onLongPress = { onMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

/* ----- dock: floating glass panel with one row; Down = full grid over blurred bg ----- */

@Composable
private fun DockArea(
    modifier: Modifier,
    apps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    cardWidth: Dp,
    gap: Dp,
    intro: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val dockFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    // Back closes the open grid (returns to the dock); when closed it falls
    // through to the activity's no-op, so Back never exits the home screen.
    androidx.activity.compose.BackHandler(enabled = expanded) { onExpandChange(false) }
    // Which dock column the user pressed Down from, so the grid opens focused on
    // that column of the 2nd row — pressing Down moves down, not back to icon #1.
    var expandFromCol by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded, intro) {
        if (!intro) return@LaunchedEffect // wait for the launch entrance
        // Claim focus over the first few frames (not after a delay): otherwise the
        // focus, briefly orphaned when the old row's node leaves, snaps to the first
        // focusable — the status-bar VPN icon — and flashes selected before we grab it.
        val target = if (expanded) gridFocus else dockFocus
        repeat(4) {
            withFrameNanos {}
            runCatching { target.requestFocus() }
        }
    }

    // Size + spacing define how many icons the dock holds; the SAME count
    // becomes the grid's columns when unfolded, and both states are centered
    // with the same column math — so icons never shift horizontally.
    BoxWithConstraints(modifier) {
        val panelInnerPadding = 18.dp
        val available = maxWidth - 80.dp - panelInnerPadding * 2
        val (cols, gapUsed) = fitRow(available, cardWidth, gap)
        val cardHeight = cardWidth * 9f / 16f
        val rows = apps.chunked(cols)
        // The grid opens focused on row 0, same column as the dock icon we came
        // from (row 0 is those same icons) — focus stays put, it doesn't jump down.
        val targetRow = 0
        val targetCol = expandFromCol.coerceIn(0, (rows.getOrNull(targetRow)?.size ?: 1) - 1)
        // Fixed block width shared by every row: complete rows are centered,
        // and a partial LAST row stays left-aligned with the columns above.
        val blockWidth = cardWidth * cols + gapUsed * (cols - 1)
        val peekH = cardHeight * 0.22f
        // Distance between the grid's row-0 spot (top, 24dp pad) and the dock's
        // row-0 spot (bottom): the grid slides straight out of / back into the
        // dock icons, so the same icons read as one set rising and sinking.
        val dockRisePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxHeight - peekH - cardHeight - 52.dp).toPx()
        }.toInt().coerceAtLeast(0)
        // Row spacing morphs from a wide "dock" gap down to the tight grid gap as
        // it opens (rows start far apart, like the dock, then draw together), and
        // back out on close.
        val rowGap by animateDpAsState(
            targetValue = if (expanded) gap else gap + cardHeight * 0.35f,
            animationSpec = tween(340, easing = FastOutSlowInEasing),
            label = "rowGap",
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            // Glass panel + row-0 icons, DISSOCIATED so they animate apart: the
            // frosted panel slides DOWN and fades out; the icons fade and drift
            // UP (toward the grid), while the grid rises them into position.
            Box(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                DockGlassPanel(
                    visible = intro && !expanded,
                    rowApps = rows.firstOrNull().orEmpty(),
                    config = config,
                    accent = accent,
                    dockFocus = dockFocus,
                    cardWidth = cardWidth,
                    gapUsed = gapUsed,
                    innerPadding = panelInnerPadding,
                    onExpandFrom = { i -> expandFromCol = i; onExpandChange(true) },
                    onLaunch = onLaunch,
                    onMenu = onMenu,
                )
            }
            // Teaser: the tops of the 2nd row's cards peeking at the bottom edge.
            // Fades in in SYNC with the dock icons (same delay), so the peek never
            // appears before row 0 on collapse.
            AnimatedVisibility(
                visible = intro && !expanded && rows.size > 1,
                enter = fadeIn(tween(200, delayMillis = 220)),
                exit = fadeOut(tween(200)),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                        rows.getOrNull(1).orEmpty().forEach { app ->
                            PeekStrip(app, cardWidth, cardHeight, peekH)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            // The grid slides straight out of the dock (row 0 aligned with the
            // dock icons) and sinks back into it — one continuous set of icons,
            // ease-in-out (S-curve). Fades in fast so it covers the dock row
            // cleanly instead of double-exposing.
            enter = fadeIn(tween(150)) +
                slideInVertically(tween(340, easing = FastOutSlowInEasing)) { dockRisePx },
            // Closing: sink back into the dock (S-curve) and only fade out at the
            // very end, so the grid stays solid the whole way down.
            exit = fadeOut(tween(120, delayMillis = 240)) +
                slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { dockRisePx },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Panel is gone; ALL icons over the blurred wallpaper, same column
            // count and same centering as the dock. Up from row 0 returns.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                items(rows.size, key = { rows.getOrNull(it)?.firstOrNull()?.pkg ?: "row$it" }) { r ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.width(blockWidth)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                                rows[r].forEachIndexed { c, app ->
                                    AppCard(
                                        app = app,
                                        isHidden = app.pkg in config.hidden,
                                        accent = accent,
                                        cardWidth = cardWidth,
                                        showLabel = config.showAppLabels,
                                        // No Up handler: Up from row 0 falls through to
                                        // the status bar. The grid closes with Back.
                                        modifier = when {
                                            r == targetRow && c == targetCol -> Modifier.focusRequester(gridFocus)
                                            else -> Modifier
                                        },
                                        onLaunch = { onLaunch(app) },
                                        onLongPress = { onMenu(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The dock's frosted-glass panel with its row-0 icons — DISSOCIATED so they
 * animate independently on expand: the frosted background slides DOWN and fades
 * out fast, while the icons fade and drift UP as the grid rises them into place.
 * Kept in its own (non-Column) composable so AnimatedVisibility resolves to the
 * plain overload, and so LocalCornerRadius updates the corners live.
 */
@Composable
private fun DockGlassPanel(
    visible: Boolean,
    rowApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    dockFocus: FocusRequester,
    cardWidth: Dp,
    gapUsed: Dp,
    innerPadding: Dp,
    onExpandFrom: (Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        // Frosted background — its exit is the exact mirror of its enter: rises
        // up + fades in on close, sinks down + fades out on open (same 300ms fade
        // / 500ms S-curve slide, just reversed).
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) +
                slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(300)) +
                slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCornerShape(LocalCornerRadius.current + 14.dp))
                    .background(Color(0xB3121418))
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
        // Icons — on OPEN they vanish almost instantly (the rising grid takes
        // over); on CLOSE they wait for the grid to sink back before fading in.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200, delayMillis = 220)),
            exit = fadeOut(tween(90)),
        ) {
            Row(
                Modifier.padding(innerPadding),
                horizontalArrangement = Arrangement.spacedBy(gapUsed),
            ) {
                rowApps.forEachIndexed { i, app ->
                    AppCard(
                        app = app,
                        isHidden = app.pkg in config.hidden,
                        accent = accent,
                        cardWidth = cardWidth,
                        showLabel = config.showAppLabels,
                        modifier = (when {
                            i == 0 -> Modifier.focusRequester(dockFocus)
                            else -> Modifier
                        }).onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                e.key == Key.DirectionDown
                            ) { onExpandFrom(i); true } else false
                        },
                        onLaunch = { onLaunch(app) },
                        onLongPress = { onMenu(app) },
                    )
                }
            }
        }
    }
}

/**
 * One second-row peek strip: the top slice of a card. It's its OWN composable
 * (not inline) so reading LocalCornerRadius here gives it an independent
 * recomposition scope — roundness changes update it live, exactly like AppCard.
 */
@Composable
private fun PeekStrip(app: AppEntry, cardWidth: Dp, cardHeight: Dp, peekH: Dp) {
    val corner = LocalCornerRadius.current
    Box(
        Modifier
            .width(cardWidth)
            .height(peekH)
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .background(app.tile),
    ) {
        if (app.banner != null) {
            Image(
                bitmap = app.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                // full card height, parent clips to peekH → only the top slice shows.
                modifier = Modifier.width(cardWidth).height(cardHeight),
            )
        }
    }
}

/** Startup screen: the animated night-sky splash (see Splash.kt). */
@Composable
private fun LoadingScreen() {
    WishySplash()
}

/** Decodes a tiny copy for the dock's pseudo-blur only (never shown sharp), so 16-bit colour is fine here. */
private fun decodeDownsampled(file: File, maxWidth: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}
