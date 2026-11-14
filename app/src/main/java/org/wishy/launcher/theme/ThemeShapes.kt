package org.wishy.launcher.theme

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import org.wishy.launcher.ui.SmoothCornerShape
import org.wishy.theme.core.BgEffect
import org.wishy.theme.core.BorderStyle
import org.wishy.theme.core.FocusIndicator
import org.wishy.theme.core.IndicatorKind
import org.wishy.theme.core.ShapeKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shapes with cut or stepped corners, for the retro themes. */
private class CutCornerShape(
    private val kind: ShapeKind,
    private val radiusPx: Float,
    private val stepPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path()
        val c = min(radiusPx, min(w, h) / 2f)
        if (c < 1f || w < 2f || h < 2f) {
            path.moveTo(0f, 0f); path.lineTo(w, 0f); path.lineTo(w, h); path.lineTo(0f, h); path.close()
            return Outline.Generic(path)
        }
        // The top-left corner, running from the left edge (0, c) to the top edge (c, 0).
        val tl = ArrayList<Offset>()
        if (kind == ShapeKind.PIXEL) {
            val n = max(1, (c / stepPx).roundToInt())
            val s = c / n
            tl.add(Offset(0f, c))
            for (i in 1..n) {
                tl.add(Offset(i * s, c - (i - 1) * s))
                tl.add(Offset(i * s, c - i * s))
            }
        } else {
            tl.add(Offset(0f, c))
            tl.add(Offset(c, 0f))
        }
        val square = kind == ShapeKind.BEVEL
        // Bevel cuts only top-left and bottom-right.
        val topLeft = tl
        val topRight = if (square) listOf(Offset(w, 0f)) else tl.map { Offset(w - it.x, it.y) }.reversed()
        val bottomRight = tl.map { Offset(w - it.x, h - it.y) }
        val bottomLeft = if (square) listOf(Offset(0f, h)) else tl.map { Offset(it.x, h - it.y) }.reversed()

        path.moveTo(topLeft[0].x, topLeft[0].y)
        for (p in topLeft.drop(1)) path.lineTo(p.x, p.y)
        for (p in topRight) path.lineTo(p.x, p.y)
        for (p in bottomRight) path.lineTo(p.x, p.y)
        for (p in bottomLeft) path.lineTo(p.x, p.y)
        path.close()
        return Outline.Generic(path)
    }
}

fun composeShape(s: TShape, dims: ThemeDims): Shape = when (s.kind) {
    ShapeKind.RECT -> RectangleShape
    ShapeKind.ROUNDED -> if (s.radius <= 0f) RectangleShape else SmoothCornerShape(dims.dp(s.radius))
    ShapeKind.PIXEL, ShapeKind.CHAMFER, ShapeKind.BEVEL ->
        if (s.radius <= 0f) RectangleShape
        else CutCornerShape(s.kind, dims.px(s.radius), max(1f, dims.px(s.pixelSize)))
}

/** Border in the theme's style. Dashed draws its own stroke; double is two thin lines. */
fun Modifier.themeBorder(b: TBorder?, env: ThemeEnv, shape: Shape): Modifier {
    if (b == null || b.width <= 0f) return this
    val color = env.colors.resolve(b.color, env.colors.outline)
    val w = env.dims.dp(b.width)
    return when (b.style) {
        BorderStyle.SOLID -> this.border(w, color, shape)
        BorderStyle.DOUBLE -> {
            val third = env.dims.dp(b.width / 3f)
            this.border(third, color, shape).padding(third).border(third, color, shape)
        }
        BorderStyle.DASHED -> {
            val strokePx = env.dims.px(b.width)
            this.drawWithContent {
                drawContent()
                val outline = shape.createOutline(size, layoutDirection, this)
                val dash = PathEffect.dashPathEffect(floatArrayOf(strokePx * 2.5f, strokePx * 2f))
                drawOutline(outline, color = color, style = Stroke(width = strokePx, pathEffect = dash))
            }
        }
    }
}

/**
 * Draws the focus indicator over the content: a ring around the card, corner brackets, an arrow beside it,
 * an underline, a translucent fill, or a bar. Does nothing for kind none.
 */
