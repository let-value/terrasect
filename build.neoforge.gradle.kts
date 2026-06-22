import dev.kikugie.stonecutter.build.StonecutterBuildExtension

plugins {
  `java-library`
  `maven-publish`
  id("net.neoforged.moddev")
  id("org.jetbrains.kotlin.jvm")
}

val sc = extensions.getByType<StonecutterBuildExtension>()

fun prop(key: String): String = sc.properties[key]

fun propOrNull(key: String): String? = sc.properties.getOrNull<String>(key)

val commonDir = rootProject.file("common")
val neoforgeDir = rootProject.file("neoforge")
val commonProject = project(":${project.name.substringBeforeLast("-")}-common")

version = prop("mod.version")

base.archivesName = "${prop("mod.id")}-neoforge"

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
    kotlin.srcDir(neoforgeDir.resolve("src/main/kotlin"))
    resources.srcDir(neoforgeDir.resolve("src/main/resources"))
    resources.srcDir(neoforgeDir.resolve("src/main/templates"))
  }
}

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
    }
  }
}

dependencies {
  implementation("thedarkcolour:kotlinforforge-neoforge:${prop("deps.kotlinforforge")}")
  implementation(commonProject)
}

val metadataProps =
  mapOf(
    "minecraft_version" to sc.current.version,
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
    "mod_version" to prop("mod.version"),
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
