package org.wishy.launcher.theme

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.wishy.launcher.data.AppEntry
import org.wishy.theme.core.ArtSource
import org.wishy.theme.core.CardAnimation
import org.wishy.theme.core.CardState
import org.wishy.theme.core.CardStyle
import org.wishy.theme.core.Fit
import org.wishy.theme.core.LabelPosition
import org.wishy.theme.core.Sampling
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private const val LONG_PRESS_MS = 450L
private const val SUPPRESS_CLICK_MS = 1_000L

/**
 * One app tile drawn from a theme's card style.
 *
 * [width] null means "fill the width the parent gives" (grids and lists). [modifier] lands on the focusable
 * card itself, so focus requesters attach to the right node.
 */
@Composable
fun ThemedAppCard(
    app: AppEntry,
    style: CardStyle,
    env: ThemeEnv,
    width: Dp?,
    isHidden: Boolean,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    /** A passive card only draws: it takes no focus, keys or touch. The wheel layout owns those for its cards. */
    passive: Boolean = false,
    /** For a passive card: draw it as the focused one. */
    passiveFocused: Boolean = false,
) {
    var ownFocus by remember { mutableStateOf(false) }
    val focused = if (passive) passiveFocused else ownFocus
    val state: CardState = when {
        focused -> style.focused
        else -> style.idle
    }

    val spec = env.focusSpec<Float>()
    val active = focused
    // A text-only row (art "none") keeps its size: growing a menu row would look wrong.
    val textOnly = style.artwork.source == ArtSource.NONE
    val targetScale = if (active && !textOnly) state.scale else state.scale
    val targetLift = if (active && !textOnly) state.offsetY else state.offsetY
    // Kept as State objects and read inside graphicsLayer, so animating costs a redraw, not a recomposition.
    val scaleS = animateFloatAsState(targetScale, spec, label = "cardScale")
    val alphaS = animateFloatAsState(state.alpha, spec, label = "cardAlpha")
    val liftS = animateFloatAsState(targetLift, spec, label = "cardLift")

    val anim = if (active && env.loops) state.animation else CardAnimation.NONE
    val phaseS = cardPhase(anim)

    val blinkMs = state.indicator?.blinkMillis ?: 600
    val wantBlink = active && env.loops && state.indicator?.blink == true
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(wantBlink, blinkMs) {
        blinkOn = true
        if (!wantBlink) return@LaunchedEffect
        while (true) {
            delay(blinkMs / 2L)
            blinkOn = !blinkOn
        }
    }

    val cardShape = remember(style.shape, env) { env.tweakShape(style.shape) }
    val shape = remember(cardShape, env) { composeShape(cardShape, env.dims) }
    val colors = env.colors
    val fill = colors.resolve(state.fill ?: style.fill, colors.surface)
    val border = state.border ?: style.border
    val defaultIndicator = remember {
        org.wishy.theme.core.FocusIndicator(kind = org.wishy.theme.core.IndicatorKind.RING, color = "@primary", width = 4f, gap = 4f)
    }
    val effectiveIndicator = state.indicator ?: if (active && border == null) defaultIndicator else null
    val shadow = state.shadow ?: style.shadow
    val shadowColor = colors.resolve(shadow?.color, Color.Black.copy(alpha = 0.35f))
    val shadowPx = shadow?.let { env.dims.px(it.blur) } ?: 0f

    // Long-press detection for remotes: key repeats after OK is held. After a long press, the key-up must
    // not also launch the app.
    var downAt by remember { mutableLongStateOf(0L) }
    var longFired by remember { mutableStateOf(false) }
    var longAt by remember { mutableLongStateOf(0L) }

    val label = style.label
    val showLabel = if (!env.tweaks.labels) false else when (label.position) {
        LabelPosition.NONE -> false
        LabelPosition.FOCUSED_ONLY -> focused
        else -> true
    }

    val outer = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).zIndex(if (active) 1f else 0f)
    Column(outer) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(style.aspectRatio)
                .graphicsLayer {
                    val wave = loopValue(anim, phaseS.value)
                    val s = scaleS.value * (if (anim == CardAnimation.PULSE) 1f + 0.03f * wave else 1f)
                    scaleX = s
                    scaleY = s
                    this.alpha = if (anim == CardAnimation.BLINK) alphaS.value * (if (wave > 0f) 1f else 0.35f) else alphaS.value
                    translationY = env.dims.px(liftS.value) - (if (anim == CardAnimation.BOB) env.dims.px(6f) * wave else 0f)
                    shadowElevation = shadowPx
                    ambientShadowColor = shadowColor
                    spotShadowColor = shadowColor
                    this.shape = shape
                    clip = false
                }
                .themeIndicator(effectiveIndicator, env, cardShape, visible = active && blinkOn)
                .then(if (passive) Modifier else Modifier.onPreviewKeyEvent { e ->
                    val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!select) return@onPreviewKeyEvent false
                    val now = SystemClock.uptimeMillis()
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                downAt = now
                                longFired = false
                            } else if (!longFired && now - downAt >= LONG_PRESS_MS) {
                                longFired = true
                                longAt = now
                                onLongPress()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            val suppress = longFired || now - longAt < SUPPRESS_CLICK_MS && longAt != 0L
                            longFired = false
                            if (!suppress) onLaunch()
                            true
                        }
                        else -> false
                    }
                }
                    .onFocusChanged {
                        if (ownFocus != it.isFocused) {
                            ownFocus = it.isFocused
                            onFocusChange(it.isFocused)
                        }
                    }
                    .focusable()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onLaunch() }, onLongPress = { onLongPress() })
                    })
                .clip(shape)
                .background(fill)
                .themeBorder(border, env, shape),
        ) {
            CardArtwork(app, style, env, state)
            if (showLabel && label.position != LabelPosition.BELOW) {
                val overlay = label.position == LabelPosition.OVERLAY_CENTER || label.position == LabelPosition.OVERLAY_START
                val text = env.resolveText(label.style)
                Box(
                    Modifier
                        .fillMaxSize(),
                    contentAlignment = when (label.position) {
                        LabelPosition.OVERLAY_CENTER -> Alignment.Center
                        LabelPosition.OVERLAY_START -> Alignment.CenterStart
                        else -> Alignment.BottomCenter
                    },
                ) {
                    if (!overlay) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(env.dims.dp(80f))
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
                        )
                    }
                    BasicText(
                        text = if (text.uppercase) app.label.uppercase() else app.label,
                        style = text.style,
                        maxLines = minOf(text.maxLines, 2),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = env.dims.dp(if (label.position == LabelPosition.OVERLAY_START) 28f else 12f),
                            vertical = env.dims.dp(10f),
                        ),
                    )
                }
            }
            if (isHidden) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }
        if (showLabel && label.position == LabelPosition.BELOW) {
            Spacer(Modifier.height(env.dims.dp(label.gap)))
            val text = env.resolveText(label.style)
            BasicText(
                text = if (text.uppercase) app.label.uppercase() else app.label,
                style = text.style,
                maxLines = minOf(text.maxLines, 2),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val NoPhase: State<Float> = mutableFloatStateOf(0f)

/** A repeating 0..1 phase for bob, pulse and blink. Costs nothing when [kind] is none. */
@Composable
private fun cardPhase(kind: CardAnimation): State<Float> {
    if (kind == CardAnimation.NONE) return NoPhase
    val t = rememberInfiniteTransition(label = "cardLoop")
    val period = when (kind) {
        CardAnimation.BLINK -> 700
        CardAnimation.BOB -> 1000
        else -> 900
    }
    return t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
}

/** Maps the phase to -1..1 (a sine for bob and pulse, a square wave for blink). */
private fun loopValue(kind: CardAnimation, phase: Float): Float = when (kind) {
    CardAnimation.NONE -> 0f
    CardAnimation.BLINK -> if (phase < 0.5f) 1f else -1f
    else -> sin(phase * 2f * PI.toFloat())
}

@Composable
private fun CardArtwork(app: AppEntry, style: CardStyle, env: ThemeEnv, state: CardState) {
    val art = style.artwork
    // A text-only card: just the fill, the label is drawn over it by the card.
    if (art.source == ArtSource.NONE) return
    val quality = if (art.sampling == Sampling.NEAREST) FilterQuality.None else FilterQuality.Medium
    val scale = when (art.fit) {
        Fit.CROP -> SmartCrop
        Fit.FIT -> ContentScale.Fit
        Fit.STRETCH -> ContentScale.FillBounds
    }
    val filter = when {
        art.mono -> MonoFilter
        state.grayscale -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        else -> null
    }
    // The theme's own covers: a picture for this exact app wins; the generic default is the last resort.
    val themeCover = rememberThemeImage(env, if (art.themeArt) "assets/art/${app.pkg}.png" else null, 768)
    val themeDefault = rememberThemeImage(env, if (art.themeArt) "assets/art/default.png" else null, 768)
    val ownBanner = if (art.source != ArtSource.ICON) app.banner else null
    val banner = themeCover ?: ownBanner ?: themeDefault
    val bannerKey = app.pkg + app.stamp + (if (banner === themeCover) "c" else if (banner === themeDefault) "d" else "")

    if (banner != null) {
        val shown = pixelated(banner, art.pixelBlock, key = bannerKey)
        Image(
            bitmap = shown ?: banner,
            contentDescription = app.label,
            contentScale = scale,
            filterQuality = quality,
            colorFilter = filter,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // No banner (or the theme asks for icons): the app icon on a plain fill.
    val placeholder = env.colors.resolve(art.placeholderFill, app.tile)
    Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
        val icon = app.icon
        if (icon != null) {
            val size = art.iconSize
            val iconModifier = when {
                size == null -> Modifier.fillMaxSize(0.5f)
                size.isPercent -> Modifier.fillMaxSize(size.value / 100f)
                else -> Modifier.size(env.dims.dp(size.value))
            }
            val shownIcon = pixelated(icon, art.pixelBlock, key = app.pkg + app.stamp + "i") ?: icon
            Image(
                bitmap = shownIcon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = quality,
                colorFilter = filter,
                modifier = iconModifier,
            )
        } else {
            val text = env.resolveText(style.label.style)
            BasicText(
                text = app.label.take(1).uppercase(),
                style = text.style.copy(fontSize = text.style.fontSize * 1.6f),
            )
        }
    }
}

/** Brightness above half becomes white, below becomes black; transparency is kept. */
private val MonoFilter: ColorFilter = run {
    val k = 40f
    val e = 128f * (1f - k)
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.299f * k, 0.587f * k, 0.114f * k, 0f, e,
                0.299f * k, 0.587f * k, 0.114f * k, 0f, e,
                0.299f * k, 0.587f * k, 0.114f * k, 0f, e,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** For a pixel-art look: shrink the banner by [block] and let it be drawn back up without smoothing. */
@Composable
private fun pixelated(src: ImageBitmap, block: Int, key: String): ImageBitmap? {
    if (block <= 1) return null
    val state = produceState<ImageBitmap?>(initialValue = null, key, block) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bmp: Bitmap = src.asAndroidBitmap()
                val w = max(1, bmp.width / block)
                val h = max(1, bmp.height / block)
                bmp.scale(w, h).asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}
