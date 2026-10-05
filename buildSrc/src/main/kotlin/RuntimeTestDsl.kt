import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType

private const val MINECRAFT_TEST_GROUP = "minecraft test"
private val FABRIC_CLIENT_GAMETEST_LANES = setOf("1.21.11", "26.1.x", "26.2.x")
private val NEOFORGE_CONNECTOR_LANES = setOf("26.1.x")

private data class MinecraftTestLane(val loader: String, val segment: String, val minecraft: String)

private data class TestDependencies(val configuration: Configuration, val notations: List<String>)

private val clientTestGaps =
  listOf(
    "Fabric 1.20.1: pinned Fabric API 0.92.6+1.20.1 does not publish fabric-client-gametest-api-v1.",
    "Fabric 1.21.1: pinned Fabric API 0.116.12+1.21.1 does not publish fabric-client-gametest-api-v1.",
    "NeoForge 1.21.1: Fabric API lacks fabric-client-gametest-api-v1 for its Fabric test mod.",
    "NeoForge 1.21.11: Sinytra Connector has no 1.21.11 artifact.",
    "NeoForge 26.2.x: Sinytra Connector and Forgified Fabric API have no 26.2 artifacts.",
  )

private fun Project.runtimeTestLanes(): List<MinecraftTestLane> =
  extensions.getByType<StonecutterControllerExtension>().tree.entries.flatMap { (loader, branch) ->
    if (loader != "fabric" && loader != "neoforge") return@flatMap emptyList()
    branch.versions.map { version -> MinecraftTestLane(loader, version.project, version.version) }
  }

private fun MinecraftTestLane.supportsClientGameTests(): Boolean =
  when (loader) {
    "fabric" -> segment in FABRIC_CLIENT_GAMETEST_LANES
    "neoforge" -> segment in NEOFORGE_CONNECTOR_LANES
    else -> false
  }

