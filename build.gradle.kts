plugins {
    java
    idea
    id("org.jetbrains.kotlin.jvm") version "2.3.0" apply false
}

allprojects {
    group = property("mod_group_id").toString()
    version = property("mod_version").toString()
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

// Wrapper task configuration
tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
}
