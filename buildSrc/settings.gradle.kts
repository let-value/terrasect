dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
  }
  versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
