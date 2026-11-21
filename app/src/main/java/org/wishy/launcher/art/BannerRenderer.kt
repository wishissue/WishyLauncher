package org.wishy.launcher.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.caverock.androidsvg.SVG
import org.wishy.theme.core.ArtColor
import org.wishy.theme.core.BannerLayout
import org.wishy.theme.core.BannerSpec
import org.wishy.theme.core.BgKind
import org.wishy.theme.core.DecorKind
import org.wishy.theme.core.FitMode
import org.wishy.theme.core.SvgInfo
import kotlin.math.roundToInt
import kotlin.random.Random

/** A logo ready to draw: either checked SVG text or a bitmap. [mono] SVGs can be recoloured. */
class LogoImage private constructor(val svgText: String?, val bitmap: Bitmap?, val aspect: Float, val mono: Boolean) {
    companion object {
        /** [info] must come from SvgSanitizer.inspect, which is what makes the text safe to draw. */
        fun svg(info: SvgInfo) = LogoImage(info.text, null, info.aspect, info.monochrome)
        fun raster(b: Bitmap) = LogoImage(null, b, b.width.toFloat() / b.height.toFloat(), false)
    }
}

/** Draws SVG text. Call only with text that passed SvgSanitizer. Never on the main thread. */
object SvgRender {
    fun render(svgText: String, w: Int, h: Int): Bitmap? = runCatching {
        val svg = SVG.getFromString(svgText)
        if (svg.documentViewBox == null) {
            val dw = svg.documentWidth; val dh = svg.documentHeight
            if (dw > 0f && dh > 0f) svg.setDocumentViewBox(0f, 0f, dw, dh)
        }
        svg.setDocumentWidth(w.toFloat())
        svg.setDocumentHeight(h.toFloat())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bmp))
        bmp
    }.getOrNull()
}

/**
 * Draws a banner. The same code makes the small editor preview and the final 1280 x 720 picture, so what the
 * person sees is what is saved. Pure drawing: no files, no network. Never on the main thread.
 */
