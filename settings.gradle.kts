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
    id("com.diffplug.spotless") version "8.7.0"
  }
}

buildscript {
  repositories {
    gradlePluginPortal()
  }
  dependencies {
    classpath("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.2")
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
    branch("common") {
      version("1.20.1", "1.20.1").buildscript("build.common-legacy.gradle.kts")
      version("1.21.1", "1.21.1").buildscript("build.common.gradle.kts")
      version("1.21.11", "1.21.11").buildscript("build.common.gradle.kts")
      version("26.1.x", "26.1").buildscript("build.common.gradle.kts")
      version("26.2.x", "26.2").buildscript("build.common.gradle.kts")
    }

    branch("fabric") {
      version("1.20.1", "1.20.1").buildscript("build.fabric.gradle.kts")
      version("1.21.1", "1.21.1").buildscript("build.fabric.gradle.kts")
      version("1.21.11", "1.21.11").buildscript("build.fabric.gradle.kts")
      version("26.1.x", "26.1").buildscript("build.fabric.gradle.kts")
      version("26.2.x", "26.2").buildscript("build.fabric.gradle.kts")
    }

    branch("neoforge") {
      version("1.21.1", "1.21.1").buildscript("build.neoforge.gradle.kts")
      version("1.21.11", "1.21.11").buildscript("build.neoforge.gradle.kts")
      version("26.1.x", "26.1").buildscript("build.neoforge.gradle.kts")
      version("26.2.x", "26.2").buildscript("build.neoforge.gradle.kts")
    }

    branch("e2e") {
      version("1.20.1", "1.20.1").buildscript("build.e2e.gradle.kts")
      version("1.21.1", "1.21.1").buildscript("build.e2e.gradle.kts")
      version("1.21.11", "1.21.11").buildscript("build.e2e.gradle.kts")
      version("26.1.x", "26.1").buildscript("build.e2e.gradle.kts")
      version("26.2.x", "26.2").buildscript("build.e2e.gradle.kts")
    }

    if (System.getenv("TERRASECT_SKIP_COMPAT").isNullOrBlank()) {
      branch("e2e-compat") {
        version("1.21.11", "1.21.11").buildscript("build.e2e-compat.gradle.kts")
        version("26.1.x", "26.1").buildscript("build.e2e-compat.gradle.kts")
        version("26.2.x", "26.2").buildscript("build.e2e-compat.gradle.kts")
      }
    }

    vcsVersion = "26.2.x"
  }
}
