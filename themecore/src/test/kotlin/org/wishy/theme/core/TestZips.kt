package org.wishy.theme.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object TestZips {
    fun make(dir: File, name: String, entries: Map<String, ByteArray>): File {
        val f = File(dir, name)
        ZipOutputStream(f.outputStream()).use { zos ->
            for ((entryName, bytes) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return f
    }

    fun themeJson(id: String, version: String): ByteArray =
        """{"schemaVersion":1,"id":"$id","version":"$version"}""".toByteArray()
}
