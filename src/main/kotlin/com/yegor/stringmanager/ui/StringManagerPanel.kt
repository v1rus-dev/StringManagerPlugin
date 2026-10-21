package com.yegor.stringmanager.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
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
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableColumn

/**
 * Content of the `String Manager` tool window: a table of keys and locales with filters.
 */
class StringManagerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = StringResourceService.getInstance(project)
    private val model = StringsTableModel(
        StringManagerBundle.message("column.key"),
        StringManagerBundle.message("column.translatable"),
    )
    private val table = JBTable(model)
    private val keyTable = JBTable(model)
    private val scroll = JBScrollPane(table)
    private val modules = JComboBox<String>()
    private val files = JComboBox<String>()
    private val statuses = JComboBox<String>()
    private val search = SearchTextField()
    private val listener: (StringSnapshot) -> Unit = { snapshot -> update(snapshot) }

    /** The table of the panel, it is exposed for the tests. */
    internal val stringsTable: JBTable get() = table

    /** The table with the keys, it is shown in the row header of the scroll pane. */
    internal val keysTable: JBTable get() = keyTable

    /** The scroll pane of the table, it is exposed for the tests. */
    internal val stringsScroll: JBScrollPane get() = scroll

    /** The field of the search, it is exposed for the tests. */
    internal val searchField: SearchTextField get() = search

    init {
        // The columns are built by the panel, the model must not recreate them, otherwise the
        // column with the keys would come back to the table of the locales.
        table.setAutoCreateColumnsFromModel(false)
        keyTable.setAutoCreateColumnsFromModel(false)
        model.onEdit = { key, locale, value -> edit(key, locale, value) }
        model.onRename = { oldKey, newKey -> rename(oldKey, newKey) }
        model.onTranslatable = { key, translatable -> changeTranslatable(key, translatable) }
        table.setDefaultRenderer(String::class.java, CellRenderer())
        table.setDefaultRenderer(Boolean::class.java, BooleanRenderer())
        table.setDefaultEditor(Boolean::class.java, BooleanEditor())
        table.rowHeight = ROW_HEIGHT
        // The columns keep their width, so that a project with many locales is scrolled horizontally.
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        keyTable.rowHeight = ROW_HEIGHT
        keyTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        statuses.model = DefaultComboBoxModel(
            arrayOf(
                StringManagerBundle.message("filter.all"),
                StringManagerBundle.message("status.missing"),
                StringManagerBundle.message("status.nonTranslatable"),
            ),
        )
        rebuildColumns()
        add(filters(), BorderLayout.NORTH)
        add(scrollPane(), BorderLayout.CENTER)
        add(buttons(), BorderLayout.SOUTH)
        modules.addActionListener { applyFilters() }
        files.addActionListener { applyFilters() }
        statuses.addActionListener { applyFilters() }
        search.addDocumentListener(SearchListener())
        service.addListener(listener)
        update(service.snapshot())
        service.refreshAsync()
    }

    override fun dispose() {
        service.removeListener(listener)
    }

    /**
     * The table of the locales is scrolled horizontally, while the column with the keys stays
     * in place: the keys are shown in the row header of the scroll pane.
     */
    private fun scrollPane(): JScrollPane {
        scroll.setRowHeaderView(keyTable)
        scroll.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, keyTable.tableHeader)
        syncSelection(table, keyTable)
        syncSelection(keyTable, table)
        return scroll
    }

    /**
     * Builds the columns of both tables.
     *
     * The column with the keys is not in the table of the locales, it is shown in the row header
     * of the scroll pane, so that it does not move when the locales are scrolled horizontally.
     */
    private fun rebuildColumns() {
        val locales = table.columnModel
        while (locales.columnCount > 0) {
            locales.removeColumn(locales.getColumn(locales.columnCount - 1))
        }
        for (index in 1 until model.columnCount) {
            val column = TableColumn(index)
            column.headerValue = model.getColumnName(index)
            locales.addColumn(column)
        }
        val keys = keyTable.columnModel
        while (keys.columnCount > 0) {
            keys.removeColumn(keys.getColumn(keys.columnCount - 1))
        }
        val key = TableColumn(0)
        key.headerValue = model.getColumnName(0)
        keys.addColumn(key)
    }

    /**
     * Selects the same row in both tables.
     */
    private fun syncSelection(source: JBTable, target: JBTable) {
        source.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val row = source.selectedRow
                if (row >= 0 && target.selectedRow != row) {
                    target.setRowSelectionInterval(row, row)
                }
            }
        }
    }

    /**
     * Index of the column of the table of the locales in the model, the column with the keys
     * is not there, it is in the row header.
     */
    private fun modelColumn(view: Int): Int = table.columnModel.getColumn(view).modelIndex

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
            row {
                label(StringManagerBundle.message("filter.search"))
                cell(search)
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
        rebuildColumns()
        updateColumnWidths()
        table.repaint()
        keyTable.repaint()
    }

    private fun updateColumnWidths() {
        for (view in 0 until table.columnCount) {
            val column = table.columnModel.getColumn(view)
            column.preferredWidth = model.columnWidth(column.modelIndex)
        }
        val width = model.columnWidth(0)
        keyTable.columnModel.getColumn(0).preferredWidth = width
        // The row header takes the width of the column with the keys. Without it, the row header
        // takes the whole width of the panel and squeezes the locales.
        scroll.rowHeader.preferredSize = Dimension(width, scroll.rowHeader.preferredSize.height)
        scroll.revalidate()
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
        keyTable.repaint()
    }

    /**
     * Shows only the rows whose key or whose translation contains the text of the search field.
     */
    private fun applySearch() {
        model.setSearchQuery(search.text)
        table.repaint()
        keyTable.repaint()
    }

    private fun edit(key: String, locale: String, value: String) {
        val entry = model.entryAt(table.selectedRow, modelColumn(table.selectedColumn))
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

    /**
     * Renames a key in all its translations.
     */
    private fun rename(oldKey: String, newKey: String) {
        val files = service.snapshot().entries[oldKey].orEmpty().map { it.file }.distinctBy { it.path }
        val answer = Messages.showYesNoDialog(
            project,
            StringManagerBundle.message("dialog.rename.message", oldKey, newKey, files.size),
            StringManagerBundle.message("dialog.rename.title"),
            Messages.getQuestionIcon(),
        )
        if (answer == Messages.YES) {
            service.renameKey(oldKey, newKey)
        }
    }

    /**
     * Marks a key as translatable or not in all its translations.
     */
    private fun changeTranslatable(key: String, translatable: Boolean) {
        // The flag is shown immediately, the files are rescanned in the background.
        model.overrideTranslatable(key, translatable)
        service.setTranslatable(key, translatable)
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
        val key = model.keyAt(table.selectedRow) ?: return
        val snapshot = service.snapshot()
        val locale = model.localeAt(modelColumn(table.selectedColumn))
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
            val index = modelColumn(column)
            if (index == model.translatableColumn()) {
                // The flag of the key is not a translation, it is not highlighted.
                component.background = table?.background
                return component
            }
            val key = model.keyAt(row)
            val entry: StringEntry? = model.entryAt(row, index)
            component.background = when {
                // A key that must not be translated is not a missing translation.
                entry == null && key != null && !model.isTranslatable(key) -> table?.background
                entry == null -> MISSING_COLOR
                key != null && !model.isTranslatable(key) -> NON_TRANSLATABLE_COLOR
                else -> table?.background
            }
            return component
        }
    }

    /**
     * Renders the `translatable` flag of a key as a check box.
     */
    private inner class BooleanRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val check = component as? JCheckBox ?: JCheckBox()
            check.text = null
            check.isSelected = value as? Boolean ?: false
            check.isOpaque = true
            check.background = table?.background
            check.isEnabled = true
            return check
        }
    }

    /**
     * Edits the `translatable` flag of a key with a check box.
     */
    private inner class BooleanEditor : javax.swing.DefaultCellEditor(JCheckBox()) {
        override fun getCellEditorValue(): Any = (editorComponent as JCheckBox).isSelected
    }

    /**
     * Searches the rows by the keys and by the translations, while the user types.
     */
    private inner class SearchListener : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) = applySearch()
        override fun removeUpdate(event: DocumentEvent) = applySearch()
        override fun changedUpdate(event: DocumentEvent) = applySearch()
    }

    companion object {
        private const val ROW_HEIGHT = 22
        private val MISSING_COLOR = JBColor(Color(0xF7D6D6), Color(0x5C2B2B))
        private val NON_TRANSLATABLE_COLOR = JBColor(Color(0xFFF3C4), Color(0x4A421A))
    }
}
