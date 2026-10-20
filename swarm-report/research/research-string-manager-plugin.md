# Research: плагин управления string-ресурсами (Android + Compose Multiplatform) для IDEA и Android Studio

Date: 2026-10-20
Experts consulted: Web, Docs (платформа), Docs (MCP), Dependencies. Codebase и Architecture пропущены: проект пустой.
Auto-review mode: tech-sanity

## Problem / Question Summary
Нужен плагин для IntelliJ IDEA и Android Studio, который находит все XML со строками во всех модулях (Android `values*/` и CMP `composeResources/values*/`, в том числе файлы с произвольными именами, например «лист Google-таблицы → файл»), показывает таблицу ключ × локаль с подсветкой пропущенных переводов и `translatable="false"`, позволяет править всё из UI и выставляет те же операции как MCP-инструменты для AI-агентов.

## Assumptions (решения, принятые без вопроса к пользователю)
- ASSUMPTION: обнаружение строк делаем собственным обходом source roots/VFS, а не через Android-репозиторий ресурсов — причина: CMP-каталог `composeResources` не является Android res-корнем, а в IDEA Android-плагина может не быть; откат: добавить опциональный адаптер к `ResourceRepositoryManager`.
- ASSUMPTION: платформенная база — 261 (IDEA 2026.1 / Android Studio Quail), JDK 21, без `untilBuild`; проверка verifier на 261 и 262 — причина: Java 21 общая для обоих, 262 требует Java 25; откат: поднять `sinceBuild` до 262 или опустить до 253.
- ASSUMPTION: MCP делаем через встроенный `com.intellij.mcpServer` (EP `mcpToolset`) в опциональном дескрипторе; свой сервер — только запасной вариант — причина: не тянем Ktor/SDK и не поднимаем второй порт; откат: встроить `kotlin-sdk`.
- ASSUMPTION: синхронизация с Google Sheets и AI-перевод в MVP не входят — причина: отдельные крупные функции.

## Approaches Found

### Approach 1: Собственный слой обнаружения + PSI-запись (рекомендуется)
- **Description:** модуль core (только `com.intellij.modules.platform` + XML) обходит source roots модулей, находит каталоги `values*` (включая `b+...`, `rXX`), парсит любые `*.xml` с `<resources>`, строит модель «ключ → файл → локаль → значение/флаги». Запись через `WriteCommandAction` над `XmlTag`. Android-специфика и MCP подключаются через `<depends optional="true" config-file=...>`.
- **Trade-offs:** больше своего кода (правила квалификаторов, слияние файлов), зато одинаково работает для Android, CMP и обычной IDEA и не зависит от внутренних API Android-плагина.
- **Evidence:** Docs: `StudioResourceRepositoryManager` завязан на `AndroidFacet`, официального заявления о стабильности нет; CMP-корень `composeResources` и произвольные `.xml` внутри `values` подтверждены докой kotlinlang; опциональная зависимость описана в plugin-dependencies.
- **Compatibility:** IDEA и Android Studio; K2-нейтрально (Kotlin-анализ не нужен).

### Approach 2: Надстройка над Android resource repository
- **Description:** брать ресурсы из `StudioResourceRepositoryManager`, для CMP — отдельный путь.
- **Trade-offs:** меньше кода для Android, но два разных пути, зависимость от нестабильного API и обязательный Android-плагин.
- **Evidence:** Docs (API помечены `@Slow`, публичность не заявлена); гипотеза «репозиторий не видит composeResources» подтверждена только рассуждением, не запуском.
- **Compatibility:** не работает в IDEA без Android-плагина.

### Approach 3: Расширить существующий CMP Translations
- **Description:** форк или вклад в `ienground/cmp-translations-plugin`.
- **Trade-offs:** быстрый старт, но плагин ограничен `composeResources/**/values*/strings.xml`, не покрывает Android `res/` и произвольные имена; чужая кодовая база и лицензия не проверены.
- **Evidence:** Web (README плагина, T4, single-source).

### Side-by-side comparison

| Dimension | 1. Свой слой | 2. Android-репозиторий | 3. Форк CMP Translations |
|---|---|---|---|
| Effort | M | M + отдельный CMP-путь | S–M |
| Maintainability | + | − (внутренние API) | ? (чужой код) |
| Compatibility | IDEA + AS | только с Android-плагином | IDEA + AS, но только CMP |
| Risk | low–med | high | med |

## MCP-инструменты

### Вариант A: встроенный MCP-сервер IDE (рекомендуется)
- Плагин `com.intellij.mcpServer` встроен в IDEA с 2025.2; EP `com.intellij.mcpServer.mcpToolset`, интерфейс `McpToolset`, аннотации `@McpTool`, `@McpDescription`, `@McpToolHints`; проект передаётся автоматически; запись через `writeCommandAction` даёт undo. Зависимость — optional через `config-file`; класс toolset должен лежать только в optional-ветке.
- Стабильность формально не заявлена (`isExperimental()` по умолчанию true); сторонние плагины JetBrains этот EP уже используют.
- **В Android Studio не подтверждено:** доки Studio описывают только роль MCP-клиента; по метаданным Marketplace плагин 26071 совместим с `ANDROID_STUDIO` для веток 252–262, но «поставляется в комплекте» ни одним источником не подтверждено.

### Вариант B: собственный сервер в плагине (запасной)
- `io.modelcontextprotocol:kotlin-sdk` 0.15.0, Streamable HTTP (рекомендуется SDK), требует Ktor 3.5.1; платформенный mcpServer сам встраивает SDK и Ktor — риск конфликта классов, политика платформы по Ktor не проверена. Нужны свой порт, аутентификация и инструкция подключения. Это новая зависимость → отдельное согласование по правилу dependencies.

