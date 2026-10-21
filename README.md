# String Manager Plugin

Плагин для **IntelliJ IDEA** и **Android Studio**, который находит и позволяет управлять **всеми** строковыми
ресурсами проекта:

- Android: `<module>/src/*/res/values*/*.xml`;
- Compose Multiplatform: `<module>/src/<sourceSet>/composeResources/values*/*.xml`.

Имена файлов могут быть любыми (`strings.xml`, `sheet.xml`, файлы, выгруженные из Google-таблиц) — ресурс
определяется атрибутом `name`, а не именем файла. Имя ресурса в Android и в Compose Multiplatform берётся
из атрибута `name`, поэтому «лист Google-таблицы → один файл» работает без специальных настроек.

---

## Возможности

| # | Возможность | Где |
|---|---|---|
| 1 | Обнаружение всех файлов со строками во всех модулях | `View | Tool Windows | String Manager` |
| 2 | Таблица «ключ × локаль» с фильтрами по модулю, файлу и статусу | tool window |
| 3 | Подсветка пропущенных переводов | tool window |
| 4 | Флаг `translatable="false"` в виде галочки в конце таблицы, меняется прямо в таблице | tool window |
| 5 | Непереводимые ключи не считаются пропущенными переводами (нет красной подсветки) | tool window |
| 6 | Правка значения перевода прямо в ячейке (с undo) | tool window |
| 7 | Переименование ключа — меняется во **всех** переводах сразу | двойной клик по колонке `Key` |
| 8 | Добавление ключа с выбором файла | кнопка `Add key...` |
| 9 | Удаление ключа | кнопка `Remove key` |
| 10 | Живое обновление при изменении файлов на диске | автоматически |
| 11 | MCP-инструменты для AI-агентов | MCP-сервер IDE |

### Как это выглядит

```
┌────────────┬────────────────────┬────────────────────┬───────────────┐
│ Key        │ default            │ ru                 │ Translatable  │
├────────────┼────────────────────┼────────────────────┼───────────────┤
│ greeting   │ Hello              │ Привет             │ ☑             │
│ bye        │ Bye                │                    │ ☑             │
│ app_name   │ My App             │ (красный фон — нет │ ☐             │
│            │                    │  перевода)         │               │
└────────────┴────────────────────┴────────────────────┴───────────────┘
```

- красный фон — ключ есть, но в этой локали перевода нет;
- жёлтый фон — ресурс помечен `translatable="false"`;
- колонка `Translatable` — общий флаг ключа во всех переводах: сняли галочку → во всех файлах, где есть
  ключ, появится `translatable="false"`; поставили → атрибут удалится;
- у ключа с `translatable="false"` пустые ячейки переводов **не** подсвечиваются красным: переводить его
  не нужно.

---

## Требования

- IntelliJ IDEA 2026.1 (build 261) или новее;
- Android Studio 2026.1 (Quail) / 2026.2 (Rabbit) или новее;
- JDK 21 (для сборки под IDEA) / JDK 25 (для сборки под Android Studio 2026.2).

Плагин не зависит от Android-плагина и работает в чистой IntelliJ IDEA.

---

## Установка

### Из готового архива

1. Скачайте `StringManagerPlugin-<version>.zip`.
2. **Settings | Plugins | ⚙ | Install Plugin from Disk…** → выберите архив → **Restart**.

Архив, собранный под IntelliJ IDEA (`-idea.zip`), подходит и для Android Studio: он скомпилирован с
`jvmTarget = 21`, а Android Studio 2026.2 работает на Java 25. Архив, собранный под Android Studio
(`-android-studio.zip`), требует Java 25 и в IntelliJ IDEA 2026.1 не загрузится.

### Из исходников

```bash
./gradlew runIde                        # запуск IntelliJ IDEA 2026.1 с плагином
./gradlew runIde -PintellijPlatformTarget=android   # запуск Android Studio 2026.2.1.8 с плагином
```

---

## Сборка

```bash
./gradlew test                                   # тесты (40 шт.)
./gradlew buildPlugin                            # → build/distributions/StringManagerPlugin-<version>.zip
./gradlew buildPlugin -PintellijPlatformTarget=android
./gradlew verifyPlugin                           # Plugin Verifier: 261, 262, 263
./gradlew verifyPluginConfiguration              # проверка требований JetBrains Marketplace
```

---

## MCP-инструменты

Набор инструментов регистрируется через extension point `com.intellij.mcpServer.mcpToolset` и включается,
если в IDE установлен плагин **MCP Server** (`com.intellij.mcpServer`):

- в IntelliJ IDEA 2026.1 он встроен;
- в Android Studio его нет из коробки — установите **MCP Server** из Marketplace
  (`Settings | Plugins | Marketplace`), затем включите сервер в `Settings | Tools | MCP Server`.

