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
val commonProject = project(":${project.name.substringBeforeLast("-")}-common")
val accessWidenerFile = "${sc.current.version}.accesswidener"
val gametestModId = "${prop("mod.id")}-e2e"

// The heavy client gametests use client-gametest-API surface (e.g. clientLevel/waitForChunksRender)
// that only exists on the latest target, and rely on version-specific terrain snapshots. They are
// compiled and entrypoint-registered only on the latest version; SmokeGameTest stays portable.
val isLatestVersion = sc.current.version == "26.2"

// The headless Xvfb workaround is only for Linux CI without a display; macOS/Windows have a real
// display and must not engage it (`which Xvfb` would fail and there is no X server anyway).
val isLinux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

val heavyGametestEntrypoints =
  if (!isLatestVersion) ""
  else
    listOf(
        "TerrasectFabricClientGameTest",
        "VanillaWorldDigestTest",
        "TerrasectWorldDigestTest",
        "MobConstraintBlockByNameGameTest",
        "MobSpawnConstraintGameTest",
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
    kotlin.setSrcDirs(
      listOf(
        fabricDir.resolve("src/main/kotlin"),
        fabricDir.resolve("src/client/kotlin"),
      )
    )
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

  val xvfbDisplay = ":99"
  var xvfbProcess: Process? = null

  val startXvfb by registering {
    onlyIf {
      isLinux &&
        System.getenv("DISPLAY").isNullOrBlank() &&
        providers
          .exec {
            commandLine("which", "Xvfb")
            isIgnoreExitValue = true
          }
          .standardOutput
          .asText
          .get()
          .isNotBlank()
    }
    doLast {
      xvfbProcess =
        ProcessBuilder("Xvfb", xvfbDisplay, "-screen", "0", "1920x1080x24")
          .redirectErrorStream(true)
          .start()
      Thread.sleep(500)
    }
  }

  val stopXvfb by registering {
    doLast {
      xvfbProcess?.destroy()
      xvfbProcess = null
    }
  }

  named<JavaExec>("runClientGameTest") {
    systemProperty("terrasect.e2eDir", e2eDir.absolutePath)
    if (project.hasProperty("updateSnapshots")) {
      systemProperty("updateSnapshots", "true")
    }
    project.gradle.startParameter.projectProperties["test"]?.let { systemProperty("test", it) }
    if (isLinux && System.getenv("DISPLAY").isNullOrBlank()) {
      dependsOn(startXvfb)
      finalizedBy(stopXvfb)
      environment("DISPLAY", xvfbDisplay)
    }
  }
}
