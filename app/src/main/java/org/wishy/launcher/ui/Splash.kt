package org.wishy.launcher.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star

/**
 * Time zero for the loading indicator. It lives for the whole process, so when startup moves from one waiting
 * point to the next (settings loaded, apps scanned, fade-in) the animation simply carries on instead of restarting.
 */
private object SplashClock {
    val start: Long = System.nanoTime()
}

private val Night = Color(0xFF080B12)
private val Container = Color(0xFF1A2233)
private val Indicator = Color(0xFF70A3FF)

/** Seconds spent morphing from one shape into the next. */
private const val MORPH_SECONDS = 0.7f

/** Degrees the whole indicator turns per second, on top of the morphing. */
private const val SPIN_DEGREES_PER_SECOND = 100f

/**
 * The shape sequence of Material 3's expressive loading indicator (https://m3.material.io/components/loading-indicator),
 * drawn with androidx.graphics:graphics-shapes: a soft burst, a nine-sided cookie, a pentagon, a pill, a sunny star,
 * a four-sided cookie and a circle. All are built around (0,0) with radius about 1 so they morph into each other
 * without jumping.
 */
private fun buildMorphs(): List<Morph> {
    val shapes = listOf(
        RoundedPolygon.star(10, 1f, 0.68f, CornerRounding(0.22f), CornerRounding(0.22f), centerX = 0f, centerY = 0f),
        RoundedPolygon.star(9, 1f, 0.82f, CornerRounding(0.5f), CornerRounding(0.5f), centerX = 0f, centerY = 0f),
        RoundedPolygon(5, 1f, 0f, 0f, CornerRounding(0.3f)),
        RoundedPolygon.rectangle(1.9f, 1.05f, CornerRounding(0.52f), centerX = 0f, centerY = 0f),
        RoundedPolygon.star(8, 1f, 0.8f, CornerRounding(0.15f), CornerRounding(0.15f), centerX = 0f, centerY = 0f),
        RoundedPolygon.star(4, 1f, 0.74f, CornerRounding(0.32f), CornerRounding(0.32f), centerX = 0f, centerY = 0f),
        RoundedPolygon.circle(8, 0.95f, 0f, 0f),
    )
    return shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) }
}

/**
 * Startup screen: only the Material 3 loading indicator on a dark backdrop. With the system's animations turned off
 * it shows a still shape instead of moving.
 */
@Composable
internal fun WishySplash() {
    val context = LocalContext.current
    val animate = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
            .getOrDefault(true)
    }
    val morphs = remember { runCatching { buildMorphs() }.getOrNull() }
    val path = remember { Path() }
    var frame by remember { mutableLongStateOf(0L) }
    if (animate) LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }

    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            // Reading [frame] here makes only this small draw block repeat each frame; nothing is recomposed.
            @Suppress("UNUSED_EXPRESSION") frame
            val t = if (animate) (System.nanoTime() - SplashClock.start) / 1e9f else 0f
            drawIndicator(morphs, path, t)
        }
    }
}

private fun DrawScope.drawIndicator(morphs: List<Morph>?, path: Path, t: Float) {
    drawCircle(Container, radius = size.minDimension / 2f)
    if (morphs.isNullOrEmpty()) {
        drawCircle(Indicator, radius = size.minDimension * 0.3f)
        return
    }
    val steps = t / MORPH_SECONDS
    val index = steps.toInt() % morphs.size
    val frac = steps - steps.toInt()
    // Ease in and out of every step so each shape holds for a moment before it melts into the next.
    val eased = frac * frac * (3f - 2f * frac)

    val radius = size.minDimension * 0.3f
    path.rewind()
    val cubics = morphs[index].asCubics(eased)
    if (cubics.isEmpty()) return
    path.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
    for (c in cubics) path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
    path.close()

    val center = Offset(size.width / 2f, size.height / 2f)
    rotate(degrees = t * SPIN_DEGREES_PER_SECOND, pivot = center) {
        translate(center.x, center.y) {
            scale(radius, radius, pivot = Offset.Zero) {
                drawPath(path, Indicator)
            }
        }
    }
}
