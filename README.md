# Terrasect

A multiloader Minecraft mod for version 1.21.11, written in Kotlin.

## Project Structure

```
terrasect/
├── common/          # Shared code between loaders
│   └── src/main/kotlin/
├── fabric/          # Fabric-specific code
│   └── src/
│       ├── main/kotlin/      # Main Fabric entrypoint
│       └── client/kotlin/    # Client-only code
├── neoforge/        # NeoForge-specific code
│   └── src/main/kotlin/
├── build.gradle.kts # Root build file
├── settings.gradle.kts
└── gradle.properties
```

## Building

To build all modules:

```bash
./gradlew build
```

To build specific modules:

```bash
./gradlew :fabric:build
./gradlew :neoforge:build
```

## Running

### Fabric

```bash
./gradlew :fabric:runClient
./gradlew :fabric:runServer
```

### NeoForge

```bash
./gradlew :neoforge:runClient
./gradlew :neoforge:runServer
```

## Development

### Adding Shared Code

Add your shared code to the `common` module under `common/src/main/kotlin/`. This code will be included in both Fabric
and NeoForge builds.

### Loader-Specific Code

- **Fabric**: Use Fabric API and `fabric.mod.json` entrypoints
- **NeoForge**: Use NeoForge events and `neoforge.mods.toml` configuration

### Kotlin Support

- **Fabric**: Uses `fabric-language-kotlin`
- **NeoForge**: Uses `kotlinforforge-neoforge`

## Configuration

All shared configuration is in the root `gradle.properties` file:

- Minecraft version: `1.21.11`
- Kotlin version: `2.3.0`
- Fabric Loader: `0.18.4`
- NeoForge: `21.11.36-beta`
- Kotlin for Forge: `5.8.0`

## License

All Rights Reserved
