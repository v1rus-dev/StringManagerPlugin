package com.yegor.stringmanager.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.yegor.stringmanager.core.model.ResourceOrigin

/**
 * Tests for [ResourceDiscovery].
 */
class ResourceDiscoveryTest : BasePlatformTestCase() {

    fun testFindsAndroidAndComposeFiles() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        myFixture.addFileToProject(
            "app/src/main/res/values-ru/sheet.xml",
            "<resources><string name=\"greeting\">Привет</string></resources>",
        )
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val files = ResourceDiscovery(project).discover()
        assertEquals(3, files.size)
        assertTrue(files.any { it.path.endsWith("app/src/main/res/values/strings.xml") })
        assertTrue(files.any { it.path.endsWith("app/src/main/res/values-ru/sheet.xml") })
        assertTrue(files.any { it.path.endsWith("shared/src/commonMain/composeResources/values/strings.xml") })
        val android = files.first { it.path.endsWith("values-ru/sheet.xml") }
        assertEquals(ResourceOrigin.ANDROID_RES, android.origin)
        assertEquals("ru", android.locale.language)
        val compose = files.first { it.path.contains("composeResources") }
        assertEquals(ResourceOrigin.COMPOSE_RESOURCES, compose.origin)
        assertNull(compose.locale.language)
    }

    fun testFindsAllXmlFilesOfValuesDirectory() {
        myFixture.addFileToProject(
            "app/src/main/res/values/colors.xml",
            "<resources><color name=\"primary\">#fff</color></resources>",
        )
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            "<resources><string name=\"greeting\">Hello</string></resources>",
        )
        val files = ResourceDiscovery(project).discover()
        assertEquals(2, files.size)
        assertTrue(files.any { it.path.endsWith("values/colors.xml") })
        assertTrue(files.any { it.path.endsWith("values/strings.xml") })
    }
}
