import dev.kikugie.stonecutter.build.StonecutterBuildExtension

// Loom-based variant of build.common.gradle.kts for Minecraft versions below 1.20.2, where
// NeoForm (and thus MDG's neoForge{} MC provider used by build.common.gradle.kts) has no release —
// NeoForged forked after 1.20.1. Loom + Mojang mappings can still supply a 1.20.1 MC jar, matching
// how the fabric/e2e modules already resolve Minecraft.
plugins {
  alias(libs.plugins.loom.back.compat)
  alias(libs.plugins.kotlin.jvm)
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

val commonDir = rootProject.file("common")
val processedCommonKotlinDir = layout.buildDirectory.dir("processed/main/kotlin")
val processedCommonJavaDir = layout.buildDirectory.dir("processed/main/java")

processCommonSourceTree(sc, commonDir)

val accessWidenerFile = "${sc.current.version}.accesswidener"

version = prop("mod.version")

base.archivesName = "${prop("mod.id")}-common"

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
    java.srcDir(processedCommonJavaDir)
    kotlin.srcDir(processedCommonKotlinDir)
    resources.srcDir(commonDir.resolve("src/main/resources"))
  }
  test {
    kotlin.srcDir(commonDir.resolve("src/test/kotlin"))
    resources.srcDir(commonDir.resolve("src/test/resources"))
  }
}

loom {
  accessWidenerPath = commonDir.resolve("src/main/resources/accesswideners/$accessWidenerFile")
}

dependencies {
  minecraft("com.mojang:minecraft:${sc.current.version}")
  loomx.applyMojangMappings()

  compileOnly("net.fabricmc:sponge-mixin:${prop("deps.mixin")}")
  compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
  implementation("net.openhft:zero-allocation-hashing:${prop("deps.zero_allocation_hashing")}")
  implementation("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")
  implementation("com.github.komputing:kbase58:${prop("deps.kbase58")}")
  compileOnly("com.electronwill.night-config:core:${prop("deps.night_config")}")
  compileOnly("com.electronwill.night-config:toml:${prop("deps.night_config")}")

  testImplementation("it.unimi.dsi:fastutil-core:8.5.18")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("de.skuzzle.test:snapshot-tests-junit5:${prop("deps.snapshot_tests")}")
  testImplementation("com.github.spullara.mustache.java:compiler:0.9.10")
  testImplementation("com.electronwill.night-config:toml:${prop("deps.night_config")}")
}

tasks {
  named<ProcessResources>("processResources") {
    includeEmptyDirs = false
    exclude("accesswideners/*.accesswidener")
    // Mixin's compatibilityLevel must be <= the JRE running the game (JAVA_17 here); capped at 21
    // so newer toolchains keep the highest level Mixin actually enables.
    val mixinCompatLevel = "JAVA_${minOf(prop("java").toInt(), 21)}"
    inputs.property("mixinCompatLevel", mixinCompatLevel)
    filesMatching("*.mixins.json") { expand("mixin_compat_level" to mixinCompatLevel) }
  }

  test {
    workingDir = commonDir
    useJUnitPlatform()
    systemProperty("terrasect.minecraftVersion", sc.current.version)
    outputs.dir(commonDir.resolve("build/test-snapshots/${sc.current.version}"))
    if (project.hasProperty("updateSnapshots")) {
      systemProperty("updateSnapshots", "true")
    }
  }
}
