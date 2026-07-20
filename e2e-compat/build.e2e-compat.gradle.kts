plugins {
  id("terrasect-mod")
  alias(libs.plugins.loom.back.compat)
  alias(libs.plugins.dependencies.embedded)
}

val fabricDir = rootProject.file("fabric")
val e2eCompatDir = rootProject.file("e2e-compat")
val gametestModId = "${mod.id}-e2e-compat"

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
  minecraft("com.mojang:minecraft:$mcVersion")
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

val resourceProps = fabricResourceProps("gametest_mod_id" to gametestModId)

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
