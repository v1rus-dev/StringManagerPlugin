package com.yegor.stringmanager.core.model

/**
 * Where a strings file was found.
 */
enum class ResourceOrigin {
    /** Android `res` directory, e.g. `app/src/main/res/values/strings.xml`. */
    ANDROID_RES,

    /** Compose Multiplatform resources, e.g. `shared/src/commonMain/composeResources/values/strings.xml`. */
    COMPOSE_RESOURCES,
}

/**
 * Locale qualifier parsed from a `values*` directory name.
 *
 * @param directory directory name as it is on disk, e.g. `values`, `values-ru-rRU`, `values-b+zh+Hans`
 * @param language ISO-639 language code, `null` for the default (`values`) directory
 * @param region region code, `null` when absent
 * @param script script code, `null` when absent (only BCP 47 `b+` qualifiers may carry a script)
 * @param bcp47 `true` when the qualifier came from a BCP 47 `b+` qualifier
 */
data class LocaleSpec(
    val directory: String,
    val language: String?,
    val region: String?,
    val script: String?,
    val bcp47: Boolean,
) {
    /** `true` for the default `values` directory. */
    val isDefault: Boolean get() = language == null

    /** Human readable locale name, e.g. `ru-RU`, `zh-Hans`, `default`. */
    val displayName: String
        get() = when {
            language == null -> "default"
            else -> buildList {
                add(language)
                script?.let { add(it) }
                region?.let { add(it) }
            }.joinToString("-")
        }
}

/**
 * A strings file discovered in the project.
 *
 * @param module name of the owning module
 * @param path project relative path of the file
 * @param origin where the file was found
 * @param locale locale qualifier of the directory holding the file
 */
data class StringFile(
    val module: String,
    val path: String,
    val origin: ResourceOrigin,
    val locale: LocaleSpec,
) {
    /** Directory holding the file, e.g. `values-ru-rRU`. */
    val directory: String get() = locale.directory
}

/**
 * A single `<string>` resource.
 *
 * @param key value of the `name` attribute
 * @param file file the resource lives in
 * @param value text of the resource
 * @param translatable value of the `translatable` attribute, `true` when the attribute is absent
 */
data class StringEntry(
    val key: String,
    val file: StringFile,
    val value: String,
    val translatable: Boolean,
)
