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
  embedded(project(path = commonProject.path, configuration = "embeddedDependencies"))

  if (legacyLoomCommon) {
    implementation(project(path = commonProject.path, configuration = "namedElements"))
  } else {
    implementation(commonProject)
  }
}

val resourceProps = fabricResourceProps()

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
}
