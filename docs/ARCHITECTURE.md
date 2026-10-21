# Architecture

How the plugin is built and why. For the user documentation see [README.md](../README.md).

## Layers

```
src/main/kotlin/com/yegor/stringmanager/
├── core/                       model, discovery, writing — no UI, no MCP
│   ├── LocaleQualifiers.kt     parsing of the locale qualifiers
│   ├── ResourceDiscovery.kt    walking through the modules
│   ├── StringResourceWriter.kt reading and writing through PSI
│   ├── StringEscaping.kt       escaping: Android yes, Compose Multiplatform no
│   ├── StringResourceService.kt the single entry point for UI and MCP
│   ├── StringResourceListener.kt live updates on VFS events
│   └── StringManagerStartupActivity.kt
├── ui/                         tool window and table (UI DSL v2)
│   ├── StringManagerToolWindowFactory.kt
│   ├── StringManagerPanel.kt
│   ├── StringsTableModel.kt
│   ├── AddStringDialog.kt
│   └── ShowStringManagerAction.kt   Tools | String Manager menu item
└── mcp/                        MCP toolset, registered in the optional file stringmanager-mcp.xml
```

The layers are separated by packages, not by Gradle modules: the MCP classes are loaded only through the
optional configuration file, so a build without the MCP Server plugin never touches them.

## Discovery

`ResourceDiscovery` walks the **content roots** and the **source roots** of every module and picks up the
directories named `values*` inside `res` (Android) and `composeResources` (Compose Multiplatform).

The Android resource repository (`StudioResourceRepositoryManager`) is deliberately not used:

- it requires the Android plugin, which is not always installed;
- `composeResources` is not an Android res root, so it would never be found anyway.

Files that contain no `<string>` (for example `colors.xml`) are dropped from the table.

## Locale qualifiers

`LocaleQualifiers` parses the directory names used by both ecosystems:

| Directory | Result |
|---|---|
| `values` | default locale |
| `values-ru` | language `ru` |
| `values-ru-rRU` | language `ru`, region `RU` (the `r` prefix and the case matter) |
| `values-b+zh+Hans` | BCP 47: language `zh`, script `Hans` |
| `values-night`, `values-v26`, `values-sw600dp` | not a locale, ignored |

## Writing

All changes go through PSI inside `WriteCommandAction`, so that:

- every change is undoable;
- formatting, comments and the order of elements survive;
- the diff stays minimal.

A detail worth knowing: a tag created with `XmlElementFactory.createTagFromText` cannot be filled with a
value after it has been inserted — `setText` on that detached node is lost. The working order is to create
the tag with a placeholder text (`<string name="k">x</string>`), insert it with `XmlTag.addSubTag`, find the
inserted tag by its `name` attribute and only then replace the value with `XmlTag.getValue().setText(...)`.

Escaping differs between the ecosystems: `setText` escapes XML characters only, so Android quoting
(`\'`, `\"`) is added by `StringEscaping` for Android files and omitted for Compose Multiplatform files.

## Service, threads and live updates

`StringResourceService` is a project service with an injected `CoroutineScope`:

- scanning happens in a background coroutine inside a `readAction`;
- listeners are notified on the EDT;
- the VFS listener (`BulkFileListener` on `VirtualFileManager.VFS_CHANGES`) only triggers a rescan — the
  scan itself is started outside of the write action.

## Table

`StringsTableModel` holds the snapshot, the filters and the editing callbacks. The panel owns the Swing
part: the check box renderer and editor for the `Translatable` column, and the highlighting of the cells.

The table uses `AUTO_RESIZE_OFF` and computes the width of every column from its content, so that a project
with many locales is scrolled horizontally instead of squeezing the locales.

The column with the keys does not move with the horizontal scroll: it is shown in the row header of the
scroll pane (`JScrollPane.setRowHeaderView`), and the two tables share the model and the selected row.

## The `translatable` flag

The flag belongs to the key, not to a locale:

- the attribute is written into the file of the **default locale** only, because Android and Compose
  Multiplatform inherit the flag from the default locale into the translations;
- removing the flag touches only the files that already have the attribute;
- a key is treated as not translatable when at least one of its resources has the attribute, and such a key
  is never reported as a missing translation.

## Reactivity

Every write is followed by a background rescan (`refreshAsync`), so the EDT is never blocked by a scan of
all files. The flag of a key is shown immediately (`StringsTableModel.overrideTranslatable`), the rescan
confirms it later.

The `translatable` attribute of a key is written into one file only, which keeps the operation fast: a key
that exists in a hundred locales is changed in one file instead of a hundred.

## MCP

The toolset implements `com.intellij.mcpserver.McpToolset` and is registered in
`src/main/resources/META-INF/stringmanager-mcp.xml`, which is referenced from `plugin.xml` as

```xml
<depends optional="true" config-file="stringmanager-mcp.xml">com.intellij.mcpServer</depends>
```

The class references `com.intellij.mcpserver.*` only from this branch, so the plugin loads in an IDE without
the MCP Server plugin.

## Tests

40 tests, all of them run against a real IDE instance:

- `LocaleQualifiersTest` — the qualifiers;
- `ResourceDiscoveryTest` — what is found in a module;
- `StringEscapingTest` — Android vs Compose Multiplatform escaping;
- `StringResourceWriterTest` — writing with a check of the whole text of the file (this is the diff check);
- `StringResourceRenameTest` — renaming and the translatable flag;
- `StringResourceServiceTest` — the snapshot, missing translations and the warnings;
- `StringsTableModelTest` — filters, columns and the translatable flag;
- `StringManagerPanelTest` — the horizontal scrolling of the table;
- `ToolWindowIconTest` — the icons of the tool window.

## Empirical checks

The implementation is based on the runs described in [../swarm-report/spikes.md](../swarm-report/spikes.md).
The most important findings:

- IntelliJ IDEA Community is not published since 2025.3 (253), the build targets `intellijIdea("2026.1")`;
- the MCP Server plugin is bundled with IntelliJ IDEA, but not with Android Studio;
- the Compose Multiplatform generator ignores `translatable="false"` and accepts any file name in `values`;
- writing through PSI keeps the formatting and the comments of the file.

## Build targets

| Target | IDE | `jvmTarget` | Artifact |
|---|---|---|---|
| `idea` (default) | IntelliJ IDEA 2026.1 | 21 | works in IDEA 2026.1+ and Android Studio 2026.1+ |
| `android` | Android Studio 2026.2.1.8 | 25 | works in Android Studio 2026.2+ only |

```bash
./gradlew buildPlugin                             # IntelliJ IDEA
./gradlew buildPlugin -PintellijPlatformTarget=android
```
