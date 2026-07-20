plugins {
  id("terrasect-mod")
  `java-library`
  `maven-publish`
  alias(libs.plugins.neoforged.moddev)
}

val neoforgeDir = rootProject.file("neoforge")

sourceSets { main { resources.srcDir(neoforgeDir.resolve("src/main/templates")) } }

neoForge {
  version = prop("deps.neo_loader")
  propOrNull("parchment.mappings")?.let { mappings ->
    parchment {
      mappingsVersion = mappings
      minecraftVersion = prop("parchment.minecraft")
    }
  }
  runs {
    create("client") { client() }
    create("server") {
      server()
      programArgument("--nogui")
    }
  }
  mods {
    create(prop("mod.id")) {
      sourceSet(sourceSets["main"])
      sourceSet(commonProject.sourceSets["main"])
    }
  }
}

evaluationDependsOn(commonProject.path)

dependencies {
  implementation("thedarkcolour:kotlinforforge-neoforge:${prop("deps.kotlinforforge")}")
  implementation(commonProject)

  // moddev's jarJar can't consume a project configuration directly (it needs per-module version
  // metadata), so nest each library common declares in its embeddedDependencies set.
  commonProject.configurations["embeddedDependencies"].dependencies.forEach {
    jarJar("${it.group}:${it.name}:${it.version}")
  }
}

val metadataProps =
  mapOf(
    "minecraft_version" to mcVersion,
    "neoforge_minecraft_version_range" to prop("deps.neo_minecraft_range"),
    "neoforge_loader_version" to prop("deps.neo_loader"),
    "neoforge_version_range" to prop("deps.neo_version_range"),
    "neoforge_kotlin_version" to prop("deps.kotlinforforge"),
    "neoforge_kotlin_version_range" to prop("deps.kotlinforforge_range"),
    "neoforge_loader_version_range" to prop("deps.neo_loader_range"),
    "java_version" to prop("java"),
    "mod_id" to prop("mod.id"),
    "mod_name" to prop("mod.name"),
    "mod_license" to prop("mod.license"),
    "mod_version" to version.toString(),
    "mod_authors" to prop("mod.authors"),
    "mod_description" to prop("mod.description"),
  )

tasks {
  named<ProcessResources>("processResources") {
    inputs.properties(metadataProps)
    includeEmptyDirs = false
    filesMatching("META-INF/neoforge.mods.toml") {
      expand(metadataProps)
    }
    filesMatching("*.mixins.json") {
      expand(metadataProps)
    }
    exclude("fabric.mod.json", "*.accesswidener")
  }

  named<Jar>("jar") {
    from(commonProject.sourceSets["main"].output) {
      exclude("accesswideners/*.accesswidener")
    }
  }
}
