package com.yegor.stringmanager.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
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
}
