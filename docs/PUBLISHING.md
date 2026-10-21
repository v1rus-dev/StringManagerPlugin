# Publishing

How to build, verify and publish a release of the String Manager plugin.

## Plugin identity

| | |
|---|---|
| Plugin id | `io.github.v1rus-dev.stringmanager` |
| Marketplace page | <https://plugins.jetbrains.com/plugin/io.github.v1rus-dev.stringmanager> |
| Group (artifact coordinates) | `com.yegor.stringmanager` in `gradle.properties` |
| Packages | `com.yegor.stringmanager.*` |

The plugin id must stay in sync with the id in `plugin.xml` and with the id of the plugin on the
Marketplace — otherwise the upload is rejected.

## Build

```bash
./gradlew clean test buildPlugin          # IntelliJ IDEA 2026.1
./gradlew buildPlugin -PintellijPlatformTarget=android
```

Artifacts in `build/distributions/`:

- `StringManagerPlugin-<version>-idea.zip` — universal, works in IDEA 2026.1+ and Android Studio 2026.1+;
- `StringManagerPlugin-<version>-android-studio.zip` — Java 25, Android Studio 2026.2+ only.

## Checks before a release

```bash
./gradlew verifyPluginConfiguration   # configuration of the project and of plugin.xml
./gradlew verifyPluginStructure       # structure of plugin.xml and of the archive
./gradlew verifyPlugin                 # compatibility with 261, 262, 263
./gradlew signPlugin                   # signature with the Marketplace ZIP Signer
```

## Publish on the JetBrains Marketplace

### 1. The vendor

Open **Marketplace → My Plugins → Create new plugin** and fill in the vendor details:

| Field | Value |
|---|---|
| Public Vendor Name | the name shown on the Marketplace |
| Vendor ID | the identifier used in the URL `plugins.jetbrains.com/vendor/<VendorID>` |
| Vendor Status | Non-trader (open source) or Trader |
| Email | the contact email |

### 2. The plugin

In the same wizard, step **Plugin Details**:

- **Plugin ID** — `io.github.v1rus-dev.stringmanager` (must match `plugin.xml`);
- **Name** — `String Manager`;
- **Description**, **Category**, **License URL** and the tags.

After the plugin is created, the Marketplace issues an **upload token**.

### 3. Upload

```bash
export PUBLISH_TOKEN=<token from the Marketplace>
./gradlew publishPlugin
```

The token is read from the `PUBLISH_TOKEN` environment variable and is never stored in the repository.
The channel is `default` (see `publishing { channels = listOf("default") }` in `build.gradle.kts`).

### 4. The listing

The description, the screenshots and the video of the plugin page are filled in in the web interface of the
Marketplace after the first upload. The texts for them are taken from [../README.md](../README.md) and
[../CHANGELOG.md](../CHANGELOG.md).

## Release checklist

1. Bump `version` in `gradle.properties`.
2. Move the changes of `CHANGELOG.md` from `Unreleased` to the new version.
3. `./gradlew clean test verifyPlugin buildPlugin`.
4. Commit and tag: `git tag v<version>`.
5. `./gradlew publishPlugin` with `PUBLISH_TOKEN` in the environment.
6. Fill in the Marketplace listing.
