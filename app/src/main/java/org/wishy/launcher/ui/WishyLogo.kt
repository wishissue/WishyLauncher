package org.wishy.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Same artwork as drawable/ic_wishy_mark.xml and artwork/wishy-logo.svg (100 x 100 box).
private const val SHADOW = "M 54.5,20 C 56.5,20 58.5,21.5 61,23.5 L 84.5,43 C 87,45 88.5,47.5 87,50 L 81.5,53.5 L 81.5,83 C 81.5,88.5 78,92 72.5,92 L 36.5,92 C 31,92 27.5,88.5 27.5,83 L 27.5,53.5 L 22,50 C 20.5,47.5 22,45 24.5,43 L 48,23.5 C 50.5,21.5 52.5,20 54.5,20 Z"
private const val HOUSE = "M 50,15 C 52,15 54,16.5 56.5,18.5 L 80,38 C 82.5,40 84,42.5 82.5,45 L 77,48.5 L 77,78 C 77,83.5 73.5,87 68,87 L 32,87 C 26.5,87 23,83.5 23,78 L 23,48.5 L 17.5,45 C 16,42.5 17.5,40 20,38 L 43.5,18.5 C 46,16.5 48,15 50,15 Z"
private const val DISC = "M 50,40.5 A 23,23 0 1,1 49.99,40.5 Z"
private const val WAVE = "M 50.00,47.00 C 51.58,47.00 52.02,50.70 53.96,51.33 C 55.90,51.96 58.42,49.22 59.70,50.15 C 60.98,51.08 59.16,54.33 60.36,55.98 C 61.55,57.63 65.20,56.90 65.69,58.40 C 66.18,59.91 62.80,61.46 62.80,63.50 C 62.80,65.54 66.18,67.09 65.69,68.60 C 65.20,70.10 61.55,69.37 60.36,71.02 C 59.16,72.67 60.98,75.92 59.70,76.85 C 58.42,77.78 55.90,75.04 53.96,75.67 C 52.02,76.30 51.58,80.00 50.00,80.00 C 48.42,80.00 47.98,76.30 46.04,75.67 C 44.10,75.04 41.58,77.78 40.30,76.85 C 39.02,75.92 40.84,72.67 39.64,71.02 C 38.45,69.37 34.80,70.10 34.31,68.60 C 33.82,67.09 37.20,65.54 37.20,63.50 C 37.20,61.46 33.82,59.91 34.31,58.40 C 34.80,56.90 38.45,57.63 39.64,55.98 C 40.84,54.33 39.02,51.08 40.30,50.15 C 41.58,49.22 44.10,51.96 46.04,51.33 C 47.98,50.70 48.42,47.00 50.00,47.00 Z"

/**
 * The Wishy mark: a house with a wavy badge in it. With [spinning] the badge turns on its own centre,
 * which doubles as the loading indicator. One turn takes [turnMillis]. With [spinning] false it sits still
 * (also used when the person asked the system to remove animations).
 */
@Composable
fun WishyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    spinning: Boolean = true,
    turnMillis: Int = 4000,
    houseColor: Color = Color.White,
    shadowColor: Color = Color(0xFF072175),
    discColor: Color = Color(0xFF70A3FF),
) {
    val shadow = remember { PathParser().parsePathString(SHADOW).toPath() }
    val house = remember { PathParser().parsePathString(HOUSE).toPath() }
    val disc = remember { PathParser().parsePathString(DISC).toPath() }
    val wave = remember { PathParser().parsePathString(WAVE).toPath() }
    val angle by rememberInfiniteTransition(label = "logoSpin").animateFloat(
        initialValue = 0f,
        targetValue = if (spinning) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(turnMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "logoAngle",
    )
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 100f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(shadow, shadowColor)
            drawPath(house, houseColor)
            drawPath(disc, discColor)
            rotate(angle, pivot = Offset(50f, 63.5f)) {
                drawPath(
                    wave,
                    houseColor,
                    style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