## Library / Dependency Recommendations
| Library | Version | KMP | Vulnerabilities | Notes |
|---|---|---|---|---|
| `org.jetbrains.intellij.platform` (Gradle plugin) | 2.19.0 (2026-09-15) | n/a | none (OSV) | Docs-агент называл 2.11.0 из поиска; верим maven-metadata. Шаблон: settings-вариант плагина |
| Kotlin Gradle plugin | 2.4.21 | n/a | none | stdlib платформы: 2026.1 → 2.3.20, 2026.2 → 2.4.0; `kotlin.stdlib.default.dependency=false` |
| Gradle | 9.8.1 (шаблон; минимум 9.0.0) | n/a | n/a | |
| `org.jetbrains.changelog` | 2.5.0 | n/a | none | |
| JDK | 21 для 253/261, 25 для 262+ | n/a | n/a | |
| Android Studio stable | Rabbit 1 = 2026.2.1.8 (ветка 262); Quail 4 = 2026.1.4.8 (ветка 261) | n/a | n/a | резолвится через installer, не Maven |
| Сторонняя XML-библиотека | не нужна | n/a | n/a | PSI платформы достаточно |

## Risks and Concerns
- major: Android Studio может не содержать `com.intellij.mcpServer` — тогда MCP там работает только если плагин установлен из Marketplace или нужен вариант B.
- major: формат записи через PSI «сохраняет форматирование» — общеизвестное свойство, в этой сессии не проверено; риск испортить diff в пользовательских XML.
- major: `translatable="false"`, CDATA, xliff, `tools:ignore` для CMP в доках не подтверждены; Android-страницы про `translatable` не найдены.
- minor: доки Android Studio для Gradle-плагина противоречивы (`bundledPlugin` против `plugin(...)` для `org.jetbrains.android`); формат версии `androidStudio()` не проверен реальным резолвом.
- minor: JBTable/TableView/ListTableModel в официальных доках не описаны — нужен ksrc/исходники платформы.
- minor: Kotlin UI DSL v1 удалён в 2026.3 — использовать только v2; К1 удалён в 2026.2.
- minor: рейтинги и отзывы существующих плагинов Marketplace не получены.

## Recommendation
Подход 1 + MCP через встроенный mcpServer (вариант A) с запасным вариантом B. Архитектура: core-слой (модель строк, обнаружение, запись) без UI и без MCP; UI (tool window / editor-вкладка с таблицей) и MCP-toolset — два потребителя одного сервиса; Android-специфика и MCP — в optional-дескрипторах. Дифференциация от существующих плагинов: единый вид Android + CMP, мультимодульность, произвольные имена файлов с группировкой по файлу, `translatable="false"`, MCP.

MVP: обнаружение по модулям, таблица ключ × локаль с фильтром по файлу/модулю/статусу, подсветка пропусков и non-translatable, правка значения и добавление ключа с выбором файла, undo, живое обновление через `BulkFileListener`, MCP: список ключей/пропусков, get/set строки, mark non-translatable. Не в MVP: plurals/string-array-редактор (после проверки), Google Sheets, AI-перевод.

Перед реализацией — эмпирические спайки (теория не считается подтверждением): (1) Gradle-проект с `androidStudio("2026.2.1.8")` и `intellijIdea(...)`, `printBundledPlugins`; (2) установка плагина в IDEA без Android-плагина; (3) PSI-запись в реальный XML и diff; (4) `Settings | Tools` в Android Studio — есть ли MCP Server; (5) генератор CMP: учитывает ли `translatable="false"` и произвольные имена файлов.

## Known Unknowns
- Версия Kotlin, встроенная в Android Studio Rabbit 1 / Quail 4 — определяется распаковкой installer (спайк 1).
- Порт и схема аутентификации встроенного MCP-сервера IDEA по умолчанию в документации не указаны.
- Название и устройство «плагина с листами Google-таблицы» не установлено; возможно, это Gradle-плагин.

## Sources
- Web: https://plugins.jetbrains.com/plugin/33682-compose-multiplatform-translations, https://github.com/ienground/cmp-translations-plugin, https://developer.android.com/studio/write/translations-editor, https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html, https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html
- Platform docs: https://plugins.jetbrains.com/docs/intellij/tool-windows.html, modifying-psi.html, documents.html, virtual-file-system.html, threading-model.html, plugin-dependencies.html, android-studio.html, api-changes-list-2026.html, using-kotlin.html, build-number-ranges.html, tools-intellij-platform-gradle-plugin-dependencies-extension.html
- Android: https://developer.android.com/guide/topics/resources/localization, https://developer.android.com/guide/topics/resources/string-resource, https://developer.android.com/studio/write/tool-attributes
- Toolchain: https://raw.githubusercontent.com/JetBrains/intellij-platform-gradle-plugin/main/CHANGELOG.md, https://github.com/JetBrains/intellij-platform-plugin-template, jb.gg/android-studio-releases-list.xml, maven-mcp (OSV)
- MCP: https://www.jetbrains.com/help/idea/mcp-server.html, https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/mcp-server/README.md, .../McpToolset.kt, https://plugins.jetbrains.com/api/plugins/26071/updates, https://raw.githubusercontent.com/modelcontextprotocol/kotlin-sdk/main/README.md, https://developer.android.com/studio/gemini/add-mcp-server
- Android Studio API: https://raw.githubusercontent.com/JetBrains/android/master/android/src/com/android/tools/idea/res/StudioResourceRepositoryManager.java
