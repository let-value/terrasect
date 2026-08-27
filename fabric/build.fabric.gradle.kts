import java.util.zip.ZipFile

plugins {
  id("terrasect-mod")
  alias(libs.plugins.loom.back.compat)
  alias(libs.plugins.dependencies.embedded)
}

val fabricDir = rootProject.file("fabric")

sourceSets { main { kotlin.srcDir(fabricDir.resolve("src/client/kotlin")) } }

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
  embedded("net.openhft:zero-allocation-hashing:${prop("deps.zero_allocation_hashing")}")
  embedded("com.github.ben-manes.caffeine:caffeine:${prop("deps.caffeine")}")
  embedded("com.github.komputing:kbase58:${prop("deps.kbase58")}")

  if (legacyLoomCommon) {
    implementation(project(path = commonProject.path, configuration = "namedElements"))
  } else {
    implementation(commonProject)
  }
}

val resourceProps = fabricResourceProps()

tasks {
  val jarTask = named<Jar>("jar")
  val expectedEmbeddedClasses =
    setOf(
      "com/electronwill/nightconfig/core/Config.class",
      "com/electronwill/nightconfig/toml/TomlFormat.class",
      "com/github/benmanes/caffeine/cache/Caffeine.class",
      "net/openhft/hashing/LongHashFunction.class",
      "org/komputing/kbase58/Base58Kt.class",
      "org/komputing/khash/sha256/extensions/PublicExtensionsKt.class",
    )
  val verifyEmbeddedDependencies by registering {
    dependsOn("assemble")
    doLast {
      val archive = jarTask.get().archiveFile.get().asFile
      ZipFile(archive).use { zip ->
        val missing = expectedEmbeddedClasses.filter { zip.getEntry(it) == null }.toSet()
        check(missing.isEmpty()) {
          "${archive.name} is missing embedded dependency classes: $missing"
        }
      }
    }
  }

  named("check") { dependsOn(verifyEmbeddedDependencies) }

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
}
