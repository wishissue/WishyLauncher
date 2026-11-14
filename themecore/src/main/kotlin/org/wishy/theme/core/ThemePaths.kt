package org.wishy.theme.core

/** Rules for file names inside a theme. A theme can only ever name files inside its own folder. */
object ThemePaths {
    private val SEGMENT = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*$")
    private const val MAX_PATH = 160

    /** True for a relative path made of plain segments: no leading slash, no "..", no backslash, no hidden segment. */
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_PATH) return false
        if (path.startsWith("/") || path.contains('\\') || path.contains('\u0000') || path.contains("..")) return false
        return path.split('/').all { it.isNotEmpty() && SEGMENT.matches(it) }
    }

    fun extension(path: String): String = path.substringAfterLast('.', "").lowercase()

    /** True if the path is safe AND is theme.json, CREDITS.md, or an image or font by extension. */
    fun isAllowedFile(path: String): Boolean {
        if (!isSafeRelativePath(path)) return false
        if (path in ThemeLimits.SPECIAL_FILES) return true
        val ext = extension(path)
        return ext in ThemeLimits.IMAGE_EXTENSIONS || ext in ThemeLimits.FONT_EXTENSIONS
    }

    /** Size limit for one file, by type. Only call with a path that passed [isAllowedFile]. */
    fun maxBytesFor(path: String): Long {
        if (path == "theme.json") return ThemeLimits.MAX_THEME_JSON_BYTES
        if (path == "CREDITS.md") return ThemeLimits.MAX_TEXT_FILE_BYTES
        val ext = extension(path)
        return if (ext in ThemeLimits.FONT_EXTENSIONS) ThemeLimits.MAX_FONT_BYTES else ThemeLimits.MAX_IMAGE_BYTES
    }
}
