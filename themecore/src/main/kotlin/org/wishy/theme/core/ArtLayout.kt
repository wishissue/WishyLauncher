package org.wishy.theme.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** The banner canvas. Everything is drawn 16:9 at 1280 x 720; smaller bitmaps are made from it for display. */
object ArtCanvas {
    const val W = 1280
    const val H = 720
    const val PREVIEW_W = 640
    const val PREVIEW_H = 360
}

data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

@Serializable
enum class FitMode { FIT, COVER }

/**
 * Where a logo goes on the canvas. Pure maths, no Android classes, so it is unit tested.
 *
 * FIT: the logo is scaled to fit inside the canvas with margins, keeps its aspect ratio, and never leaves the canvas.
 *      Squarish logos (icons) are also capped to 60 percent of the height so they do not shout.
 * COVER: the logo is scaled until it covers the whole canvas (the usual choice for a photo), keeps its aspect ratio,
 *      and can only be panned so that it still covers the canvas.
 * [scale] multiplies the automatic size (FIT: 0.3 to the largest size that still fits; COVER: 1 or more).
 * [dx], [dy] move the centre by that fraction of the canvas, and are limited so the rules above still hold.
 */
object BannerLayout {
    const val MIN_SCALE = 0.3f
    const val MAX_SCALE = 1.6f
    private const val PAD_X = 0.12f
    private const val PAD_Y = 0.14f
    private const val SQUARE_MAX_HEIGHT = 0.6f

    fun logoRect(cw: Float, ch: Float, aspect: Float, mode: FitMode, scale: Float = 1f, dx: Float = 0f, dy: Float = 0f): FRect {
        val a = aspect.coerceIn(0.05f, 20f)
        var w: Float
        var h: Float
        if (mode == FitMode.COVER) {
            if (cw / ch > a) { w = cw; h = cw / a } else { h = ch; w = ch * a }
            val s = scale.coerceIn(1f, MAX_SCALE)
            w *= s; h *= s
        } else {
            val bw = cw * (1 - 2 * PAD_X)
            val bh = ch * (1 - 2 * PAD_Y)
            if (bw / bh > a) { h = bh; w = h * a } else { w = bw; h = w / a }
            if (a < 1.6f && h > ch * SQUARE_MAX_HEIGHT) { h = ch * SQUARE_MAX_HEIGHT; w = h * a }
            val maxScale = min(cw / w, ch / h)
            val s = scale.coerceIn(MIN_SCALE, max(MIN_SCALE, min(MAX_SCALE, maxScale)))
            w *= s; h *= s
        }
        val cx = cw / 2 + dx.coerceIn(-0.5f, 0.5f) * cw
        val cy = ch / 2 + dy.coerceIn(-0.5f, 0.5f) * ch
        val left = clampAxis(cx - w / 2, w, cw)
        val top = clampAxis(cy - h / 2, h, ch)
        return FRect(left, top, left + w, top + h)
    }

    /** Size of the largest logo-scale that still fits (used by the editor to stop the "bigger" button). */
    fun maxFitScale(cw: Float, ch: Float, aspect: Float): Float {
        val r = logoRect(cw, ch, aspect, FitMode.FIT, 1f)
        return min(MAX_SCALE, min(cw / r.width, ch / r.height))
    }

    private fun clampAxis(start: Float, size: Float, canvas: Float): Float =
        if (size >= canvas) start.coerceIn(canvas - size, 0f) else start.coerceIn(0f, canvas - size)

    /** Start and end points (x0, y0, x1, y1) of a linear gradient across the canvas at [angleDeg] (0 = left to right, 90 = top to bottom). */
    fun gradientPoints(w: Float, h: Float, angleDeg: Int): FloatArray {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f; val cy = h / 2f
        return floatArrayOf(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    }
}

/** Colour helpers on 0xAARRGGBB integers. */
object ArtColor {
    private val HEX = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    /** Accepts "#RGB", "#RRGGBB" with or without the "#". Returns an opaque ARGB int, or null. */
    fun parseHex(s: String?): Int? {
        val m = HEX.matchEntire(s?.trim() ?: return null) ?: return null
        var h = m.groupValues[1]
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        return (0xFF000000L or h.toLong(16)).toInt()
    }

    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    private fun lin(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return (0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)).toFloat()
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF; val y = (b shr shift) and 0xFF
            return (x + (y - x) * k).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun darken(argb: Int, t: Float) = mix(argb, 0xFF000000.toInt(), t)
    fun lighten(argb: Int, t: Float) = mix(argb, 0xFFFFFFFF.toInt(), t)

