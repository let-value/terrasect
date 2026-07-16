import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import net.fabricmc.loom.task.GenerateSourcesTask

plugins {
  id("dev.kikugie.loom-back-compat")
  id("org.jetbrains.kotlin.jvm")
  id("io.github.gmazzo.dependencies.embedded")
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

val commonDir = rootProject.file("common")
val fabricDir = rootProject.file("fabric")
val fabricClientKotlinSrc = fabricDir.resolve("src/client/kotlin")
val processedFabricClientKotlinDir = layout.buildDirectory.dir("processed/client/kotlin")

project
  .fileTree(fabricClientKotlinSrc) { include("**/*.kt") }
  .forEach { file ->
    sc.process(file, "build/processed/client/kotlin/${file.relativeTo(fabricClientKotlinSrc).path}")
  }

val commonProject = project(":${project.name.substringBeforeLast("-")}-common")
val accessWidenerFile = "${sc.current.version}.accesswidener"

// Sub-1.20.2 builds common with Loom (build.common-legacy) since NeoForm has no release there.
// Loom remaps that common's default artifact to intermediary, so consuming it normally puts
// intermediary-targeted mixins on a Mojang-mapped dev runtime and every mixin silently fails to
// apply. Consume its `namedElements` (un-remapped) variant instead. Newer commons are MDG-built
// (never remapped), so they're consumed directly.
val legacyLoomCommon = sc.current.version == "1.20.1"

version = prop("mod.version")

base.archivesName = "${prop("mod.id")}-fabric"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(prop("java").toInt())
  }
}

kotlin {
  jvmToolchain(prop("java").toInt())
}

sourceSets {
  main {
    kotlin.srcDir(fabricDir.resolve("src/main/kotlin"))
    kotlin.srcDir(processedFabricClientKotlinDir)
    resources.srcDir(fabricDir.resolve("src/main/resources"))
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

  if (legacyLoomCommon) {
    implementation(project(path = commonProject.path, configuration = "namedElements"))
    // `namedElements` carries only the un-remapped jar, not common's transitive runtime libraries,
    // so pull common's third-party runtime deps explicitly (same version props). Newer commons
    // bring these transitively via the plain project dependency.
    runtimeOnly("net.openhft:zero-allocation-hashing:${prop("deps.zero_allocation_hashing")}")
    runtimeOnly("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")
    runtimeOnly("com.github.komputing:kbase58:${prop("deps.kbase58")}")
  } else {
    implementation(commonProject)
  }
}

val resourceProps =
  mapOf(
    "version" to version.toString(),
    "mod_id" to prop("mod.id"),
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

  named<Jar>("jar") {
    from(commonProject.sourceSets["main"].output) {
      exclude("META-INF/accesstransformer.cfg", "accesswideners/*.accesswidener")
    }
  }

  register<Sync>("unpackMinecraftSources") {
    group = "minecraft sources"
    description = "Unpack Mojang-mapped decompiled Minecraft sources to minecraft/${project.name}/"
    dependsOn("genSourcesWithVineflower")
    val genTask = named<GenerateSourcesTask>("genSourcesWithVineflower")
    from(zipTree(genTask.flatMap { it.sourcesOutputJar }))
    into(rootProject.file("minecraft/${project.name}"))
  }
}
