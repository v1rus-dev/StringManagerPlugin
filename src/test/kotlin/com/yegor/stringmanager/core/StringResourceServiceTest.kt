package com.yegor.stringmanager.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [StringResourceService].
 */
class StringResourceServiceTest : BasePlatformTestCase() {

    fun testSkipsFilesWithoutStrings() {
        myFixture.addFileToProject(
            "app/src/main/res/values/colors.xml",
            "<resources><color name=\"primary\">#fff</color></resources>",
        )
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val snapshot = service.snapshot()
        assertEquals(1, snapshot.files.size)
        assertTrue(snapshot.files.single().path.endsWith("values/strings.xml"))
        assertEquals(setOf("greeting"), snapshot.entries.keys)
    }

    fun testDetectsMissingTranslation() {
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
        val snapshot = service.snapshot()
        assertEquals(1, snapshot.entriesOf("greeting", "default").size)
        assertEquals(1, snapshot.entriesOf("greeting", "ru").size)
        assertTrue(snapshot.entriesOf("greeting", "de").isEmpty())
    }

    fun testWarnsAboutScriptWithoutBaseLanguage() {
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values-b+zh+Hans/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        assertEquals(1, service.snapshot().warnings.size)
        assertTrue(service.snapshot().warnings.single().contains("values-zh"))
    }

    fun testDoesNotWarnWhenBaseLanguageExists() {
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values-zh/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values-b+zh+Hans/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        assertTrue(service.snapshot().warnings.isEmpty())
    }
}
