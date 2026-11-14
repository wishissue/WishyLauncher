package org.wishy.theme.core

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Extracts a theme zip into an empty folder. Every entry name is checked, every file is
 * size-limited while it is written, and the total is limited by bytes, count and compression ratio.
 * Only regular files are written; zip entries can never create links.
 */
object SafeUnzip {
    fun extract(zip: File, destDir: File) {
        if (!destDir.isDirectory || (destDir.list()?.isNotEmpty() != false)) {
            throw IllegalArgumentException("Destination must be an existing, empty folder.")
        }
        val zipSize = zip.length()
        if (zipSize <= 0 || zipSize > ThemeLimits.MAX_ZIP_BYTES) {
            throw ThemeRejectedException("The theme file is empty or too large.")
        }
        val root = destDir.canonicalFile
        val rootPrefix = root.path + File.separator
        var files = 0
        var total = 0L

        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) {
                    val dirName = name.trimEnd('/')
                    if (dirName.isNotEmpty() && !ThemePaths.isSafeRelativePath(dirName)) {
                        throw ThemeRejectedException("Unsafe folder name in the theme.")
                    }
                    zin.closeEntry()
                    continue
                }
                if (!ThemePaths.isAllowedFile(name)) {
                    throw ThemeRejectedException("File type or name not allowed in a theme: $name")
                }
                files++
                if (files > ThemeLimits.MAX_FILES) throw ThemeRejectedException("The theme has too many files.")

                val target = File(root, name).canonicalFile
                if (!target.path.startsWith(rootPrefix)) throw ThemeRejectedException("Unsafe path in the theme.")
                if (target.exists()) throw ThemeRejectedException("The theme contains a file twice: $name")
                target.parentFile?.mkdirs()

                val limit = ThemePaths.maxBytesFor(name)
                var written = 0L
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        written += n
                        total += n
                        if (written > limit) throw ThemeRejectedException("A file in the theme is too large: $name")
                        if (total > ThemeLimits.MAX_TOTAL_UNPACKED_BYTES) {
                            throw ThemeRejectedException("The theme is too large when unpacked.")
                        }
                        if (total > zipSize * ThemeLimits.MAX_COMPRESSION_RATIO) {
                            throw ThemeRejectedException("The theme is compressed suspiciously well.")
                        }
                        out.write(buf, 0, n)
                    }
                }
                zin.closeEntry()
            }
        }
        if (!File(root, "theme.json").isFile) throw ThemeRejectedException("The theme has no theme.json.")
    }
}
