package org.wishy.launcher.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.delay
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.CategoryCfg
import org.wishy.launcher.data.LauncherConfig
import org.wishy.theme.core.Anchor
import org.wishy.theme.core.AppsArrangement
import org.wishy.theme.core.AppsNode
import org.wishy.theme.core.BgFit
import org.wishy.theme.core.ClockNode
import org.wishy.theme.core.ColumnNode
import org.wishy.theme.core.DateNode
import org.wishy.theme.core.Fit
import org.wishy.theme.core.FocusScope
import org.wishy.theme.core.FocusedAppNameNode
import org.wishy.theme.core.Frame
import org.wishy.theme.core.ImageNode
import org.wishy.theme.core.Insets
import org.wishy.theme.core.Node
import org.wishy.theme.core.RowNode
import org.wishy.theme.core.Sampling
import org.wishy.theme.core.ScrollMode
import org.wishy.theme.core.SectionsArrangement
import org.wishy.theme.core.SectionsNode
import org.wishy.theme.core.ShapeKind
import org.wishy.theme.core.ShapeNode
import org.wishy.theme.core.ShowWhen
import org.wishy.theme.core.SpacerNode
import org.wishy.theme.core.StackNode
import org.wishy.theme.core.TextNode
import org.wishy.theme.core.UserWallpaper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val PlainRect = TShape(ShapeKind.RECT, 0f, 4f)

/**
 * State shared by every node while the home screen is drawn. Fields are Compose state, so a node that reads
 * one recomposes when it changes and no other node does.
 */
@Stable
class HomeUi {
    var sections by mutableStateOf<List<Pair<CategoryCfg, List<AppEntry>>>>(emptyList())
    var flatApps by mutableStateOf<List<AppEntry>>(emptyList())
    var config by mutableStateOf(LauncherConfig())
    var time by mutableStateOf("")
    var date by mutableStateOf("")
    var selected by mutableIntStateOf(0)
    var focusedPkg by mutableStateOf<String?>(null)
    var movePkg by mutableStateOf<String?>(null)
    var flat by mutableStateOf(true)
    var moveFocus: FocusRequester = FocusRequester()
    var onLaunch: (AppEntry) -> Unit = {}
    var onMenu: (AppEntry) -> Unit = {}
    var onGridColumns: (Int) -> Unit = {}

    val selectedIndex: Int get() = selected.coerceIn(0, max(0, sections.lastIndex))

    /** The apps the theme's app list shows right now. */
    val shown: List<AppEntry>
        get() = if (flat) flatApps else sections.getOrNull(selectedIndex)?.second ?: emptyList()
}

/** Draws the theme's wallpaper, dimming and overlay effect. Sits behind everything, including the status bar. */
@Composable
fun ThemedBackground(env: ThemeEnv, userWallpaper: ImageBitmap?, modifier: Modifier = Modifier) {
    val bg = env.doc.background
    val maxDim = max(env.screenWidthPx, env.screenHeightPx).coerceAtMost(2560)
    val themeImage = rememberThemeImage(env, bg.image, maxDim)
    val image: ImageBitmap? = when (bg.userWallpaper) {
        UserWallpaper.ALLOWED -> userWallpaper ?: themeImage
        UserWallpaper.IGNORED -> themeImage
        UserWallpaper.REQUIRED -> userWallpaper
    }
    val base = env.colors.resolve(bg.color, env.colors.background)
    val effect = remember(bg.effect, bg.effectOpacity, env) { Modifier.themeEffect(bg.effect, bg.effectOpacity, env) }
    val tileBrush = remember(image, bg.fit) {
        if (image != null && bg.fit == BgFit.TILE) ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)) else null
    }
    val quality = if (bg.sampling == Sampling.NEAREST) FilterQuality.None else FilterQuality.Medium

    Box(modifier.fillMaxSize().background(base)) {
        if (image != null) {
            if (tileBrush != null) {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(tileBrush) })
            } else {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = when (bg.fit) {
                        BgFit.FIT -> ContentScale.Fit
                        BgFit.STRETCH -> ContentScale.FillBounds
                        else -> ContentScale.Crop
                    },
                    filterQuality = quality,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (bg.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bg.dim)))
        Box(Modifier.fillMaxSize().then(effect))
    }
}

/**
 * The themed home screen: interprets the theme's node tree. The launcher keeps ownership of keys, long-press
 * menus, move mode and settings; the theme decides only how things look and where they sit.
 */
