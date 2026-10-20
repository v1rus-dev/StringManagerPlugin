package com.yegor.stringmanager.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.vfs.VirtualFile
import com.yegor.stringmanager.core.StringResourceService
import com.yegor.stringmanager.core.model.StringFile
import kotlinx.coroutines.currentCoroutineContext

/**
 * MCP tools that give AI agents access to the string resources of the project.
 *
 * The toolset is registered in an optional configuration file, so it is only loaded when the
 * built-in MCP Server plugin is installed.
 */
class StringManagerToolset : McpToolset {

    @McpTool
    @McpDescription("Lists all string keys of the project, one per line.")
    suspend fun list_string_keys(): String {
        val snapshot = snapshot()
        return snapshot.keys.joinToString("\n")
    }

    @McpTool
    @McpDescription("Lists translations that are missing: every line contains a key and the locales without a value.")
    suspend fun list_missing_translations(): String {
        val snapshot = snapshot()
        return snapshot.keys.mapNotNull { key ->
            val missing = snapshot.locales.filter { snapshot.entriesOf(key, it).isEmpty() }
            if (missing.isEmpty()) null else "$key: ${missing.joinToString(", ")}"
        }.joinToString("\n")
    }

    @McpTool
    @McpDescription("Lists all files with string resources, one per line, with their module and locale.")
    suspend fun list_string_files(): String {
        val snapshot = snapshot()
        return snapshot.files.joinToString("\n") { file ->
            "${file.module}: ${file.path} (${file.locale.displayName})"
        }
    }

    @McpTool
    @McpDescription("Returns the value of a string for the given locale, e.g. 'ru' or 'default'.")
    suspend fun get_string(key: String, locale: String): String {
        val snapshot = snapshot()
        val entry = snapshot.entriesOf(key, locale).firstOrNull()
            ?: return "String '$key' is not translated to '$locale'"
        return entry.value
    }

    @McpTool
    @McpDescription("Sets the value of a string for the given locale. Creates the string when it does not exist yet.")
    suspend fun set_string(key: String, locale: String, value: String): String {
        val project = currentCoroutineContext().project
        val service = StringResourceService.getInstance(project)
        val (file, virtual) = target(service, locale) ?: return "There is no file for locale '$locale'"
        service.setValueIn(virtual, key, value, file.origin)
        return "String '$key' of locale '$locale' is set to '$value'"
    }

    @McpTool
    @McpDescription("Adds a new string to the file of the given locale.")
    suspend fun add_string(key: String, locale: String, value: String, translatable: Boolean = true): String {
        val project = currentCoroutineContext().project
        val service = StringResourceService.getInstance(project)
        val (file, virtual) = target(service, locale) ?: return "There is no file for locale '$locale'"
        service.addStringIn(virtual, key, value, file.origin, translatable)
        return "String '$key' is added to locale '$locale'"
    }

    @McpTool
    @McpDescription("Removes a string from the file of the given locale.")
    suspend fun remove_string(key: String, locale: String): String {
        val project = currentCoroutineContext().project
        val service = StringResourceService.getInstance(project)
        val (file, virtual) = target(service, locale) ?: return "There is no file for locale '$locale'"
        service.removeStringIn(virtual, key)
        return "String '$key' is removed from locale '$locale'"
    }

    @McpTool
    @McpDescription("Marks a string as translatable or not, which sets or removes the attribute translatable=\"false\".")
    suspend fun mark_translatable(key: String, locale: String, translatable: Boolean): String {
        val project = currentCoroutineContext().project
        val service = StringResourceService.getInstance(project)
        val (file, virtual) = target(service, locale) ?: return "There is no file for locale '$locale'"
        service.setTranslatableIn(virtual, key, translatable)
        val state = if (translatable) "translatable" else "not translatable"
        return "String '$key' of locale '$locale' is marked as $state"
    }

    private suspend fun snapshot() = StringResourceService.getInstance(currentCoroutineContext().project).scanSnapshot()

    private suspend fun target(
        service: StringResourceService,
        locale: String,
    ): Pair<StringFile, VirtualFile>? {
        val snapshot = service.scanSnapshot()
        val file = snapshot.files.firstOrNull { it.locale.displayName == locale }
            ?: return null
        val virtual = service.fileOf(file.path) ?: return null
        return file to virtual
    }
}
