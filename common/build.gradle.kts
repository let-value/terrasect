plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("net.neoforged.moddev")
}

val modId = property("mod_id").toString()

base {
    archivesName.set("$modId-common")
}

// Vanilla-mode: Use NeoForm to get Minecraft classes without any loader-specific code
// This is the recommended approach for common/shared code in multiloader projects
neoForge {
    // Look for versions on https://projects.neoforged.net/neoforged/neoform
    neoFormVersion = property("neoform_version").toString()

    parchment {
        mappingsVersion = property("parchment_mappings_version").toString()
        minecraftVersion = property("parchment_minecraft_version").toString()
    }

    // Basic runs for testing common code in vanilla context
    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
        register("data") {
            data()
        }
    }
}

dependencies {
    // No loader-specific dependencies in common module
    // Common module only contains code that works with vanilla Minecraft
}

// Common is a library module - it doesn't produce a runnable mod
// Both fabric and neoforge modules will depend on this
