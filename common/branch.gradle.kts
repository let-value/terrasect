// Stonecutter's default branch-root script. This project is never built directly — it only
// exists so `:common:<version>` subprojects have a shared parent. dev.kikugie.loom-back-compat's
// settings plugin unconditionally injects a Fabric Loom classpath dependency onto every project in
// the tree via `gradle.beforeProject {}`, so this project needs its own buildscript repositories
// to resolve it (and Loom's transitive deps), even though it never applies Loom itself.
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
