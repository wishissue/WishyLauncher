package org.wishy.theme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileHashTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun knownDigest() {
        val f = tmp.newFile().apply { writeText("abc") }
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, FileHash.sha256Hex(f))
        FileHash.verifySha256(f, expected.uppercase())
    }

    @Test fun mismatchAndMalformedAreRejected() {
        val f = tmp.newFile().apply { writeText("abc") }
        try { FileHash.verifySha256(f, "0".repeat(64)); fail() } catch (e: ThemeRejectedException) { }
        try { FileHash.verifySha256(f, "zz"); fail() } catch (e: ThemeRejectedException) { }
    }
}
