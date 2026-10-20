package com.yegor.stringmanager.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Starts the live update of the string resources.
 */
class StringManagerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = StringResourceService.getInstance(project)
        val connection = project.messageBus.connect(project)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, StringResourceListener(service))
        service.refreshAsync()
    }
}