fun MinecraftTestDsl(root: Project) {
  val launcher = root.layout.buildDirectory.file("minecraft-test/headlessmc.jar")
  val bootstrap =
    root.tasks.register("minecraftTestBootstrap", MinecraftTestBootstrapTask::class.java) {
      group = MINECRAFT_TEST_GROUP
      url.set(MinecraftTestPins.hmcUrl)
      checksum.set(MinecraftTestPins.hmcSha256)
      this.launcher.set(launcher)
      feriumUrl.set(MinecraftTestPins.feriumUrl)
      feriumChecksum.set(MinecraftTestPins.feriumSha256)
      ferium.set(root.layout.buildDirectory.file("minecraft-test/ferium"))
    }

  val cleanTests = mutableListOf<TaskProvider<out Task>>()
  val compatTests = mutableListOf<TaskProvider<out Task>>()

  root.runtimeTestLanes().forEach { lane ->
    val project = root.project(":${lane.loader}:${lane.segment}")
    val productionTask = project.provider {
      project.tasks.findByName("remapJar") ?: project.tasks.named("jar").get()
    }
    val productionArtifact =
      root.files(productionTask.map { it.outputs.files }).builtBy(productionTask)
    val client = lane.supportsClientGameTests()
    val testProject = if (client) root.project(":e2e:${lane.segment}") else project
    val testArtifact =
      root
        .files(
          testProject.layout.buildDirectory.file(
            "libs/${if (client) "terrasect-tests" else "terrasect-server-tests-${lane.loader}"}-${project.property("mod.version")}+${lane.minecraft}-gametest.jar"
          )
        )
        .builtBy("${testProject.path}:${if (client) "gametestModJar" else "serverSmokeModJar"}")

    val base = runtimeDependencies(project, lane)
    val bridge = if (client && lane.loader == "neoforge") connectorDependencies(project) else null
    val cleanDependencies = root.files(base.configuration)
    val cleanNotations = base.notations.toMutableList()
    if (client && lane.loader == "fabric") {
      cleanDependencies.from(clientGametestApi(project))
    } else if (bridge != null) {
      cleanDependencies.from(bridge.configuration)
      cleanNotations += bridge.notations
    }

    cleanTests +=
      registerPipeline(
        root = root,
        project = project,
        lane = lane,
        scenario = "Build",
        dependencies = cleanDependencies,
        requestedDependencies = cleanNotations,
        bootstrap = bootstrap,
        artifacts = root.files(productionArtifact, testArtifact),
        clientGametestMod =
          if (client) "terrasect-e2e"
          else if (lane.loader == "fabric") "terrasect-server-tests" else "terrasect_server_tests",
        clientTests = client,
        e2eDirectory = root.file("e2e"),
        modpackDefinition = null,
        resolveWithFerium = false,
        completionMarkers = if (client) cleanMarkers else serverMarkers,
        testFilter =
          if (client) "SmokeGameTest,LootConstraintBlockAllGameTest" else "ServerSmokeGameTest",
      )

    val compatProject =
      if (client) root.findProject(":e2e-compat:${lane.segment}") ?: return@forEach else project
    val compatTestArtifact =
      root
        .files(
          compatProject.layout.buildDirectory.file(
            "libs/${if (client) "terrasect-compat-tests" else "terrasect-server-tests-${lane.loader}"}-${project.property("mod.version")}+${lane.minecraft}-gametest.jar"
          )
        )
        .builtBy("${compatProject.path}:${if (client) "gametestModJar" else "serverSmokeModJar"}")
    val compat = compatDependencies(project, lane)
    val compatFiles = root.files(base.configuration, compat.configuration)
    val compatNotations = (base.notations + compat.notations).toMutableList()
    if (client && lane.loader == "fabric") {
      compatFiles.from(clientGametestApi(project))
    } else if (bridge != null) {
      compatFiles.from(bridge.configuration)
      compatNotations += bridge.notations
    }
    compatTests +=
      registerPipeline(
        root = root,
        project = project,
        lane = lane,
        scenario = "Compat",
        dependencies = compatFiles,
        requestedDependencies = compatNotations,
        bootstrap = bootstrap,
        artifacts = root.files(productionArtifact, compatTestArtifact),
        clientGametestMod =
          if (client) "terrasect-e2e-compat"
          else if (lane.loader == "fabric") "terrasect-server-tests" else "terrasect_server_tests",
        clientTests = client,
        e2eDirectory = root.file("e2e-compat"),
        modpackDefinition =
          root.file("runtime-tests/modpacks/compat/${lane.loader}-${lane.segment}.json"),
        resolveWithFerium = true,
        completionMarkers = if (client) compatMarkers(lane) else serverMarkers,
        testFilter = if (client) compatFilter(lane) else "ServerSmokeGameTest",
      )
  }

  root.tasks.register("minecraftTestSupport") {
    group = MINECRAFT_TEST_GROUP
    description = "Report client GameTest API and bridge blockers in the version matrix."
    doLast { clientTestGaps.forEach { logger.lifecycle("SERVER SMOKE FALLBACK: $it") } }
  }
  root.tasks.register("minecraftTestBuild") {
    group = MINECRAFT_TEST_GROUP
    description = "Run clean client or server smoke packs for every supported loader/version lane."
    dependsOn(cleanTests)
  }
  root.tasks.register("minecraftTestCompat") {
    group = MINECRAFT_TEST_GROUP
    description = "Run client or server smoke tests in pinned third-party modpacks for every lane."
    dependsOn(compatTests)
    doFirst {
      if (root.findProject(":e2e-compat:26.2.x") == null)
        throw GradleException(
          "Client compatibility tests require TERRASECT_SKIP_COMPAT to be unset."
        )
    }
  }
  root.tasks.register("minecraftTest") {
    group = MINECRAFT_TEST_GROUP
    description =
      "Run clean and compatibility packs across all lanes, using server smoke where client tests are unavailable."
    dependsOn("minecraftTestBuild", "minecraftTestCompat", "minecraftTestSupport")
  }
}

