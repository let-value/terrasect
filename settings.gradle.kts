pluginManagement {
    repositories {
        mavenLocal()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "NeoForge"
            url = uri("https://maven.neoforged.net/releases")
        }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("net.neoforged.moddev") version settings.extra["moddev_version"] as String
        id("fabric-loom") version settings.extra["loom_version"] as String
        id("org.jetbrains.kotlin.jvm") version settings.extra["kotlin_version"] as String
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "terrasect"

include("common")
include("fabric")
include("neoforge")
