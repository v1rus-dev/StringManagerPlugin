package com.yegor.stringmanager.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Triggers a refresh of the snapshot when files of the project change on disk.
 */
class StringResourceListener(private val service: StringResourceService) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        if (events.any { it.path.endsWith(XML_EXTENSION) }) {
            // The listener is called inside a write action, the scan is started outside of it.
            ApplicationManager.getApplication().invokeLater { service.refreshAsync() }
        }
    }

    companion object {
        private const val XML_EXTENSION = ".xml"
    }
}
