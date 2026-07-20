plugins {
  id("terrasect-mod")
  alias(libs.plugins.loom.back.compat)
  alias(libs.plugins.dependencies.embedded)
}

val fabricDir = rootProject.file("fabric")
val e2eDir = rootProject.file("e2e")
val gametestModId = "${mod.id}-e2e"
val oldGametestParadigm = mcVersion in listOf("1.20.1", "1.21.1")

// Files under gametest-client wrap latest-only tests in `//? if latest { ... //?}`; Stonecutter
// strips that content from the compiled source for non-latest versions, so the entrypoint list is
// derived from the same marker instead of a hand-maintained class name array.
val latestOnlyMarker = Regex("""//\?\s*if\s+latest\s*\{""")
val gametestObjectDeclaration = Regex("""object\s+(\w+)\s*:\s*FabricClientGameTest\b""")

// The "gametest" source set doesn't follow Gradle's default sourceSet convention, so Stonecutter's
// automatic per-branch preprocessing (which only hooks conventional source set locations) doesn't
// apply here; process it explicitly instead.
fun processGametestClientKotlin(): File {
  val srcDir = e2eDir.resolve("src/gametest-client/kotlin")
  val outDir = layout.buildDirectory.dir("processed/gametest-client/kotlin").get().asFile
  fileTree(srcDir) { include("**/*.kt") }
    .forEach { file -> sc.process(file, "$outDir/${file.relativeTo(srcDir).path}") }
  return outDir
}

fun clientGametestEntries(): List<String> =
  e2eDir
    .resolve("src/gametest-client/kotlin/terrasect")
    .listFiles { file -> file.extension == "kt" }
    .orEmpty()
    .sortedBy { it.name }
    .flatMap { file ->
      val text = file.readText()
      if (!isLatest && latestOnlyMarker.containsMatchIn(text)) emptyList()
      else gametestObjectDeclaration.findAll(text).map { it.groupValues[1] }.toList()
    }

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
        add(
          entrypointBlock("fabric-client-gametest", clientGametestEntries().map { kotlinEntry(it) })
        )
      }
    }
    .joinToString(",\n")

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
          add(processGametestClientKotlin())
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
  add(
    "gametestImplementation",
    "de.skuzzle.test:snapshot-tests-junit5:${prop("deps.snapshot_tests")}",
  )
}

val resourceProps =
  fabricResourceProps(
    "gametest_mod_id" to gametestModId,
    "gametest_entrypoints" to gametestEntrypoints,
    "gametest_mixins" to
      if (oldGametestParadigm) "\"terrasect-e2e.mixins.json\""
      else "\"terrasect-e2e-client.mixins.json\"",
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
