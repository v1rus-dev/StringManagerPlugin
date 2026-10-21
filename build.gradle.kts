import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// Цель сборки/проверки: idea (IntelliJ IDEA 2026.1) или android (Android Studio 2026.2.1.8).
// -PintellijPlatformTarget=android
val platformTarget = providers.gradleProperty("intellijPlatformTarget").orElse("idea")

// Имя цели входит в имя архива, иначе сборки для IDEA и для Android Studio затирают друг друга.
val platformName = if (platformTarget.get() == "android") "android-studio" else "idea"

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        if (platformTarget.get() == "android") {
            androidStudio("2026.2.1.8")
            // The MCP Server plugin is not bundled with Android Studio, it is taken from the Marketplace.
            plugin("com.intellij.mcpServer", "262.9437.214")
        } else {
            // IntelliJ IDEA Community больше не публикуется с 2025.3 (253) — единый дистрибутив IntelliJ IDEA.
            intellijIdea("2026.1")
            bundledPlugin("com.intellij.mcpServer")
        }
        testFramework(TestFrameworkType.Platform)
        testBundledPlugin("com.intellij.modules.xml")
        bundledModule("intellij.libraries.kotlinx.coroutines.core")
    }
}

kotlin {
    compilerOptions {
        // Android Studio 2026.2 (branch 262) runs on Java 25, IntelliJ IDEA 2026.1 (branch 261) on Java 21.
        jvmTarget = if (platformTarget.get() == "android") JvmTarget.JVM_25 else JvmTarget.JVM_21
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.v1rus-dev.stringmanager"
        name = "String Manager"
        version = project.version.toString()
        description =
            "Manages all string resources of a project: Android res/values* and Compose Multiplatform " +
                "composeResources/values* directories. Shows a table of keys and locales, highlights missing " +
                "translations and translatable=false resources, allows editing, renaming, adding and removing " +
                "keys, and exposes the same operations as MCP tools for AI agents."

        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }

        vendor {
            name = "Yegor"
            url = "https://github.com/v1rus-dev/StringManagerPlugin"
            email = "YegorCheprasov@gmail.com"
        }
    }

    // Публикация в JetBrains Marketplace: ./gradlew publishPlugin с переменной окружения PUBLISH_TOKEN.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }

    pluginVerification {
        ides {
            // Базовые версии проверяются по умолчанию (IDEA 2026.1).
            recommended()
        }
    }
}

// Архивы для IDEA и для Android Studio имеют разные имена, чтобы не затирать друг друга.
tasks.named("buildPlugin", Zip::class) {
    archiveFileName = "StringManagerPlugin-${project.version}-$platformName.zip"
}
