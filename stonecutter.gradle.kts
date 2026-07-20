plugins {
  alias(libs.plugins.stonecutter)
  alias(libs.plugins.spotless)
}

stonecutter active "26.2.x"

allprojects {
  repositories {
    mavenCentral()
    maven("https://jitpack.io") { name = "JitPack" }
    maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
    exclusiveContent {
      forRepository { maven("https://api.modrinth.com/maven") }
      filter { includeGroup("maven.modrinth") }
    }
  }
}

val latestProject = "26.2.x"

stonecutter parameters
  {
    val loader = branch.id
    // e2e and e2e-compat build a Fabric client, so they also need the fabric.* property table.
    val propertyTags =
      if (loader == "e2e" || loader == "e2e-compat") arrayOf(loader, "fabric") else arrayOf(loader)
    properties { tags(current.project, *propertyTags) }
    constants {
      match(loader, "fabric", "neoforge")
      put("latest", current.project == latestProject)
    }
  }

spotless {
  java {
    target("common/src/**/*.java")
    toggleOffOn()
    googleJavaFormat()
  }

  kotlin {
    target(
      "common/src/**/*.kt",
      "fabric/src/**/*.kt",
      "neoforge/src/**/*.kt",
      "e2e/src/**/*.kt",
      "e2e-compat/src/**/*.kt",
    )
    toggleOffOn()
    ktfmt().googleStyle()
  }

  kotlinGradle {
    target(
      "*.gradle.kts",
      "common/*.gradle.kts",
      "fabric/*.gradle.kts",
      "neoforge/*.gradle.kts",
      "e2e/*.gradle.kts",
      "e2e-compat/*.gradle.kts",
    )
    ktfmt().googleStyle()
  }
}
