plugins {
  id("dev.kikugie.stonecutter")
  id("com.diffplug.spotless")
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

stonecutter parameters
  {
    val loader = branch.id
    properties { tags(current.project, loader) }
    constants { match(loader, "fabric", "neoforge") }
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
    target("*.gradle.kts", "common/*.gradle.kts", "fabric/*.gradle.kts", "neoforge/*.gradle.kts")
    ktfmt().googleStyle()
  }
}
