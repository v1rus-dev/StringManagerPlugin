package com.yegor.stringmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [LocaleQualifiers].
 */
class LocaleQualifiersTest {

    @Test
    fun `parses default directory`() {
        val locale = LocaleQualifiers.parse("values")
        assertNull(locale.language)
        assertNull(locale.region)
        assertNull(locale.script)
        assertEquals("default", locale.displayName)
    }

    @Test
    fun `parses language`() {
        val locale = LocaleQualifiers.parse("values-ru")
        assertEquals("ru", locale.language)
        assertNull(locale.region)
        assertEquals("ru", locale.displayName)
    }

    @Test
    fun `parses language and region`() {
        val locale = LocaleQualifiers.parse("values-ru-rRU")
        assertEquals("ru", locale.language)
        assertEquals("RU", locale.region)
        assertEquals("ru-RU", locale.displayName)
    }

    @Test
    fun `parses numeric region`() {
        val locale = LocaleQualifiers.parse("values-es-r419")
        assertEquals("es", locale.language)
        assertEquals("419", locale.region)
    }

    @Test
    fun `parses bcp47 script`() {
        val locale = LocaleQualifiers.parse("values-b+zh+Hans")
        assertEquals("zh", locale.language)
        assertEquals("Hans", locale.script)
        assertNull(locale.region)
        assertEquals(true, locale.bcp47)
        assertEquals("zh-Hans", locale.displayName)
    }

    @Test
    fun `parses bcp47 script and region`() {
        val locale = LocaleQualifiers.parse("values-b+zh+Hant+TW")
        assertEquals("zh", locale.language)
        assertEquals("Hant", locale.script)
        assertEquals("TW", locale.region)
    }

    @Test
    fun `ignores non locale qualifiers`() {
        assertNull(LocaleQualifiers.parse("values-night").language)
        assertNull(LocaleQualifiers.parse("values-v26").language)
        assertNull(LocaleQualifiers.parse("values-sw600dp").language)
        assertNull(LocaleQualifiers.parse("values-land").language)
    }

    @Test
    fun `ignores mobile country code`() {
        val locale = LocaleQualifiers.parse("values-mcc310-ru")
        assertEquals("ru", locale.language)
    }
}
