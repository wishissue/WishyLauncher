package org.wishy.theme.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** RFC 7396 JSON Merge Patch, used by theme "overrides". */
object MergePatch {
    /** Top-level keys an override may not change. */
    val PROTECTED_KEYS: Set<String> = setOf("schemaVersion", "id", "version", "license", "credits", "overrides")

    fun apply(target: JsonElement, patch: JsonElement): JsonElement {
        if (patch !is JsonObject) return patch
        val result = LinkedHashMap<String, JsonElement>()
        if (target is JsonObject) result.putAll(target)
        for ((key, value) in patch) {
            if (value is JsonNull) {
                result.remove(key)
            } else {
                result[key] = apply(result[key] ?: JsonNull, value)
            }
        }
        return JsonObject(result)
    }

    /** Applies [patch] to a theme document, refusing patches that touch protected keys. */
    fun applyOverride(document: JsonObject, patch: JsonElement): JsonObject {
        if (patch !is JsonObject) throw ThemeRejectedException("An override must be a JSON object.")
        val touched = patch.keys.intersect(PROTECTED_KEYS)
        if (touched.isNotEmpty()) {
            throw ThemeRejectedException("An override may not change: " + touched.sorted().joinToString(", "))
        }
        return apply(document, patch) as JsonObject
    }
}
