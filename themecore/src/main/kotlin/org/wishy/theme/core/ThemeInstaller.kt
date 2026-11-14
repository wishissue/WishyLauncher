package org.wishy.theme.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Installs, updates and removes themes under [root]/themes, staging under [root]/staging.
 * Both folders are under the same parent so a folder rename stays on one file system and is atomic.
 *
 * Install order: unpack into a new staging folder, check, then swap folders. If anything fails
 * before the swap, the installed copy is not touched. If the swap itself fails, the old copy is put back.
 */
class ThemeInstaller(root: File) {
    val themesDir = File(root, "themes")
    private val stagingDir = File(root, "staging")

    /** Call once at start-up: finishes or undoes a swap that was cut short, and clears staging. */
    fun recoverLeftovers() {
        themesDir.mkdirs()
        themesDir.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name.endsWith(".old")) {
                val live = File(themesDir, f.name.removeSuffix(".old"))
                if (live.exists()) f.deleteRecursively() else move(f, live)
            }
        }
        stagingDir.deleteRecursively()
    }

    fun installedDir(id: String): File? {
        requireValidId(id)
        return File(themesDir, id).takeIf { File(it, "theme.json").isFile }
    }

    /**
     * Installs [zip] as theme [expectedId] at [expectedVersion]. The caller has already checked the hash.
     * [validate] runs on the unpacked folder (full theme checks) and throws [ThemeRejectedException] to refuse it.
     * Returns the installed folder.
     */
    fun install(zip: File, expectedId: String, expectedVersion: String, validate: (File) -> Unit = {}): File {
        requireValidId(expectedId)
        if (!ThemeLimits.VERSION_REGEX.matches(expectedVersion)) {
            throw ThemeRejectedException("Invalid version in the repository entry.")
        }
        themesDir.mkdirs()
        stagingDir.mkdirs()
        val work = File(stagingDir, "$expectedId-${System.nanoTime()}")
        if (!work.mkdirs()) throw ThemeRejectedException("Could not create a staging folder.")
        try {
            SafeUnzip.extract(zip, work)
            checkManifestMatches(work, expectedId, expectedVersion)
            validate(work)
            return swapIn(work, expectedId)
        } finally {
            work.deleteRecursively()
        }
    }

    fun remove(id: String) {
        requireValidId(id)
        File(themesDir, id).deleteRecursively()
        File(themesDir, "$id.old").deleteRecursively()
    }

    private fun swapIn(work: File, id: String): File {
        val live = File(themesDir, id)
        val old = File(themesDir, "$id.old")
        if (old.exists()) old.deleteRecursively()
        val hadLive = live.exists()
        if (hadLive) move(live, old)
        try {
            move(work, live)
        } catch (e: Exception) {
            if (hadLive) {
                live.deleteRecursively()
                move(old, live)
            }
            throw ThemeRejectedException("Could not install the theme: " + (e.message ?: "unknown error"))
        }
        if (hadLive) old.deleteRecursively()
        return live
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun checkManifestMatches(dir: File, id: String, version: String) {
        val file = File(dir, "theme.json")
        if (file.length() > ThemeLimits.MAX_THEME_JSON_BYTES) throw ThemeRejectedException("theme.json is too large.")
        var parsed: JsonObject? = null
        try {
            parsed = Json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
        } catch (e: Exception) {
            parsed = null
        }
        val obj = parsed ?: throw ThemeRejectedException("theme.json is not valid JSON.")
        val foundId = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        val foundVersion = (obj["version"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        if (foundId != id) throw ThemeRejectedException("The theme's id does not match the repository entry.")
        if (foundVersion != version) throw ThemeRejectedException("The theme's version does not match the repository entry.")
    }

    private fun requireValidId(id: String) {
        if (!ThemeLimits.ID_REGEX.matches(id)) throw ThemeRejectedException("Invalid theme id.")
    }
}