    /** White or near-black, whichever reads better on [bg]. */
    fun contrastOn(bg: Int): Int {
        val l = luminance(bg)
        return if (1.05f / (l + 0.05f) >= (l + 0.05f) / 0.05f) 0xFFFFFFFF.toInt() else 0xFF111827.toInt()
    }

    /**
     * A representative colour of a picture: the average of its mostly-opaque pixels, counting colourful pixels more
     * so a white background does not wash out a coloured icon. Null when there is nothing opaque.
     */
    fun dominant(pixels: IntArray): Int? {
        var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 128) continue
            val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
            val mx = max(pr, max(pg, pb)); val mn = min(pr, min(pg, pb))
            val sat = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            val wgt = sat + 0.05
            r += pr * wgt; g += pg * wgt; b += pb * wgt; total += wgt
        }
        if (total <= 0.0) return null
        return (0xFF shl 24) or ((r / total).toInt() shl 16) or ((g / total).toInt() shl 8) or (b / total).toInt()
    }
}

@Serializable
enum class BgKind { SOLID, GRADIENT, IMAGE, TRANSPARENT }

/** [a] and [b] are "#RRGGBB". [angle] is for gradients. [image] names a stored background picture. */
@Serializable
data class BackgroundSpec(
    val kind: BgKind = BgKind.SOLID,
    val a: String = "#1F2937",
    val b: String = "#111827",
    val angle: Int = 90,
    val image: String? = null,
)

/** A faint pattern drawn between the background and the logo. */
@Serializable
enum class DecorKind { NONE, CIRCLES, RINGS, STRIPES, DOTS, GLOW }

/** Everything needed to draw one banner again. Stored next to the picture so a banner can be edited later. */
@Serializable
data class BannerSpec(
    val background: BackgroundSpec = BackgroundSpec(),
    val mode: FitMode = FitMode.FIT,
    val scale: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    /** "#RRGGBB" to recolour a single-colour logo, or null to draw it as it is. */
    val tint: String? = null,
    /** A faint pattern behind the logo. [decorSeed] fixes where its pieces go so the banner redraws the same way. */
    val decor: DecorKind = DecorKind.NONE,
    val decorSeed: Int = 0,
)

data class DesignPreset(val id: String, val name: String, val background: BackgroundSpec, val tint: String?)

object ArtPresets {
    val all: List<DesignPreset> = listOf(
        DesignPreset("midnight", "Midnight", BackgroundSpec(BgKind.GRADIENT, "#1E293B", "#0F172A", 90), "#FFFFFF"),
        DesignPreset("ocean", "Ocean", BackgroundSpec(BgKind.GRADIENT, "#0EA5E9", "#1D4ED8", 135), "#FFFFFF"),
        DesignPreset("sunset", "Sunset", BackgroundSpec(BgKind.GRADIENT, "#F97316", "#BE185D", 135), "#FFFFFF"),
        DesignPreset("forest", "Forest", BackgroundSpec(BgKind.GRADIENT, "#16A34A", "#064E3B", 135), "#FFFFFF"),
        DesignPreset("violet", "Violet", BackgroundSpec(BgKind.GRADIENT, "#7C3AED", "#2563EB", 135), "#FFFFFF"),
        DesignPreset("graphite", "Graphite", BackgroundSpec(BgKind.SOLID, "#374151", "#374151", 90), "#FFFFFF"),
        DesignPreset("snow", "Snow", BackgroundSpec(BgKind.GRADIENT, "#F8FAFC", "#CBD5E1", 90), "#111827"),
        DesignPreset("sand", "Sand", BackgroundSpec(BgKind.GRADIENT, "#FDE68A", "#F59E0B", 135), "#111827"),
    )

    /** Quick solid colours for the Background step. */
    val solidColors: List<String> = listOf(
        "#111827", "#374151", "#6B7280", "#F8FAFC", "#DC2626", "#EA580C",
        "#CA8A04", "#16A34A", "#0D9488", "#2563EB", "#7C3AED", "#DB2777",
    )

    val gradients: List<BackgroundSpec> = all.filter { it.background.kind == BgKind.GRADIENT }.map { it.background }

    fun byId(id: String): DesignPreset? = all.firstOrNull { it.id == id }
}

