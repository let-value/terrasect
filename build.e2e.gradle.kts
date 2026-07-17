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

// Sub-1.20.2 builds common with Loom (build.common-legacy), whose default artifact Loom remaps to
// intermediary; consuming it normally puts intermediary-targeted mixins on a Mojang-mapped dev
// runtime and every mixin silently fails to apply. Consume its `namedElements` (un-remapped)
// variant
// instead. Newer commons are MDG-built (never remapped) and consumed directly.
val legacyLoomCommon = sc.current.version == "1.20.1"
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

// `mod.latest` (stonecutter.properties.toml) is the single source of truth for the newest version.
val isLatestVersion = sc.current.version == prop("mod.latest")

// Versions predating the 1.21.2 gametest-framework overhaul use the old @GameTest/FabricGameTest
// server registration and have no client-gametest API (1.20.1, 1.21.1); newer versions use the
// client-gametest API. The cross-version server smoke test therefore registers via the old
// paradigm on old versions and via the client suite's coverage on new ones.
val oldGametestParadigm = sc.current.version in listOf("1.20.1", "1.21.1")

// The heavy client gametests use client-gametest-API surface (e.g. clientLevel/waitForChunksRender)
// that only exists on the latest target, and rely on version-specific terrain snapshots. They are
// compiled and entrypoint-registered only on the latest version; SmokeGameTest and
// LootConstraintBlockAllGameTest stay portable across the client-capable versions.
val heavyClientTests =
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
    "ForcedStructureGameTest",
    "OriginAnchorGameTest",
  )

fun kotlinEntry(value: String) =
  "      { \"value\": \"terrasect.$value\", \"adapter\": \"kotlin\" }"

fun entrypointBlock(name: String, entries: List<String>) =
  "    \"$name\": [\n${entries.joinToString(",\n")}\n    ]"

// The server smoke test (ServerSmokeInit forces the preset at mod init; ServerSmokeGameTest asserts
// the pipeline) runs on every version. On old-paradigm versions it registers via `fabric-gametest`;
// on client-capable versions the existing client suite provides the same DimensionContext guard.
val gametestEntrypoints =
  buildList {
      add(entrypointBlock("main", listOf(kotlinEntry("ServerSmokeInit"))))
      if (oldGametestParadigm) {
        add(entrypointBlock("fabric-gametest", listOf(kotlinEntry("ServerSmokeGameTest"))))
      } else {
        val client = buildList {
          add(kotlinEntry("SmokeGameTest"))
          add(kotlinEntry("LootConstraintBlockAllGameTest"))
          if (isLatestVersion) addAll(heavyClientTests.map { kotlinEntry(it) })
        }
        add(entrypointBlock("fabric-client-gametest", client))
      }
    }
    .joinToString(",\n")

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
    enableGameTests = oldGametestParadigm
    enableClientGameTests = !oldGametestParadigm
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
        // Version/loader-agnostic sources (GameTestFilter, ServerSmokeGuard/Init) compile
        // everywhere.
        add(e2eDir.resolve("src/gametest/kotlin"))
        if (oldGametestParadigm) {
          add(e2eDir.resolve("src/gametest-server-old/kotlin"))
        } else {
          // Client-gametest suite exists only where the client-gametest API does.
          add(e2eDir.resolve("src/gametest-client/kotlin"))
          if (isLatestVersion) add(e2eDir.resolve("src/gametest-latest/kotlin"))
        }
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
  embedded("com.electronwill.night-config:toml:${prop("deps.night_config")}")

  val commonDep =
    if (legacyLoomCommon) project(path = commonProject.path, configuration = "namedElements")
    else commonProject
  implementation(commonDep)

  add("gametestImplementation", sourceSets["main"].output)
  add("gametestImplementation", commonDep)

  // `namedElements` carries only the un-remapped jar, not common's transitive runtime libraries, so
  // pull common's third-party runtime deps explicitly (same version props). Newer commons bring
  // these transitively via the plain project dependency.
  if (legacyLoomCommon) {
    runtimeOnly("net.openhft:zero-allocation-hashing:${prop("deps.zero_allocation_hashing")}")
    runtimeOnly("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")
    runtimeOnly("com.github.komputing:kbase58:${prop("deps.kbase58")}")
  }
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
    "gametest_entrypoints" to gametestEntrypoints,
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

  // runClientGameTest exists only on client-capable versions; runGametest only where server
  // gametests are enabled (old paradigm). Configure whichever is present.
  matching { it.name == "runClientGameTest" || it.name == "runGameTest" }
    .configureEach {
      this as JavaExec
      systemProperty("terrasect.e2eDir", e2eDir.absolutePath)
      if (name == "runGameTest") {
        // Force the smoke preset for this launch only, so the dedicated server's overworld builds
        // the full pipeline without affecting any other run.
        systemProperty("terrasect.serverSmoke", "true")
      }
      if (project.hasProperty("updateSnapshots")) {
        systemProperty("updateSnapshots", "true")
      }
      project.gradle.startParameter.projectProperties["test"]?.let { systemProperty("test", it) }
    }
}

apply(from = rootProject.file("gradle/xvfb.gradle.kts"))
