import dev.kikugie.stonecutter.build.StonecutterBuildExtension

plugins {
  alias(libs.plugins.loom.back.compat)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dependencies.embedded)
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

val commonDir = rootProject.file("common")
val fabricDir = rootProject.file("fabric")
val e2eCompatDir = rootProject.file("e2e-compat")
val commonProject = project(":common:${project.name}")
val accessWidenerFile = "${sc.current.version}.accesswidener"
val gametestModId = "${prop("mod.id")}-e2e-compat"

val isLatest = sc.current.version == "26.2"

version = prop("mod.version")

base.archivesName = "${prop("mod.id")}-e2e-compat"

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
    kotlin.srcDir(fabricDir.resolve("src/main/kotlin"))
    kotlin.srcDir(fabricDir.resolve("src/client/kotlin"))
    resources.srcDir(fabricDir.resolve("src/main/resources"))
  }
  named("gametest") {
    val kotlinDirs = mutableListOf(e2eCompatDir.resolve("src/gametest/kotlin"))
    val resourceDir =
      e2eCompatDir.resolve(
        if (isLatest) "src/gametestLatest/resources" else "src/gametest/resources"
      )
    if (isLatest) {
      kotlinDirs += e2eCompatDir.resolve("src/gametestLatest/kotlin")
    }
    kotlin.setSrcDirs(kotlinDirs)
    resources.setSrcDirs(listOf(resourceDir))
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

  modRuntimeOnly("maven.modrinth:glitchcore:${prop("deps.compat_glitchcore")}")
  modRuntimeOnly("maven.modrinth:biomes-o-plenty:${prop("deps.compat_biomesoplenty")}")
  modRuntimeOnly("maven.modrinth:terrablender:${prop("deps.compat_terrablender")}")
  modRuntimeOnly("maven.modrinth:distanthorizons:${prop("deps.compat_distanthorizons")}")
  if (!isLatest) {
    runtimeOnly("com.electronwill.night-config:core:${prop("deps.night_config")}")
    runtimeOnly("com.electronwill.night-config:toml:${prop("deps.night_config")}")
    runtimeOnly("net.jodah:typetools:0.6.3")
  }
  if (isLatest) {
    modRuntimeOnly("maven.modrinth:c2me-fabric:${prop("deps.compat_c2me")}")
  }

  implementation(commonProject)
  embedded("com.electronwill.night-config:toml:${prop("deps.night_config")}")

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
    systemProperty("terrasect.e2eDir", e2eCompatDir.absolutePath)
    if (project.hasProperty("updateSnapshots")) {
      systemProperty("updateSnapshots", "true")
    }
    project.gradle.startParameter.projectProperties["test"]?.let { systemProperty("test", it) }
  }
}

apply(from = rootProject.file("gradle/xvfb.gradle.kts"))