@Composable
fun ThemedHome(
    env: ThemeEnv,
    sections: List<Pair<CategoryCfg, List<AppEntry>>>,
    flatApps: List<AppEntry>,
    config: LauncherConfig,
    time: String,
    date: String,
    movePkg: String?,
    moveFocus: FocusRequester,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onGridColumns: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val doc = env.doc
    val ui = remember { HomeUi() }
    val flat = remember(doc) { !doc.hasSections || doc.appsNode.focusScope == FocusScope.ALL }
    // Written during composition, before any child reads it: children always see this frame's values.
    ui.sections = sections
    ui.flatApps = flatApps
    ui.config = config
    ui.time = time
    ui.date = date
    ui.movePkg = movePkg
    ui.flat = flat
    ui.moveFocus = moveFocus
    ui.onLaunch = onLaunch
    ui.onMenu = onMenu
    ui.onGridColumns = onGridColumns

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                // Channel and page keys switch sections, whether or not the theme draws a section switcher.
                if (e.type != KeyEventType.KeyDown || ui.movePkg != null || flat || ui.sections.size < 2) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.PageDown, Key.ChannelDown -> { ui.selected = (ui.selectedIndex + 1).coerceAtMost(ui.sections.lastIndex); true }
                    Key.PageUp, Key.ChannelUp -> { ui.selected = (ui.selectedIndex - 1).coerceAtLeast(0); true }
                    else -> false
                }
            },
    ) {
        CompositionLocalProvider(LocalThemeEnv provides env) {
            NodeView(doc.screens.home.root, env, ui, Modifier.fillMaxSize())
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Layout helpers
// ----------------------------------------------------------------------------------------------

private fun anchorX(a: Anchor): Float = when (a) {
    Anchor.TOP_START, Anchor.START, Anchor.BOTTOM_START -> 0f
    Anchor.TOP, Anchor.CENTER, Anchor.BOTTOM -> 0.5f
    Anchor.TOP_END, Anchor.END, Anchor.BOTTOM_END -> 1f
}

private fun anchorY(a: Anchor): Float = when (a) {
    Anchor.TOP_START, Anchor.TOP, Anchor.TOP_END -> 0f
    Anchor.START, Anchor.CENTER, Anchor.END -> 0.5f
    Anchor.BOTTOM_START, Anchor.BOTTOM, Anchor.BOTTOM_END -> 1f
}

/** Places a node inside a stack: size from w/h, position from x/y measured to the chosen anchor point. */
private fun Modifier.themeFrame(f: Frame, dims: ThemeDims): Modifier =
    this.zIndex(f.zIndex.toFloat()).layout { measurable, c ->
        val pw = c.maxWidth
        val ph = c.maxHeight
        val wPx = f.w?.let { dims.lengthPx(it, pw) }?.takeIf { it > 0 }
        val hPx = f.h?.let { dims.lengthPx(it, ph) }?.takeIf { it > 0 }
        val p = measurable.measure(
            Constraints(
                minWidth = wPx ?: 0, maxWidth = wPx ?: c.maxWidth,
                minHeight = hPx ?: 0, maxHeight = hPx ?: c.maxHeight,
            )
        )
        val x = f.x?.let { dims.lengthPx(it, pw) } ?: 0
        val y = f.y?.let { dims.lengthPx(it, ph) } ?: 0
        val px = x - (anchorX(f.anchor) * p.width).roundToInt()
        val py = y - (anchorY(f.anchor) * p.height).roundToInt()
        // Report the parent's full size so the stack does not shrink around a positioned child.
        val w = if (c.hasBoundedWidth) c.maxWidth else p.width
        val h = if (c.hasBoundedHeight) c.maxHeight else p.height
        layout(w, h) { p.place(px, py) }
    }

/** Only the size part of a frame, for children of rows and columns. */
private fun Modifier.themeSizeOnly(f: Frame, dims: ThemeDims): Modifier =
    this.layout { measurable, c ->
        val wPx = f.w?.let { dims.lengthPx(it, c.maxWidth) }?.takeIf { it > 0 }
        val hPx = f.h?.let { dims.lengthPx(it, c.maxHeight) }?.takeIf { it > 0 }
        val p = measurable.measure(
            Constraints(
                minWidth = wPx ?: c.minWidth, maxWidth = wPx ?: c.maxWidth,
                minHeight = hPx ?: c.minHeight, maxHeight = hPx ?: c.maxHeight,
            )
        )
        layout(p.width, p.height) { p.place(0, 0) }
    }

private fun Modifier.themePadding(ins: Insets, dims: ThemeDims): Modifier =
    this.layout { measurable, c ->
        val l = dims.lengthPx(ins.left, c.maxWidth)
        val r = dims.lengthPx(ins.right, c.maxWidth)
        val t = dims.lengthPx(ins.top, c.maxHeight)
        val b = dims.lengthPx(ins.bottom, c.maxHeight)
        val p = measurable.measure(c.offset(-(l + r), -(t + b)))
        layout(c.constrainWidth(p.width + l + r), c.constrainHeight(p.height + t + b)) { p.place(l, t) }
    }

private fun stackChildModifier(c: Node, env: ThemeEnv): Modifier {
    val f = c.frame
    if (f != null) return Modifier.themeFrame(f, env.dims)
    return when (c) {
        is StackNode, is ShapeNode, is ImageNode, is AppsNode -> Modifier.fillMaxSize()
        is SectionsNode -> Modifier.fillMaxWidth()
        else -> Modifier
    }
}

private fun flowChildModifier(c: Node, env: ThemeEnv): Modifier {
    val f = c.frame
    return if (f != null) Modifier.themeSizeOnly(f, env.dims) else Modifier
}

private fun alignV(a: org.wishy.theme.core.TextAlign): Alignment.Vertical = when (a) {
    org.wishy.theme.core.TextAlign.START -> Alignment.Top
    org.wishy.theme.core.TextAlign.CENTER -> Alignment.CenterVertically
    org.wishy.theme.core.TextAlign.END -> Alignment.Bottom
}

private fun alignH(a: org.wishy.theme.core.TextAlign): Alignment.Horizontal = when (a) {
    org.wishy.theme.core.TextAlign.START -> Alignment.Start
    org.wishy.theme.core.TextAlign.CENTER -> Alignment.CenterHorizontally
    org.wishy.theme.core.TextAlign.END -> Alignment.End
}

private fun fitToScale(f: Fit): ContentScale = when (f) {
    Fit.CROP -> ContentScale.Crop
    Fit.FIT -> ContentScale.Fit
    Fit.STRETCH -> ContentScale.FillBounds
}

// ----------------------------------------------------------------------------------------------
// Nodes
// ----------------------------------------------------------------------------------------------

@Composable
private fun NodeView(node: Node, env: ThemeEnv, ui: HomeUi, modifier: Modifier = Modifier) {
    if (!node.visible) return
    when (node.showWhen) {
        ShowWhen.CLOCK_ENABLED -> if (!ui.config.showClock) return
        ShowWhen.DATE_ENABLED -> if (ui.date.isEmpty()) return
        ShowWhen.HAS_FOCUSED_APP -> if (ui.focusedPkg == null) return
        ShowWhen.ALWAYS -> {}
    }
    val pad = node.padding
    val m = if (pad != null) modifier.themePadding(pad, env.dims) else modifier

    when (node) {
        is StackNode -> Box(m) {
            node.children.forEach { c -> NodeView(c, env, ui, stackChildModifier(c, env)) }
        }
        is RowNode -> Row(
            m,
            horizontalArrangement = Arrangement.spacedBy(env.dims.dp(node.gap)),
            verticalAlignment = alignV(node.align),
        ) {
            node.children.forEach { c ->
                val w = c.weight
                val base = if (w != null && w > 0f) Modifier.weight(w) else Modifier
                NodeView(c, env, ui, base.then(flowChildModifier(c, env)))
            }
        }
        is ColumnNode -> Column(
            m,
            verticalArrangement = Arrangement.spacedBy(env.dims.dp(node.gap)),
            horizontalAlignment = alignH(node.align),
        ) {
            node.children.forEach { c ->
                val w = c.weight
                val base = if (w != null && w > 0f) Modifier.weight(w) else Modifier
                NodeView(c, env, ui, base.then(flowChildModifier(c, env)))
            }
        }
        is ShapeNode -> {
            val shape = remember(node, env) { composeShape(node.shape, env.dims) }
            val fill = env.colors.resolve(node.fill, Color.Transparent)
            val sh = node.shadow
            val shadowColor = env.colors.resolve(sh?.color, Color.Black.copy(alpha = 0.35f))
            val shadowPx = sh?.let { env.dims.px(it.blur) } ?: 0f
            Box(
                m
                    .graphicsLayer {
                        shadowElevation = shadowPx
                        ambientShadowColor = shadowColor
                        spotShadowColor = shadowColor
                        this.shape = shape
                        clip = false
                    }
                    .themeFill(fill, shape)
                    .themeBorder(node.border, env, shape),
            )
        }
        is ImageNode -> {
            val bmp = rememberThemeImage(env, node.source, 2048)
            if (bmp != null) {
                val tint = env.colors.resolve(node.tint, Color.Unspecified)
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = fitToScale(node.fit),
                    alpha = node.alpha,
                    filterQuality = if (node.sampling == Sampling.NEAREST) FilterQuality.None else FilterQuality.Medium,
                    colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint, BlendMode.SrcIn) else null,
                    modifier = m,
                )
            }
        }
        is TextNode -> ThemedText(node.text, node.style, env, m)
        is ClockNode -> ClockText(node.showSeconds, node.style, env, ui, m)
        is DateNode -> ThemedText(ui.date, node.style, env, m)
        is FocusedAppNameNode -> {
            val pkg = ui.focusedPkg
            val name = if (pkg == null) null else ui.flatApps.firstOrNull { it.pkg == pkg }?.label
            if (name != null) ThemedText(name, node.style, env, m)
        }
        is SectionsNode -> SectionsView(node, env, ui, m)
        is AppsNode -> AppsView(node, env, ui, m)
        is SpacerNode -> {
            val size = node.size
            val dp = if (size == null || size.isPercent) Dp(0f) else env.dims.dp(size.value)
            Spacer(m.size(dp))
        }
    }
}

