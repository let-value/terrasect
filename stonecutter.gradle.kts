plugins {
  id("dev.kikugie.stonecutter")
  id("com.diffplug.spotless")
}

stonecutter active "1.21.11-fabric"

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
    val (version, loader) = current.project.split("-", limit = 2)

    properties {
      tags(version, loader)
      if (loader == "e2e") {
        tags("fabric")
      }
    }

    constants {
      match(loader, "fabric", "neoforge")
    }
  }

spotless {
  java {
    target("common/src/**/*.java")
    googleJavaFormat()
  }

  kotlin {
    target("common/src/**/*.kt", "fabric/src/**/*.kt", "neoforge/src/**/*.kt", "e2e/src/**/*.kt")
    ktfmt().googleStyle()
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
  }
}