| Инструмент | Что делает |
|---|---|
| `list_string_keys` | все ключи проекта |
| `list_missing_translations` | ключи и локали без перевода |
| `list_string_files` | все файлы со строками, их модуль и локаль |
| `get_string` | значение строки в локали |
| `set_string` | записать значение (создаёт строку, если её нет) |
| `add_string` | добавить строку |
| `remove_string` | удалить строку |
| `rename_string` | переименовать ключ во всех переводах |
| `mark_translatable` | поставить/снять `translatable="false"` |

---

## Архитектура

```
src/main/kotlin/com/yegor/stringmanager/
├── core/                       модель, обнаружение, запись — без UI и без MCP
│   ├── LocaleQualifiers.kt     разбор квалификаторов локалей (values, values-ru, values-ru-rRU, values-b+zh+Hans)
│   ├── ResourceDiscovery.kt    обход модулей: content roots + source roots
│   ├── StringResourceWriter.kt чтение и запись через PSI (XmlFile / XmlTag)
│   ├── StringEscaping.kt       экранирование: Android — да, Compose Multiplatform — нет
│   ├── StringResourceService.kt единая точка входа для UI и MCP
│   ├── StringResourceListener.kt  живое обновление по VFS-событиям
│   └── StringManagerStartupActivity.kt
├── ui/                         tool window и таблица (UI DSL v2)
│   ├── StringManagerToolWindowFactory.kt
│   ├── StringManagerPanel.kt
│   ├── StringsTableModel.kt
│   ├── AddStringDialog.kt
│   └── ShowStringManagerAction.kt   пункт меню Tools | String Manager
└── mcp/                        MCP-toolset, регистрируется в optional-конфигурации stringmanager-mcp.xml
```

Ключевые технические решения:

- **обнаружение** — собственный обход модулей (`ModuleManager` + `ModuleRootManager`), без
  `StudioResourceRepositoryManager` и `AndroidFacet`: Compose Multiplatform-каталоги не являются
  Android res-корнем, а Android-плагина в IDEA может не быть;
- **запись** — через PSI внутри `WriteCommandAction`, поэтому работает undo, сохраняются форматирование,
  комментарии и структура файла; новая строка добавляется так, чтобы не трогать остальные элементы файла;
- **локали** — `values` (default), `values-ru`, `values-ru-rRU` (регион с префиксом `r`, регистр важен),
  `values-b+zh+Hans` (BCP 47, дефисы заменены на `+`);
- **подсветка** — пропуск перевода считается только для переводимых ключей.

---

## Что проверено эмпирически

Результаты запусков — в [`swarm-report/spikes.md`](swarm-report/spikes.md). Коротко:

- IntelliJ IDEA Community больше не публикуется с 2025.3 (253) — сборка идёт против `intellijIdea("2026.1")`;
- в Android Studio 2026.2.1.8 плагина MCP Server нет, в IDEA 2026.1 — есть;
- генератор Compose Multiplatform **игнорирует** `translatable="false"` (не ошибка, а неизвестный атрибут),
  поэтому в CMP галочка — это только проявление атрибута в XML;
- произвольные имена файлов в `values` поддерживаются генератором Compose Multiplatform;
- запись через PSI сохраняет форматирование, комментарии и минимальный diff.

---

## Ограничения MVP

- редактируются только `<string>`; `<plurals>` и `<string-array>` не редактируются (их ключи не видны в таблице);
- синхронизация с Google Sheets и AI-перевод не входят в MVP;
- MCP-инструменты требуют плагина MCP Server.

---

## Публикация в JetBrains Marketplace

Всё необходимое уже настроено в `build.gradle.kts`: id `com.yegor.stringmanager`, версия из `gradle.properties`,
описание, `sinceBuild = 261` без `untilBuild`, vendor, иконки `pluginIcon.svg` / `pluginIcon_dark.svg` (40×40),
канал публикации `default`.

### 1. Создать плагин в Marketplace

1. Откройте <https://plugins.jetbrains.com/pluginManager/add> (нужен аккаунт JetBrains Account).
2. Укажите **Plugin id**: `com.yegor.stringmanager`.
3. Скопируйте сгенерированный **Token**.

### 2. Опубликовать

```bash
export PUBLISH_TOKEN=<токен из Marketplace>
./gradlew publishPlugin
```

Токен читается из переменной окружения `PUBLISH_TOKEN` и не попадает в репозиторий.

### 3. Проверки перед публикацией

```bash
./gradlew verifyPluginConfiguration   # конфигурация проекта и plugin.xml
./gradlew verifyPluginStructure       # структура plugin.xml и архива
./gradlew verifyPlugin                # совместимость с 261, 262, 263
./gradlew signPlugin                  # подпись Marketplace ZIP Signer (нужен ключ)
```

### 4. Заполнить страницу в Marketplace

Описание, скриншоты, видео и категорию («Other Tools» / «Localization») задаются в веб-интерфейсе
Marketplace после первой публикации. Текст для описания и для release notes — в этом README
и в [CHANGELOG.md](CHANGELOG.md).

---

## Лицензия

Apache License 2.0 — см. [LICENSE](LICENSE).
