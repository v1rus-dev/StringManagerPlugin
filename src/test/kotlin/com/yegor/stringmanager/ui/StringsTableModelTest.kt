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
        // Key, two locales and the translatable flag.
        assertEquals(4, model.columnCount)
        assertEquals("greeting", model.getValueAt(0, 0))
        assertEquals("Hello", model.getValueAt(0, 1))
        assertEquals("Привет", model.getValueAt(0, 2))
        assertEquals(true, model.getValueAt(0, 3))
    }

    fun testShowsTranslatableFlag() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string>" +
                "<string name=\"locked\" translatable=\"false\">Locked</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        assertTrue(model.isTranslatable("greeting"))
        assertFalse(model.isTranslatable("locked"))
        assertEquals(Boolean::class.java, model.getColumnClass(2))
    }

    fun testChangesTranslatableFlag() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        var changed: String? = null
        model.onTranslatable = { key, _ -> changed = key }
        model.setValueAt(false, 0, 2)
        assertEquals("greeting", changed)
    }

    fun testRenamesKey() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val model = StringsTableModel()
        model.setSnapshot(service.snapshot())
        var renamed: Pair<String, String>? = null
        model.onRename = { old, new -> renamed = old to new }
        model.setValueAt("hello", 0, 0)
        assertEquals("greeting" to "hello", renamed)
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

    fun testKeepsWidthOfColumns() {
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
        // The columns are wide enough to show the values, so that the table is scrolled horizontally.
        assertTrue(model.columnWidth(0) >= 120)
        assertTrue(model.columnWidth(1) >= 120)
        assertTrue(model.columnWidth(1) <= 400)
    }
}