private val serverMarkers = linkedMapOf("ServerSmokeGameTest" to "server smoke: OK")

private val cleanMarkers =
  linkedMapOf(
    "SmokeGameTest" to "smoke: OK",
    "LootConstraintBlockAllGameTest" to "loot constraints: OK",
  )

private fun compatMarkers(lane: MinecraftTestLane): Map<String, String> = buildMap {
  put("CompatSmokeGameTest", "compat smoke: OK")
  put("ModdedBiomeConstraintGameTest", "modded biome constraint:")
  if (lane.loader == "fabric")
    put("CreatePonderCompatGameTest", "Create Ponder compatibility test passed")
  if (lane.loader == "fabric" && lane.segment == "26.2.x") {
    put("BiomesOPlentyCompatGameTest", "bop compat: OK")
    put("TerraBlenderCompatGameTest", "terrablender compat: OK")
    put("DistantHorizonsCompatGameTest", "dh compat: OK")
    put("C2MECompatGameTest", "c2me compat: OK")
  }
}

private fun compatFilter(lane: MinecraftTestLane): String =
  compatMarkers(lane).keys.joinToString(",")

private fun runtimeDependencies(project: Project, lane: MinecraftTestLane): TestDependencies {
  val notations =
    if (lane.loader == "fabric") {
      listOf(
        "net.fabricmc.fabric-api:fabric-api:${project.property("deps.fabric_api")}",
        "net.fabricmc:fabric-language-kotlin:${project.property("deps.fabric_kotlin")}",
      )
    } else {
      val version = project.property("deps.kotlinforforge")
      listOf(
        "thedarkcolour:kotlinforforge-neoforge:$version",
        "thedarkcolour:kfflang-neoforge:$version",
        "thedarkcolour:kfflib-neoforge:$version",
        "thedarkcolour:kffmod-neoforge:$version",
      )
    }
  return TestDependencies(configuration(project, notations), notations)
}

private fun connectorDependencies(project: Project): TestDependencies {
  val notations =
    listOf(
      "org.sinytra:connector:${project.property("deps.connector")}",
      "org.sinytra.forgified-fabric-api:forgified-fabric-api:${project.property("deps.forgified_fabric_api")}",
      "org.sinytra.launchpad:launchpad:${project.property("deps.launchpad")}",
      "net.fabricmc:fabric-language-kotlin:${project.project(":fabric:${project.name}").property("deps.fabric_kotlin")}",
    )
  return TestDependencies(configuration(project, notations, true), notations)
}

private fun compatDependencies(project: Project, lane: MinecraftTestLane): TestDependencies {
  val notationByProperty =
    linkedMapOf(
      "glitchcore" to "glitchcore",
      "biomesoplenty" to "biomes-o-plenty",
      "terrablender" to "terrablender",
      "distanthorizons" to "distanthorizons",
    )
  if (lane.loader == "fabric" && lane.supportsClientGameTests()) {
    notationByProperty["create"] = "create-fly"
    if (lane.segment == "26.2.x") notationByProperty["c2me"] = "c2me-fabric"
  }
  val notations = notationByProperty.map { (property, slug) ->
    "maven.modrinth:$slug:${project.property("deps.compat_$property")}"
  }
  return TestDependencies(configuration(project, notations), notations)
}

private fun clientGametestApi(project: Project): FileCollection {
  val fabricApi =
    configuration(
      project,
      listOf("net.fabricmc.fabric-api:fabric-api:${project.property("deps.fabric_api")}"),
      true,
    )
  return fabricApi.incoming
    .artifactView {
      componentFilter {
        it is ModuleComponentIdentifier && it.module == "fabric-client-gametest-api-v1"
      }
    }
    .files
}

private fun configuration(
  project: Project,
  notations: List<String>,
  transitive: Boolean = false,
): Configuration =
  project.configurations
    .detachedConfiguration(*notations.map(project.dependencies::create).toTypedArray())
    .apply { isTransitive = transitive }

