# String Manager Plugin

Плагин для IntelliJ IDEA и Android Studio, который находит и позволяет управлять **всеми** строковыми
ресурсами проекта: Android (`<module>/src/*/res/values*/*.xml`) и Compose Multiplatform
(`<module>/src/<sourceSet>/composeResources/values*/*.xml`).

Имена файлов могут быть любыми (`strings.xml`, `sheet.xml`, файлы, выгруженные из Google-таблиц) —
ресурс определяется атрибутом `name`, а не именем файла.

## Возможности

- обнаружение всех файлов со строками во всех модулях проекта;
- таблица «ключ × локаль» с фильтрами по модулю, файлу и статусу;
- подсветка пропущенных переводов и ресурсов с `translatable="false"`;
- правка значения прямо в таблице, добавление и удаление ключей (с выбором файла), всё через undo;
- живое обновление при изменении файлов на диске;
- MCP-инструменты для AI-агентов (список ключей, список пропусков, чтение/запись строки,
  пометка `translatable`).

## Сборка

```bash
./gradlew buildPlugin        # сборка против IntelliJ IDEA 2026.1
./gradlew test               # тесты core
./gradlew verifyPlugin       # проверка совместимости (261, 262, 263)
```

Сборка под Android Studio:

```bash
./gradlew buildPlugin -PintellijPlatformTarget=android
```

Артефакт: `build/distributions/StringManagerPlugin-<version>.zip`.

## Требования

- IntelliJ IDEA 2026.1 (build 261) и новее, Android Studio 2026.1+;
- JDK 21.

## MCP

MCP-инструменты регистрируются через `com.intellij.mcpServer.mcpToolset` и работают, если установлен
плагин **MCP Server** (`com.intellij.mcpServer`). В IntelliJ IDEA он встроен, в Android Studio —
устанавливается из Marketplace.

## Архитектура

- `core` — модель, обнаружение, чтение и запись через PSI; зависит только от платформы и XML;
- `ui` — tool window с таблицей (UI DSL v2);
- `mcp` — toolset для MCP-сервера; регистрируется в optional-конфигурации `stringmanager-mcp.xml`.

Подробности эмпирических проверок — в [swarm-report/spikes.md](swarm-report/spikes.md).