@Composable
private fun ThemedText(text: String, styleName: String?, env: ThemeEnv, modifier: Modifier) {
    val r = env.resolveText(styleName)
    BasicText(
        text = if (r.uppercase) text.uppercase() else text,
        style = r.style,
        maxLines = r.maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ClockText(showSeconds: Boolean, styleName: String?, env: ThemeEnv, ui: HomeUi, modifier: Modifier) {
    if (!showSeconds) {
        ThemedText(ui.time, styleName, env, modifier)
        return
    }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.getDefault()
    val h24 = ui.config.h24
    val text by produceState(initialValue = "", h24, locale) {
        val fmt = SimpleDateFormat(if (h24) "HH:mm:ss" else "h:mm:ss a", locale)
        while (true) {
            value = fmt.format(Date())
            delay(1000L - System.currentTimeMillis() % 1000L + 20L)
        }
    }
    ThemedText(text, styleName, env, modifier)
}

// ----------------------------------------------------------------------------------------------
// Sections
// ----------------------------------------------------------------------------------------------

@Composable
private fun SectionsView(n: SectionsNode, env: ThemeEnv, ui: HomeUi, modifier: Modifier) {
    val gap = env.dims.dp(n.gap)
    val sections = ui.sections
    if (n.arrangement == SectionsArrangement.LIST) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            sections.forEachIndexed { i, s -> SectionItem(i, s.first.name, n, env, ui) }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            sections.forEachIndexed { i, s -> SectionItem(i, s.first.name, n, env, ui) }
        }
    }
}

