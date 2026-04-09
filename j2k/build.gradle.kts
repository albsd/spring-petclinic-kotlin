import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    kotlin("jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.3.0"
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
        instrumentationTools()
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
        """
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
}

tasks.named<RunIdeTask>("runIde") {
    val sourceDirProp = providers.gradleProperty("sourceDir")
        .orElse(rootProject.projectDir.parent + "/src/main/java")
    val outputDirProp = providers.gradleProperty("outputDir")
        .orElse(rootProject.projectDir.parent + "/converted-kotlin")
    val projectDirProp = providers.gradleProperty("projectDir")
        .orElse(rootProject.projectDir.parent)

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
