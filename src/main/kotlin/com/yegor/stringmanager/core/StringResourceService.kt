package com.yegor.stringmanager.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.yegor.stringmanager.core.model.ResourceOrigin
import com.yegor.stringmanager.core.model.StringEntry
import com.yegor.stringmanager.core.model.StringFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Snapshot of all string resources of a project.
 *
 * @param files discovered files
 * @param entries resources of every key, grouped by the key
 * @param warnings problems found while scanning, e.g. missing base locale directories
 */
data class StringSnapshot(
    val files: List<StringFile>,
    val entries: Map<String, List<StringEntry>>,
    val warnings: List<String>,
) {
    /** Locales present in the project, ordered by their display name. */
    val locales: List<String>
        get() = files.map { it.locale.displayName }.distinct().sorted()

    /** All keys of the project, sorted alphabetically. */
    val keys: List<String>
        get() = entries.keys.sorted()

    /** Entries of a key, grouped by locale. */
    fun entriesOf(key: String, locale: String): List<StringEntry> =
        entries[key].orEmpty().filter { it.file.locale.displayName == locale }

    companion object {
        val EMPTY = StringSnapshot(emptyList(), emptyMap(), emptyList())
    }
}

/**
 * Single entry point for all string resource operations of a project.
 *
 * Both the tool window and the MCP tools use this service, so that they always see the same data
 * and apply the same rules.
 */
