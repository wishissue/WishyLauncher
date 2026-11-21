package org.wishy.launcher.theme

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.delay
import org.wishy.launcher.data.AppEntry
import org.wishy.launcher.data.CategoryCfg
import org.wishy.launcher.data.LauncherConfig
import org.wishy.launcher.data.NetStatus
import org.wishy.launcher.ui.AppIcons
import org.wishy.theme.core.Anchor
import org.wishy.theme.core.AppsArrangement
import org.wishy.theme.core.AppsNode
import org.wishy.theme.core.BgFit
import org.wishy.theme.core.CardStyle
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
import org.wishy.theme.core.StatusIconsNode
import org.wishy.theme.core.StatusItem
import org.wishy.theme.core.TextBind
import org.wishy.theme.core.TextNode
import org.wishy.theme.core.UserWallpaper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
    var flat by mutableStateOf(true)
    var onLaunch: (AppEntry) -> Unit = {}
    var onMenu: (AppEntry) -> Unit = {}
    var onGridColumns: (Int) -> Unit = {}
    var net by mutableStateOf(NetStatus())
    var onVpn: () -> Unit = {}
    var onNetwork: () -> Unit = {}
    var onSettings: () -> Unit = {}

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
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onGridColumns: (Int) -> Unit,
    modifier: Modifier = Modifier,
    net: NetStatus = NetStatus(),
    onVpn: () -> Unit = {},
    onNetwork: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val doc = env.doc
    val ui = remember { HomeUi() }
    val flat = remember(doc, config.themeSections) { !doc.hasSections || doc.appsNode.focusScope == FocusScope.ALL || !(config.themeSections || doc.sectionsAlways) }
    // Written during composition, before any child reads it: children always see this frame's values.
    ui.sections = sections
    ui.flatApps = flatApps
    ui.config = config
    ui.time = time
    ui.date = date
    ui.flat = flat
    ui.onLaunch = onLaunch
    ui.onMenu = onMenu
    ui.onGridColumns = onGridColumns
    ui.net = net
    ui.onVpn = onVpn
    ui.onNetwork = onNetwork
    ui.onSettings = onSettings

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                // Channel and page keys switch sections, whether or not the theme draws a section switcher.
                if (e.type != KeyEventType.KeyDown || flat || ui.sections.size < 2) return@onPreviewKeyEvent false
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
    if (node !is StatusIconsNode) {
        when (node.showWhen) {
            ShowWhen.CLOCK_ENABLED -> if (!ui.config.showClock) return
            ShowWhen.DATE_ENABLED -> if (ui.date.isEmpty()) return
            ShowWhen.HAS_FOCUSED_APP -> if (ui.focusedPkg == null) return
            ShowWhen.STATUS_BAR_ENABLED -> if (!ui.config.showStatusBar) return
            ShowWhen.HAS_SECTIONS -> if (ui.flat || ui.sections.size < 2) return
            ShowWhen.ONLINE -> if (!ui.net.connected) return
            ShowWhen.ALWAYS -> {}
        }
    } else {
        if (node.showWhen == ShowWhen.CLOCK_ENABLED && !ui.config.showClock) return
        if (node.showWhen == ShowWhen.DATE_ENABLED && ui.date.isEmpty()) return
        if (node.showWhen == ShowWhen.HAS_FOCUSED_APP && ui.focusedPkg == null) return
        if (node.showWhen == ShowWhen.HAS_SECTIONS && (ui.flat || ui.sections.size < 2)) return
        if (node.showWhen == ShowWhen.ONLINE && !ui.net.connected) return
    }
    val pad = node.padding
    val m = if (pad != null) modifier.themePadding(pad, env.dims) else modifier

    when (node) {
        is StackNode -> Box(m) {
            node.children.forEach { c -> NodeView(c, env, ui, stackChildModifier(c, env)) }
        }
        is RowNode -> Row(
            m,
            horizontalArrangement = Arrangement.spacedBy(env.dims.dp(node.gap * env.tweaks.gapScale)),
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
            verticalArrangement = Arrangement.spacedBy(env.dims.dp(node.gap * env.tweaks.gapScale)),
            horizontalAlignment = alignH(node.align),
        ) {
            node.children.forEach { c ->
                val w = c.weight
                val base = if (w != null && w > 0f) Modifier.weight(w) else Modifier
                NodeView(c, env, ui, base.then(flowChildModifier(c, env)))
            }
        }
        is ShapeNode -> {
            val shape = remember(node, env) { composeShape(env.tweakShape(node.shape), env.dims) }
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
        is TextNode -> ThemedText(boundText(node, ui), node.style, env, m)
        is StatusIconsNode -> StatusIconsView(node, env, ui, m)
        is ClockNode -> ClockText(node.showSeconds, node.style, env, ui, m)
        is DateNode -> ThemedText(ui.date, node.style, env, m)
        is FocusedAppNameNode -> {
            val pkg = ui.focusedPkg
            val name = if (pkg == null) null else ui.flatApps.firstOrNull { it.pkg == pkg }?.label
            if (name != null) ThemedText(name, node.style, env, m)
        }
        is SectionsNode -> if (!ui.flat) SectionsView(node, env, ui, m)
        is AppsNode -> AppsView(node, env, ui, m)
        is SpacerNode -> {
            val size = node.size
            val dp = if (size == null || size.isPercent) Dp(0f) else env.dims.dp(size.value)
            Spacer(m.size(dp))
        }
    }
}

