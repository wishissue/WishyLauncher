package org.wishy.launcher.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize

/**
 * Time zero for the loading bar. It lives for the whole process, so when startup moves from one waiting point to the
 * next (settings loaded, apps scanned, fade-in) the snake simply carries on instead of starting over.
 */
private object SplashClock {
    val start: Long = System.nanoTime()
}

private val Night = Color(0xFF080B12)
private val Track = Color(0xFF1A2233)
private val SnakeHead = Color(0xFF70A3FF)

/** One trip of the snake along the bar, in seconds. */
private const val LAP_SECONDS = 1.5f

/** How much of the bar the snake covers. */
private const val SNAKE_LENGTH = 0.38f

/**
 * Startup screen: the Wishy mark with a slim loading bar under it. A short "snake" slides along the bar, and when its
 * head reaches the end its tail is already coming back in at the start. With the system's animations turned off it
 * shows the bar with the snake parked in the middle.
 */
@Composable
internal fun WishySplash() {
    val context = LocalContext.current
    val animate = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
            .getOrDefault(true)
    }
    var frame by remember { mutableLongStateOf(0L) }
    if (animate) LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }

    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WishyLogo(size = 96.dp, spinning = false)
            Spacer(Modifier.height(36.dp))
            Canvas(Modifier.size(DpSize(220.dp, 6.dp))) {
                // Reading [frame] here makes only this small draw block repeat each frame; nothing is recomposed.
                val lap = if (animate) frame.let { ((System.nanoTime() - SplashClock.start) / 1e9f / LAP_SECONDS) % 1f } else 0.5f
                drawSnakeBar(lap)
            }
        }
    }
}

/** [lap] is how far the head has travelled, from 0 (just entering) to 1 (back where it started). */
private fun DrawScope.drawSnakeBar(lap: Float) {
    val w = size.width
    val h = size.height
    val r = CornerRadius(h / 2f, h / 2f)
    drawRoundRect(Track, size = Size(w, h), cornerRadius = r)

    // The head runs from just off the left edge to just past the right edge, so the snake slides fully in and out.
    val head = lap * (1f + SNAKE_LENGTH)
    val tail = head - SNAKE_LENGTH
    clipRect(0f, 0f, w, h) {
        val x0 = tail * w
        val x1 = head * w
        // Tail is transparent so the snake fades in behind its bright head.
        drawRoundRect(
            Brush.horizontalGradient(
                colors = listOf(SnakeHead.copy(alpha = 0f), SnakeHead),
                startX = x0, endX = x1,
            ),
            topLeft = Offset(x0, 0f), size = Size(x1 - x0, h), cornerRadius = r,
        )
    }
}