@Composable
private fun SectionItem(index: Int, name: String, n: SectionsNode, env: ThemeEnv, ui: HomeUi) {
    var focused by remember { mutableStateOf(false) }
    val selected = index == ui.selectedIndex
    val indicator = n.selectedIndicator
    val base = env.resolveText(n.style)
    val color = if (selected || focused) env.colors.onSurface else base.style.color
    Box(
        Modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) ui.selected = index
            }
            .focusable()
            .themeIndicator(if (selected) indicator else null, env, PlainRect, visible = true)
            .pointerInput(index) { detectTapGestures(onTap = { ui.selected = index }) },
    ) {
        if (n.arrangement == SectionsArrangement.DOTS) {
            val dot = env.dims.dp(14f)
            Box(Modifier.size(dot).background(if (selected || focused) env.colors.primary else env.colors.outline, CircleShape))
        } else {
            BasicText(
                text = if (base.uppercase) name.uppercase() else name,
                style = base.style.copy(color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Apps
// ----------------------------------------------------------------------------------------------

/** Two focus moves closer together than this count as a held or repeated key. */
private const val REPEAT_WINDOW_MS = 160L

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberScrollSpec(env: ThemeEnv, mode: ScrollMode): BringIntoViewSpec =
    remember(env.scrollMs, env.easing, mode) {
        object : BringIntoViewSpec {
            // Adaptive: the first move after a pause animates briefly; while keys are repeating (a new request
            // arrives before the last one could finish) the list jumps, so it never lags behind the cursor.
            private var lastRequestAt = 0L
            private var jumpNext = false

            override val scrollAnimationSpec: AnimationSpec<Float>
                get() = if (env.scrollMs <= 0 || jumpNext) snap() else tween(env.scrollMs, easing = env.easing)

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val now = android.os.SystemClock.uptimeMillis()
                jumpNext = now - lastRequestAt < REPEAT_WINDOW_MS
                lastRequestAt = now
                if (mode == ScrollMode.CENTER_FOCUSED) return offset + size / 2f - containerSize / 2f
                // Same as the default: scroll the least that brings the item fully into view.
                val trailing = offset + size
                return when {
                    offset >= 0f && trailing <= containerSize -> 0f
                    offset < 0f && trailing > containerSize -> 0f
                    abs(offset) < abs(trailing - containerSize) -> offset
                    else -> trailing - containerSize
                }
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppsView(n: AppsNode, env: ThemeEnv, ui: HomeUi, modifier: Modifier) {
    val style = env.doc.cardStyles[n.card] ?: return
    val apps = ui.shown
    val firstFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(apps.isNotEmpty()) {
        if (!focusedOnce && apps.isNotEmpty() && ui.movePkg == null) {
            focusedOnce = true
            delay(60)
            runCatching { firstFocus.requestFocus() }
        }
    }
    val scrollMode = if (n.arrangement == AppsArrangement.CROSS) ScrollMode.CENTER_FOCUSED else n.scroll
    val spec = rememberScrollSpec(env, scrollMode)
    val gap = env.dims.dp(n.gap)
    val headroom = env.dims.dp(24f)
    val sectionKey = if (ui.flat) -1 else ui.selectedIndex

    BoxWithConstraints(modifier) {
        val availW = maxWidth
        val availH = maxHeight
        val cardW: Dp = n.itemWidth?.let { env.dims.lengthDp(it, availW) }
            ?: n.itemHeight?.let { env.dims.lengthDp(it, availH) * style.aspectRatio }
            ?: env.dims.dp(300f)

        val card: @Composable (AppEntry, Int, Dp?) -> Unit = { app, index, w ->
            val moving = app.pkg == ui.movePkg
            ThemedAppCard(
                app = app,
                style = style,
                env = env,
                width = w,
                isMoving = moving,
                isHidden = app.pkg in ui.config.hidden,
                modifier = when {
                    moving -> Modifier.focusRequester(ui.moveFocus)
                    index == 0 -> Modifier.focusRequester(firstFocus)
                    else -> Modifier
                },
                onFocusChange = { f ->
                    if (f) ui.focusedPkg = app.pkg
                    else if (ui.focusedPkg == app.pkg) ui.focusedPkg = null
                },
                onLaunch = { if (ui.movePkg == null) ui.onLaunch(app) },
                onLongPress = { if (ui.movePkg == null) ui.onMenu(app) },
            )
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            key(sectionKey) {
                when (n.arrangement) {
                    AppsArrangement.RAIL, AppsArrangement.CROSS -> {
                        val state = rememberLazyListState()
                        val side = if (n.arrangement == AppsArrangement.CROSS) max(0f, (availW.value - cardW.value) / 2f).let { Dp(it) } else headroom
                        LazyRow(
                            state = state,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(horizontal = side, vertical = headroom),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app -> card(app, i, cardW) }
                        }
                    }
                    AppsArrangement.GRID -> {
                        val cols = n.columns ?: max(1, ((availW + gap) / (cardW + gap)).toInt())
                        LaunchedEffect(cols) { ui.onGridColumns(cols) }
                        val state = rememberLazyGridState()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            state = state,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(headroom),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app -> card(app, i, null) }
                        }
                    }
                    AppsArrangement.LIST -> {
                        val state = rememberLazyListState()
                        val width: Dp? = if (n.itemWidth != null) cardW else null
                        LazyColumn(
                            state = state,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(headroom),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app -> card(app, i, width) }
                        }
                    }
                }
            }
        }
    }
}
