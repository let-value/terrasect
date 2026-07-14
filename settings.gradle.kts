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

plugins {
  id("dev.kikugie.stonecutter")
  id("dev.kikugie.loom-back-compat")
  id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "terrasect"

stonecutter {
  val latestProject = "26.2.x"
  val latestVersion = "26.2"

  create(rootProject) {
    fun match(project: String, vararg loaders: String, version: String = project) {
      version("$project-common", version).buildscript("build.common.gradle.kts")
      for (loader in loaders) {
        version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
      }
    }

    match("1.21.1", "fabric", "neoforge")
    match("1.21.11", "fabric", "neoforge")
    match("26.1.x", "fabric", "neoforge", version = "26.1")
    match(latestProject, "fabric", "neoforge", version = latestVersion)
    vcsVersion = "1.21.11-fabric"
  }

  create("e2e") {
    version("1.21.11", "1.21.11").buildscript("../build.e2e.gradle.kts")
    version("26.1.x", "26.1.2").buildscript("../build.e2e.gradle.kts")
    version(latestProject, latestVersion).buildscript("../build.e2e.gradle.kts")
    vcsVersion = latestProject
  }

  // Third-party mod compatibility coverage, kept out of `e2e` so the core test suite never
  // requires a third-party mod jar to be resolvable/present. See build.e2e-compat.gradle.kts for
  // the mod list.
  //  - 1.21 and 1.21.1: `fabric-client-gametest-api-v1` was never backported into fabric-api for
  //    those versions (confirmed against the newest available release for each, incl. 1.21.1's
  //    2026-07-01 release) — no client gametest, compat or otherwise, can run there at all. This
  //    is a permanent upstream limitation, not something fixable from this repo.
  //  - 1.21.11: on this version, Loom must remap third-party mod jars (intermediary -> named),
  //    and that remap step silently drops each mod's bundled Jar-in-Jar libraries
  //    (META-INF/jars/*) instead of re-extracting them — unlike latest, where mods load
  //    un-remapped straight from the Maven cache and Fabric Loader's own Jar-in-Jar extraction
  //    handles it normally. GlitchCore/BiomesOPlenty/TerraBlender all bundle the same three
  //    plain (non-Fabric-mod) libraries this way; worked around in build.e2e-compat.gradle.kts by
  //    declaring those libraries as plain runtimeOnly dependencies, bypassing the mod-remap
  //    pipeline entirely. C2ME's Jar-in-Jar entries are its own Fabric sub-mods (not plain
  //    libraries) and can't be unbundled the same way, so it stays latest-only.
  create("e2e-compat") {
    version(latestProject, latestVersion).buildscript("../build.e2e-compat.gradle.kts")
    version("1.21.11", "1.21.11").buildscript("../build.e2e-compat.gradle.kts")
    vcsVersion = latestProject
  }
}
