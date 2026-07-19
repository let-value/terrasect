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
val e2eCompatDir = rootProject.file("e2e-compat")
val fabricClientKotlinSrc = fabricDir.resolve("src/client/kotlin")
val processedFabricClientKotlinDir = layout.buildDirectory.dir("processed/client/kotlin")
val commonProject = project(":common:${project.name.substringBeforeLast("-")}")
val accessWidenerFile = "${sc.current.version}.accesswidener"
val gametestModId = "${prop("mod.id")}-e2e-compat"

// `fabric/src/client/kotlin` is Stonecutter-gated (`//? if`) canonical source — the raw directory
// only reflects whichever version is currently checked out (`stonecutter active` in the root
// stonecutter.gradle.kts), not whatever version this e2e-compat instance targets. The real
// `:<version>-fabric` project resolves this the same way (see build.fabric.gradle.kts): run each
// file through `sc.process` per target version before using it as a source dir, instead of
// compiling the raw directory directly.
project
  .fileTree(fabricClientKotlinSrc) { include("**/*.kt") }
  .forEach { file ->
    sc.process(file, "build/processed/client/kotlin/${file.relativeTo(fabricClientKotlinSrc).path}")
  }

// The four content-specific tests (BOP/TerraBlender/DH/C2ME) call raw Minecraft APIs directly
// with no Stonecutter version-gating, unlike CompatSmokeGameTest which only touches version-safe
// terrasect.compat shims — they only compile against the latest version. Every version still gets
// the smoke test (mods loaded + full constraint pipeline active); the targeted tests are
// latest-only until they're worth porting across the version matrix too.
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
    kotlin.setSrcDirs(listOf(fabricDir.resolve("src/main/kotlin"), processedFabricClientKotlinDir))
    resources.setSrcDirs(listOf(fabricDir.resolve("src/main/resources")))
  }
  named("gametest") {
    // Deliberately does NOT include e2e's own gametest kotlin dir: that would drag in every one of
    // e2e's other, latest-only test files (MobConstraintGameTest, StructureConstraintGameTest,
    // ...) as compile-time dependencies of every e2e-compat version too. GameTestFilter is copied
    // into e2e-compat's own tree instead — small and stable enough not to be worth deduplicating
    // across module boundaries at the cost of that coupling.
    val kotlinDirs = mutableListOf(e2eCompatDir.resolve("src/gametest/kotlin"))
    // gametestLatest's fabric.mod.json is a superset of the base one (smoke + targeted tests), so
    // it fully replaces rather than supplements the base resources dir — using both would collide
    // on the fabric.mod.json path.
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

  // Third-party mod compatibility coverage — this is the ONLY project that pulls these in;
  // `e2e` stays free of third-party mods so the core suite never depends on one being resolvable.
  // Runtime-only: Terrasect never references their classes, only their registered resource
  // ids/tags via the existing SelectionConstraints machinery.
  //
  // Pinned by Modrinth *version id*, not the human version string: Modrinth lets a project reuse
  // the same version string across loaders (e.g. Biomes O' Plenty's Fabric and NeoForge 26.2
  // builds are both "26.1.2.0.42"), and the maven.modrinth proxy resolves the plain string
  // ambiguously — it silently served the NeoForge jar for a `biomes-o-plenty:26.1.2.0.42`
  // coordinate here. The version id is unique per file and resolves deterministically.
  // GlitchCore is a hard runtime dependency of Biomes O' Plenty (shared utility library, not
  // requested by name but required to load it).
  modRuntimeOnly("maven.modrinth:glitchcore:${prop("deps.compat_glitchcore")}")
  modRuntimeOnly("maven.modrinth:biomes-o-plenty:${prop("deps.compat_biomesoplenty")}")
  modRuntimeOnly("maven.modrinth:terrablender:${prop("deps.compat_terrablender")}")
  modRuntimeOnly("maven.modrinth:distanthorizons:${prop("deps.compat_distanthorizons")}")
  if (!isLatest) {
    // On versions where Loom still needs to remap the mod jar (i.e. not on pure, un-remapped
    // Mojang mappings like latest), the remap step silently drops each mod's bundled
    // Jar-in-Jar libraries (META-INF/jars/*) instead of re-extracting them — GlitchCore,
    // BiomesOPlenty, and TerraBlender all bundle the same three plain (non-Fabric-mod) library
    // jars this way, so they crash at init with ClassNotFoundException for nightconfig/typetools.
    // Declaring the same libraries directly bypasses the mod-remap pipeline entirely.
    runtimeOnly("com.electronwill.night-config:core:${prop("deps.night_config")}")
    runtimeOnly("com.electronwill.night-config:toml:${prop("deps.night_config")}")
    runtimeOnly("net.jodah:typetools:0.6.3")
  }
  // C2ME rewrites chunk generation/loading to run concurrently — the most likely mod to expose
  // thread-safety bugs in Terrasect's own generation pipeline (shared mutable state accessed off
  // the vanilla single-threaded assumption), so it belongs in compat coverage even though it adds
  // no world-gen content of its own. Latest-only for now: unlike GlitchCore/BOP/TerraBlender's
  // bundled libraries, C2ME's Jar-in-Jar entries are its own Fabric sub-mods (separate
  // fabric.mod.json/entrypoints each), not plain libraries — they can't be unbundled via a
  // Maven Central coordinate, so the same remap-drops-JiJ workaround doesn't apply to it.
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
