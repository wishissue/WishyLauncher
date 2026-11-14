package org.wishy.launcher.theme

import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wishy.theme.core.ColorScheme
import org.wishy.theme.core.Length
import org.wishy.theme.core.LoadedTheme
import org.wishy.theme.core.ThemeDoc
import java.io.File
import kotlin.math.floor
import kotlin.math.max

// The theme model reuses some names that Compose also uses. These aliases keep call sites readable.
typealias TShape = org.wishy.theme.core.Shape
typealias TShadow = org.wishy.theme.core.Shadow
typealias TTextStyle = org.wishy.theme.core.TextStyle
typealias TBorder = org.wishy.theme.core.Border
typealias TAlign = org.wishy.theme.core.TextAlign

/** Converts theme design units to pixels, dp and sp. Independent of the global UI-scale setting; [userScale] is folded into [unitPx]. */
@Immutable
class ThemeDims(val unitPx: Float, private val density: Float, private val fontScale: Float) {
    fun px(units: Float): Float = units * unitPx
    fun dp(units: Float): Dp = Dp(units * unitPx / density)
    fun sp(units: Float): TextUnit = (units * unitPx / (density * fontScale)).sp

    /** A length in px. Percentages need a bounded parent; with an unbounded one they count as 0. */
    fun lengthPx(l: Length, parentPx: Int): Int {
        if (l.isPercent) return if (parentPx in 0 until BIG) (parentPx * l.value / 100f).toInt() else 0
        return px(l.value).toInt()
    }

    fun lengthDp(l: Length, parent: Dp): Dp = if (l.isPercent) parent * (l.value / 100f) else dp(l.value)

    private companion object { const val BIG = 1 shl 24 }
}

/** Resolves colour literals and tokens such as @primary, @custom.sky and @primary/40. */
@Immutable
class ThemeColors(scheme: ColorScheme) {
    val background = parse(scheme.background)
    val surface = parse(scheme.surface)
    val onSurface = parse(scheme.onSurface)
    val primary = parse(scheme.primary)
    val onPrimary = parse(scheme.onPrimary)
    val surfaceVariant = scheme.surfaceVariant?.let(::parse) ?: surface
    val onSurfaceMuted = scheme.onSurfaceMuted?.let(::parse) ?: onSurface.copy(alpha = 0.7f)
    val accent = scheme.accent?.let(::parse) ?: primary
    val outline = scheme.outline?.let(::parse) ?: onSurface.copy(alpha = 0.3f)
    val scrim = scheme.scrim?.let(::parse) ?: Color.Black.copy(alpha = 0.5f)
    private val custom: Map<String, Color> = scheme.custom.mapValues { parse(it.value) }
    private val cache = HashMap<String, Color>()

    fun resolve(ref: String?, fallback: Color): Color {
        if (ref == null) return fallback
        cache[ref]?.let { return it }
        val c = compute(ref) ?: fallback
        cache[ref] = c
        return c
    }

    private fun compute(ref: String): Color? {
        if (ref.startsWith("#")) return parse(ref)
        if (!ref.startsWith("@")) return null
        val body = ref.substring(1)
        val name = body.substringBefore('/')
        val pct = body.substringAfter('/', "").toIntOrNull()
        val base = when (name) {
            "background" -> background
            "surface" -> surface
            "surfaceVariant" -> surfaceVariant
            "onSurface" -> onSurface
            "onSurfaceMuted" -> onSurfaceMuted
            "primary" -> primary
            "onPrimary" -> onPrimary
            "accent" -> accent
            "outline" -> outline
            "scrim" -> scrim
            else -> if (name.startsWith("custom.")) custom[name.removePrefix("custom.")] else null
        } ?: return null
        return if (pct == null) base else base.copy(alpha = base.alpha * (pct.coerceIn(0, 100) / 100f))
    }

    private fun parse(hex: String): Color =
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Transparent)
}

class ResolvedText(val style: TextStyle, val uppercase: Boolean, val maxLines: Int)

