plugins {
  java
  kotlin("jvm")
}

version =
  if (loader == "fabric" || loader == "neoforge") "${mod.version}+$mcVersion" else mod.version

base.archivesName = "${mod.id}-$loader"

java { toolchain { languageVersion = JavaLanguageVersion.of(prop("java").toInt()) } }

kotlin { jvmToolchain(prop("java").toInt()) }
