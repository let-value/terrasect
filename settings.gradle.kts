pluginManagement {
    repositories {
        mavenLocal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForge" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("dev.kikugie.stonecutter") version "0.9.6"
        id("dev.kikugie.loom-back-compat") version "0.3"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
        id("io.github.gmazzo.dependencies.embedded") version "1.1.0"
        id("net.neoforged.moddev") version "2.0.139"
        id("dev.kikugie.fletching-table") version "0.1.0-alpha.22"
    }
}

plugins {
    id("dev.kikugie.stonecutter")
    id("dev.kikugie.loom-back-compat")
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "terrasect"

stonecutter {
    create(rootProject) {
        fun match(project: String, vararg loaders: String, version: String = project) {
            version("$project-common", version).buildscript("build.common.gradle.kts")
            for (loader in loaders) {
                version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
            }
        }

        match("1.21.11", "fabric", "neoforge")
        match("26.2.x", "fabric", "neoforge", version = "26.2")
        vcsVersion = "1.21.11-fabric"
    }
}
