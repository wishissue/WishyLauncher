package org.wishy.launcher.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.wishy.launcher.R
import java.io.File

/**
 * A wallpaper in a dark and a light tone so it suits both colour modes of a theme. Either it ships inside the APK
 * ([dark] and [light] are drawables) or it was downloaded from the store ([darkFile] and [lightFile] are files, and
 * [title] is its name). A picture that has only one tone uses it for both.
 */
class BundledWallpaper(
    /** Stored in the user's settings. Never change an id once released. */
    val id: String,
    @StringRes val label: Int,
    @DrawableRes val dark: Int,
    @DrawableRes val light: Int,
    val title: String? = null,
    val darkFile: File? = null,
    val lightFile: File? = null,
) {
    @Composable
    fun displayName(): String = title ?: stringResource(label)
}

object Wallpapers {
    private val bundled: List<BundledWallpaper> = listOf(
        BundledWallpaper("wishy", R.string.wpx_wishy, R.drawable.wp_wishy_dark, R.drawable.wp_wishy_light),
        BundledWallpaper("sheet", R.string.wpx_sheet, R.drawable.wp_sheet_dark, R.drawable.wp_sheet_light),
        BundledWallpaper("fold", R.string.wpx_fold, R.drawable.wp_fold_dark, R.drawable.wp_fold_light),
        BundledWallpaper("amber", R.string.wpx_amber, R.drawable.wp_amber_dark, R.drawable.wp_amber_light),
        BundledWallpaper("glass_chip", R.string.wpx_glass_chip, R.drawable.wp_glass_chip_dark, R.drawable.wp_glass_chip_light),
        BundledWallpaper("adwaita", R.string.wpx_adwaita, R.drawable.wp_adwaita_dark, R.drawable.wp_adwaita_light),
        BundledWallpaper("curvy", R.string.wpx_curvy, R.drawable.wp_curvy_dark, R.drawable.wp_curvy_light),
        BundledWallpaper("curvaturingster", R.string.wpx_curvaturingster, R.drawable.wp_curvaturingster_dark, R.drawable.wp_curvaturingster_light),
        BundledWallpaper("dithered_sun", R.string.wpx_dithered_sun, R.drawable.wp_dithered_sun_dark, R.drawable.wp_dithered_sun_light),
        BundledWallpaper("pills", R.string.wpx_pills, R.drawable.wp_pills_dark, R.drawable.wp_pills_light),
        BundledWallpaper("symbolic", R.string.wpx_symbolic, R.drawable.wp_symbolic_dark, R.drawable.wp_symbolic_light),
        BundledWallpaper("flow", R.string.wpx_flow, R.drawable.wp_flow, R.drawable.wp_flow),
        BundledWallpaper("volna", R.string.wpx_volna, R.drawable.wp_volna, R.drawable.wp_volna),
        BundledWallpaper("milkyway", R.string.wpx_milkyway, R.drawable.wp_milkyway, R.drawable.wp_milkyway),
        BundledWallpaper("altai", R.string.wpx_altai, R.drawable.wp_altai, R.drawable.wp_altai),
    )

    /** Wallpapers fetched from the store, found on disk by [refreshDownloads]. */
    @Volatile private var downloaded: List<BundledWallpaper> = emptyList()

    /** Order matters: it is the order shown in Settings. Bundled first, then the ones the person downloaded. */
    val all: List<BundledWallpaper> get() = bundled + downloaded

    /** The first release shipped drawn gradients with these ids. Anyone who picked one now gets the closest new picture. */
    private val LEGACY = mapOf(
        "aurora" to "sheet", "dusk" to "fold", "ember" to "amber", "orchid" to "pills",
        "ocean" to "glass_chip", "forest" to "symbolic", "mono" to "curvy",
    )

    /** Null for an empty or unknown id (for example one saved by a newer version), which means "none chosen". */
    fun byId(id: String): BundledWallpaper? = all.firstOrNull { it.id == (LEGACY[id] ?: id) }

