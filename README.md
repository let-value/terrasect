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

The mod uses Mixin to hook into Minecraft's world generation system at the `ChunkGenerator` level. When a chunk generator is created, the mod wraps its `BiomeSource` with a custom implementation that always returns the configured biome (Plains by default) for any coordinate in the world.

### Technical Details

Key components:
- **ChunkGeneratorMixin**: Intercepts chunk generator initialization and replaces the biome source
- **SingleBiomeWrapper**: A custom BiomeSource that returns only the configured biome for all coordinates
- **TerrasectConfig**: Configuration class that specifies which biome to use
- **Terrasect**: Main mod class that initializes the mod

The mixin operates at world generation time, ensuring that:
1. All chunks generate with the same biome
2. The biome affects terrain generation, structure spawning, mob spawning, and weather
3. The change is seamless and works with vanilla world generation

### Biome Examples

Here are some interesting biomes you can try:

**Survival Friendly:**
- `minecraft:plains` - Default, good for starting out
- `minecraft:forest` - Lots of trees
- `minecraft:savanna` - Acacia trees and villages

**Challenging:**
- `minecraft:desert` - Hot and dry, limited resources
- `minecraft:ice_spikes` - Extremely cold with unique terrain
- `minecraft:deep_dark` - Dark, dangerous, and full of sculk

**Unique:**
- `minecraft:mushroom_fields` - Peaceful, no hostile mobs
- `minecraft:cherry_grove` - Beautiful pink cherry trees
- `minecraft:badlands` - Colorful terracotta landscape

**Extreme:**
- `minecraft:basalt_deltas` - Nether biome (if used in overworld)
- `minecraft:warped_forest` - Another nether biome option
- `minecraft:the_end` - End dimension biome

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