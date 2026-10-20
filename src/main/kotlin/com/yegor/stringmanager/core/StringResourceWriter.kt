package com.yegor.stringmanager.core

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.openapi.vfs.VirtualFile
import com.yegor.stringmanager.core.model.ResourceOrigin

/**
 * Reads and writes `<string>` resources through PSI, so that every change is undoable
 * and keeps the formatting and the comments of the file.
 */
class StringResourceWriter(private val project: Project) {

    /**
     * Reads all `<string>` resources of a file.
     */
    fun read(file: VirtualFile): List<Pair<String, StringEntryValue>> {
        val xml = xmlFile(file) ?: return emptyList()
        return xml.rootTag
            ?.findSubTags(STRING_TAG)
            ?.mapNotNull { tag ->
                val key = tag.getAttributeValue(NAME_ATTRIBUTE, null) ?: return@mapNotNull null
                key to StringEntryValue(
                    value = tag.getValue().text,
                    translatable = !isNonTranslatable(tag),
                )
            }
            .orEmpty()
    }

    /**
     * Sets the text of an existing string.
     */
    fun setValue(file: VirtualFile, key: String, value: String, origin: ResourceOrigin) {
        write(file, "Set value of '$key'") {
            val tag = findTag(file, key) ?: return@write false
            tag.getValue().setText(StringEscaping.escape(value, origin))
            true
        }
    }

    /**
     * Adds a new string to a file.
     */
    fun addString(
        file: VirtualFile,
        key: String,
        value: String,
        origin: ResourceOrigin,
        translatable: Boolean = true,
    ) {
        write(file, "Add string '$key'") {
            val xml = xmlFile(file) ?: return@write false
            val root = xml.rootTag ?: return@write false
            if (findTag(file, key) != null) {
                return@write false
            }
            // The placeholder text keeps the tag with a value node, so that it can be replaced below.
            val tag = XmlElementFactory.getInstance(project)
                .createTagFromText("<$STRING_TAG $NAME_ATTRIBUTE=\"${StringEscaping.escapeAttribute(key)}\">x</$STRING_TAG>")
            root.addSubTag(tag, false)
            val inserted = root.findSubTags(STRING_TAG)
                .firstOrNull { it.getAttributeValue(NAME_ATTRIBUTE, null) == key }
                ?: return@write false
            inserted.getValue().setText(StringEscaping.escape(value, origin))
            if (!translatable) {
                inserted.setAttribute(TRANSLATABLE_ATTRIBUTE, "", NON_TRANSLATABLE_VALUE)
            }
            true
        }
    }

    /**
     * Removes a string from a file.
     */
    fun removeString(file: VirtualFile, key: String) {
        write(file, "Remove string '$key'") {
            val tag = findTag(file, key) ?: return@write false
            tag.delete()
            true
        }
    }

    /**
     * Marks a string as translatable or not.
     */
    fun setTranslatable(file: VirtualFile, key: String, translatable: Boolean) {
        write(file, "Change translatable flag of '$key'") {
            val tag = findTag(file, key) ?: return@write false
            if (translatable) {
                val attribute = tag.getAttribute(TRANSLATABLE_ATTRIBUTE, null)
                attribute?.delete()
            } else {
                tag.setAttribute(TRANSLATABLE_ATTRIBUTE, "", NON_TRANSLATABLE_VALUE)
            }
            true
        }
    }

    private fun findTag(file: VirtualFile, key: String): XmlTag? {
        val root = xmlFile(file)?.rootTag ?: return null
        return root.findSubTags(STRING_TAG).firstOrNull { it.getAttributeValue(NAME_ATTRIBUTE, null) == key }
    }

    private fun xmlFile(file: VirtualFile): XmlFile? =
        PsiManager.getInstance(project).findFile(file) as? XmlFile

    private fun isNonTranslatable(tag: XmlTag): Boolean =
        tag.getAttributeValue(TRANSLATABLE_ATTRIBUTE, null)?.equals(NON_TRANSLATABLE_VALUE, ignoreCase = true) == true

    private fun write(file: VirtualFile, name: String, action: () -> Boolean) {
        WriteCommandAction.runWriteCommandAction(project, name, null, {
            WriteAction.run<Throwable> { action() }
        })
    }

    companion object {
        private const val STRING_TAG = "string"
        private const val NAME_ATTRIBUTE = "name"
        private const val TRANSLATABLE_ATTRIBUTE = "translatable"
        private const val NON_TRANSLATABLE_VALUE = "false"
    }
}

/**
 * Value of a single `<string>` resource.
 */
data class StringEntryValue(
    val value: String,
    val translatable: Boolean,
)
