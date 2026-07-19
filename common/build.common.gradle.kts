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

sourceSets { main { resources.srcDir(generatedAccessConverterResources) } }

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
