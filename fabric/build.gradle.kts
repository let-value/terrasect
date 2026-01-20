plugins {
    id("fabric-loom")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm")
}

val modId = property("mod_id").toString()

base {
    archivesName.set("$modId-fabric")
}

repositories {
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }

    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("run")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client.set(true)
    }
}

dependencies {
    // Minecraft
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    
    // Fabric
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    
    // Common module
    implementation(project(":common"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mod_id", modId)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "mod_id" to modId
        )
    }
}

tasks.jar {
    from(project(":common").sourceSets.main.get().output)
    
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }

    repositories {
        // Add repositories to publish to here
    }
}
