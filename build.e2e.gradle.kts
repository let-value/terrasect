import dev.kikugie.stonecutter.build.StonecutterBuildExtension

plugins {
  id("dev.kikugie.loom-back-compat")
  id("org.jetbrains.kotlin.jvm")
  id("io.github.gmazzo.dependencies.embedded")
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

val commonDir = rootProject.file("common")
val fabricDir = rootProject.file("fabric")
val e2eDir = rootProject.file("e2e")
val fabricClientKotlinSrc = fabricDir.resolve("src/client/kotlin")
val processedFabricClientKotlinDir = layout.buildDirectory.dir("processed/client/kotlin")
val commonProject = project(":${project.name.substringBeforeLast("-")}-common")
val accessWidenerFile = "${sc.current.version}.accesswidener"
val gametestModId = "${prop("mod.id")}-e2e"

// `fabric/src/client/kotlin` is Stonecutter-gated (`//? if`) canonical source — the raw directory
// only reflects whichever version is currently checked out (`stonecutter active` in the root
// stonecutter.gradle.kts), not necessarily whatever version this e2e instance targets. The real
// `:<version>-fabric` project resolves this the same way (see build.fabric.gradle.kts): run each
// file through `sc.process` per target version before using it as a source dir, instead of
// compiling the raw directory directly.
project
  .fileTree(fabricClientKotlinSrc) { include("**/*.kt") }
  .forEach { file ->
    sc.process(file, "build/processed/client/kotlin/${file.relativeTo(fabricClientKotlinSrc).path}")
  }

// The heavy client gametests use client-gametest-API surface (e.g. clientLevel/waitForChunksRender)
// that only exists on the latest target, and rely on version-specific terrain snapshots. They are
// compiled and entrypoint-registered only on the latest version; SmokeGameTest and
// LootConstraintBlockAllGameTest stay portable.
// `mod.latest` (stonecutter.properties.toml) is the single source of truth for the newest version.
val isLatestVersion = sc.current.version == prop("mod.latest")

val heavyGametestEntrypoints =
  if (!isLatestVersion) ""
  else
    listOf(
        "TerrasectFabricClientGameTest",
        "VanillaWorldDigestTest",
        "TerrasectWorldDigestTest",
        "MobConstraintBlockByNameGameTest",
        "StructureConstraintVanillaGameTest",
        "StructureConstraintHighDensityGameTest",
        "StructureConstraintLocateGameTest",
        "StructureConstraintBanByModGameTest",
        "StructureConstraintAllowByModGameTest",
        "StructureConstraintBanByTagGameTest",
        "StructureConstraintAllowByTagGameTest",
        "StructureConstraintBanByNameGameTest",
        "StructureConstraintAllowByNameGameTest",
        "StructureConstraintLocateRuinedPortalGameTest",
        "StructureConstraintStatisticsTest",
      )
      .joinToString("") { ",\n      { \"value\": \"terrasect.$it\", \"adapter\": \"kotlin\" }" }

version = prop("mod.version")

base.archivesName = "${prop("mod.id")}-e2e"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(prop("java").toInt())
  }
}

kotlin {
  jvmToolchain(prop("java").toInt())
}

fabricApi {
  configureTests {
    createSourceSet = true
    modId = gametestModId
    eula = true
    enableGameTests = false
    enableClientGameTests = true
    clearRunDirectory = true
  }
}

sourceSets {
  main {
    kotlin.setSrcDirs(listOf(fabricDir.resolve("src/main/kotlin"), processedFabricClientKotlinDir))
    resources.setSrcDirs(listOf(fabricDir.resolve("src/main/resources")))
  }
  named("gametest") {
    kotlin.setSrcDirs(
      buildList {
        add(e2eDir.resolve("src/gametest/kotlin"))
        if (isLatestVersion) add(e2eDir.resolve("src/gametest-latest/kotlin"))
      }
    )
    resources.setSrcDirs(listOf(e2eDir.resolve("src/gametest/resources")))
  }
}

loom {
  fabricModJsonPath = fabricDir.resolve("src/main/resources/fabric.mod.json")
  accessWidenerPath = commonDir.resolve("src/main/resources/accesswideners/$accessWidenerFile")
  mods {
    create(prop("mod.id")) {
      sourceSet(sourceSets["main"])
    }
  }
}

dependencies {
  minecraft("com.mojang:minecraft:${sc.current.version}")
  loomx.applyMojangMappings()

  modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
  modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
  modImplementation("net.fabricmc:fabric-language-kotlin:${prop("deps.fabric_kotlin")}")

  implementation(commonProject)

  add("gametestImplementation", sourceSets["main"].output)
  add("gametestImplementation", commonProject)
  add(
    "gametestImplementation",
    "de.skuzzle.test:snapshot-tests-junit5:${prop("deps.snapshot_tests")}",
  )
}

val resourceProps =
  mapOf(
    "version" to version.toString(),
    "mod_id" to prop("mod.id"),
    "gametest_mod_id" to gametestModId,
    "mod_name" to prop("mod.name"),
    "mod_description" to prop("mod.description"),
    "mod_authors" to prop("mod.authors"),
    "mod_license" to prop("mod.license"),
    "fabric_loader_version" to prop("deps.fabric_loader"),
    "minecraft_version" to sc.current.version,
    "java_version" to prop("java"),
    "fabric_api_version" to prop("deps.fabric_api"),
    "fabric_kotlin_version" to prop("deps.fabric_kotlin"),
    "access_widener_file" to accessWidenerFile,
    "heavy_gametest_entrypoints" to heavyGametestEntrypoints,
  )

tasks {
  test {
    enabled = false
  }

  named<ProcessResources>("processResources") {
    inputs.properties(resourceProps)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
      expand(resourceProps)
    }
    exclude("accesswideners/*.accesswidener")
    from(commonDir.resolve("src/main/resources/accesswideners/$accessWidenerFile")) {
      into("accesswideners")
    }
    exclude("META-INF/**")
  }

  named<ProcessResources>("processGametestResources") {
    inputs.properties(resourceProps)
    filesMatching("fabric.mod.json") {
      expand(resourceProps)
    }
  }

  named<Jar>("jar") {
    from(commonProject.sourceSets["main"].output) {
      exclude("META-INF/accesstransformer.cfg", "accesswideners/*.accesswidener")
    }
  }

  named<JavaExec>("runClientGameTest") {
    systemProperty("terrasect.e2eDir", e2eDir.absolutePath)
    if (project.hasProperty("updateSnapshots")) {
      systemProperty("updateSnapshots", "true")
    }
    project.gradle.startParameter.projectProperties["test"]?.let { systemProperty("test", it) }
  }
}

apply(from = rootProject.file("gradle/xvfb.gradle.kts"))
