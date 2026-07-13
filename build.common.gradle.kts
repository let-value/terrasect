import dev.kikugie.stonecutter.build.StonecutterBuildExtension

plugins {
  `java-library`
  id("org.jetbrains.kotlin.jvm")
  id("net.neoforged.moddev")
  id("dev.kikugie.fletching-table")
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

fun propOrNull(key: String): String? = sc.properties.getOrNull<String>(key)

val commonDir = rootProject.file("common")
val commonKotlinSrc = commonDir.resolve("src/main/kotlin")
val processedCommonKotlinDir = layout.buildDirectory.dir("processed/main/kotlin")
val commonJavaSrc = commonDir.resolve("src/main/java")
val processedCommonJavaDir = layout.buildDirectory.dir("processed/main/java")

project
  .fileTree(commonKotlinSrc) { include("**/*.kt") }
  .forEach { file ->
    sc.process(file, "build/processed/main/kotlin/${file.relativeTo(commonKotlinSrc).path}")
  }

project
  .fileTree(commonJavaSrc) { include("**/*.java") }
  .forEach { file ->
    sc.process(file, "build/processed/main/java/${file.relativeTo(commonJavaSrc).path}")
  }

val accessWidenerFile = "${sc.current.version}.accesswidener"
val generatedAccessConverterResources =
  layout.buildDirectory.dir("generated/accessConverter/resources")

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
    resources.srcDir(generatedAccessConverterResources)
  }
  test {
    kotlin.srcDir(commonDir.resolve("src/test/kotlin"))
    resources.srcDir(commonDir.resolve("src/test/resources"))
  }
}

fletchingTable {
  accessConverter.register(sourceSets.main) {
    add("accessconverters/$accessWidenerFile")
  }
}

neoForge {
  neoFormVersion = propOrNull("deps.neoform") ?: sc.current.version
  propOrNull("parchment.mappings")?.let { mappings ->
    parchment {
      mappingsVersion = mappings
      minecraftVersion = prop("parchment.minecraft")
    }
  }
  addModdingDependenciesTo(sourceSets["test"])
}

dependencies {
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
  val generateAccessConverterWidener by
    registering(Copy::class) {
      from(commonDir.resolve("src/main/resources/accesswideners/$accessWidenerFile")) {
        filter { line: String ->
          if (line.startsWith("accessWidener v2 ")) "accessWidener v2 named" else line
        }
      }
      into(generatedAccessConverterResources.map { it.dir("accessconverters") })
    }

  named<ProcessResources>("processResources") {
    dependsOn(generateAccessConverterWidener)
    includeEmptyDirs = false
    exclude("accesswideners/*.accesswidener")
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
