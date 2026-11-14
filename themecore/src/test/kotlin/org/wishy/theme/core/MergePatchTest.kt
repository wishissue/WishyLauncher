package org.wishy.theme.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MergePatchTest {
    private fun j(s: String) = Json.parseToJsonElement(s)

    @Test fun rfc7396Examples() {
        val target = j("""{"a":"b","c":{"d":"e","f":"g"}}""")
        val patch = j("""{"a":"z","c":{"f":null}}""")
        assertEquals(j("""{"a":"z","c":{"d":"e"}}"""), MergePatch.apply(target, patch))
    }

    @Test fun arraysAreReplacedWhole() {
        assertEquals(j("""{"a":[2]}"""), MergePatch.apply(j("""{"a":[1,2,3]}"""), j("""{"a":[2]}""")))
    }

    @Test fun nonObjectPatchReplacesTarget() {
        assertEquals(j("""[1]"""), MergePatch.apply(j("""{"a":1}"""), j("""[1]""")))
    }

    @Test fun nullsInNewObjectsAreDropped() {
        assertEquals(j("""{"a":{"b":1}}"""), MergePatch.apply(j("""{}"""), j("""{"a":{"b":1,"c":null}}""")))
    }

    @Test fun overrideCannotTouchProtectedKeys() {
        val doc = j("""{"id":"x","motion":{"focusMillis":200}}""") as JsonObject
        try {
            MergePatch.applyOverride(doc, j("""{"id":"y"}"""))
            fail("expected rejection")
        } catch (e: ThemeRejectedException) {
            // expected
        }
        assertEquals(
            j("""{"id":"x","motion":{"focusMillis":0}}"""),
            MergePatch.applyOverride(doc, j("""{"motion":{"focusMillis":0}}""")),
        )
    }
}