@Service(Service.Level.PROJECT)
class StringResourceService(
    private val project: Project,
    private val scope: CoroutineScope,
) {

    private val discovery = ResourceDiscovery(project)
    private val writer = StringResourceWriter(project)

    @Volatile
    private var snapshot: StringSnapshot = StringSnapshot.EMPTY

    private val listeners = CopyOnWriteArrayList<(StringSnapshot) -> Unit>()

    /** Returns the last known state of the project. */
    fun snapshot(): StringSnapshot = snapshot

    /** Subscribes to the updates of the snapshot, the listener is called on the EDT. */
    fun addListener(listener: (StringSnapshot) -> Unit) {
        listeners += listener
    }

    /** Unsubscribes from the updates of the snapshot. */
    fun removeListener(listener: (StringSnapshot) -> Unit) {
        listeners -= listener
    }

    /** Rescans the project in the background and notifies the listeners. */
    fun refreshAsync() {
        scope.launch(Dispatchers.Default) {
            val fresh = readAction { scan() }
            snapshot = fresh
            ApplicationManager.getApplication().invokeLater {
                listeners.forEach { it(fresh) }
            }
        }
    }

    /** Rescans the project and updates the snapshot. */
    fun refresh() {
        snapshot = scan()
    }

    /** Rescans the project in a read action. */
    suspend fun scanSnapshot(): StringSnapshot = readAction { scan() }

    /** Sets the value of a string, the write happens on the EDT. */
    suspend fun setValueIn(file: VirtualFile, key: String, value: String, origin: ResourceOrigin) {
        withContext(Dispatchers.EDT) { setValue(file, key, value, origin) }
    }

    /** Adds a new string, the write happens on the EDT. */
    suspend fun addStringIn(
        file: VirtualFile,
        key: String,
        value: String,
        origin: ResourceOrigin,
        translatable: Boolean = true,
    ) {
        withContext(Dispatchers.EDT) { addString(file, key, value, origin, translatable) }
    }

    /** Removes a string, the write happens on the EDT. */
    suspend fun removeStringIn(file: VirtualFile, key: String) {
        withContext(Dispatchers.EDT) { removeString(file, key) }
    }

    /** Marks a string as translatable or not, the write happens on the EDT. */
    suspend fun setTranslatableIn(file: VirtualFile, key: String, translatable: Boolean) {
        withContext(Dispatchers.EDT) { setTranslatable(file, key, translatable) }
    }

    /** Finds the file of a module by its project relative path. */
    fun fileOf(path: String): VirtualFile? {
        val absolute = LocalFileSystem.getInstance().findFileByPath(path)
        if (absolute != null) {
            return absolute
        }
        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                root.findFileByRelativePath(path)?.let { return it }
            }
        }
        return null
    }

    /** Sets the value of a string in the given file. */
    fun setValue(file: VirtualFile, key: String, value: String, origin: ResourceOrigin) {
        writer.setValue(file, key, value, origin)
        refreshAsync()
    }

    /** Adds a new string to the given file. */
    fun addString(
        file: VirtualFile,
        key: String,
        value: String,
        origin: ResourceOrigin,
        translatable: Boolean = true,
    ) {
        writer.addString(file, key, value, origin, translatable)
        refreshAsync()
    }

    /** Removes a string from the given file. */
    fun removeString(file: VirtualFile, key: String) {
        writer.removeString(file, key)
        refreshAsync()
    }

    /** Marks a string as translatable or not. */
    fun setTranslatable(file: VirtualFile, key: String, translatable: Boolean) {
        writer.setTranslatable(file, key, translatable)
        refreshAsync()
    }

    /**
     * Renames a key in all files where it is defined, so that all translations get the new name.
     */
    fun renameKey(key: String, newKey: String) {
        val files = filesOf(key).mapNotNull { fileOf(it.path) }
        writer.renameKey(files, key, newKey)
        refreshAsync()
    }

    /**
     * Marks a key as translatable or not.
     *
     * The attribute is written into the files of the default locale only, because Android and
     * Compose Multiplatform inherit the flag from the default locale into the translations.
     * Removing the flag touches only the files that already have it.
     */
    fun setTranslatable(key: String, translatable: Boolean) {
        val files = filesForTranslatable(key, translatable).mapNotNull { fileOf(it.path) }
        writer.setTranslatable(files, key, translatable)
        refreshAsync()
    }

    /**
     * Returns the files that have to be changed to set or to remove the flag of a key.
     */
    private fun filesForTranslatable(key: String, translatable: Boolean): List<StringFile> {
        val entries = snapshot.entries[key].orEmpty()
        if (translatable) {
            // Only the files that already have the attribute are touched.
            return entries.filter { !it.translatable }.map { it.file }.distinctBy { it.path }
        }
        val defaults = entries.filter { it.file.locale.isDefault }.map { it.file }.distinctBy { it.path }
        return if (defaults.isNotEmpty()) defaults else entries.map { it.file }.distinctBy { it.path }
    }

    /** Renames a key, the write happens on the EDT. */
    suspend fun renameKeyIn(key: String, newKey: String) {
        withContext(Dispatchers.EDT) { renameKey(key, newKey) }
    }

    /** Marks a key as translatable or not, the write happens on the EDT. */
    suspend fun setTranslatableIn(key: String, translatable: Boolean) {
        withContext(Dispatchers.EDT) { setTranslatable(key, translatable) }
    }

    private fun filesOf(key: String): List<StringFile> =
        snapshot.entries[key].orEmpty().map { it.file }.distinctBy { it.path }

    /** Reads the raw value of a string as it is stored in the file. */
    fun readRaw(file: VirtualFile, key: String): String? =
        writer.read(file).firstOrNull { it.first == key }?.second?.value

    private fun scan(): StringSnapshot {
        val discovered = discovery.discover()
        val entries = linkedMapOf<String, MutableList<StringEntry>>()
        val files = mutableListOf<StringFile>()
        val warnings = mutableListOf<String>()
        for (file in discovered) {
            val virtual = fileOf(file.path) ?: continue
            val values = writer.read(virtual)
            if (values.isEmpty()) {
                // Files without strings, e.g. `colors.xml`, are not a part of the table.
                continue
            }
            files += file
            for ((key, value) in values) {
                entries.getOrPut(key) { mutableListOf() } += StringEntry(
                    key = key,
                    file = file,
                    value = StringEscaping.unescape(value.value, file.origin),
                    translatable = value.translatable,
                )
            }
        }
        warnings += validate(files)
        return StringSnapshot(files, entries, warnings)
    }

    /**
     * Checks that every script specific Compose directory has a base language directory,
     * otherwise the Compose resources generator fails with an exception.
     */
    private fun validate(files: List<StringFile>): List<String> {
        val result = mutableListOf<String>()
        val composeLocales = files
            .filter { it.origin == ResourceOrigin.COMPOSE_RESOURCES }
            .map { it.locale }
            .distinct()
        val baseLanguages = composeLocales.filter { it.script == null }.mapNotNull { it.language }.toSet()
        for (locale in composeLocales) {
            val language = locale.language ?: continue
            if (locale.script != null && language !in baseLanguages) {
                result += "Locale '${locale.directory}' has no base language directory 'values-$language', " +
                    "check that the Compose resources generator accepts such a locale"
            }
        }
        return result
    }

    companion object {
        /** Returns the service of a project. */
        fun getInstance(project: Project): StringResourceService = project.service()
    }
}
