import dev.kikugie.stonecutter.build.StonecutterBuildExtension

plugins {
  `java-library`
  id("dev.kikugie.loom-back-compat")
  id("org.jetbrains.kotlin.jvm")
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

val commonDir = rootProject.file("common")
val processedCommonKotlinDir = layout.buildDirectory.dir("processed/main/kotlin")
val processedCommonKotlinFiles =
  listOf(
      "terrasect/compat/ResourceKeyCompat.kt",
      "terrasect/compat/LootContextCompat.kt",
      "terrasect/compat/NoiseRouterCompat.kt",
      "terrasect/gui/RegionDebugEntry.kt",
    )
    .map { path ->
      sc.process(
        commonDir.resolve("src/main/kotlin/$path"),
        "build/processed/main/kotlin/${path.substringAfterLast("/")}",
      )
    }

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
    java.srcDir(commonDir.resolve("src/main/java"))
    kotlin.srcDir(commonDir.resolve("src/main/kotlin"))
    kotlin.srcDir(processedCommonKotlinDir)
    kotlin.exclude(
      "terrasect/compat/ResourceKeyCompat.kt",
      "terrasect/compat/LootContextCompat.kt",
      "terrasect/compat/NoiseRouterCompat.kt",
      "terrasect/gui/RegionDebugEntry.kt",
    )
    resources.srcDir(commonDir.resolve("src/main/resources"))
  }
  test {
    kotlin.srcDir(commonDir.resolve("src/test/kotlin"))
    resources.srcDir(commonDir.resolve("src/test/resources"))
  }
}

dependencies {
  minecraft("com.mojang:minecraft:${sc.current.version}")
  loomx.applyMojangMappings()

  compileOnly("net.fabricmc:sponge-mixin:${prop("deps.mixin")}")
  compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
  implementation("net.openhft:zero-allocation-hashing:${prop("deps.zero_allocation_hashing")}")
  implementation("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")
  implementation("com.github.komputing:kbase58:${prop("deps.kbase58")}")

  testImplementation("it.unimi.dsi:fastutil-core:8.5.18")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("de.skuzzle.test:snapshot-tests-junit5:${prop("deps.snapshot_tests")}")
  testImplementation("com.github.spullara.mustache.java:compiler:0.9.10")
}

tasks {
  named<ProcessResources>("processResources") {
    includeEmptyDirs = false
    exclude("accesswideners/26.2.accesswidener")
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
