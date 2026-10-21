package com.yegor.stringmanager.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Tests for the icon of the tool window.
 */
class ToolWindowIconTest : BasePlatformTestCase() {

    fun testIconIsPresent() {
        val light = javaClass.getResource("/icons/toolWindow.svg")
        assertNotNull("Light icon is missing", light)
        val dark = javaClass.getResource("/icons/toolWindow_dark.svg")
        assertNotNull("Dark icon is missing", dark)
    }

    fun testIconIsValidSvg() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/icons/toolWindow.svg"))
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        val root = document.documentElement
        assertEquals("svg", root.tagName)
        assertEquals("16", root.getAttribute("width"))
        assertEquals("16", root.getAttribute("height"))
        assertTrue("Icon must contain lines", root.getElementsByTagName("path").length >= 3)
    }

    fun testIconIsRegisteredInPluginXml() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/META-INF/plugin.xml"))
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        val nodes = document.getElementsByTagName("toolWindow")
        assertEquals(1, nodes.length)
        val toolWindow = nodes.item(0) as Element
        assertEquals("/icons/toolWindow.svg", toolWindow.getAttribute("icon"))
    }
}
