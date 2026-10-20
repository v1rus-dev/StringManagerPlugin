package com.yegor.stringmanager.core

import com.yegor.stringmanager.core.model.LocaleSpec

/**
 * Parses locale qualifiers of Android and Compose Multiplatform `values*` directories.
 *
 * Both ecosystems use the same directory naming, so a single parser serves them:
 * `values` (default), `values-ru`, `values-ru-rRU`, `values-b+zh+Hans`.
 */
object LocaleQualifiers {

    private const val PREFIX = "values"

    /** Android/CMP region qualifier: `r` followed by two letters or three digits. */
    private val REGION = Regex("^r([A-Za-z]{2}|[0-9]{3})$")

    /** Android/CMP language qualifier: two or three lower case letters. */
    private val LANGUAGE = Regex("^[a-z]{2,3}$")

    /**
     * Parses a `values*` directory name.
     *
     * @param directory directory name, e.g. `values`, `values-ru-rRU`, `values-b+zh+Hans`
     * @return parsed locale, never `null`
     */
    fun parse(directory: String): LocaleSpec {
        if (!directory.startsWith(PREFIX)) {
            return LocaleSpec(directory, null, null, null, bcp47 = false)
        }
        val rest = directory.removePrefix(PREFIX).removePrefix("-")
        if (rest.isEmpty()) {
            return LocaleSpec(directory, null, null, null, bcp47 = false)
        }
        val parts = rest.split("-").toMutableList()

        // Mobile country/network codes come first and are not a locale.
        while (parts.isNotEmpty() && (parts.first().all { it.isDigit() } || parts.first().startsWith("mcc") || parts.first().startsWith("mnc"))) {
            parts.removeAt(0)
        }
        if (parts.isEmpty()) {
            return LocaleSpec(directory, null, null, null, bcp47 = false)
        }

        // BCP 47 qualifier: `b+zh+Hans`, the whole tag lives in a single directory part.
        val first = parts.first()
        if (first.startsWith("b+")) {
            return parseBcp47(directory, first.removePrefix("b+"))
        }

        var language: String? = null
        if (LANGUAGE.matches(first)) {
            language = first
            parts.removeAt(0)
        }
        var region: String? = null
        if (parts.isNotEmpty()) {
            val match = REGION.matchEntire(parts.first())
            if (match != null) {
                region = match.groupValues[1]
                parts.removeAt(0)
            }
        }
        return LocaleSpec(directory, language, region, null, bcp47 = false)
    }

    /**
     * Parses the payload of a BCP 47 qualifier: `zh+Hans`, `zh+Hant+TW`.
     */
    private fun parseBcp47(directory: String, tag: String): LocaleSpec {
        val chunks = tag.split("+").filter { it.isNotEmpty() }
        var language: String? = null
        var script: String? = null
        var region: String? = null
        for (chunk in chunks) {
            when {
                chunk.length == 4 && chunk[0].isUpperCase() -> script = chunk
                chunk.length == 2 && chunk[0].isUpperCase() -> region = chunk
                language == null -> language = chunk
            }
        }
        return LocaleSpec(directory, language, region, script, bcp47 = true)
    }
}