/** Picks a good-looking banner design without asking anything. Deterministic: the same inputs give the same banner. */
object AutoDesign {
    private fun presetFor(seed: String): DesignPreset {
        // Dark-ish presets first so an unknown app gets a calm banner; light ones stay available by hand.
        val calm = ArtPresets.all.filter { it.id in setOf("midnight", "ocean", "violet", "forest", "graphite", "sunset") }
        return calm[abs(seed.hashCode() % calm.size)]
    }

    /**
     * @param mono the logo is a single colour and can be recoloured
     * @param brandHex the owner's brand colour when a source supplied one
     * @param dominantArgb the main colour of a coloured logo or app icon
     */
    fun forLogo(seed: String, mono: Boolean, brandHex: String?, dominantArgb: Int?): BannerSpec {
        val brand = ArtColor.parseHex(brandHex)
        if (mono && brand != null) {
            val a = ArtColor.lighten(brand, 0.06f)
            val b = ArtColor.darken(brand, 0.28f)
            return BannerSpec(
                background = BackgroundSpec(BgKind.GRADIENT, ArtColor.toHex(a), ArtColor.toHex(b), 135),
                tint = ArtColor.toHex(ArtColor.contrastOn(brand)),
            )
        }
        if (mono) {
            val p = presetFor(seed)
            return BannerSpec(background = p.background, tint = p.tint)
        }
        if (dominantArgb != null) {
            val l = ArtColor.luminance(dominantArgb)
            return if (l < 0.02f) {
                BannerSpec(background = BackgroundSpec(BgKind.GRADIENT, "#F1F5F9", "#CBD5E1", 90))
            } else {
                val a = ArtColor.darken(dominantArgb, 0.62f)
                val b = ArtColor.darken(dominantArgb, 0.82f)
                BannerSpec(background = BackgroundSpec(BgKind.GRADIENT, ArtColor.toHex(a), ArtColor.toHex(b), 135))
            }
        }
        val p = presetFor(seed)
        return BannerSpec(background = p.background)
    }
}

/**
 * "Surprise me": a random banner made by mixing backgrounds, colours, logo layouts and decorations that already exist
 * in the editor. Pure maths (no Android classes) so it is unit tested. Every call with a different [Random] gives a
 * different banner, and every result keeps the logo readable:
 *  - a single-colour logo gets the black or white that reads best on the background;
 *  - a coloured logo gets a background pushed away from the logo's own colour until the contrast is at least
 *    [MIN_CONTRAST] (or a plain dark or light fallback);
 *  - the logo is at least [MIN_SCALE_PICK] of its automatic size and always stays on the canvas (BannerLayout clamps it),
 *    and decorations are drawn faint, behind the logo.
 */
object SurpriseDesign {
    const val MIN_CONTRAST = 2.2f
    const val MIN_SCALE_PICK = 0.72f
    private val ANGLES = intArrayOf(0, 45, 90, 135, 180, 225, 315)