fun Modifier.themeIndicator(
    ind: FocusIndicator?,
    env: ThemeEnv,
    cardShape: TShape,
    visible: Boolean,
): Modifier {
    if (ind == null || ind.kind == IndicatorKind.NONE || !visible) return this
    val color = env.colors.resolve(ind.color, env.colors.primary)
    val stroke = max(1f, env.dims.px(ind.width))
    val gap = env.dims.px(ind.gap)
    val len = env.dims.px(ind.length)
    val radius = if (cardShape.kind == ShapeKind.ROUNDED || cardShape.kind == ShapeKind.CHAMFER || cardShape.kind == ShapeKind.BEVEL)
        env.dims.px(cardShape.radius) else 0f
    return this.drawWithContent {
        drawContent()
        when (ind.kind) {
            IndicatorKind.RING -> {
                val pad = gap + stroke / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-pad, -pad),
                    size = Size(size.width + 2 * pad, size.height + 2 * pad),
                    cornerRadius = CornerRadius(radius + gap),
                    style = Stroke(width = stroke),
                )
            }
            IndicatorKind.CORNERS -> {
                val l = max(len, stroke * 2f)
                val x0 = -gap; val y0 = -gap
                val x1 = size.width + gap; val y1 = size.height + gap
                fun seg(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = stroke)
                seg(Offset(x0 - stroke / 2f, y0), Offset(x0 + l, y0)); seg(Offset(x0, y0), Offset(x0, y0 + l))
                seg(Offset(x1 + stroke / 2f, y0), Offset(x1 - l, y0)); seg(Offset(x1, y0), Offset(x1, y0 + l))
                seg(Offset(x0 - stroke / 2f, y1), Offset(x0 + l, y1)); seg(Offset(x0, y1), Offset(x0, y1 - l))
                seg(Offset(x1 + stroke / 2f, y1), Offset(x1 - l, y1)); seg(Offset(x1, y1), Offset(x1, y1 - l))
            }
            IndicatorKind.ARROW -> {
                val tip = -gap
                val base = tip - max(len, stroke * 2f)
                val cy = size.height / 2f
                val half = max(len, stroke * 2f) * 0.7f
                val p = Path().apply {
                    moveTo(tip, cy); lineTo(base, cy - half); lineTo(base, cy + half); close()
                }
                drawPath(p, color)
            }
            IndicatorKind.UNDERLINE ->
                drawRect(color, Offset(0f, size.height + gap), Size(size.width, stroke))
            IndicatorKind.FILL ->
                drawRoundRect(color, Offset.Zero, size, CornerRadius(radius))
            IndicatorKind.BAR ->
                drawRect(color, Offset(-gap - stroke, 0f), Size(stroke, size.height))
            IndicatorKind.NONE -> {}
        }
    }
}

/**
 * A cheap full-screen overlay (scanlines, grid, dither) made by tiling one tiny bitmap with a shader.
 * No per-frame work beyond one textured rectangle.
 */
fun Modifier.themeEffect(effect: BgEffect, opacity: Float, env: ThemeEnv): Modifier {
    if (effect == BgEffect.NONE || opacity <= 0f) return this
    val u = env.dims.unitPx
    val bmp: Bitmap = when (effect) {
        BgEffect.SCANLINES -> {
            val k = max(3, (3f * u).roundToInt())
            createBitmap(1, k).also { b ->
                for (y in 0 until max(1, k / 3)) b[0, y] = android.graphics.Color.BLACK
            }
        }
        BgEffect.GRID -> {
            val k = max(8, (40f * u).roundToInt())
            createBitmap(k, k).also { b ->
                val t = max(1, (1f * u).roundToInt())
                for (i in 0 until k) for (j in 0 until t) {
                    b[i, j] = android.graphics.Color.BLACK
                    b[j, i] = android.graphics.Color.BLACK
                }
            }
        }
        else -> { // DITHER: a 2x2 checkerboard of blocks
            val s = max(1, (1.5f * u).roundToInt())
            createBitmap(2 * s, 2 * s).also { b ->
                for (x in 0 until s) for (y in 0 until s) {
                    b[x, y] = android.graphics.Color.BLACK
                    b[x + s, y + s] = android.graphics.Color.BLACK
                }
            }
        }
    }
    val brush = ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    return this.drawBehind { drawRect(brush = brush, alpha = opacity.coerceIn(0f, 1f)) }
}

/** Plain colour rectangle in a shape; kept here so callers do not mix up Compose's and the theme's Shape. */
fun Modifier.themeFill(color: Color, shape: Shape): Modifier =
    if (color == Color.Transparent || color == Color.Unspecified) this
    else this.drawBehind {
        drawOutline(shape.createOutline(size, layoutDirection, this), color)
    }
