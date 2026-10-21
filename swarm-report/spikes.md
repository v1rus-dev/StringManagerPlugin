# Эмпирические спайки

Дата: 2026-10-20/21. Окружение: Linux, JDK 21.0.12, Gradle 9.8.1, Kotlin 2.4.21,
`org.jetbrains.intellij.platform` 2.19.0.

Все команды запускались из каталога проекта, результаты приведены дословно (сокращено по длине).

---

## Спайк (a): Gradle-проект с `androidStudio(...)` и `intellijIdea(...)`

**Что запускал:** отдельный проект `/tmp/opencode/spike-a` с плагином `org.jetbrains.intellij.platform` 2.19.0,
задача `printBundledPlugins` (она уже есть в плагине, в отличие от предположения в промте).

**Результат 1 — `intellijIdeaCommunity("2026.1")` НЕ резолвится:**

```
[org.jetbrains.intellij.platform] IntelliJ IDEA Community (IC) is no longer published since 2025.3 (253), use: intellijIdea("2026.1")
> Could not find idea:ideaIC:2026.1.
```

IntelliJ IDEA Community больше не публикуется начиная с 2025.3 (253). При этом в maven-репозитории
IntelliJ Platform артефакт `com.jetbrains.intellij.idea:ideaIC:2026.1` **есть**
(`https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2026.1/ideaIC-2026.1.zip`,
645 МБ скачан и распакован вручную), но Gradle-плагин ищет его по старому координатному пути `idea:ideaIC`
и не находит.

**Результат 2 — `intellijIdea("2026.1")` резолвится успешно.** Скачанный дистрибутив:
`IntelliJ IDEA 2026.1, build 261.22158.277`, 60 bundled-плагинов, включая `MCP Server`.

**Результат 3 — `androidStudio("2026.2.1.8")` резолвится успешно** (версия из
`https://jb.gg/android-studio-releases-list.xml`, канал Release, build `AI-262.9437.185.2621.16467764`).
Bundled-плагины включают `org.jetbrains.android (Android)`, `com.intellij.compose (Compose Multiplatform)`,
`com.google.tools.ij.aiplugin (Gemini)`.

**Вывод:** в `build.gradle.kts` используется `intellijIdea("2026.1")` (единый дистрибутив);
цель сборки переключается свойством `-PintellijPlatformTarget=android` (тогда `androidStudio("2026.2.1.8")`).

---

## Спайк (b): установка плагина в IDEA без Android-плагина

**Что запускал:**

1. `./gradlew buildPlugin` → `build/distributions/StringManagerPlugin-0.1.0.zip`
   (в архиве только `lib/StringManagerPlugin-0.1.0.jar`, внешних зависимостей нет).
2. `./gradlew verifyPlugin` → проверка против `IU-261.27258.48`, `IU-262.10968.63`, `IU-263.7031.23`:
   вердикт `Compatible. 6 usages of deprecated API. 6 usages of experimental API`.
3. Установку: распаковка архива в `plugins/` локально скачанного IntelliJ IDEA 2026.1 и headless-запуск
   `bin/idea.sh inspect /tmp/opencode/sample-project ...` (в проекте-образце лежат
   `app/src/main/res/values/strings.xml` и `app/src/main/res/values-ru/strings.xml`).

**Результат:** плагин загрузился в IDEA 2026.1, где Android-плагина нет. В `idea.log`:

```
Loaded bundled plugins: IDEA CORE (261.22158.277), ..., MCP Server (261.22158.277), String Manager (0.1.0), ...
```

Ошибок загрузки (`Cannot load plugin`, `ClassNotFoundException`, `NoClassDefFoundError`) в логе нет,
headless-анализ проекта прошёл. В `plugin.xml` нет зависимости от `org.jetbrains.android` —
плагин работает в чистой IDEA.

---

## Спайк (c): запись через PSI в реальном XML

**Что запускал:** `./gradlew test` — тесты `StringResourceWriterTest` на реальных XML-файлах
(temp-файловая система IDE), сравнение полного текста файла до/после (это и есть diff).

