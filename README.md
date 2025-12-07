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
2. Install NeoForge 21.1.216 or higher
3. Place the compiled JAR in your `mods` folder
4. Launch Minecraft and create a new world

## Development

### Initial Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/let-value/terrasect.git
   cd terrasect
   ```

2. **Generate IDE project files**:
   
   For IntelliJ IDEA:
   ```bash
   ./gradlew idea
   ```
   
   For Eclipse:
   ```bash
   ./gradlew eclipse
   ```

3. **Import the project** into your IDE as a Gradle project.
   
   Note: VS Code users can open the project directly - the Java extension will detect the Gradle project automatically.

### Accessing Minecraft Source Code

The NeoForge ModDev plugin automatically decompiles Minecraft source code with Parchment mappings for better readability:

- **Source location**: After running any Gradle task, decompiled sources are available in your IDE
- **Mappings**: Uses Parchment mappings for human-readable parameter names and javadocs
- **Navigation**: You can navigate to Minecraft classes directly from your IDE (Ctrl+Click on class names)

To manually trigger source decompilation:
```bash
./gradlew prepareRuns
```

### Running the Mod

#### In-Game Testing

Run the mod in a development environment:

**Client (with GUI)**:
```bash
./gradlew runClient
```

**Server (no GUI)**:
```bash
./gradlew runServer
```

These tasks will:
- Launch Minecraft with your mod loaded
- Enable hot-swapping for faster iteration (in some IDEs)
- Provide debug logging for development
- Create working directories in `run/client`, `run/server`, etc.

#### IDE Run Configurations

After importing the project in your IDE, you'll have Gradle-based run configurations:
- **runClient** - Launch Minecraft client with mod
- **runServer** - Launch dedicated server with mod

These configurations are automatically created by IntelliJ IDEA from the Gradle `runs` configuration. In Eclipse, you can run these tasks from the Gradle Tasks view.

### Testing

#### Unit Tests

Run unit tests:
```bash
./gradlew test
```

Unit tests are located in `src/test/java/` and test core logic without requiring Minecraft to run.

### Debugging

1. **Enable Mixin Debug Output**: Already enabled in `build.gradle`
   - Debug export location: `.mixin.out/`
   - Verbose logging: Enabled by default

2. **Debug Logging**: Check `logs/debug.log` in the run directory for detailed mod behavior

3. **Breakpoint Debugging**: 
   - Use your IDE's debugger with the run configurations
   - Set breakpoints in your mod code or Minecraft source
   - Run debug configuration instead of regular run

### Project Structure

```
terrasect/
├── src/
│   ├── main/
│   │   ├── java/com/terrasect/          # Main mod code
│   │   │   ├── Terrasect.java           # Mod entry point
│   │   │   ├── config/                  # Configuration classes
│   │   │   └── mixin/                   # Mixin classes for worldgen
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── neoforge.mods.toml   # Mod metadata
│   │       └── terrasect.mixins.json    # Mixin configuration
│   ├── test/
│   │   └── java/com/terrasect/          # Unit tests
│   └── generated/                       # Auto-generated resources (gitignored)
├── run/                                  # Game run directories (gitignored)
│   ├── client/                          # Client run workspace
│   ├── server/                          # Server run workspace
│   └── gametest/                        # GameTest workspace
├── build.gradle                          # Build configuration
├── gradle.properties                     # Project properties
└── README.md                             # This file
```

### Common Development Tasks

**Rebuild after changes**:
```bash
./gradlew clean build
```

**Regenerate IDE project files**:
```bash
./gradlew idea  # For IntelliJ IDEA
./gradlew eclipse  # For Eclipse
```

**View all available tasks**:
```bash
./gradlew tasks
```

**Check for dependency updates**:
```bash
./gradlew dependencies
```

## License

MIT

## Author

let-value