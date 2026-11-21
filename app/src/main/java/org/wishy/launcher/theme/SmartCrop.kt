package org.wishy.launcher.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor

/**
 * A "crop" that never cuts much off. Plain Crop fills the tile and throws away whatever sticks out, which chops the
 * logo of any banner that is not almost exactly the tile's shape. This fills the tile only when the picture is close
 * to the tile's shape (the edge loss stays under [MAX_CUT]); otherwise the whole picture is shown, centred, on the
 * tile colour behind it.
 */
object SmartCrop : ContentScale {
    private const val MAX_CUT = 0.10f

    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        if (srcSize.width <= 0f || srcSize.height <= 0f) return ScaleFactor(1f, 1f)
        val sw = dstSize.width / srcSize.width
        val sh = dstSize.height / srcSize.height
        val crop = maxOf(sw, sh)
        val fit = minOf(sw, sh)
        // crop / fit - 1 is the share of the picture that would be cut off along its longer side.
        val s = if (crop / fit - 1f <= MAX_CUT) crop else fit
        return ScaleFactor(s, s)
    }
}
