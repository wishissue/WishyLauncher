package org.wishy.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

val ACCENTS = listOf(
    Color(0xFF8AB4F8),
    Color(0xFF81C995),
    Color(0xFFFDD663),
    Color(0xFFF28B82),
    Color(0xFFD7AEFB),
    Color(0xFF78D9EC),
)

data class WallpaperPreset(val name: String, val colors: List<Color>)

val WALLPAPERS = listOf(
    WallpaperPreset("Midnight", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    WallpaperPreset("Aurora", listOf(Color(0xFF13547A), Color(0xFF2B825B), Color(0xFF80D0C7))),
    WallpaperPreset("Sunset", listOf(Color(0xFF41295A), Color(0xFF8F4A3E), Color(0xFFD76D77))),
    WallpaperPreset("Deep", listOf(Color(0xFF000428), Color(0xFF004683))),
    WallpaperPreset("Charcoal", listOf(Color(0xFF16161A), Color(0xFF232526), Color(0xFF2F3437))),
)

fun WallpaperPreset.brush(): Brush = Brush.linearGradient(colors)

/** Deterministic tile color for apps that ship no banner artwork. */
fun tileColor(pkg: String): Color {
    val palette = listOf(
        Color(0xFF37474F), Color(0xFF4E342E), Color(0xFF1B5E20),
        Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF880E4F),
        Color(0xFF3E2723), Color(0xFF263238), Color(0xFF33691E),
    )
    var h = 0
    for (c in pkg) h = h * 31 + c.code
    return palette[((h % palette.size) + palette.size) % palette.size]
}

/** WishIssue design tokens. Flat surfaces, no gradients, no glow. Primary blue is for actions, selection and links only. */
class WishPalette(
    val bg: Color, val surface: Color, val surfaceHigh: Color, val outline: Color,
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val text: Color, val textDim: Color, val success: Color,
) {
    companion object {
        val Dark = WishPalette(
            bg = Color(0xFF080B12), surface = Color(0xFF111725), surfaceHigh = Color(0xFF182235), outline = Color(0xFF303B4F),
            primary = Color(0xFFA8C8FF), onPrimary = Color(0xFF082C63),
            primaryContainer = Color(0xFF1B3A6B), onPrimaryContainer = Color(0xFFD6E5FF),
            text = Color(0xFFF1F4FB), textDim = Color(0xFFB0BCD0), success = Color(0xFF72D6A0),
        )
        val Light = WishPalette(
            bg = Color(0xFFE6F1FF), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFF3F7FF), outline = Color(0xFFD4DEED),
            primary = Color(0xFF1560D4), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD6E5FF), onPrimaryContainer = Color(0xFF0A2A5E),
            text = Color(0xFF101828), textDim = Color(0xFF536176), success = Color(0xFF237A4B),
        )
    }
}

val LocalWish = staticCompositionLocalOf { WishPalette.Dark }

/** Read the current palette inside any composable: `Wish.Text`, `Wish.Surface`, ... It follows [LiteTvTheme]'s `dark`. */
object Wish {
    val Bg: Color @Composable get() = LocalWish.current.bg
    val Surface: Color @Composable get() = LocalWish.current.surface
    val SurfaceHigh: Color @Composable get() = LocalWish.current.surfaceHigh
    val Outline: Color @Composable get() = LocalWish.current.outline
    val Primary: Color @Composable get() = LocalWish.current.primary
    val OnPrimary: Color @Composable get() = LocalWish.current.onPrimary
    val PrimaryContainer: Color @Composable get() = LocalWish.current.primaryContainer
    val OnPrimaryContainer: Color @Composable get() = LocalWish.current.onPrimaryContainer
    val Text: Color @Composable get() = LocalWish.current.text
    val TextDim: Color @Composable get() = LocalWish.current.textDim
    val Success: Color @Composable get() = LocalWish.current.success
}

/** [dark] false gives the light WishIssue set. The classic layouts are always dark, so callers pass true for them. */
@Composable
fun LiteTvTheme(accent: Color, dark: Boolean = true, content: @Composable () -> Unit) {
    val p = if (dark) WishPalette.Dark else WishPalette.Light
    CompositionLocalProvider(LocalWish provides p) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = accent, onPrimary = p.onPrimary,
                    surface = p.bg, onSurface = p.text,
                    surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
                    background = p.bg, onBackground = p.text,
                )
            } else {
                lightColorScheme(
                    primary = p.primary, onPrimary = p.onPrimary,
                    surface = p.bg, onSurface = p.text,
                    surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
                    background = p.bg, onBackground = p.text,
                )
            },
            content = content,
        )
    }
}
