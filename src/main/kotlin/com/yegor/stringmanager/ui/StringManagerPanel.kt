package com.yegor.stringmanager.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.yegor.stringmanager.StringManagerBundle
import com.yegor.stringmanager.core.StringResourceService
import com.yegor.stringmanager.core.StringSnapshot
import com.yegor.stringmanager.core.model.StringEntry
import com.yegor.stringmanager.core.model.StringFile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

/**
 * Content of the `String Manager` tool window: a table of keys and locales with filters.
 */
class StringManagerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = StringResourceService.getInstance(project)
    private val model = StringsTableModel(StringManagerBundle.message("column.key"))
    private val table = JBTable(model)
    private val modules = JComboBox<String>()
    private val files = JComboBox<String>()
    private val statuses = JComboBox<String>()
    private val listener: (StringSnapshot) -> Unit = { snapshot -> update(snapshot) }

    init {
        model.onEdit = { key, locale, value -> edit(key, locale, value) }
        table.setDefaultRenderer(String::class.java, CellRenderer())
        table.rowHeight = 22
        statuses.model = DefaultComboBoxModel(
            arrayOf(
                StringManagerBundle.message("filter.all"),
                StringManagerBundle.message("status.missing"),
                StringManagerBundle.message("status.nonTranslatable"),
            ),
        )
        add(filters(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(buttons(), BorderLayout.SOUTH)
        modules.addActionListener { applyFilters() }
        files.addActionListener { applyFilters() }
        statuses.addActionListener { applyFilters() }
        service.addListener(listener)
        update(service.snapshot())
        service.refreshAsync()
    }

    override fun dispose() {
        service.removeListener(listener)
    }

    private fun filters(): JPanel {
        val component = panel {
            row {
                label(StringManagerBundle.message("filter.module"))
                cell(modules)
                label(StringManagerBundle.message("filter.file"))
                cell(files)
                label(StringManagerBundle.message("filter.status"))
                cell(statuses)
            }
        }
        return component
    }

    private fun buttons(): JPanel {
        val panel = JPanel()
        panel.add(
            JButton(StringManagerBundle.message("action.refresh")).apply {
                addActionListener { service.refreshAsync() }
            },
        )
        panel.add(
            JButton(StringManagerBundle.message("action.addKey")).apply {
                addActionListener { addKey() }
            },
        )
        panel.add(
            JButton(StringManagerBundle.message("action.removeKey")).apply {
                addActionListener { removeKey() }
            },
        )
        return panel
    }

    private fun update(snapshot: StringSnapshot) {
        refreshFilters(snapshot)
        model.setSnapshot(snapshot)
        table.repaint()
    }

    private fun refreshFilters(snapshot: StringSnapshot) {
        val selectedModule = modules.selectedItem as? String
        val selectedFile = files.selectedItem as? String
        val all = StringManagerBundle.message("filter.all")
        modules.model = DefaultComboBoxModel(
            (listOf(all) + snapshot.files.map { it.module }.distinct().sorted()).toTypedArray(),
        )
        files.model = DefaultComboBoxModel(
            (listOf(all) + snapshot.files.map { it.path }.distinct().sorted()).toTypedArray(),
        )
        modules.selectedItem = selectedModule ?: all
        files.selectedItem = selectedFile ?: all
        applyFilters()
    }

    private fun applyFilters() {
        val all = StringManagerBundle.message("filter.all")
        model.setModuleFilter((modules.selectedItem as? String)?.takeIf { it != all })
        model.setFileFilter((files.selectedItem as? String)?.takeIf { it != all })
        model.setStatusFilter(
            when (statuses.selectedItem) {
                StringManagerBundle.message("status.missing") -> StatusFilter.MISSING
                StringManagerBundle.message("status.nonTranslatable") -> StatusFilter.NON_TRANSLATABLE
                else -> StatusFilter.ALL
            },
        )
        table.repaint()
    }

    private fun edit(key: String, locale: String, value: String) {
        val entry = model.entryAt(table.selectedRow, table.selectedColumn)
        if (entry != null) {
            val file = service.fileOf(entry.file.path) ?: return
            service.setValue(file, key, value, entry.file.origin)
            return
        }
        // The cell has no translation yet, the key is added to the file of the locale.
        val target = service.snapshot().files.firstOrNull { it.locale.displayName == locale }
        if (target == null) {
            Messages.showErrorDialog(
                project,
                StringManagerBundle.message("message.noFiles"),
                StringManagerBundle.message("toolWindow.title"),
            )
            return
        }
        val file = service.fileOf(target.path) ?: return
        service.addString(file, key, value, target.origin)
    }

    private fun addKey() {
        val snapshot = service.snapshot()
        if (snapshot.files.isEmpty()) {
            Messages.showInfoMessage(
                project,
                StringManagerBundle.message("message.noFiles"),
                StringManagerBundle.message("toolWindow.title"),
            )
            return
        }
        val dialog = AddStringDialog(project, snapshot.files, preferredFile(snapshot))
        if (!dialog.showAndGet()) {
            return
        }
        val target = dialog.selectedFile() ?: return
        val file = service.fileOf(target.path) ?: return
        service.addString(file, dialog.key(), dialog.value(), target.origin, dialog.translatable())
    }

    private fun removeKey() {
        val key = model.selectedKey() ?: return
        val snapshot = service.snapshot()
        val locale = model.localeAt(table.selectedColumn)
        val entries = snapshot.entries[key].orEmpty()
        val target = entries.firstOrNull { locale == null || it.file.locale.displayName == locale }?.file
            ?: entries.firstOrNull()?.file
            ?: preferredFile(snapshot)
        val file = service.fileOf(target.path) ?: return
        service.removeString(file, key)
    }

    /**
     * Returns the file that is offered by default: the file of the default locale that holds the most keys.
     */
    private fun preferredFile(snapshot: StringSnapshot): StringFile {
        val counts = mutableMapOf<String, Int>()
        snapshot.entries.values.flatten().forEach { entry ->
            if (entry.file.locale.isDefault) {
                counts[entry.file.path] = (counts[entry.file.path] ?: 0) + 1
            }
        }
        return snapshot.files
            .filter { it.locale.isDefault }
            .maxByOrNull { counts[it.path] ?: 0 }
            ?: snapshot.files.first()
    }

    /**
     * Highlights cells without a translation and cells of resources marked as `translatable="false"`.
     */
    private inner class CellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            val entry: StringEntry? = model.entryAt(row, column)
            if (column == 0) {
                component.background = table?.background
                return component
            }
            component.background = when {
                entry == null -> MISSING_COLOR
                !entry.translatable -> NON_TRANSLATABLE_COLOR
                else -> table?.background
            }
            return component
        }
    }

    companion object {
        private val MISSING_COLOR = JBColor(Color(0xF7D6D6), Color(0x5C2B2B))
        private val NON_TRANSLATABLE_COLOR = JBColor(Color(0xFFF3C4), Color(0x4A421A))
    }
}