**Результат (подтверждено):**

- изменение значения существующей строки: меняется только текст, отступы, комментарии и порядок элементов сохраняются;
- удаление строки: удаляется только целевой `<string>`, форматирование сохраняется;
- установка/снятие `translatable="false"`: атрибут добавляется/удаляется, остальное не меняется;
- добавление новой строки: **нужен обходной путь** — тег, созданный через `XmlElementFactory.createTagFromText`,
  нельзя наполнить значением после вставки (`setText` на не привязанном к AST узле тега теряется).
  Рабочий порядок: создать тег с текстом-заглушкой (`<string name="k">x</string>`), вставить его
  через `XmlTag.addSubTag`, затем найти вставленный тег по атрибуту `name` и заменить значение
  через `XmlTag.getValue().setText(...)`. Отступы при этом сохраняются корректно.

**Нюанс экранирования:** `XmlTagValue.setText` экранирует только XML-спецсимволы (`&`, `<`).
Android-экранирование (`\'`, `\"`) платформа не делает — оно добавлено в core (`StringEscaping`);
для Compose Multiplatform экранирование не применяется.

---

## Спайк (d): есть ли MCP Server в Android Studio

**Что проверял:** распакованный Android Studio 2026.2.1.8 (Rabbit 1) — каталоги в `plugins/`
(77 плагинов) и `printBundledPlugins` из Gradle-плагина.

**Результат:** плагина `mcpserver` в Android Studio 2026.2.1.8 **НЕТ**.
В IDEA 2026.1 (261.22158.277) он **ЕСТЬ**: `plugins/mcpserver`, id `com.intellij.mcpServer`,
EP `com.intellij.mcpServer.mcpToolset`, интерфейс `com.intellij.mcpserver.McpToolset`,
аннотации `com.intellij.mcpserver.annotations.{McpTool, McpDescription, McpToolHints}`.

**Следствие:** в Android Studio наш MCP-toolset не активируется, пока плагин `MCP Server`
не установлен из Marketplace. Он там есть: xmlId `com.intellij.mcpServer`, сборки для веток 261 и 262,
в т.ч. `262.9437.214` (совместима с Android Studio Rabbit 1 `262.9437.185`).

---

## Спайк (e): генератор Compose Multiplatform и `translatable="false"`

**Что запускал:** минимальный KMP-проект `/tmp/opencode/spike-e` (Kotlin 2.4.21, Compose 1.12.2,
зависимость `compose.components.resources`) с ресурсами:

```
composeResources/values/strings.xml           → <string name="greeting">, <string name="locked" translatable="false">
composeResources/values/sheet.xml             → <string name="from_sheet">
composeResources/values-ru/strings.xml
composeResources/values-b+zh+Hans/strings.xml (без values-zh)
```

Задачи: `convertXmlValueResourcesForCommonMain`, `generateResourceAccessorsForCommonMain`, `compileKotlinJvm`.

**Результат:**

1. **Генерация прошла успешно**, ошибок нет.
2. **`translatable="false"` не учитывается и не ломает генерацию.** В скомпилированном ресурсе
   (`build/generated/.../values/strings.commonMain.cvr`) атрибута нет, а строка `locked` попала
   в аксессоры как обычная:

   ```
   internal val Res.string.locked: StringResource by lazy { StringResource("string:locked", "locked", ...) }
   ```

   То есть в Compose Multiplatform атрибут `translatable` — просто неизвестный атрибут, а не признак
   «не переводить». Наш плагин показывает его, но семантики за ним в CMP нет.
3. **Произвольные имена файлов в `values` работают**: `sheet.xml` дал аксессор `Res.string.from_sheet`.
4. **Квалификаторы разбираются как ожидается**:
   `values-b+zh+Hans` → `LanguageQualifier("zh") + ScriptQualifier("Hans")`,
   `values-ru` → `LanguageQualifier("ru")`, `values` → без квалификаторов.
