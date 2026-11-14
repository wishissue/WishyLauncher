package org.wishy.theme.core

import java.io.File
import java.security.MessageDigest

object FileHash {
    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Compares case-insensitively. Throws if the file does not match [expectedHex]. */
    fun verifySha256(file: File, expectedHex: String) {
        val expected = expectedHex.trim().lowercase()
        if (!Regex("^[0-9a-f]{64}$").matches(expected)) {
            throw ThemeRejectedException("The repository gave an invalid checksum.")
        }
        if (sha256Hex(file) != expected) {
            throw ThemeRejectedException("The download does not match its checksum.")
        }
    }
}
