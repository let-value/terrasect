import net.fabricmc.loom.task.RemapJarTask

plugins {
  id("terrasect-mod")
  alias(libs.plugins.loom.back.compat)
  alias(libs.plugins.dependencies.embedded)
}

val fabricDir = rootProject.file("fabric")
val e2eDir = rootProject.file("e2e")
val gametestModId = "${mod.id}-e2e"
val clientGameTestsAvailable = mcVersion !in listOf("1.20.1", "1.21.1")
val gametestLibraries = configurations.create("gametestLibraries")

val gametestObjectDeclaration = Regex("""object\s+(\w+)\s*:\s*FabricClientGameTest\b""")

val clientTestDirectories = buildList {
  add(e2eDir.resolve("src/gametest-client/kotlin"))
  if (isLatest) add(e2eDir.resolve("src/gametestLatest/kotlin"))
}

fun clientGametestEntries(): List<String> = clientTestDirectories.flatMap { directory ->
  fileTree(directory) { include("**/*.kt") }
    .sortedBy { it.path }
    .flatMap { file ->
      gametestObjectDeclaration.findAll(file.readText()).map { it.groupValues[1] }.toList()
    }
}

fun kotlinEntry(value: String) =
  "      { \"value\": \"terrasect.gametest.$value\", \"adapter\": \"kotlin\" }"

fun entrypointBlock(name: String, entries: List<String>) =
  "    \"$name\": [\n${entries.joinToString(",\n")}\n    ]"

val gametestEntrypoints =
  if (clientGameTestsAvailable) {
    entrypointBlock("fabric-client-gametest", clientGametestEntries().map { kotlinEntry(it) })
  } else {
    listOf(
        entrypointBlock("main", listOf(kotlinEntry("ServerSmokeInit"))),
        entrypointBlock("fabric-gametest", listOf(kotlinEntry("ServerSmokeGameTest"))),
      )
      .joinToString(",\n")
  }

fabricApi {
  configureTests {
    createSourceSet = true
    modId = gametestModId
    eula = true
    enableGameTests = !clientGameTestsAvailable
    enableClientGameTests = clientGameTestsAvailable
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
    if (clientGameTestsAvailable) {
      kotlin.setSrcDirs(listOf(e2eDir.resolve("src/gametest/kotlin")) + clientTestDirectories)
      java.setSrcDirs(listOf(e2eDir.resolve("src/gametest-client/java")))
      resources.setSrcDirs(
        listOf(
          e2eDir.resolve("src/gametest/resources"),
          e2eDir.resolve("src/gametest-client/resources"),
        )
      )
    } else {
      kotlin.setSrcDirs(
        listOf(
          e2eDir.resolve("src/server/kotlin"),
          e2eDir.resolve("src/gametest-server-old/kotlin"),
        )
      )
      java.setSrcDirs(listOf(e2eDir.resolve("src/gametest-server-old/java")))
      resources.setSrcDirs(
        listOf(
          e2eDir.resolve("src/gametest/resources"),
          e2eDir.resolve("src/gametest-server-old/resources"),
        )
      )
    }
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
  val snapshotTests = "de.skuzzle.test:snapshot-tests-junit5:${prop("deps.snapshot_tests")}"
  add("gametestImplementation", snapshotTests)
  add(gametestLibraries.name, snapshotTests)
}

val resourceProps =
  fabricResourceProps(
    "gametest_mod_id" to gametestModId,
    "gametest_entrypoints" to gametestEntrypoints,
    "gametest_mixins" to
      if (clientGameTestsAvailable) "\"terrasect-e2e-client.mixins.json\""
      else "\"terrasect-e2e.mixins.json\"",
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

  val gametestThinJar =
    register<Jar>("gametestThinJar") {
      archiveClassifier.set("gametest-dev")
      from(sourceSets["gametest"].output)
    }

  val gametestFatJar =
    register<Jar>("gametestFatJar") {
      archiveBaseName.set("terrasect-tests")
      archiveVersion.set("${version}+$mcVersion")
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      from(gametestThinJar.flatMap { it.archiveFile }.map { zipTree(it) })
      from({ gametestLibraries.map(::zipTree) })
      exclude(
        "META-INF/MANIFEST.MF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "module-info.class",
      )
    }

  if (mcVersion.startsWith("26.")) {
    gametestFatJar.configure { archiveClassifier.set("gametest") }
    register("gametestModJar") { dependsOn(gametestFatJar) }
  } else
    register<RemapJarTask>("gametestModJar") {
      inputFile.set(gametestFatJar.flatMap { it.archiveFile })
      archiveBaseName.set("terrasect-tests")
      archiveVersion.set("${version}+$mcVersion")
      archiveClassifier.set("gametest")
      sourceNamespace.set("named")
      targetNamespace.set("intermediary")
      classpath.from(sourceSets["gametest"].compileClasspath)
      onlyIf { clientGameTestsAvailable }
    }

  matching { it.name == "runClientGameTest" || it.name == "runGameTest" }
    .configureEach {
      this as JavaExec
      systemProperty("terrasect.e2eDir", e2eDir.absolutePath)
      if (name == "runClientGameTest") {
        val optionsFile = layout.buildDirectory.file("run/clientGameTest/options.txt")
        doFirst {
          val requested =
            project
              .findProperty("test")
              ?.toString()
              .orEmpty()
              .split(',')
              .map(String::trim)
              .filter(String::isNotBlank)
          val available = clientGametestEntries().map(String::lowercase).toSet()
          val unknown = requested.filter { it.lowercase() !in available }
          require(unknown.isEmpty()) { "Unknown client GameTests: $unknown" }
          val file = optionsFile.get().asFile
          file.parentFile.mkdirs()
          if (!file.exists()) {
            file.writeText("menuBackgroundBlurriness:0\n")
          }
        }
      }
      if (name == "runGameTest") systemProperty("terrasect.serverSmoke", "true")
      if (project.hasProperty("updateSnapshots")) {
        systemProperty("updateSnapshots", "true")
      }
      project.gradle.startParameter.projectProperties["test"]?.let { systemProperty("test", it) }
    }
}

apply(from = rootProject.file("gradle/xvfb.gradle.kts"))
