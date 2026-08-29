import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType

private const val MINECRAFT_TEST_GROUP = "minecraft test"

private data class MinecraftTestLane(val loader: String, val segment: String, val minecraft: String)

private fun Project.runtimeProp(key: String): String =
  extensions.getByType<StonecutterControllerExtension>().properties.getOrNull<String>(key) ?: ""

private fun Project.runtimeTestLanes(): List<MinecraftTestLane> =
  extensions.getByType<StonecutterControllerExtension>().tree.entries.flatMap { (loader, branch) ->
    if (loader != "fabric" && loader != "neoforge") return@flatMap emptyList()
    branch.versions.map { version ->
      MinecraftTestLane(loader, version.project, version.version)
    }
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

  val buildTests = mutableListOf<TaskProvider<MinecraftTestLaunchTask>>()
  val publishedTests = mutableListOf<TaskProvider<MinecraftTestLaunchTask>>()
  val compatTests = mutableListOf<TaskProvider<MinecraftTestLaunchTask>>()

  root.runtimeTestLanes().forEach { lane ->
    val project = root.project(":${lane.loader}:${lane.segment}")
    val jar = project.tasks.named("jar", Jar::class.java)
    val runtimeDependencies = runtimeDependencies(project, lane)
    val gametest =
      if (lane.loader == "fabric" && lane.segment !in setOf("1.20.1", "1.21.1")) {
        val testProject = root.project(":e2e:${lane.segment}")
        root
          .files(
            testProject.layout.buildDirectory.file(
              "libs/terrasect-tests-${root.runtimeProp("mod.version")}+${lane.minecraft}.jar"
            )
          )
          .builtBy("${testProject.path}:gametestModJar")
      } else null
    val buildArtifacts = root.files(jar.flatMap { it.archiveFile }).builtBy(jar)
    if (gametest != null) buildArtifacts.from(gametest)
    val buildDependencies = root.files(runtimeDependencies.configuration)
    if (gametest != null) buildDependencies.from(clientGametestApi(project))

    buildTests +=
      registerPipeline(
        root,
        project,
        lane,
        "Build",
        buildDependencies,
        bootstrap,
        buildArtifacts,
        if (gametest == null) "" else "terrasect-e2e",
        root.file("e2e"),
        lane.loader == "fabric",
      )

    val published = configuration(project, runtimeDependencies.notations)
    val publishedDependencies = root.files(published)
    if (gametest != null) publishedDependencies.from(clientGametestApi(project))
    publishedTests +=
      registerPipeline(
        root,
        project,
        lane,
        "Published",
        publishedDependencies,
        bootstrap,
        gametest ?: root.files(),
        if (gametest == null) "" else "terrasect-e2e",
        root.file("e2e"),
        true,
      )

    if (lane.loader == "fabric" && root.findProject(":e2e-compat:${lane.segment}") != null) {
      val compatDependencies =
        root.files(
          clientGametestApi(project),
          compatModDependencies(project, root, lane),
        )
      val testProject = root.project(":e2e-compat:${lane.segment}")
      val compatGametest =
        root
          .files(
            testProject.layout.buildDirectory.file(
              "libs/terrasect-compat-tests-${root.runtimeProp("mod.version")}+${lane.minecraft}.jar"
            )
          )
          .builtBy("${testProject.path}:gametestModJar")
      val compatArtifacts = root.files(jar.flatMap { it.archiveFile }, compatGametest).builtBy(jar)
      compatTests +=
        registerPipeline(
          root,
          project,
          lane,
          "Compat",
          compatDependencies,
          bootstrap,
          compatArtifacts,
          "terrasect-e2e-compat",
          root.file("e2e-compat"),
          false,
        )
    }
  }

  root.tasks.register("minecraftTestBuild") {
    group = MINECRAFT_TEST_GROUP
    description = "Test every locally built Terrasect artifact with HeadlessMC."
    dependsOn(buildTests)
  }
  root.tasks.register("minecraftTestPublished") {
    group = MINECRAFT_TEST_GROUP
    description = "Test every published Terrasect artifact from Modrinth with HeadlessMC."
    dependsOn(publishedTests)
  }
  root.tasks.register("minecraftTestCompat") {
    group = MINECRAFT_TEST_GROUP
    description = "Test local Terrasect artifacts with every supported compatibility mod."
    dependsOn(compatTests)
  }
  root.tasks.register("minecraftTest") {
    group = MINECRAFT_TEST_GROUP
    description = "Run build, published, and compatibility Minecraft tests."
    dependsOn("minecraftTestBuild", "minecraftTestPublished", "minecraftTestCompat")
  }
}

private data class TestDependencies(
  val configuration: Configuration,
  val notations: List<String>,
)

private fun runtimeDependencies(project: Project, lane: MinecraftTestLane): TestDependencies {
  val notations =
    if (lane.loader == "fabric") {
      emptyList()
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

private fun compatModDependencies(
  project: Project,
  root: Project,
  lane: MinecraftTestLane,
): FileCollection {
  val compatProject = root.project(":e2e-compat:${lane.segment}")
  val dependencies = project.configurations.detachedConfiguration().apply { isTransitive = false }
  compatProject.afterEvaluate {
    val notations =
      listOf("modImplementation", "modRuntimeOnly")
        .flatMap { compatProject.configurations.getByName(it).dependencies }
        .filter {
          it.group == "maven.modrinth" ||
            (it.group == "net.fabricmc.fabric-api" && it.name == "fabric-api") ||
            (it.group == "net.fabricmc" && it.name == "fabric-language-kotlin")
        }
        .map { "${it.group}:${it.name}:${it.version}" }
    dependencies.dependencies.addAll(notations.map(project.dependencies::create))
  }
  return root.files(dependencies)
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
  bootstrap: TaskProvider<MinecraftTestBootstrapTask>,
  artifacts: FileCollection,
  clientGametestMod: String,
  e2eDirectory: File,
  resolveWithFerium: Boolean,
): TaskProvider<MinecraftTestLaunchTask> {
  val id = "${lane.loader}-${lane.segment}-${scenario.lowercase()}"
  val definitionDirectory = if (scenario == "Build") "artifact" else scenario.lowercase()
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
      this.resolveWithFerium.set(resolveWithFerium)
      modpackDefinition.set(
        root.file("runtime-tests/modpacks/$definitionDirectory/${lane.loader}-${lane.segment}.json")
      )
      ferium.set(bootstrap.flatMap { it.ferium })
      modpackDirectory.set(root.layout.buildDirectory.dir("minecraft-test/modpacks/$id"))
      dependsOn(bootstrap)
    }
  return project.tasks.register("minecraftTest$scenario", MinecraftTestLaunchTask::class.java) {
    group = MINECRAFT_TEST_GROUP
    description = "Test the ${scenario.lowercase()} pack for ${lane.loader} ${lane.minecraft}."
    loader.set(lane.loader)
    minecraft.set(lane.minecraft)
    successMarker.set(if (clientGametestMod.isEmpty()) "Initializing Terrasect common..." else "")
    this.clientGametestMod.set(clientGametestMod)
    testFilter.set(root.providers.gradleProperty("test").orElse(""))
    this.e2eDirectory.set(e2eDirectory.absolutePath)
    timeoutSeconds.set(1200L)
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
    dependsOn(bootstrap, prepare)
    outputs.upToDateWhen { false }
  }
}
