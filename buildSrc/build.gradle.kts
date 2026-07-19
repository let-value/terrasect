plugins { `kotlin-dsl` }

repositories {
  gradlePluginPortal()
  mavenCentral()
  maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
}

dependencies { implementation(libs.stonecutter) }