object BannerRenderer {
    fun render(spec: BannerSpec, logo: LogoImage?, background: Bitmap?, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val fw = w.toFloat(); val fh = h.toFloat()
        val bg = spec.background
        when (bg.kind) {
            BgKind.TRANSPARENT -> Unit
            BgKind.SOLID -> c.drawColor(ArtColor.parseHex(bg.a) ?: 0xFF1F2937.toInt())
            BgKind.GRADIENT -> {
                val a = ArtColor.parseHex(bg.a) ?: 0xFF1F2937.toInt()
                val b = ArtColor.parseHex(bg.b) ?: 0xFF111827.toInt()
                val p = BannerLayout.gradientPoints(fw, fh, bg.angle)
                val paint = Paint().apply { shader = LinearGradient(p[0], p[1], p[2], p[3], a, b, Shader.TileMode.CLAMP) }
                c.drawRect(0f, 0f, fw, fh, paint)
            }
            BgKind.IMAGE -> {
                if (background != null) {
                    val r = BannerLayout.logoRect(fw, fh, background.width.toFloat() / background.height, FitMode.COVER)
                    c.drawBitmap(background, null, RectF(r.left, r.top, r.right, r.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
                    c.drawColor(0x4D000000) // a light shade keeps a logo readable on a busy picture
                } else c.drawColor(0xFF1F2937.toInt())
            }
        }
        drawDecor(c, spec, fw, fh)
        if (logo == null) return out
        val r = BannerLayout.logoRect(fw, fh, logo.aspect, spec.mode, spec.scale, spec.dx, spec.dy)
        val dst = RectF(r.left, r.top, r.right, r.bottom)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val tint = if (logo.mono) ArtColor.parseHex(spec.tint) else null
        if (tint != null) paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        if (logo.svgText != null) {
            val bw = r.width.roundToInt().coerceIn(1, 2048)
            val bh = r.height.roundToInt().coerceIn(1, 2048)
            val lb = SvgRender.render(logo.svgText, bw, bh) ?: return out
            c.drawBitmap(lb, null, dst, paint)
            lb.recycle()
        } else if (logo.bitmap != null) {
            c.drawBitmap(logo.bitmap, null, dst, paint)
        }
        return out
    }

    /**
     * The faint pattern of a "Surprise me" banner. Deterministic from [BannerSpec.decorSeed] and sized from the canvas,
     * so the small preview and the saved picture show the same pattern. Light or dark depending on the background so it
     * never competes with the logo.
     */
    private fun drawDecor(c: Canvas, spec: BannerSpec, w: Float, h: Float) {
        if (spec.decor == DecorKind.NONE || spec.background.kind == BgKind.TRANSPARENT || spec.background.kind == BgKind.IMAGE) return
        val base = ArtColor.parseHex(spec.background.a) ?: return
        val ink = if (ArtColor.luminance(base) > 0.45f) 0x000000 else 0xFFFFFF
        val rnd = Random(spec.decorSeed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun col(alpha: Int) = (alpha shl 24) or ink
        when (spec.decor) {
            DecorKind.CIRCLES -> repeat(4 + rnd.nextInt(3)) {
                paint.style = Paint.Style.FILL; paint.color = col(18 + rnd.nextInt(18))
                c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, h * (0.15f + rnd.nextFloat() * 0.4f), paint)
            }
            DecorKind.RINGS -> repeat(3 + rnd.nextInt(3)) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = h * 0.02f; paint.color = col(28 + rnd.nextInt(20))
                c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, h * (0.2f + rnd.nextFloat() * 0.5f), paint)
            }
            DecorKind.STRIPES -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = h * (0.04f + rnd.nextFloat() * 0.05f); paint.color = col(20)
                val gap = h * (0.14f + rnd.nextFloat() * 0.1f)
                var x = -h
                while (x < w + h) { c.drawLine(x, h, x + h, 0f, paint); x += gap }
            }
            DecorKind.DOTS -> {
                paint.style = Paint.Style.FILL; paint.color = col(26)
                val gap = h * (0.09f + rnd.nextFloat() * 0.05f); val rad = gap * 0.16f
                var y = gap / 2
                while (y < h) { var x = gap / 2; while (x < w) { c.drawCircle(x, y, rad, paint); x += gap }; y += gap }
            }
            DecorKind.GLOW -> {
                val cx = w * (0.2f + rnd.nextFloat() * 0.6f); val cy = h * (0.2f + rnd.nextFloat() * 0.6f)
                val rad = h * (0.7f + rnd.nextFloat() * 0.5f)
                paint.style = Paint.Style.FILL
                paint.shader = RadialGradient(cx, cy, rad, (60 shl 24) or ink, ink, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
            }
            DecorKind.NONE -> Unit
        }
    }

    /** Decodes a picture file or bytes to at most [maxSide] pixels on the long side. Null if it is not an image. */
    fun decodeLimited(bytes: ByteArray, maxSide: Int): Bitmap? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        if (b.outWidth <= 0 || b.outHeight <= 0) return null
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    }.getOrNull()

    /** Encodes as PNG. Used to keep a raster logo as an original file. */
    fun png(b: Bitmap): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, o)
        return o.toByteArray()
    }

    /** Main colour of [logo] for choosing a background, from a small copy. Null if nothing is opaque. */
    fun dominantColor(logo: LogoImage): Int? = runCatching {
        val small = if (logo.svgText != null) SvgRender.render(logo.svgText, 48, (48f / logo.aspect).roundToInt().coerceIn(8, 480))
        else logo.bitmap?.let { Bitmap.createScaledBitmap(it, 48, (48f / logo.aspect).roundToInt().coerceIn(8, 480), true) }
        small ?: return null
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        small.recycle()
        ArtColor.dominant(px)
    }.getOrNull()
}
