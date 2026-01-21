plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("net.neoforged.moddev")
}

val modId = property("mod_id").toString()

base { archivesName.set("$modId-common") }

neoForge {
  neoFormVersion = property("neoform_version").toString()

  parchment {
    mappingsVersion = property("parchment_mappings_version").toString()
    minecraftVersion = property("parchment_minecraft_version").toString()
  }

  runs {
    register("client") { client() }
    register("server") { server() }
    register("serverData") { serverData() }
  }
}
