package com.yegor.stringmanager.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.yegor.stringmanager.core.model.ResourceOrigin

/**
 * Tests for [StringResourceWriter]: checks that PSI keeps formatting, comments and the diff minimal.
 */
class StringResourceWriterTest : BasePlatformTestCase() {

    private lateinit var writer: StringResourceWriter

    override fun setUp() {
        super.setUp()
        writer = StringResourceWriter(project)
    }

    fun testSetValueKeepsFormattingAndComments() {
        val file = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <!-- greeting shown on the main screen -->
                <string name="greeting">Hello</string>
                <string name="bye">Bye</string>
            </resources>
            """.trimIndent(),
        )
        writer.setValue(file.virtualFile, "greeting", "Hi there", ResourceOrigin.ANDROID_RES)
        assertEquals(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <!-- greeting shown on the main screen -->
                <string name="greeting">Hi there</string>
                <string name="bye">Bye</string>
            </resources>
            """.trimIndent(),
            file.text,
        )
    }

    fun testAddStringKeepsFormatting() {
        val file = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="greeting">Hello</string>
            </resources>
            """.trimIndent(),
        )
        writer.addString(file.virtualFile, "farewell", "Bye", ResourceOrigin.ANDROID_RES)
        assertEquals(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="greeting">Hello</string>
                <string name="farewell">Bye</string>
            </resources>
            """.trimIndent(),
            file.text,
        )
    }

    fun testRemoveString() {
        val file = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="greeting">Hello</string>
                <string name="bye">Bye</string>
            </resources>
            """.trimIndent(),
        )
        writer.removeString(file.virtualFile, "greeting")
        assertEquals(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="bye">Bye</string>
            </resources>
            """.trimIndent(),
            file.text,
        )
    }

    fun testSetTranslatable() {
        val file = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """
            <resources>
                <string name="greeting">Hello</string>
            </resources>
            """.trimIndent(),
        )
        writer.setTranslatable(file.virtualFile, "greeting", false)
        assertTrue(file.text.contains("translatable=\"false\""))
        writer.setTranslatable(file.virtualFile, "greeting", true)
        assertFalse(file.text.contains("translatable"))
    }

    fun testReadValues() {
        val file = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """
            <resources>
                <string name="greeting">Hello</string>
                <string name="locked" translatable="false">Locked</string>
            </resources>
            """.trimIndent(),
        )
        val values = writer.read(file.virtualFile)
        assertEquals(2, values.size)
        assertEquals("Hello", values.first { it.first == "greeting" }.second.value)
        assertTrue(values.first { it.first == "greeting" }.second.translatable)
        assertFalse(values.first { it.first == "locked" }.second.translatable)
    }
}
