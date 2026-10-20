package com.yegor.stringmanager.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.yegor.stringmanager.StringManagerBundle
import com.yegor.stringmanager.core.model.StringFile
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent

/**
 * Dialog that asks for the key, the value and the file of a new string.
 */
class AddStringDialog(
    project: Project,
    private val files: List<StringFile>,
    preferred: StringFile,
) : DialogWrapper(project) {

    private val key = JBTextField()
    private val value = JBTextField()
    private val file = JComboBox<String>()
    private val translatable = JBCheckBox(StringManagerBundle.message("dialog.translatable"), true)

    init {
        title = StringManagerBundle.message("dialog.add.title")
        val paths = files.map { it.path }.sorted()
        file.model = DefaultComboBoxModel(paths.toTypedArray())
        file.selectedItem = preferred.path
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(StringManagerBundle.message("dialog.key")) {
            cell(key).align(Align.FILL)
        }
        row(StringManagerBundle.message("dialog.value")) {
            cell(value).align(Align.FILL)
        }
        row(StringManagerBundle.message("dialog.file")) {
            cell(file).align(Align.FILL)
        }
        row {
            cell(translatable)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = key

    override fun doValidate(): ValidationInfo? {
        if (key.text.isBlank()) {
            return ValidationInfo(StringManagerBundle.message("dialog.key"), key)
        }
        return null
    }

    /** Returns the key entered by a user. */
    fun key(): String = key.text.trim()

    /** Returns the value entered by a user. */
    fun value(): String = value.text

    /** Returns the file chosen by a user. */
    fun selectedFile(): StringFile? {
        val path = file.selectedItem as? String ?: return null
        return files.firstOrNull { it.path == path }
    }

    /** Returns `true` when the new string has to be translatable. */
    fun translatable(): Boolean = translatable.isSelected
}
