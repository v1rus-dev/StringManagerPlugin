package com.yegor.stringmanager.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.yegor.stringmanager.core.StringResourceService

/**
 * Tests for [StringsTableModel].
 */
class StringsTableModelTest : BasePlatformTestCase() {

    fun testShowsKeysAndLocales() {
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
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        assertEquals(listOf("greeting"), model.keys)
        assertEquals(listOf("default", "ru"), model.locales)
        assertEquals(3, model.columnCount)
        assertEquals("greeting", model.getValueAt(0, 0))
        assertEquals("Hello", model.getValueAt(0, 1))
        assertEquals("Привет", model.getValueAt(0, 2))
    }

    fun testFiltersMissingTranslations() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string><string name=\"bye\">Bye</string></resources>",
        )
        myFixture.addFileToProject(
            "app/src/main/res/values-ru/strings.xml",
            "<resources><string name=\"greeting\">Привет</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        model.setStatusFilter(StatusFilter.MISSING)
        assertEquals(listOf("bye"), model.keys)
    }

    fun testFiltersNonTranslatable() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"locked\" translatable=\"false\">Locked</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        model.setStatusFilter(StatusFilter.NON_TRANSLATABLE)
        assertEquals(listOf("locked"), model.keys)
        model.setStatusFilter(StatusFilter.MISSING)
        assertEquals(emptyList<String>(), model.keys)
    }

    fun testFiltersByModule() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        model.setModuleFilter("another-module")
        assertEquals(emptyList<String>(), model.keys)
    }
}
