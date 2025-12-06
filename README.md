# Terrasect

A Minecraft mod for NeoForge and Forge 1.21+ that hooks into world generation and makes the entire world a single biome.

## Features

- **Single Biome World Generation**: Transforms the entire Minecraft world to use only one biome (Plains by default)
- **World Generation Hook**: Uses mixins to intercept the `ChunkGenerator` and replace the biome source
- **NeoForge/Forge Compatible**: Built for Minecraft 1.21.1+ with NeoForge (also compatible with Forge due to NeoForge's compatibility layer)
- **Configurable**: Can be configured to use any biome (edit the config class to change the target biome)

## Configuration

The mod uses Plains biome by default. To change the biome:

1. The target biome is set in `TerrasectConfig.java` (default: "minecraft:plains")
2. You can modify the `targetBiomeId` field to use any valid biome identifier
3. Examples of valid biome IDs:
   - `minecraft:desert`
   - `minecraft:jungle`
   - `minecraft:ice_spikes`
   - `minecraft:mushroom_fields`
   - `minecraft:deep_dark`

Future versions may include a configuration file for easier customization without code changes.

## How It Works

The mod uses Mixin to hook into Minecraft's world generation system at the `ChunkGenerator` level. When a chunk generator is created, the mod wraps its `BiomeSource` with a custom implementation that always returns the Plains biome for any coordinate in the world.

Key components:
- **ChunkGeneratorMixin**: Intercepts chunk generator initialization and replaces the biome source
- **SingleBiomeWrapper**: A custom BiomeSource that returns only Plains biome for all coordinates
- **Terrasect**: Main mod class that initializes the mod

## Building

To build the mod, you need:
- Java 21 or higher
- Gradle (included via wrapper)

Run:
```bash
./gradlew build
```

The compiled mod JAR will be in `build/libs/`.

## Installation

1. Install Minecraft 1.21.1
2. Install NeoForge 21.1.72 or higher
3. Place the compiled JAR in your `mods` folder
4. Launch Minecraft and create a new world

## Development

The project structure:
- `src/main/java/com/terrasect/` - Main mod code
  - `Terrasect.java` - Main mod class
  - `mixin/ChunkGeneratorMixin.java` - Mixin for world generation
  - `worldgen/` - World generation components
- `src/main/resources/` - Resources and configuration
  - `META-INF/neoforge.mods.toml` - Mod metadata
  - `terrasect.mixins.json` - Mixin configuration

## License

MIT

## Author

let-value