/** Everything a theme needs while drawing. Built once per theme, screen size and setting change. */
@Immutable
class ThemeEnv(
    val doc: ThemeDoc,
    val dir: File,
    val dims: ThemeDims,
    val colors: ThemeColors,
    val fonts: Map<String, FontFamily>,
    val focusMs: Int,
    val scrollMs: Int,
    val easing: Easing,
    /** False when the user turned animations down: looping effects (blink, bob, pulse) stay still. */
    val loops: Boolean,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
) {
    private val textCache = HashMap<String, ResolvedText>()

    fun <T> focusSpec(): AnimationSpec<T> = if (focusMs <= 0) snap() else tween(focusMs, easing = easing)

    fun resolveText(name: String?): ResolvedText = textCache.getOrPut(name ?: "") {
        val d = name?.let { doc.textStyles[it] } ?: TTextStyle()
        val family = d.font?.let { fonts[it] }
        val shadow = d.shadow?.let {
            Shadow(
                color = colors.resolve(it.color, Color.Black.copy(alpha = 0.5f)),
                offset = Offset(dims.px(it.dx), dims.px(it.dy)),
                blurRadius = dims.px(it.blur),
            )
        }
        val align = when (d.align) {
            TAlign.CENTER -> TextAlign.Center
            TAlign.END -> TextAlign.End
            TAlign.START -> TextAlign.Start
            null -> TextAlign.Unspecified
        }
        val style = TextStyle(
            color = colors.resolve(d.color, colors.onSurface),
            fontSize = dims.sp(d.size),
            fontFamily = family,
            letterSpacing = dims.sp(d.letterSpacing),
            lineHeight = d.lineHeight?.let { dims.sp(d.size * it) } ?: TextUnit.Unspecified,
            textAlign = align,
            shadow = shadow,
        )
        ResolvedText(style, d.uppercase, d.maxLines ?: Int.MAX_VALUE)
    }
}

val LocalThemeEnv = compositionLocalOf<ThemeEnv?> { null }

fun themeEasing(name: String): Easing = when {
    name == "linear" -> LinearEasing
    name == "decelerate" -> LinearOutSlowInEasing
    name == "accelerate" -> FastOutLinearInEasing
    name.startsWith("steps:") -> {
        val n = name.removePrefix("steps:").toIntOrNull()?.coerceIn(1, 16) ?: 1
        Easing { f -> floor(f * n) / n }
    }
    else -> FastOutSlowInEasing
}

private fun loadFonts(theme: LoadedTheme): Map<String, FontFamily> {
    val out = HashMap<String, FontFamily>()
    for ((key, def) in theme.doc.fonts) {
        runCatching {
            val tf = Typeface.createFromFile(File(theme.dir, def.file))
            out[key] = FontFamily(tf)
        }
    }
    return out
}

/**
 * [userScale] multiplies the whole theme (1 = as designed). [animations]: 0 full, 1 reduced, 2 off.
 */
@Composable
fun rememberThemeEnv(theme: LoadedTheme, dark: Boolean, userScale: Float, animations: Int): ThemeEnv {
    val density = LocalDensity.current
    val dm = LocalContext.current.resources.displayMetrics
    val fonts = remember(theme) { loadFonts(theme) }
    return remember(theme, dark, userScale, animations, density.density, density.fontScale, fonts) {
        val doc = theme.doc
        val unit = dm.heightPixels.toFloat() / doc.designHeight * userScale
        var focus = doc.motion.focusMillis
        var scroll = doc.motion.scrollMillis
        when (animations) {
            1 -> { focus /= 2; scroll /= 2 }
            2 -> { focus = 0; scroll = 0 }
        }
        ThemeEnv(
            doc = doc,
            dir = theme.dir,
            dims = ThemeDims(unit, density.density, density.fontScale),
            colors = ThemeColors(doc.schemeFor(dark)),
            fonts = fonts,
            focusMs = focus,
            scrollMs = scroll,
            easing = themeEasing(doc.motion.easing),
            loops = animations == 0,
            screenWidthPx = dm.widthPixels,
            screenHeightPx = dm.heightPixels,
        )
    }
}

/** Decodes theme images down to a sensible size and refuses anything over the 4096 px limit. */
object ThemeImages {
    fun decode(file: File, maxDim: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null
        var sample = 1
        while (max(w, h) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    }
}

/** Loads a theme image on a background thread. Null while loading or if it cannot be decoded. */
@Composable
fun rememberThemeImage(env: ThemeEnv, path: String?, maxDim: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, env.dir, path, maxDim) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { ThemeImages.decode(File(env.dir, path), maxDim) }.getOrNull()
        }
    }
    return state.value
}
