plugins {
  id("terrasect-mod")
  `java-library`
  alias(libs.plugins.neoforged.moddev)
  alias(libs.plugins.fletching.table)
}

val generatedAccessConverterResources =
  layout.buildDirectory.dir("generated/accessConverter/resources")

sourceSets { main { resources.srcDir(generatedAccessConverterResources) } }

fletchingTable {
  accessConverter.register(sourceSets.main) {
    add("accessconverters/$accessWidenerFile")
  }
}

neoForge {
  neoFormVersion = propOrNull("deps.neoform") ?: mcVersion
  propOrNull("parchment.mappings")?.let { mappings ->
    parchment {
      mappingsVersion = mappings
      minecraftVersion = prop("parchment.minecraft")
    }
  }
  addModdingDependenciesTo(sourceSets["test"])
}

// Single source of truth for the runtime libraries bundled into every loader jar. common compiles
// against them (implementation extends this) and exposes them so fabric/neoforge embed exactly this
// set instead of re-listing coordinates.
val embeddedDependencies: Configuration by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

configurations.named("implementation") { extendsFrom(embeddedDependencies) }

dependencies {
  compileOnly("net.fabricmc:sponge-mixin:${prop("deps.mixin")}")
  compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
  embeddedDependencies(
    "net.openhft:zero-allocation-hashing:${prop("deps.zero_allocation_hashing")}"
  )
  embeddedDependencies("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")
  embeddedDependencies("com.github.komputing:kbase58:${prop("deps.kbase58")}")
  embeddedDependencies("com.electronwill.night-config:toml:${prop("deps.night_config")}")

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
    systemProperty("terrasect.minecraftVersion", mcVersion)
    outputs.dir(commonDir.resolve("build/test-snapshots/$mcVersion"))
    if (project.hasProperty("updateSnapshots")) {
      systemProperty("updateSnapshots", "true")
    }
  }
}
