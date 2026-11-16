package org.wishy.launcher.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import com.caverock.androidsvg.SVG
import org.wishy.theme.core.ArtColor
import org.wishy.theme.core.BannerLayout
import org.wishy.theme.core.BannerSpec
import org.wishy.theme.core.BgKind
import org.wishy.theme.core.FitMode
import org.wishy.theme.core.SvgInfo
import kotlin.math.roundToInt

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