    private fun contrast(a: Int, b: Int): Float {
        val la = ArtColor.luminance(a); val lb = ArtColor.luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** HSV (hue in degrees, s and v 0..1) to an opaque ARGB int. */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val hh = (((h % 360f) + 360f) % 360f) / 60f
        val c = v * s
        val x = c * (1f - abs(hh % 2f - 1f))
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private fun randomPair(rnd: Random, brand: Int?, dominant: Int?): Pair<Int, Int> {
        // Source of colours: presets, solid swatches, random hues, and (when known) the brand or the logo's own colour.
        val sources = buildList {
            add(0); add(1); add(2); add(2)
            if (brand != null) { add(3); add(3) }
            if (dominant != null) add(4)
        }
        return when (sources[rnd.nextInt(sources.size)]) {
            0 -> {
                val p = ArtPresets.all[rnd.nextInt(ArtPresets.all.size)].background
                (ArtColor.parseHex(p.a) ?: 0xFF1F2937.toInt()) to (ArtColor.parseHex(p.b) ?: 0xFF111827.toInt())
            }
            1 -> {
                val c = ArtColor.parseHex(ArtPresets.solidColors[rnd.nextInt(ArtPresets.solidColors.size)]) ?: 0xFF374151.toInt()
                c to ArtColor.darken(c, 0.25f)
            }
            2 -> {
                val h = rnd.nextFloat() * 360f
                val shift = 20f + rnd.nextFloat() * 50f
                val s = 0.5f + rnd.nextFloat() * 0.4f
                hsv(h, s, 0.55f + rnd.nextFloat() * 0.4f) to hsv(h + shift, s, 0.25f + rnd.nextFloat() * 0.4f)
            }
            3 -> brand!!.let { ArtColor.lighten(it, 0.05f + rnd.nextFloat() * 0.1f) to ArtColor.darken(it, 0.2f + rnd.nextFloat() * 0.25f) }
            else -> dominant!!.let { ArtColor.darken(it, 0.45f + rnd.nextFloat() * 0.3f) to ArtColor.darken(it, 0.7f + rnd.nextFloat() * 0.2f) }
        }
    }

    /** Pushes [a]/[b] away from the logo colour until they read against it. Never returns a pair with less than [MIN_CONTRAST]. */
    private fun readableAgainst(logoColor: Int, a: Int, b: Int): Pair<Int, Int> {
        fun ok(x: Int, y: Int) = contrast(logoColor, x) >= MIN_CONTRAST && contrast(logoColor, y) >= MIN_CONTRAST
        if (ok(a, b)) return a to b
        val darkSide = ArtColor.luminance(logoColor) > 0.3f
        var x = a; var y = b
        for (step in 1..4) {
            x = if (darkSide) ArtColor.darken(x, 0.35f) else ArtColor.lighten(x, 0.4f)
            y = if (darkSide) ArtColor.darken(y, 0.35f) else ArtColor.lighten(y, 0.4f)
            if (ok(x, y)) return x to y
        }
        return if (darkSide) 0xFF111827.toInt() to 0xFF0F172A.toInt() else 0xFFF8FAFC.toInt() to 0xFFCBD5E1.toInt()
    }

    /**
     * @param mono the logo is one colour and can be recoloured
     * @param brandHex the brand colour when a source gave one
     * @param dominantArgb the main colour of a coloured logo, if known
     * @param aspect width / height of the logo (wide logos are kept from growing past the canvas by BannerLayout)
     */
    fun generate(rnd: Random, mono: Boolean, brandHex: String?, dominantArgb: Int?, aspect: Float = 1f): BannerSpec {
        val brand = ArtColor.parseHex(brandHex)
        var (a, b) = randomPair(rnd, brand, dominantArgb)
        // A coloured logo with a known colour must read against the background.
        if (!mono && dominantArgb != null) { val r = readableAgainst(dominantArgb, a, b); a = r.first; b = r.second }

        val solid = rnd.nextInt(5) == 0
        val bg = if (solid) BackgroundSpec(BgKind.SOLID, ArtColor.toHex(a), ArtColor.toHex(a), 90)
        else BackgroundSpec(BgKind.GRADIENT, ArtColor.toHex(a), ArtColor.toHex(b), ANGLES[rnd.nextInt(ANGLES.size)])

        val tint = if (mono) {
            // A single-colour logo: whichever of white or near-black reads best on both ends of the gradient.
            val white = 0xFFFFFFFF.toInt(); val ink = 0xFF111827.toInt()
            val wMin = min(contrast(white, a), contrast(white, if (solid) a else b))
            val iMin = min(contrast(ink, a), contrast(ink, if (solid) a else b))
            ArtColor.toHex(if (wMin >= iMin) white else ink)
        } else null

        // Layout: a little smaller than the automatic size at most, nudged a little off centre. Wide logos stay centred.
        val wide = aspect > 2.6f
        val scale = MIN_SCALE_PICK + rnd.nextFloat() * (1f - MIN_SCALE_PICK)
        val dx = if (wide) 0f else (rnd.nextFloat() - 0.5f) * 0.12f
        val dy = (rnd.nextFloat() - 0.5f) * 0.10f

        val decor = DecorKind.values()[rnd.nextInt(DecorKind.values().size)]
        return BannerSpec(
            background = bg,
            mode = FitMode.FIT,
            scale = scale,
            dx = dx,
            dy = dy,
            tint = tint,
            decor = decor,
            decorSeed = rnd.nextInt(1_000_000),
        )
    }

    /** [count] different banners (different colours, angle, layout or decoration), for the person to pick from. */
    fun variations(count: Int, rnd: Random, mono: Boolean, brandHex: String?, dominantArgb: Int?, aspect: Float = 1f): List<BannerSpec> {
        val out = ArrayList<BannerSpec>(count)
        var tries = 0
        while (out.size < count && tries < count * 20) {
            tries++
            val s = generate(rnd, mono, brandHex, dominantArgb, aspect)
            if (out.none { it.background == s.background && it.decor == s.decor }) out += s
        }
        while (out.size < count) out += generate(rnd, mono, brandHex, dominantArgb, aspect)
        return out
    }
}
