package com.yegor.stringmanager.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.yegor.stringmanager.StringManagerBundle

/**
 * Opens the `String Manager` tool window from the `Tools` menu.
 *
 * It also brings the tool window back when it was accidentally closed or hidden.
 */
class ShowStringManagerAction : AnAction(), DumbAware {

    init {
        templatePresentation.text = StringManagerBundle.message("action.show")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate(null)
    }

    companion object {
        /** Id of the tool window, it must match the id in `plugin.xml`. */
        const val TOOL_WINDOW_ID = "String Manager"
    }
}
