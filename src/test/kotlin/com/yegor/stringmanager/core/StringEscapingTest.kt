package com.yegor.stringmanager.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [StringEscaping].
 */
class StringEscapingTest {

    @Test
    fun `escapes quotes for android`() {
        assertEquals(
            "It\\'s",
            StringEscaping.escape("It's", com.yegor.stringmanager.core.model.ResourceOrigin.ANDROID_RES),
        )
    }

    @Test
    fun `does not escape quotes for compose`() {
        assertEquals(
            "It's",
            StringEscaping.escape("It's", com.yegor.stringmanager.core.model.ResourceOrigin.COMPOSE_RESOURCES),
        )
    }

    @Test
    fun `restores android value`() {
        assertEquals(
            "It's",
            StringEscaping.unescape(
                "It\\'s",
                com.yegor.stringmanager.core.model.ResourceOrigin.ANDROID_RES,
            ),
        )
    }

    @Test
    fun `keeps android escape sequences`() {
        assertEquals(
            "first line\\nsecond",
            StringEscaping.unescape(
                "first line\\nsecond",
                com.yegor.stringmanager.core.model.ResourceOrigin.ANDROID_RES,
            ),
        )
    }
}
