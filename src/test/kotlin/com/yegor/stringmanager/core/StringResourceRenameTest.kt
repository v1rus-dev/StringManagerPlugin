package com.yegor.stringmanager.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.yegor.stringmanager.core.model.ResourceOrigin

/**
 * Tests for renaming of keys and for the `translatable` flag of a key.
 */
class StringResourceRenameTest : BasePlatformTestCase() {

    fun testRenamesKeyInAllFiles() {
        val default = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """
            <resources>
                <string name="greeting">Hello</string>
                <string name="bye">Bye</string>
            </resources>
            """.trimIndent(),
        )
        val russian = myFixture.addFileToProject(
            "app/src/main/res/values-ru/strings.xml",
            """
            <resources>
                <string name="greeting">Привет</string>
            </resources>
            """.trimIndent(),
        )
        val writer = StringResourceWriter(project)
        writer.renameKey(listOf(default.virtualFile, russian.virtualFile), "greeting", "hello")
        assertTrue(default.text.contains("name=\"hello\""))
        assertFalse(default.text.contains("name=\"greeting\""))
        assertTrue(russian.text.contains("name=\"hello\""))
        assertTrue(default.text.contains("name=\"bye\""))
    }

    fun testSetsTranslatableInAllFiles() {
        val default = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val russian = myFixture.addFileToProject(
            "app/src/main/res/values-ru/strings.xml",
            "<resources><string name=\"greeting\">Привет</string></resources>",
        )
        val writer = StringResourceWriter(project)
        writer.setTranslatable(listOf(default.virtualFile, russian.virtualFile), "greeting", false)
        assertTrue(default.text.contains("translatable=\"false\""))
        assertTrue(russian.text.contains("translatable=\"false\""))
        writer.setTranslatable(listOf(default.virtualFile, russian.virtualFile), "greeting", true)
        assertFalse(default.text.contains("translatable"))
        assertFalse(russian.text.contains("translatable"))
    }

    fun testServiceRenamesKeyInAllTranslations() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        myFixture.addFileToProject(
            "app/src/main/res/values-ru/strings.xml",
            "<resources><string name=\"greeting\">Привет</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        service.renameKey("greeting", "hello")
        val snapshot = service.snapshot()
        assertTrue(snapshot.entries.containsKey("hello"))
        assertFalse(snapshot.entries.containsKey("greeting"))
        assertEquals(1, snapshot.entriesOf("hello", "default").size)
        assertEquals(1, snapshot.entriesOf("hello", "ru").size)
    }

    fun testServiceMarksKeyAsNotTranslatable() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        myFixture.addFileToProject(
            "app/src/main/res/values-ru/strings.xml",
            "<resources><string name=\"greeting\">Привет</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        service.setTranslatable("greeting", false)
        val snapshot = service.snapshot()
        assertFalse(snapshot.entriesOf("greeting", "default").single().translatable)
        assertFalse(snapshot.entriesOf("greeting", "ru").single().translatable)
    }

    fun testKeepsOriginOfFile() {
        val file = myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val writer = StringResourceWriter(project)
        writer.setValue(file.virtualFile, "greeting", "Hi", ResourceOrigin.COMPOSE_RESOURCES)
        assertTrue(file.text.contains("Hi"))
    }
}