    /** Re-reads the downloaded wallpapers. Cheap; call at start and after a download or removal. */
    fun refreshDownloads(context: Context) {
        downloaded = WallpaperStore.installed(context)
    }

    /**
     * Biggest picture a TV GPU can be trusted with. Many TV chips report a 4096 texture limit; a bigger bitmap is
     * either refused or silently shrunk by the driver, which looks like a blurry wallpaper. 4096 still covers 4K (3840).
     */
    private const val MAX_TEXTURE_EDGE = 4096

    /** Never decode for a screen smaller than this, so a wrong metrics report cannot produce a thumbnail. */
    private const val MIN_TARGET = 1280

    /**
     * The long edge, in real pixels, of the surface the launcher draws on (1920 on a 1080p TV, 3840 on a 4K one).
     * Read from the window itself, not from a constant, so the picture is decoded for the screen it is shown on.
     */
    fun displayLongEdge(context: Context): Int {
        val fromWindow = runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { maxOf(it.width(), it.height()) } else 0
        }.getOrDefault(0)
        val dm = context.resources.displayMetrics
        val fromMetrics = maxOf(dm.widthPixels, dm.heightPixels)
        return maxOf(fromWindow, fromMetrics).coerceIn(MIN_TARGET, MAX_TEXTURE_EDGE)
    }

    /**
     * Largest power-of-two reduction that still leaves the decoded long edge at or above [targetLongEdge] (so it is
     * never softer than needed), and that keeps it inside [MAX_TEXTURE_EDGE]. A picture already smaller than the
     * target is not reduced at all, and never enlarged here.
     */
    internal fun sampleFor(srcLongEdge: Int, targetLongEdge: Int): Int {
        var sample = 1
        while (srcLongEdge / (sample * 2) >= targetLongEdge) sample *= 2
        while (srcLongEdge / sample > MAX_TEXTURE_EDGE) sample *= 2
        return sample
    }

    private fun options(srcW: Int, srcH: Int, targetLongEdge: Int) = BitmapFactory.Options().apply {
        inSampleSize = sampleFor(maxOf(srcW, srcH), targetLongEdge)
        // Full 8-bit colour: RGB_565 bands visibly on gradients and makes photos look cheap.
        inPreferredConfig = Bitmap.Config.ARGB_8888
        // No density scaling: a wallpaper is sized for the screen, not for a dp bucket.
        inScaled = false
    }

    /**
     * Decodes a wallpaper for showing full screen: sharp for this display, never below it unless the file itself is
     * smaller. Call off the main thread.
     */
    fun decodeForDisplay(context: Context, wallpaper: BundledWallpaper, dark: Boolean): Bitmap? =
        decode(context, wallpaper, dark, maxWidth = displayLongEdge(context))

    /** Same for a picture file (the person's own wallpaper). Null when it is not a readable picture. */
    fun decodeFileForDisplay(context: Context, file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(file.path, options(bounds.outWidth, bounds.outHeight, displayLongEdge(context)))
    }

    /**
     * Decodes a wallpaper so its long edge stays at or above [maxWidth] where the file allows (smaller values give
     * thumbnails and the tiny pseudo-blur copy). Call off the main thread.
     */
    fun decode(context: Context, wallpaper: BundledWallpaper, dark: Boolean, maxWidth: Int): Bitmap? {
        val file = if (dark) wallpaper.darkFile else wallpaper.lightFile
        val res = if (dark) wallpaper.dark else wallpaper.light
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        if (file != null) BitmapFactory.decodeFile(file.path, bounds)
        else BitmapFactory.decodeResource(context.resources, res, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = options(bounds.outWidth, bounds.outHeight, maxWidth)
        return if (file != null) BitmapFactory.decodeFile(file.path, opts)
        else BitmapFactory.decodeResource(context.resources, res, opts)
    }
}
