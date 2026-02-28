# Terrasect: Minecraft Mod (Multiloader)

## Project Overview
Terrasect is a multiloader Minecraft mod developed for version **1.21.11**, written in **Kotlin 2.3.0**. It follows a standard multi-module architecture to support both **Fabric** and **NeoForge** loaders from a shared codebase.

- **Primary Language:** Kotlin (with Java 21)
- **Target Platform:** Minecraft 1.21.11
- **Architecture:** Shared common logic with loader-specific implementation modules.

## Architecture & Modules
- **`common/`**: Contains the core logic shared across all loaders.
  - `terrasect.Terrasect`: Main initialization class.
  - `terrasect.definition`: Region-based registry and generation strategies (e.g., Hex, Voronoi).
  - `terrasect.sdf`: Likely contains Signed Distance Field logic for world generation.
- **`fabric/`**: Fabric-specific implementation and entrypoints.
  - `terrasect.TerrasectFabric`: Implements `ModInitializer`.
- **`neoforge/`**: NeoForge-specific implementation and entrypoints.
  - `terrasect.TerrasectNeoForge`: Main `@Mod` class, handling NeoForge event buses.
- **`compat/c2me/`**: A Git submodule pointing to [C2ME-fabric](https://github.com/RelativityMC/C2ME-fabric.git), used for performance enhancements.

## Building and Running
The project uses **Gradle** as its build system.

- **Build all modules:** `./gradlew build`
- **Build specific loader:** `./gradlew :fabric:build` or `./gradlew :neoforge:build`
- **Run Fabric Client:** `./gradlew :fabric:runClient`
- **Run NeoForge Client:** `./gradlew :neoforge:runClient`
- **Run Fabric Server:** `./gradlew :fabric:runServer`
- **Run NeoForge Server:** `./gradlew :neoforge:runServer`

## Development Conventions
- **Code Style:** Enforced by **Spotless**.
  - **Kotlin:** `ktfmt` with Google style.
  - **Java:** `google-java-format`.
  - **Format Check:** `./gradlew spotlessCheck`
  - **Apply Formatting:** `./gradlew spotlessApply`
- **Testing:** The project supports snapshot-based testing.
  - **Update Snapshots:** Use the `-PupdateSnapshots=true` flag during testing to update reference snapshots.
- **Mod IDs and Constants:** Centralized in `terrasect.Constants` within the `common` module.

## Key Versions (from `gradle.properties`)
- **Kotlin:** 2.3.0
- **Java:** 21
- **Minecraft:** 1.21.11
- **Fabric Loader:** 0.18.4
- **NeoForge Loader:** 21.11.36-beta
- **Kotlin for Forge:** 6.0.0
