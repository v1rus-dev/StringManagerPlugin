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
}