5. **Утверждение промта о падении при script-специфичном каталоге без базового языка НЕ подтвердилось**
   на CMP 1.12.2: генерация успешна, а runtime-код (`ResourceEnvironment.kt`) при отсутствии подходящего item'а
   явно предусматривает fallback на script-тегированные ресурсы
   («fall back to script-tagged items only if nothing else matches»). Исключений такого вида в
   `ResourceReader`/`ResourceEnvironment` нет.
   Поэтому в плагине это оставлено как **мягкое предупреждение** («проверьте, что генератор это допускает»),
   а не как «сборка упадёт».

---

## Спайк (f): сборка против Android Studio 2026.2.1.8 (ветка 262)

**Что запускал:** `./gradlew buildPlugin -PintellijPlatformTarget=android`.

**Результат:** сборка успешна, но вылез нюанс из промта про Java 25:

```
⛔ Inconsistent JVM Target Compatibility Between Java and Java/Kotlin Tasks
  Inconsistent JVM-target compatibility detected for tasks 'compileJava' (25) and 'compileKotlin' (21).
```

Android Studio 2026.2 (ветка 262) требует Java 25, поэтому `compileJava` в целевой IDE настроен на 25,
а заданный вручную `jvmTarget = 21` для Kotlin конфликтует. Исправлено условием в `build.gradle.kts`:

```kotlin
jvmTarget = if (platformTarget.get() == "android") JvmTarget.JVM_25 else JvmTarget.JVM_21
```

Также подтвердилось, что `plugin("com.intellij.mcpServer", "262.9437.214")` (Marketplace) резолвится
для Android Studio, тогда как `bundledPlugin("com.intellij.mcpServer")` применим только к IDEA 2026.1.

---

## Доработки после MVP

- **Переименование ключа** — `StringResourceWriter.renameKey(files, oldKey, newKey)`: меняет атрибут `name`
  во всех `<string>` с таким именем во всех файлах, где ключ определён, одной командой (undo откатывает всё сразу).
- **Флаг `translatable` как галочка** — последняя колонка таблицы, рендерится и редактируется чекбоксом;
  флаг общий для ключа и записывается во все файлы, где ключ есть.
- **Красная подсветка только для переводимых ключей** — если ключ помечен `translatable="false"`, пустые ячейки
  переводов не подсвечиваются.
- **Горизонтальная прокрутка** — `table.autoResizeMode = AUTO_RESIZE_OFF` (иначе `JTable` сжимал колонки
  под ширину панели и полосы прокрутки не было) + ширина колонки по содержимому, 120…400 px.
- **Иконка** — `icons/toolWindow.svg` и `icons/toolWindow_dark.svg` (16×16, четыре линии), подключена через
  `icon="/icons/toolWindow.svg"` в `<toolWindow>`; для Marketplace — `META-INF/pluginIcon.svg` (40×40).
- **Пункт меню `Tools | String Manager`** — действие `StringManager.Show` с `add-to-group group-id="ToolsMenu"`,
  оно возвращает tool window, если его случайно скрыли.
- **Проверки Marketplace**: `verifyPluginConfiguration` и `verifyPluginStructure` — обе проходят;
  Plugin Verifier — `Compatible` против 261/262/263.
- **Plugin id с дефисом** (`io.github.v1rus-dev.stringmanager`) принят валидатором структуры
  `verifyPluginStructure` — ограничений на дефис в id нет.

## Прочие проверенные факты

- `com.intellij.modules.xml` — валидная зависимость (модуль объявлен в `xml/plugin/resources/META-INF/plugin.xml`).
- Инъекция `CoroutineScope` в конструктор сервиса поддерживается платформой
  (`ComponentManagerImpl.findConstructorAndInstantiateClass`, `constructorInjection.kt`).
- UI DSL v2 (`com.intellij.ui.dsl.builder`) доступен в 261; v1 не используется.
- `bundledPlugin("com.intellij.mcpServer")` работает для IDEA 2026.1; для Android Studio MCP Server
  берётся из Marketplace через `plugin("com.intellij.mcpServer", "262.9437.214")`.
- `project.baseDir` помечен deprecated в 261 — используется `project.guessProjectDir()`.
