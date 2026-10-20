package com.yegor.stringmanager.core

import com.yegor.stringmanager.core.model.ResourceOrigin

/**
 * Escaping of string values.
 *
 * Android expects quotes and apostrophes to be escaped, Compose Multiplatform does not,
 * so the escaping differs per origin.
 */
object StringEscaping {

    /**
     * Escapes a value before it is written into an XML file.
     *
     * @param value value entered by a user
     * @param origin origin of the target file
     * @return value ready to be written
     */
    fun escape(value: String, origin: ResourceOrigin): String = when (origin) {
        ResourceOrigin.ANDROID_RES -> value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
        ResourceOrigin.COMPOSE_RESOURCES -> value
    }

    /**
     * Restores the value as it was entered by a user.
     *
     * @param value value read from an XML file
     * @param origin origin of the source file
     * @return value as entered by a user
     */
    fun unescape(value: String, origin: ResourceOrigin): String = when (origin) {
        ResourceOrigin.ANDROID_RES -> value
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        ResourceOrigin.COMPOSE_RESOURCES -> value
    }

    /**
     * Escapes a value so that it can be used as an XML attribute value.
     */
    fun escapeAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace("\"", "&quot;")
}
