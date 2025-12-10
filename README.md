# Terrasect

Multi-loader starter for Fabric and NeoForge targeting Minecraft 1.21.1+. The project keeps a shared `common` module for logic and resources, and per-loader entrypoints for Fabric and NeoForge so developers can run and test the mod across ecosystems.

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
   NeoForge compilation is wired in, and you can layer in the full userdev plugin later if you want runtime debugging from the same workspace.

## Development notes
- The build uses official Mojang mappings for Fabric and a lightweight NeoForge dev dependency to keep compilation fast. Game sources and models are available in IDEs via the mapping downloads performed by Loom and the NeoForge dependency.
- `common` exposes a simple `Terrasect.hello()` method with a unit test to validate shared logic; extend this module for gameplay features.
- Gradle 8.10.2 is configured to satisfy Loom 1.8.x requirements.
