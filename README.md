# String Manager Plugin

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2026.1%2B-000.svg)](https://www.jetbrains.com/idea/)
[![Android Studio](https://img.shields.io/badge/Android_Studio-2026.1%2B-3DDC84.svg)](https://developer.android.com/studio)

Manage **all** string resources of a project in IntelliJ IDEA and Android Studio: Android `res/values*`
directories and Compose Multiplatform `composeResources/values*` directories.

The plugin does not care about file names — `strings.xml`, `sheet.xml`, or a file exported from a
spreadsheet are all treated the same way, because a resource is identified by its `name` attribute.

## Contents

- [Features](#features)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Using the plugin](#using-the-plugin)
- [MCP tools](#mcp-tools)
- [Known limitations](#known-limitations)
- [For developers](#for-developers)
- [License](#license)

## Features

- **Discovery** of every strings file in every module of the project.
- **Table of keys and locales** with filters by module, by file and by status.
- **Missing translations** are highlighted.
- **`translatable="false"`** is shown as a check box at the end of the row and can be changed right in
  the table. Keys that must not be translated are never reported as missing.
- **Editing** of a translation directly in the table, with undo.
- **Renaming** of a key — the new name is applied to all translations at once.
- **Adding and removing** of keys, with a choice of the target file.
- **Live refresh** when the files change on disk.
- **Horizontal scrolling** when a project has many locales; the column with the keys stays in place.
- **MCP tools** so that AI agents can work with the same resources.

## Compatibility

| IDE | Version |
|---|---|
| IntelliJ IDEA | 2026.1 (build 261) or newer |
| Android Studio | 2026.1 (Quail) or 2026.2 (Rabbit) or newer |

The plugin has no dependency on the Android plugin and works in a plain IntelliJ IDEA.

## Installation

### From a release archive

1. Download `StringManagerPlugin-<version>.zip` from the [releases](https://github.com/yegor/StringManagerPlugin/releases).
2. Open **Settings | Plugins | ⚙ | Install Plugin from Disk…**, choose the archive, and restart the IDE.

The archive built for IntelliJ IDEA works in Android Studio as well. The archive built for Android Studio
requires Java 25 and therefore does not load in IntelliJ IDEA 2026.1.

### From sources

```bash
./gradlew runIde                                        # IntelliJ IDEA 2026.1 with the plugin
./gradlew runIde -PintellijPlatformTarget=android       # Android Studio 2026.2.1.8 with the plugin
```

## Using the plugin

Open the tool window **String Manager** — it is available in the right stripe, in
**View | Tool Windows | String Manager**, and in the main menu **Tools | String Manager** (that menu item also
brings the tool window back if it was closed by accident).

```
┌────────────┬────────────────────┬────────────────────┬───────────────┐
│ Key        │ default            │ ru                 │ Translatable  │
├────────────┼────────────────────┼────────────────────┼───────────────┤
│ greeting   │ Hello              │ Привет             │ ☑             │
│ bye        │ Bye                │                    │ ☑             │
│ app_name   │ My App             │ (red — no          │ ☐             │
│            │                    │  translation)      │               │
└────────────┴────────────────────┴────────────────────┴───────────────┘
```

- **Red** background — the key has no value in this locale.
- **Yellow** background — the resource is marked as `translatable="false"`.
- **Translatable** — the flag of the whole key. Unchecking it writes `translatable="false"` into the file of
  the default locale, the translations inherit it from there; checking it again removes the attribute.
  A key that is not translatable is not highlighted as a missing translation.

### Editing

| Action | How |
|---|---|
| Change a translation | Click the cell and type the new value |
| Rename a key | Edit the `Key` column, confirm the dialog — all translations are renamed at once |
| Add a key | **Add key…** — choose the file; the file with the most keys of the default locale is offered by default |
| Remove a key | Select the row and press **Remove key** |
| Refresh | **Refresh** |

Every change is a single undoable action, formatting and comments of the XML file are preserved.

### Filters

The panel above the table filters the rows by module, by file and by status:

- **All** — every key;
- **Missing translation** — keys that have to be translated but are missing in at least one locale;
- **Not translatable** — keys marked as `translatable="false"`.

## MCP tools

The plugin exposes the same operations as MCP tools, so that AI agents (Claude Code, Codex, Junie, …) can
work with the resources of the project:

| Tool | What it does |
|---|---|
| `list_string_keys` | All keys of the project |
| `list_missing_translations` | Keys and the locales without a value |
| `list_string_files` | All files with strings, with their module and locale |
| `get_string` | The value of a string in a locale |
| `set_string` | Writes a value, creates the string when it does not exist |
| `add_string` | Adds a string to a locale |
| `remove_string` | Removes a string from a locale |
| `rename_string` | Renames a key in all its translations |
| `mark_translatable` | Sets or removes `translatable="false"` |

The tools work when the **MCP Server** plugin (`com.intellij.mcpServer`) is installed. It is bundled with
IntelliJ IDEA; in Android Studio install **MCP Server** from the Marketplace and enable the server in
**Settings | Tools | MCP Server**.

## Known limitations

- only `<string>` resources are managed; `<plurals>` and `<string-array>` are not shown in the table;
- no synchronization with Google Sheets and no automatic translation yet.

## For developers

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — how the plugin is built and why;
- [docs/PUBLISHING.md](docs/PUBLISHING.md) — how to build, verify and publish a release;
- [swarm-report/spikes.md](swarm-report/spikes.md) — the empirical checks the implementation is based on.

```bash
./gradlew test          # 40 tests
./gradlew buildPlugin   # build/distributions/StringManagerPlugin-<version>.zip
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
