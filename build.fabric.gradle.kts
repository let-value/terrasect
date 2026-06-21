import dev.kikugie.stonecutter.build.StonecutterBuildExtension

plugins {
    id("dev.kikugie.loom-back-compat")
    id("org.jetbrains.kotlin.jvm")
    id("io.github.gmazzo.dependencies.embedded")
}

val sc = extensions.getByType<StonecutterBuildExtension>()
fun prop(key: String): String = sc.properties[key]

val commonDir = rootProject.file("common")
val fabricDir = rootProject.file("fabric")
val commonProject = project(":${project.name.substringBeforeLast("-")}-common")
val accessWidenerFile = "${sc.current.version}.accesswidener"

version = prop("mod.version")
base.archivesName = "${prop("mod.id")}-fabric"

repositories {
    mavenCentral()
    maven("https://jitpack.io") { name = "JitPack" }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(prop("java").toInt())
    }
}

kotlin {
    jvmToolchain(prop("java").toInt())
}

sourceSets {
    main {
        kotlin.srcDirs(
            fabricDir.resolve("src/main/kotlin"),
            fabricDir.resolve("src/client/kotlin"),
        )
        resources.srcDir(fabricDir.resolve("src/main/resources"))
    }
}

loom {
    fabricModJsonPath = fabricDir.resolve("src/main/resources/fabric.mod.json")
    accessWidenerPath = commonDir.resolve("src/main/resources/accesswideners/$accessWidenerFile")
    mods {
        create(prop("mod.id")) {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${prop("deps.fabric_kotlin")}")

    implementation(commonProject)
}

val resourceProps =
    mapOf(
        "version" to version.toString(),
        "mod_id" to prop("mod.id"),
        "mod_name" to prop("mod.name"),
        "mod_description" to prop("mod.description"),
        "mod_authors" to prop("mod.authors"),
        "mod_license" to prop("mod.license"),
        "fabric_loader_version" to prop("deps.fabric_loader"),
        "minecraft_version" to sc.current.version,
        "java_version" to prop("java"),
        "fabric_api_version" to prop("deps.fabric_api"),
        "fabric_kotlin_version" to prop("deps.fabric_kotlin"),
        "access_widener_file" to accessWidenerFile,
    )

tasks {
    named<ProcessResources>("processResources") {
        inputs.properties(resourceProps)
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
            expand(resourceProps)
        }
        exclude("accesswideners/*.accesswidener")
        from(commonDir.resolve("src/main/resources/accesswideners/$accessWidenerFile")) {
            into("accesswideners")
        }
        exclude("META-INF/**")
    }

    named<Jar>("jar") {
        from(commonProject.sourceSets["main"].output) {
            exclude("META-INF/accesstransformer.cfg", "accesswideners/*.accesswidener")
        }
    }
}
