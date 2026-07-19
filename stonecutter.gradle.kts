plugins {
  id("dev.kikugie.stonecutter")
  id("com.diffplug.spotless")
}

stonecutter active "26.2.x-fabric"

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
    // common branch nodes are bare version ids (no "-loader" suffix, e.g. "26.2.x"); only
    // fabric/neoforge nodes carry a loader suffix to split off.
    val parts = current.project.split("-", limit = 2)
    if (parts.size == 2) {
      val (version, loader) = parts
      properties { tags(version, loader) }
      constants { match(loader, "fabric", "neoforge") }
    } else {
      properties { tags(current.project, "common") }
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
    target("*.gradle.kts", "common/*.gradle.kts")
    ktfmt().googleStyle()
  }
}
