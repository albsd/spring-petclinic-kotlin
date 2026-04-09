import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.gradle.api.tasks.Input

plugins {
    kotlin("jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.albsd"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1.7")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.albsd.j2k"
        name = "J2K"
        version = "0.0.1"
        description = """
            Java-To-Kotlin converter for CI pipelines.
            Reads Java source from j2k.sourceDir
            and writes Kotlin output to j2k.outputDir.
        """.trimIndent()
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
}

tasks.named<RunIdeTask>("runIde") {
    val sourceDirProp = providers.gradleProperty("sourceDir")
        .orElse(rootProject.projectDir.parentFile.resolve("src/main/java").absolutePath)
    val outputDirProp = providers.gradleProperty("outputDir")
        .orElse(rootProject.projectDir.parentFile.resolve("converted-kotlin").absolutePath)
    val projectDirProp = providers.gradleProperty("projectDir")
        .orElse(rootProject.projectDir.parentFile.absolutePath)

    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dj2k.sourceDir=${sourceDirProp.get()}",
            "-Dj2k.outputDir=${outputDirProp.get()}",
            "-Djava.awt.headless=true",
            "-Didea.is.internal=true",
            "-Didea.auto.reload.plugins=false",
            "-Didea.suppressed.plugins.id=com.intellij.gradle"
        )
    })

    args(projectDirProp.get())
}
