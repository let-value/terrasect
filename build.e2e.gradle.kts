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
val commonProject = project(":common:${project.name}")
val accessWidenerFile = "${sc.current.version}.accesswidener"

val legacyLoomCommon = sc.current.version == "1.20.1"
val gametestModId = "${prop("mod.id")}-e2e"

val isLatestVersion = sc.current.version == prop("mod.latest")

val oldGametestParadigm = sc.current.version in listOf("1.20.1", "1.21.1")

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
    "OceanArchetypeGameTest",
    "LandlockedArchetypeGameTest",
    "FlatlandsArchetypeGameTest",
    "HighlandsArchetypeGameTest",
    "NetherNoiseConstraintGameTest",
    "EndNoiseConstraintGameTest",
    "NetherMobConstraintGameTest",
    "NetherStructureConstraintGameTest",
    "DimensionContextIsolationGameTest",
  )

fun kotlinEntry(value: String) =
  "      { \"value\": \"terrasect.$value\", \"adapter\": \"kotlin\" }"

fun entrypointBlock(name: String, entries: List<String>) =
  "    \"$name\": [\n${entries.joinToString(",\n")}\n    ]"

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
    kotlin.srcDir(fabricDir.resolve("src/main/kotlin"))
    kotlin.srcDir(fabricDir.resolve("src/client/kotlin"))
    resources.srcDir(fabricDir.resolve("src/main/resources"))
  }
  named("gametest") {
    kotlin.setSrcDirs(
      buildList {
        add(e2eDir.resolve("src/gametest/kotlin"))
        if (oldGametestParadigm) {
          add(e2eDir.resolve("src/gametest-server-old/kotlin"))
        } else {
          add(e2eDir.resolve("src/gametest-client/kotlin"))
          if (isLatestVersion) add(e2eDir.resolve("src/gametest-latest/kotlin"))
        }
      }
    )
    java.setSrcDirs(
      listOf(
        e2eDir.resolve(
          if (oldGametestParadigm) "src/gametest-server-old/java" else "src/gametest-client/java"
        )
      )
    )
    resources.setSrcDirs(
      listOf(
        e2eDir.resolve("src/gametest/resources"),
        e2eDir.resolve(
          if (oldGametestParadigm) "src/gametest-server-old/resources"
          else "src/gametest-client/resources"
        ),
      )
    )
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
    "gametest_mixins" to
      (if (oldGametestParadigm) "\"terrasect-e2e.mixins.json\""
      else "\"terrasect-e2e-client.mixins.json\""),
    "mixin_compat_level" to "JAVA_${minOf(prop("java").toInt(), 21)}",
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
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
      expand(resourceProps)
    }
  }

  named<Jar>("jar") {
    from(commonProject.sourceSets["main"].output) {
      exclude("META-INF/accesstransformer.cfg", "accesswideners/*.accesswidener")
    }
  }

  matching { it.name == "runClientGameTest" || it.name == "runGameTest" }
    .configureEach {
      this as JavaExec
      systemProperty("terrasect.e2eDir", e2eDir.absolutePath)
      if (name == "runClientGameTest") {
        val optionsFile = layout.buildDirectory.file("run/clientGameTest/options.txt")
        doFirst {
          val file = optionsFile.get().asFile
          file.parentFile.mkdirs()
          if (!file.exists()) {
            file.writeText("menuBackgroundBlurriness:0\n")
          }
        }
      }
      if (name == "runGameTest") {
        systemProperty("terrasect.serverSmoke", "true")
      }
      if (project.hasProperty("updateSnapshots")) {
        systemProperty("updateSnapshots", "true")
      }
      project.gradle.startParameter.projectProperties["test"]?.let { systemProperty("test", it) }
    }
}

apply(from = rootProject.file("gradle/xvfb.gradle.kts"))
