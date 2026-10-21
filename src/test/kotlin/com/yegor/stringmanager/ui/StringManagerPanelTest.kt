package com.yegor.stringmanager.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.yegor.stringmanager.core.StringResourceService
import javax.swing.JTable

/**
 * Tests for [StringManagerPanel].
 */
class StringManagerPanelTest : BasePlatformTestCase() {

    fun testTableIsScrolledHorizontally() {
        val panel = StringManagerPanel(project)
        val table = panel.stringsTable
        // The table keeps the width of its columns, so that the locales are not squeezed.
        assertEquals(JTable.AUTO_RESIZE_OFF, table.autoResizeMode)
        // The viewport does not stretch the table, that is what makes the horizontal scrollbar appear.
        assertFalse(table.getScrollableTracksViewportWidth())
    }

    fun testColumnWithKeysIsFrozen() {
        val panel = StringManagerPanel(project)
        // The column with the keys is not in the table of the locales, the first one is a locale ...
        assertEquals(1, panel.stringsTable.columnModel.getColumn(0).modelIndex)
        // ... it is shown in the row header, so that it does not move with the horizontal scroll.
        assertSame(panel.keysTable, panel.stringsScroll.rowHeader?.view)
        assertEquals(1, panel.keysTable.columnCount)
        assertEquals(0, panel.keysTable.columnModel.getColumn(0).modelIndex)
    }

    fun testColumnWithKeysStaysFrozenAfterSearch() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string><string name=\"bye\">Bye</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val panel = StringManagerPanel(project)
        panel.searchField.text = "greeting"
        // The search rebuilds the columns of the table, the keys stay in the row header.
        assertEquals(1, panel.stringsTable.columnModel.getColumn(0).modelIndex)
        assertEquals(1, panel.keysTable.columnCount)
    }

    fun testRowHeaderTakesTheWidthOfTheKeys() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val panel = StringManagerPanel(project)
        val width = panel.keysTable.columnModel.getColumn(0).preferredWidth
        // The row header is as wide as the column with the keys, otherwise it squeezes the locales.
        assertEquals(width, panel.stringsScroll.rowHeader.preferredSize.width)
    }

    fun testSearchFiltersRows() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string><string name=\"bye\">Bye</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val panel = StringManagerPanel(project)
        val model = panel.stringsTable.model as StringsTableModel
        panel.searchField.text = "greeting"
        assertEquals(listOf("greeting"), model.keys)
        panel.searchField.text = "hello"
        assertEquals(listOf("greeting"), model.keys)
        panel.searchField.text = ""
        assertEquals(listOf("bye", "greeting"), model.keys)
    }

    fun testTranslatableFlagIsShownImmediately() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val service = StringResourceService.getInstance(project)
        service.refresh()
        val panel = StringManagerPanel(project)
        val model = panel.stringsTable.model as StringsTableModel
        val column = model.translatableColumn()
        assertEquals(true, model.getValueAt(0, column))
        model.setValueAt(false, 0, column)
        // The flag is shown right away, the files are rescanned in the background.
        assertEquals(false, model.getValueAt(0, column))
    }
}
