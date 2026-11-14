package org.wishy.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.wishy.launcher.R

/**
 * A wallpaper that ships inside the APK, in a dark and a light tone so it suits both colour modes of a theme.
 * The pictures are drawn from scratch by tools/gen-wallpapers.py (see ARTWORK_ATTRIBUTION.md).
 */
class BundledWallpaper(
    /** Stored in the user's settings. Never change an id once released. */
    val id: String,
    @StringRes val label: Int,
    @DrawableRes val dark: Int,
    @DrawableRes val light: Int,
)

object Wallpapers {
    /** Order matters: it is the order shown in Settings. Keep in step with ORDER in tools/gen-wallpapers.py. */
    val all: List<BundledWallpaper> = listOf(
        BundledWallpaper("wishy", R.string.wpx_wishy, R.drawable.wp_wishy_dark, R.drawable.wp_wishy_light),
        BundledWallpaper("aurora", R.string.wpx_aurora, R.drawable.wp_aurora_dark, R.drawable.wp_aurora_light),
        BundledWallpaper("dusk", R.string.wpx_dusk, R.drawable.wp_dusk_dark, R.drawable.wp_dusk_light),
        BundledWallpaper("ember", R.string.wpx_ember, R.drawable.wp_ember_dark, R.drawable.wp_ember_light),
        BundledWallpaper("orchid", R.string.wpx_orchid, R.drawable.wp_orchid_dark, R.drawable.wp_orchid_light),
        BundledWallpaper("ocean", R.string.wpx_ocean, R.drawable.wp_ocean_dark, R.drawable.wp_ocean_light),
        BundledWallpaper("forest", R.string.wpx_forest, R.drawable.wp_forest_dark, R.drawable.wp_forest_light),
        BundledWallpaper("mono", R.string.wpx_mono, R.drawable.wp_mono_dark, R.drawable.wp_mono_light),
    )

    /** Null for an empty or unknown id (for example one saved by a newer version), which means "none chosen". */
    fun byId(id: String): BundledWallpaper? = all.firstOrNull { it.id == id }

    /**
     * Decodes a wallpaper to at most about [maxWidth] pixels wide. Call off the main thread.
     * Uses full 8-bit colour on purpose: 16-bit (RGB_565) would show visible banding on these smooth gradients.
     */
    fun decode(context: Context, wallpaper: BundledWallpaper, dark: Boolean, maxWidth: Int): Bitmap? {
        val res = if (dark) wallpaper.dark else wallpaper.light
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, res, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(context.resources, res, opts)
    }
}
