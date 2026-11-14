package org.wishy.theme.core

/** Every size and count limit for themes, in one place. Mirrors THEME_SCHEMA.md section 1. */
object ThemeLimits {
    const val MAX_THEME_JSON_BYTES = 256L * 1024
    const val MAX_NODES = 400
    const val MAX_DEPTH = 12
    const val MAX_FILES = 200
    const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    const val MAX_FONT_BYTES = 2L * 1024 * 1024
    const val MAX_TEXT_FILE_BYTES = 256L * 1024
    const val MAX_IMAGE_DIMENSION = 4096
    const val MAX_TOTAL_UNPACKED_BYTES = 40L * 1024 * 1024
    const val MAX_ZIP_BYTES = 20L * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 100L

    val IMAGE_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp")
    val FONT_EXTENSIONS: Set<String> = setOf("ttf", "otf")
    val SPECIAL_FILES: Set<String> = setOf("theme.json", "CREDITS.md")

    val ID_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,39}$")
    val VERSION_REGEX = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
}

/** Thrown when a theme, zip or manifest breaks a rule. The message is safe to show to the user. */
class ThemeRejectedException(message: String) : Exception(message)
