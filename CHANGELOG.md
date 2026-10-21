<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# String Manager Plugin Changelog

## [Unreleased]

### Added

- Search by keys and by translations at the same time, the case is ignored
- The column with the keys stays in place when the locales are scrolled horizontally
- The changes of the `translatable` flag are shown right away, without waiting for a refresh

## [0.1.0] - 2026-10-21

### Added

- Discovery of Android `res/values*` and Compose Multiplatform `composeResources/values*` files in all modules
- Table of keys and locales with filters by module, file and status
- Highlighting of missing translations and of `translatable="false"` resources
- Editing of a translation right in the table, with undo
- Renaming of a key in all its translations at once
- `Translatable` flag of a key as a check box at the end of the table
- Adding and removing of keys with a choice of the file
- Live refresh when the files change on disk
- Tool window action in the `Tools` menu
- MCP tools for AI agents: list keys, list missing translations, list files, get, set, add, remove,
  rename a string and mark it as not translatable

[Unreleased]: https://github.com/yegor/StringManagerPlugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/yegor/StringManagerPlugin/releases/tag/v0.1.0
