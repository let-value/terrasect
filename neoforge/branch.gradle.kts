buildscript {
  repositories {
    mavenLocal()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.neoforged.net/releases") { name = "NeoForge" }
    maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    gradlePluginPortal()
    mavenCentral()
  }
}