private fun registerPipeline(
  root: Project,
  project: Project,
  lane: MinecraftTestLane,
  scenario: String,
  dependencies: FileCollection,
  requestedDependencies: List<String>,
  bootstrap: TaskProvider<MinecraftTestBootstrapTask>,
  artifacts: FileCollection,
  clientGametestMod: String,
  clientTests: Boolean,
  e2eDirectory: File,
  modpackDefinition: File?,
  resolveWithFerium: Boolean,
  completionMarkers: Map<String, String>,
  testFilter: String,
): TaskProvider<MinecraftTestLaunchTask> {
  val id = "${lane.loader}-${lane.segment}-${scenario.lowercase()}"
  val prepare =
    project.tasks.register(
      "minecraftTest${scenario}Prepare",
      MinecraftTestPrepareTask::class.java,
    ) {
      group = MINECRAFT_TEST_GROUP
      this.dependencies.from(dependencies)
      this.artifacts.from(artifacts)
      loader.set(lane.loader)
      minecraft.set(lane.minecraft)
      loaderVersion.set(
        project
          .property(if (lane.loader == "fabric") "deps.fabric_loader" else "deps.neo_loader")
          .toString()
      )
      this.requestedDependencies.set(requestedDependencies)
      this.resolveWithFerium.set(resolveWithFerium)
      if (modpackDefinition != null) this.modpackDefinition.set(modpackDefinition)
      ferium.set(bootstrap.flatMap { it.ferium })
      modpackDirectory.set(root.layout.buildDirectory.dir("minecraft-test/modpacks/$id"))
      dependsOn(bootstrap)
    }
  val javaLauncher =
    project.extensions.getByType<JavaToolchainService>().launcherFor {
      languageVersion.set(JavaLanguageVersion.of(project.property("java").toString()))
    }
  return project.tasks.register("minecraftTest$scenario", MinecraftTestLaunchTask::class.java) {
    group = MINECRAFT_TEST_GROUP
    description = "Test the ${scenario.lowercase()} pack for ${lane.loader} ${lane.minecraft}."
    loader.set(lane.loader)
    minecraft.set(lane.minecraft)
    this.loaderVersion.set(
      project
        .property(if (lane.loader == "fabric") "deps.fabric_loader" else "deps.neo_loader")
        .toString()
    )
    javaVersion.set(project.property("java").toString())
    gameJavaExecutable.set(javaLauncher.map { it.executablePath.asFile.absolutePath })
    this.scenario.set(scenario.lowercase())
    this.clientTests.set(clientTests)
    this.completionMarkers.set(completionMarkers)
    this.clientGametestMod.set(
      if (lane.loader == "neoforge") clientGametestMod.replace('-', '_') else clientGametestMod
    )
    this.testFilter.set(root.providers.gradleProperty("test").orElse(testFilter))
    this.e2eDirectory.set(e2eDirectory.absolutePath)
    timeoutSeconds.set(1800L)
    launcher.set(bootstrap.flatMap { it.launcher })
    modpackDirectory.set(prepare.flatMap { it.modpackDirectory })
    minecraftDirectory.set(
      root.layout.dir(
        root.provider {
          File(
            root.gradle.gradleUserHomeDir,
            "caches/terrasect-minecraft/${lane.loader}-${lane.segment}",
          )
        }
      )
    )
    runtimeDirectory.set(root.layout.buildDirectory.dir("minecraft-test/runtime/$id"))
    launchLog.set(root.layout.buildDirectory.file("minecraft-test/logs/$id.log"))
    resultFile.set(root.layout.buildDirectory.file("minecraft-test/results/$id.json"))
    dependsOn(bootstrap, prepare)
    if (scenario == "Compat") mustRunAfter("${project.path}:minecraftTestBuild")
    outputs.upToDateWhen { false }
  }
}