/** The text a text node shows: its literal text, or one fixed piece of live data. */
private fun boundText(n: TextNode, ui: HomeUi): String = when (n.bind) {
    TextBind.NONE -> n.text
    TextBind.SECTION_NAME -> if (ui.flat) "" else ui.sections.getOrNull(ui.selectedIndex)?.first?.name.orEmpty()
    TextBind.APP_COUNT -> ui.shown.size.toString()
    TextBind.FOCUSED_PACKAGE -> ui.focusedPkg.orEmpty()
    TextBind.FOCUSED_POSITION -> {
        val i = ui.shown.indexOfFirst { it.pkg == ui.focusedPkg }
        if (i < 0) "" else "${i + 1} / ${ui.shown.size}"
    }
    TextBind.NETWORK_NAME -> when {
        ui.net.ethernet -> "Ethernet"
        ui.net.wifi -> "Wi-Fi"
        else -> "Offline"
    }
}

/** The VPN, network and settings buttons, drawn the way the theme says. What they do stays with the launcher. */
@Composable
private fun StatusIconsView(n: StatusIconsNode, env: ThemeEnv, ui: HomeUi, modifier: Modifier) {
    val color = env.colors.resolve(n.color, env.colors.onSurface)
    val dim = env.colors.resolve(n.inactiveColor, color.copy(alpha = 0.4f))
    val fill = env.colors.resolve(n.focusFill, color.copy(alpha = 0.2f))
    val shape = remember(n.shape, env) { composeShape(env.tweakShape(n.shape), env.dims) }
    val size = env.dims.dp(n.iconSize)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(env.dims.dp(n.gap * env.tweaks.gapScale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items = if (n.items.contains(StatusItem.SETTINGS)) n.items else n.items + StatusItem.SETTINGS
        items.forEach { item ->
            when (item) {
                StatusItem.VPN -> if (ui.config.showVpnButton) {
                    StatusIconButton(AppIcons.Vpn, "VPN", ui.net.vpn, color, dim, fill, shape, size) { ui.onVpn() }
                }
                StatusItem.NETWORK -> if (ui.config.showStatusBar) {
                    val icon = when {
                        ui.net.ethernet -> AppIcons.Ethernet
                        ui.net.wifi -> AppIcons.Wifi
                        else -> AppIcons.WifiOff
                    }
                    StatusIconButton(icon, "Network", ui.net.connected, color, dim, fill, shape, size) { ui.onNetwork() }
                }
                StatusItem.SETTINGS -> StatusIconButton(AppIcons.Gear, "Settings", true, color, dim, fill, shape, size) { ui.onSettings() }
            }
        }
    }
}

private fun colorLuminance(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

@Composable
private fun StatusIconButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    color: Color,
    dim: Color,
    fill: Color,
    shape: androidx.compose.ui.graphics.Shape,
    size: Dp,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val baseColor = if (active) color else dim
    val lowContrast = abs(colorLuminance(baseColor) - colorLuminance(fill)) < 0.35f
    val iconColor = if (focused && lowContrast) Color(1f - baseColor.red, 1f - baseColor.green, 1f - baseColor.blue, baseColor.alpha) else baseColor
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) fill else Color.Transparent)
            .onPreviewKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (select && e.type == KeyEventType.KeyUp) { onClick(); true } else select
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(size * 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = icon,
            contentDescription = description,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(size),
        )
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
    val gap = env.dims.dp(n.gap * env.tweaks.gapScale)
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
    val scaleS by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.08f else 1f, env.focusSpec(), label = "secScale")
    val textColor = when {
        focused -> env.colors.primary
        selected -> env.colors.onSurface
        else -> base.style.color.copy(alpha = 0.7f)
    }
    val pillShape = remember(env) { org.wishy.launcher.ui.SmoothCornerShape(env.dims.dp(10f)) }

    Box(
        Modifier
            .graphicsLayer {
                scaleX = scaleS
                scaleY = scaleS
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) ui.selected = index
            }
            .focusable()
            .clip(pillShape)
            .background(
                when {
                    focused -> env.colors.primary.copy(alpha = 0.25f)
                    selected -> env.colors.surfaceVariant.copy(alpha = 0.4f)
                    else -> Color.Transparent
                }
            )
            .themeBorder(
                if (focused) TBorder(color = "@primary", width = 2f) else null,
                env,
                pillShape
            )
            .padding(horizontal = env.dims.dp(14f), vertical = env.dims.dp(6f))
            .themeIndicator(if (selected) indicator else null, env, PlainRect, visible = true)
            .pointerInput(index) { detectTapGestures(onTap = { ui.selected = index }) },
    ) {
        if (n.arrangement == SectionsArrangement.DOTS) {
            val dot = env.dims.dp(14f)
            Box(Modifier.size(dot).background(if (selected || focused) env.colors.primary else env.colors.outline, CircleShape))
        } else {
            BasicText(
                text = if (base.uppercase) name.uppercase() else name,
                style = base.style.copy(
                    color = textColor,
                    fontWeight = if (focused || selected) androidx.compose.ui.text.font.FontWeight.Bold else base.style.fontWeight
                ),
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
private fun rememberScrollSpec(env: ThemeEnv, mode: ScrollMode, marginPx: Float = 0f): BringIntoViewSpec =
    remember(env.scrollMs, env.easing, mode, marginPx) {
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
                // Scroll the least that brings the item fully into view, keeping [marginPx] free on both sides. The
                // focused card is drawn bigger than its slot (scale, focus ring), so without the margin the
                // selected card at the edge of the list had its outer side cropped by the list's bounds.
                val trailing = offset + size
                val lo = marginPx
                val hi = containerSize - marginPx
                return when {
                    size + 2f * marginPx >= containerSize -> offset - lo // too big to inset: line up its start
                    offset >= lo && trailing <= hi -> 0f
                    offset < lo -> offset - lo
                    else -> trailing - hi
                }
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppsView(n: AppsNode, env: ThemeEnv, ui: HomeUi, modifier: Modifier) {
    val style = env.doc.cardStyles[n.card] ?: return
    if (n.arrangement == AppsArrangement.WHEEL) {
        WheelView(n, style, env, ui, modifier)
        return
    }
    val apps = ui.shown
    val focusRequesters = remember(apps) { List(apps.size) { FocusRequester() } }
    val firstFocus = remember(focusRequesters) { focusRequesters.firstOrNull() ?: FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(apps.isNotEmpty()) {
        if (!focusedOnce && apps.isNotEmpty()) {
            focusedOnce = true
            delay(60)
            runCatching { firstFocus.requestFocus() }
        }
    }
    val scrollMode = if (n.arrangement == AppsArrangement.CROSS) ScrollMode.CENTER_FOCUSED else n.scroll
    val gap = env.dims.dp(n.gap * env.tweaks.gapScale)
    val baseHeadroom = env.dims.dp(24f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sectionKey = if (ui.flat) -1 else ui.selectedIndex
    val scope = rememberCoroutineScope()
    var railState by remember { mutableStateOf<androidx.compose.foundation.lazy.LazyListState?>(null) }
    // Loop to the other end: the target may not be composed yet, so scroll to it first, then retry focusing for a few frames.
    val jumpTo: (Int) -> Unit = { target ->
        scope.launch {
            runCatching { railState?.scrollToItem(target) }
            repeat(8) {
                if (runCatching { focusRequesters[target].requestFocus() }.isSuccess) return@launch
                androidx.compose.runtime.withFrameNanos { }
            }
        }
    }
    val wrapEnabled = n.wrap || n.arrangement == AppsArrangement.RAIL || n.arrangement == AppsArrangement.CROSS

    BoxWithConstraints(modifier) {
        val availW = maxWidth
        val availH = maxHeight
        val cardW0: Dp = n.itemWidth?.let { env.dims.lengthDp(it, availW) }
            ?: n.itemHeight?.let { env.dims.lengthDp(it, availH) * style.aspectRatio }
            ?: env.dims.dp(300f)
        val cardW: Dp = cardW0 * env.tweaks.size

        // How far the selected card reaches beyond its slot: the extra size from scaling plus the focus
        // ring and its gap. The list keeps this much room around the selected card so it is never cut off.
        val maxScale = maxOf(style.focused.scale, 1f)
        val ringReach = style.focused.indicator?.let { it.width + it.gap } ?: 8f
        val overhang: Dp = cardW * ((maxScale - 1f) / 2f) + env.dims.dp(ringReach) + 4.dp
        val headroom: Dp = maxOf(baseHeadroom, overhang)
        val spec = rememberScrollSpec(env, scrollMode, with(density) { overhang.toPx() })

        val card: @Composable (AppEntry, Int, Dp?, CardStyle) -> Unit = { app, index, w, cardStyle ->
            val itemFocus = focusRequesters.getOrElse(index) { firstFocus }
            ThemedAppCard(
                app = app,
                style = cardStyle,
                env = env,
                width = w,
                isHidden = app.pkg in ui.config.hidden,
                modifier = Modifier.focusRequester(itemFocus)
                    .onPreviewKeyEvent { e ->
                        if (!wrapEnabled || apps.size <= 1) return@onPreviewKeyEvent false
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionRight -> {
                                if (index == apps.lastIndex) {
                                    jumpTo(0)
                                    true
                                } else false
                            }
                            Key.DirectionLeft -> {
                                if (index == 0) {
                                    jumpTo(apps.lastIndex)
                                    true
                                } else false
                            }
                            else -> false
                        }
                    },
                onFocusChange = { f ->
                    if (f) ui.focusedPkg = app.pkg
                    else if (ui.focusedPkg == app.pkg) ui.focusedPkg = null
                },
                onLaunch = { ui.onLaunch(app) },
                onLongPress = { ui.onMenu(app) },
            )
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            key(sectionKey) {
                when (n.arrangement) {
                    AppsArrangement.RAIL, AppsArrangement.CROSS -> {
                        val state = rememberLazyListState()
                        SideEffect { railState = state }
                        val side = if (n.arrangement == AppsArrangement.CROSS) max(0f, (availW.value - cardW.value) / 2f).let { Dp(it) } else headroom
                        LazyRow(
                            state = state,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(horizontal = side, vertical = headroom),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app -> card(app, i, cardW, style) }
                        }
                    }
                    AppsArrangement.WHEEL -> {}
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
                            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app -> card(app, i, null, style) }
                        }
                    }
                    AppsArrangement.BENTO -> {
                        val cols = (n.columns ?: 5).coerceIn(3, 8)
                        LaunchedEffect(cols) { ui.onGridColumns(cols) }
                        val bigStyle = n.bigCard?.let { env.doc.cardStyles[it] } ?: style
                        // Square-ish cells. A wide tile spans two of them and is exactly as tall as one, so its
                        // aspect is worked out from the real cell size.
                        val cellW = (availW - headroom * 2 - gap * (cols - 1)) / cols
                        val cellH = cellW / style.aspectRatio
                        val wideW = cellW * 2 + gap
                        val wideStyle = remember(bigStyle, cellW, cellH) {
                            bigStyle.copy(aspect = "${wideW.value}:${cellH.value}")
                        }
                        // Wide tiles sit first and last in every group of 2*cols-2 apps, which fills two whole rows.
                        val group = 2 * cols - 2
                        val isWide = { i: Int -> i % group == 0 || i % group == group - 1 }
                        val state = rememberLazyGridState()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            state = state,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(headroom),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            itemsIndexed(
                                apps,
                                key = { _, a -> a.pkg },
                                span = { i, _ -> GridItemSpan(if (isWide(i)) 2 else 1) },
                            ) { i, app -> card(app, i, null, if (isWide(i)) wideStyle else style) }
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
                            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app -> card(app, i, width, style) }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Wheel
// ----------------------------------------------------------------------------------------------

private const val WHEEL_LONG_PRESS_MS = 450L
/** Radius of the ring as a share of the node's height. Themes draw their ring with this number. */
private const val WHEEL_RADIUS = 0.62f
/** Angle between two neighbouring apps, in radians. */
private const val WHEEL_STEP = 0.37f
/** Apps drawn above and below the middle one. */
private const val WHEEL_HALF = 3
/** Design units between the node's left edge and the left edge of the unscaled middle card. */
private const val WHEEL_INSET = 40f

private fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b

/**
 * A ring turning on the left of the node. Only the app in the middle is selected; up and down turn the ring so the
 * next app arrives in the middle. The ring has no end: past the last app comes the first. The launcher keeps
 * ownership of launching, the long-press menu and move mode; this only decides where the cards are drawn.
 *
 * Geometry (design units, so a theme can draw a matching ring with shape nodes): the ring's radius is
 * [WHEEL_RADIUS] times the node's height, its centre is that radius to the left of the middle card, and the middle
 * card sits level with the node's vertical centre, [WHEEL_INSET] plus 0.56 card widths from the node's left edge.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WheelView(n: AppsNode, style: CardStyle, env: ThemeEnv, ui: HomeUi, modifier: Modifier) {
    val apps = ui.shown
    val size = apps.size
    key(if (ui.flat) -1 else ui.selectedIndex) {
        var sel by remember { mutableIntStateOf(0) }
        val pos = remember { Animatable(0f) }
        val fast = remember { BooleanArray(1) }
        var lastMoveAt by remember { mutableLongStateOf(0L) }
        var hasFocus by remember { mutableStateOf(false) }
        val wheelFocus = remember { FocusRequester() }
        var focusedOnce by remember { mutableStateOf(false) }
        var downAt by remember { mutableLongStateOf(0L) }
        var longFired by remember { mutableStateOf(false) }
        var longAt by remember { mutableLongStateOf(0L) }

        val current: AppEntry? = if (size == 0) null else apps[floorMod(sel, size)]

        fun step(delta: Int) {
            val now = android.os.SystemClock.uptimeMillis()
            // Keys repeating (held) turn the ring faster, so it never trails behind the finger.
            fast[0] = now - lastMoveAt < REPEAT_WINDOW_MS
            lastMoveAt = now
            sel += delta
        }

        LaunchedEffect(sel) {
            val target = sel.toFloat()
            if (env.scrollMs <= 0) pos.snapTo(target)
            else pos.animateTo(target, tween(if (fast[0]) minOf(env.scrollMs, 50) else minOf(env.scrollMs, 110), easing = env.easing))
        }
        LaunchedEffect(size > 0) {
            if (!focusedOnce && size > 0) {
                focusedOnce = true
                delay(60)
                runCatching { wheelFocus.requestFocus() }
            }
        }
        LaunchedEffect(hasFocus, current?.pkg) { ui.focusedPkg = if (hasFocus) current?.pkg else null }
        DisposableEffect(Unit) { onDispose { ui.focusedPkg = null } }

        BoxWithConstraints(
            modifier
                .focusRequester(wheelFocus)
                .onFocusChanged { hasFocus = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (size == 0) return@onPreviewKeyEvent false
                    val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    when {
                        e.key == Key.DirectionUp -> { if (e.type == KeyEventType.KeyDown) step(-1); true }
                        e.key == Key.DirectionDown -> { if (e.type == KeyEventType.KeyDown) step(1); true }
                        select -> {
                            val now = android.os.SystemClock.uptimeMillis()
                            when (e.type) {
                                KeyEventType.KeyDown -> {
                                    if (e.nativeKeyEvent.repeatCount == 0) {
                                        downAt = now
                                        longFired = false
                                    } else if (!longFired && now - downAt >= WHEEL_LONG_PRESS_MS) {
                                        longFired = true
                                        longAt = now
                                        current?.let { ui.onMenu(it) }
                                    }
                                    true
                                }
                                KeyEventType.KeyUp -> {
                                    val suppress = longFired || (now - longAt < 1_000L && longAt != 0L)
                                    longFired = false
                                    if (!suppress) current?.let { ui.onLaunch(it) }
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            if (size == 0) return@BoxWithConstraints
            val density = LocalDensity.current
            val cardW: Dp = (n.itemWidth?.let { env.dims.lengthDp(it, maxWidth) } ?: env.dims.dp(240f)) * env.tweaks.size
            val cardWpx = with(density) { cardW.toPx() }
            val cardHpx = cardWpx / style.aspectRatio
            val hPx = constraints.maxHeight.toFloat()
            val radius = hPx * WHEEL_RADIUS
            val x0 = env.dims.px(WHEEL_INSET) + cardWpx * 0.56f
            val base by remember { derivedStateOf { pos.value.roundToInt() } }
            val half = minOf(WHEEL_HALF, (size - 1) / 2)

            // The band the cards ride on, centred one radius to the left of the middle card.
            val ringColor = env.colors.resolve(n.ringColor, Color.Transparent)
            val edgeColor = env.colors.resolve(n.ringEdgeColor, Color.Transparent)
            val ringPx = env.dims.px(n.ringWidth)
            if (ringPx > 0f) {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val c = Offset(x0 - radius, hPx / 2f)
                        drawCircle(ringColor, radius, c, style = Stroke(ringPx))
                        val edge = env.dims.px(2f).coerceAtLeast(1f)
                        drawCircle(edgeColor, radius - ringPx / 2f, c, style = Stroke(edge))
                        drawCircle(edgeColor, radius + ringPx / 2f, c, style = Stroke(edge))
                    },
                )
            }

            // Touch: drag up or down to turn the ring.
            Box(
                Modifier.fillMaxSize().pointerInput(cardHpx) {
                    var acc = 0f
                    val stepPx = cardHpx * 0.5f
                    detectVerticalDragGestures(onDragStart = { acc = 0f }) { change, dy ->
                        change.consume()
                        acc += dy
                        while (acc <= -stepPx) { step(1); acc += stepPx }
                        while (acc >= stepPx) { step(-1); acc -= stepPx }
                    }
                },
            )
            for (k in -half..half) {
                val v = base + k
                key(v) {
                    val app = apps[floorMod(v, size)]
                    Box(
                        Modifier
                            .zIndex(-abs(k).toFloat())
                            .offset {
                                val t = (v - pos.value) * WHEEL_STEP
                                IntOffset(
                                    (x0 - radius * (1f - cos(t)) - cardWpx / 2f).roundToInt(),
                                    (hPx / 2f + radius * sin(t) - cardHpx / 2f).roundToInt(),
                                )
                            }
                            .graphicsLayer {
                                val d = abs(v - pos.value)
                                val s = 1f - 0.32f * minOf(d, 1f) - 0.1f * (minOf(d, 2f) - 1f).coerceAtLeast(0f) - 0.05f * (d - 2f).coerceAtLeast(0f)
                                scaleX = s
                                scaleY = s
                                alpha = (1f - 0.36f * d).coerceIn(0f, 1f)
                            }
                            .pointerInput(v) {
                                detectTapGestures(
                                    onTap = { if (v == sel) ui.onLaunch(app) else step(v - sel) },
                                    onLongPress = { if (v == sel) ui.onMenu(app) },
                                )
                            },
                    ) {
                        ThemedAppCard(
                            app = app,
                            style = style,
                            env = env,
                            width = cardW,
                            isHidden = app.pkg in ui.config.hidden,
                            passive = true,
                            passiveFocused = v == sel,
                            onFocusChange = {},
                            onLaunch = {},
                            onLongPress = {},
                        )
                    }
                }
            }
        }
    }
}
