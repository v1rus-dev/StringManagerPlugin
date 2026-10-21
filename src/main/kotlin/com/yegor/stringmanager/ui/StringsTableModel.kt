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
 * Table model of the tool window: the first column holds the keys, the other columns hold the locales,
 * the last column holds the `translatable` flag of the key.
 */
class StringsTableModel(private val keyColumnName: String = "Key", private val translatableColumnName: String = "Translatable") :
    AbstractTableModel() {

    /** Called when a user edits a translation. */
    var onEdit: ((key: String, locale: String, value: String) -> Unit)? = null

    /** Called when a user renames a key. */
    var onRename: ((oldKey: String, newKey: String) -> Unit)? = null

    /** Called when a user changes the `translatable` flag of a key. */
    var onTranslatable: ((key: String, translatable: Boolean) -> Unit)? = null

    private var snapshot: StringSnapshot = StringSnapshot.EMPTY
    private var currentLocales: List<String> = emptyList()
    private var currentKeys: List<String> = emptyList()
    private var moduleFilter: String? = null
    private var fileFilter: String? = null
    private var statusFilter: StatusFilter = StatusFilter.ALL
    private val translatableOverrides = mutableMapOf<String, Boolean>()

    /** Locales of the project, they are the columns of the table. */
    val locales: List<String> get() = currentLocales

    /** Keys of the table, they are the rows. */
    val keys: List<String> get() = currentKeys

    /** Replaces the data of the table. */
    fun setSnapshot(snapshot: StringSnapshot) {
        this.snapshot = snapshot
        translatableOverrides.clear()
        applyFilters()
    }

    /**
     * Shows the new value of the flag of a key immediately, before the files are rescanned.
     */
    fun overrideTranslatable(key: String, translatable: Boolean) {
        translatableOverrides[key] = translatable
        for (row in 0 until rowCount) {
            if (currentKeys.getOrNull(row) == key) {
                fireTableRowsUpdated(row, row)
            }
        }
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
        if (column == 0 || column > currentLocales.size || row >= currentKeys.size) {
            return null
        }
        val locale = currentLocales.getOrNull(column - 1) ?: return null
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

    /**
     * Returns `true` when the key has to be translated.
     *
     * The flag is shared by all translations of the key, it is `false` when at least one of them
     * is marked as `translatable="false"`.
     */
    fun isTranslatable(key: String): Boolean {
        translatableOverrides[key]?.let { return it }
        val entries = snapshot.entries[key].orEmpty()
        return entries.isNotEmpty() && entries.all { it.translatable }
    }

    /**
     * Preferred width of a column in pixels.
     *
     * The columns keep their natural width, so that a project with many locales
     * is scrolled horizontally instead of squeezing the locales.
     */
    fun columnWidth(column: Int): Int {
        if (column == translatableColumn()) {
            return TRANSLATABLE_COLUMN_WIDTH
        }
        var longest = getColumnName(column).length
        for (row in 0 until rowCount) {
            longest = maxOf(longest, getValueAt(row, column).toString().length)
        }
        return (longest * CHARACTER_WIDTH + COLUMN_PADDING).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
    }

    override fun getRowCount(): Int = currentKeys.size

    override fun getColumnCount(): Int = currentLocales.size + 2

    override fun getColumnName(column: Int): String = when {
        column == 0 -> keyColumnName
        column == translatableColumn() -> translatableColumnName
        else -> currentLocales.getOrElse(column - 1) { "" }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == translatableColumn()) Boolean::class.java else String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val key = keyAt(rowIndex) ?: return
        when {
            columnIndex == 0 -> {
                val renamed = value?.toString()?.trim().orEmpty()
                if (renamed.isNotEmpty() && renamed != key) {
                    onRename?.invoke(key, renamed)
                }
            }
            columnIndex == translatableColumn() -> {
                onTranslatable?.invoke(key, value as? Boolean ?: true)
            }
            else -> {
                val locale = localeAt(columnIndex) ?: return
                onEdit?.invoke(key, locale, value?.toString().orEmpty())
            }
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val key = currentKeys.getOrElse(rowIndex) { return "" }
        if (columnIndex == 0) {
            return key
        }
        if (columnIndex == translatableColumn()) {
            return isTranslatable(key)
        }
        return entryAt(rowIndex, columnIndex)?.value.orEmpty()
    }

    private fun translatableColumn(): Int = currentLocales.size + 1

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
                // A key is missing when it has to be translated and at least one locale has no value.
                StatusFilter.MISSING -> isTranslatable(key) &&
                    snapshot.locales.any { locale -> snapshot.entriesOf(key, locale).isEmpty() }
                StatusFilter.NON_TRANSLATABLE -> entries.any { !it.translatable }
            }
        }
        fireTableStructureChanged()
    }

    companion object {
        private const val CHARACTER_WIDTH = 8
        private const val COLUMN_PADDING = 16
        private const val MIN_COLUMN_WIDTH = 120
        private const val MAX_COLUMN_WIDTH = 400
        private const val TRANSLATABLE_COLUMN_WIDTH = 110
    }
}
