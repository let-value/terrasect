# Terrasect

Multi-loader starter for Fabric and NeoForge targeting Minecraft 1.21.x. The project keeps a shared `common` module for logic and resources, and per-loader entrypoints for Fabric and NeoForge so developers can run and test the mod across ecosystems.

## Modules
- `common` – shared code and resources, plus JUnit 5 unit tests.
- `fabric` – Fabric loader entrypoint configured with Fabric Loom and client/server run configs.
- `neoforge` – NeoForge entrypoint wired to the shared code using the NeoForge dev jars for compilation.

## Getting started
1. Install a Java 21 JDK.
2. Refresh dependencies:
   ```
   ./gradlew --version
   ```
3. Run tests and build all loaders:
   ```
   ./gradlew build
   ```
4. Launch a Fabric development client or server:
   ```
   ./gradlew :fabric:runClient
   ./gradlew :fabric:runServer
   ```
5. (Optional) Unpack Minecraft sources into `minecraft/` for browsing:
   ```
   ./gradlew unpackMinecraft
   ```

## Development notes
- The Gradle build currently includes only `common`, `fabric`, and `neoforge` (the `versions/` folder is ignored for now).
- Fabric uses official Mojang mappings via Fabric Loom; NeoForge/common use `net.neoforged.moddev`.
- Gradle wrapper is `9.2.1`.
