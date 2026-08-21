package com.github.fanziyun.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemVerTest {

    @Test
    fun `compares core version numbers numerically`() {
        assertTrue(SemVer.compare("1.10.0", "1.9.0") > 0)
        assertEquals(0, SemVer.compare("1.2", "1.2.0"))
    }

    @Test
    fun `ignores version prefix and build metadata`() {
        assertEquals(0, SemVer.compare("v1.2.3+fabric", "1.2.3+neoforge"))
        assertEquals(0, SemVer.compare("V2.0", "2.0.0"))
    }

    @Test
    fun `orders pre-release versions before a release`() {
        val versions = listOf(
            "1.0.0",
            "1.0.0-rc.1",
            "1.0.0-beta.11",
            "1.0.0-beta.2",
            "1.0.0-alpha",
        )

        assertEquals(
            listOf(
                "1.0.0-alpha",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0",
            ),
            versions.sortedWith(SemVer.COMPARATOR),
        )
    }

    @Test
    fun `sorts valid versions after invalid version labels`() {
        assertTrue(SemVer.compare("1.0.0", "snapshot") > 0)
        assertTrue(SemVer.compare("snapshot-b", "snapshot-a") > 0)
    }
}
