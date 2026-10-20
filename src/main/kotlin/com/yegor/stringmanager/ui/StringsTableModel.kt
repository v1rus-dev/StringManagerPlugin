package com.yegor.stringmanager.ui

import com.yegor.stringmanager.core.StringSnapshot
import com.yegor.stringmanager.core.model.StringEntry
import com.yegor.stringmanager.core.model.StringFile
import javax.swing.table.AbstractTableModel

/**
 * Filter applied to the rows of the table.
 */
enum class StatusFilter {
    ALL,
    MISSING,
    NON_TRANSLATABLE,
}

/**
 * Table model of the tool window: the first column holds the keys, the other columns hold the locales.
 */
class StringsTableModel(private val keyColumnName: String = "Key") : AbstractTableModel() {

    /** Called when a user edits a cell. */
    var onEdit: ((key: String, locale: String, value: String) -> Unit)? = null

    private var snapshot: StringSnapshot = StringSnapshot.EMPTY
    private var currentLocales: List<String> = emptyList()
    private var currentKeys: List<String> = emptyList()

    /** Locales of the project, they are the columns of the table. */
    val locales: List<String> get() = currentLocales

    /** Keys of the table, they are the rows. */
    val keys: List<String> get() = currentKeys
    private var moduleFilter: String? = null
    private var fileFilter: String? = null
    private var statusFilter: StatusFilter = StatusFilter.ALL

    /** Replaces the data of the table. */
    fun setSnapshot(snapshot: StringSnapshot) {
        this.snapshot = snapshot
        applyFilters()
    }

    /** Sets the module the table is limited to, `null` means all modules. */
    fun setModuleFilter(module: String?) {
        moduleFilter = module
        applyFilters()
    }

    /** Sets the file the table is limited to, `null` means all files. */
    fun setFileFilter(file: String?) {
        fileFilter = file
        applyFilters()
    }

    /** Sets the status the table is limited to. */
    fun setStatusFilter(status: StatusFilter) {
        statusFilter = status
        applyFilters()
    }

    /** Returns the entry of a cell, `null` when the cell has no translation. */
    fun entryAt(row: Int, column: Int): StringEntry? {
        if (column == 0 || row >= currentKeys.size) {
            return null
        }
        val locale = locales.getOrNull(column - 1) ?: return null
        return snapshot.entriesOf(currentKeys[row], locale).firstOrNull()
    }

    /** Returns the key of a row. */
    fun keyAt(row: Int): String? = currentKeys.getOrNull(row)

    /** Returns the locale of a column. */
    fun localeAt(column: Int): String? = currentLocales.getOrNull(column - 1)

    /** Returns all files of the snapshot. */
    fun files(): List<StringFile> = snapshot.files

    /** Returns the key of the selected cell. */
    fun selectedKey(): String? {
        val row = selectedRow
        return if (row >= 0) keyAt(row) else null
    }

    /** Row of the selected cell. */
    var selectedRow: Int = -1

    private fun applyFilters() {
        val files = snapshot.files.filter { file ->
            (moduleFilter == null || file.module == moduleFilter) &&
                (fileFilter == null || file.path == fileFilter)
        }
        val visible = files.map { it.path }.toSet()
        val allKeys = snapshot.entries.keys.sorted()
        currentLocales = snapshot.locales
        currentKeys = allKeys.filter { key ->
            val entries = snapshot.entries[key].orEmpty().filter { it.file.path in visible }
            when (statusFilter) {
                StatusFilter.ALL -> entries.isNotEmpty()
                // A key is missing when at least one locale of the project has no value for it.
                StatusFilter.MISSING -> snapshot.locales.any { locale ->
                    snapshot.entriesOf(key, locale).isEmpty()
                }
                StatusFilter.NON_TRANSLATABLE -> entries.any { !it.translatable }
            }
        }
        fireTableStructureChanged()
    }

    override fun getRowCount(): Int = currentKeys.size

    override fun getColumnCount(): Int = currentLocales.size + 1

    override fun getColumnName(column: Int): String =
        if (column == 0) keyColumnName else currentLocales.getOrElse(column - 1) { "" }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0) {
            return
        }
        val key = keyAt(rowIndex) ?: return
        val locale = localeAt(columnIndex) ?: return
        onEdit?.invoke(key, locale, value?.toString().orEmpty())
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex > 0

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        if (columnIndex == 0) {
            return currentKeys.getOrElse(rowIndex) { "" }
        }
        return entryAt(rowIndex, columnIndex)?.value.orEmpty()
    }
}
