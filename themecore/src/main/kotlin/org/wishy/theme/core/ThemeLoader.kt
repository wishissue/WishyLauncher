package org.wishy.theme.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.File

/** Facts about the current screen that theme "overrides" can react to. */
data class LoadContext(
    val aspect: Float,
    val heightPx: Int,
    val colorScheme: ColorSchemeName,
    val reducedMotion: Boolean,
)

class LoadedTheme(val doc: ThemeDoc, val dir: File) {
    val id: String get() = doc.id
}

/** Small summary for lists, readable even when the full theme is not valid for this screen. */
data class ThemeSummary(val id: String, val name: String, val version: String, val author: String, val description: String, val license: String)

object ThemeLoader {
    private val ALLOWED_WHEN = setOf("minAspect", "maxAspect", "minHeightPx", "maxHeightPx", "colorScheme", "reducedMotion")

    /** Loads, applies matching overrides, decodes strictly and checks. Throws [ThemeRejectedException] with a readable reason. */
    fun load(dir: File, ctx: LoadContext): LoadedTheme {
        val file = File(dir, "theme.json")
        if (!file.isFile) throw ThemeRejectedException("theme.json is missing.")
        if (file.length() > ThemeLimits.MAX_THEME_JSON_BYTES) throw ThemeRejectedException("theme.json is too large.")

        var root: JsonObject = parseObject(file.readText(Charsets.UTF_8))
        root = JsonObject(root.filterKeys { it != "\$schema" })

        val overrides = root["overrides"]
        if (overrides != null) {
            val list = overrides as? JsonArray ?: throw ThemeRejectedException("overrides must be a list.")
            if (list.size > 12) throw ThemeRejectedException("Too many overrides.")
            for (entry in list) {
                val o = entry as? JsonObject ?: throw ThemeRejectedException("An override must be an object.")
                val unknown = o.keys - setOf("when", "merge")
                if (unknown.isNotEmpty()) throw ThemeRejectedException("Unknown override keys: $unknown")
                val whenObj = o["when"] as? JsonObject ?: throw ThemeRejectedException("An override needs a 'when' object.")
                val merge = o["merge"] ?: throw ThemeRejectedException("An override needs a 'merge' object.")
                if (matches(whenObj, ctx)) root = MergePatch.applyOverride(root, merge)
            }
        }
        root = JsonObject(root.filterKeys { it != "overrides" })

        val doc = try {
            ThemeJson.decodeFromJsonElement(ThemeDoc.serializer(), root)
        } catch (e: SerializationException) {
            throw ThemeRejectedException("theme.json is not valid: " + (e.message ?: "unknown error").lineSequence().first().take(200))
        } catch (e: IllegalArgumentException) {
            throw ThemeRejectedException("theme.json is not valid: " + (e.message ?: "unknown error").lineSequence().first().take(200))
        }
        ThemeChecker.check(doc, dir)
        return LoadedTheme(doc, dir)
    }

    /** Reads only the descriptive fields. Returns null if the file is unreadable or the id is not valid. */
    fun readSummary(dir: File): ThemeSummary? {
        return try {
            val file = File(dir, "theme.json")
            if (!file.isFile || file.length() > ThemeLimits.MAX_THEME_JSON_BYTES) return null
            val o = parseObject(file.readText(Charsets.UTF_8))
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val id = s("id") ?: return null
            if (!ThemeLimits.ID_REGEX.matches(id)) return null
            ThemeSummary(
                id = id,
                name = s("name")?.take(48) ?: id,
                version = s("version")?.takeIf { ThemeLimits.VERSION_REGEX.matches(it) } ?: "0.0.0",
                author = s("author")?.take(80) ?: "",
                description = s("description")?.take(400) ?: "",
                license = s("license")?.take(64) ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseObject(text: String): JsonObject {
        val el = try {
            ThemeJson.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ThemeRejectedException("theme.json is not valid JSON.")
        }
        return el as? JsonObject ?: throw ThemeRejectedException("theme.json must contain a JSON object.")
    }

    private fun matches(w: JsonObject, ctx: LoadContext): Boolean {
        val unknown = w.keys - ALLOWED_WHEN
        if (unknown.isNotEmpty()) throw ThemeRejectedException("Unknown override condition: $unknown")
        for ((k, v) in w) {
            val ok = when (k) {
                "minAspect" -> ctx.aspect >= num(v)
                "maxAspect" -> ctx.aspect <= num(v)
                "minHeightPx" -> ctx.heightPx >= num(v)
                "maxHeightPx" -> ctx.heightPx <= num(v)
                "colorScheme" -> (v as? JsonPrimitive)?.contentOrNull == if (ctx.colorScheme == ColorSchemeName.DARK) "dark" else "light"
                "reducedMotion" -> (v as? JsonPrimitive)?.booleanOrNull == ctx.reducedMotion
                else -> false
            }
            if (!ok) return false
        }
        return true
    }

    private fun num(v: JsonElement): Double =
        (v as? JsonPrimitive)?.doubleOrNull ?: throw ThemeRejectedException("An override condition must be a number.")
}
