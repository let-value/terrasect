# Development Guide for Terrasect

This guide provides detailed information for developers working on the Terrasect mod.

## Prerequisites

- **Java 21 JDK** (Temurin, Adoptium, or Oracle)
- **Git** for version control
- **IDE** (IntelliJ IDEA recommended, Eclipse or VS Code also supported)
- **Gradle** (included via wrapper, no separate installation needed)

## First-Time Setup

### 1. Clone and Build

```bash
git clone https://github.com/let-value/terrasect.git
cd terrasect
./gradlew build
```

This will:
- Download all dependencies (including Minecraft and NeoForge)
- Decompile Minecraft source with Parchment mappings
- Compile the mod
- Run unit tests
- Generate the mod JAR

### 2. IDE Setup

#### IntelliJ IDEA (Recommended)

1. Generate run configurations:
   ```bash
   ./gradlew genIntellijRuns
   ```

2. Open IntelliJ IDEA and select "Open" (not "Import Project")

3. Navigate to the `terrasect` folder and open it

4. Wait for Gradle import to complete

5. Run configurations will appear in the top-right dropdown:
   - `runClient` - Launch game client
   - `runServer` - Launch dedicated server
   - `runData` - Generate data files
   - `runGameTestServer` - Run game tests

#### Eclipse

1. Generate Eclipse project files:
   ```bash
   ./gradlew eclipse
   ```

2. Open Eclipse and select "File" → "Import" → "Existing Projects into Workspace"

3. Browse to the `terrasect` folder and import

#### VS Code

1. Install the "Extension Pack for Java" extension

2. Generate run configurations:
   ```bash
   ./gradlew genVSCodeRuns
   ```

3. Open the `terrasect` folder in VS Code

4. Run configurations will be available in the Run and Debug panel

## Working with Minecraft Source Code

### Accessing Decompiled Source

The NeoForge ModDev plugin automatically provides access to Minecraft's decompiled source code:

1. **Navigate to Minecraft classes**: 
   - In your IDE, Ctrl+Click (or Cmd+Click on Mac) on any Minecraft class name
   - Example: Click on `ChunkGenerator` in your mixin to view its source

2. **View with better names**:
   - Parchment mappings provide readable parameter names
   - Example: Instead of `func_12345_a(int p_12346_)` you see `generateChunk(int chunkX)`

3. **Search Minecraft code**:
   - Use your IDE's "Find in Path" or "Search Everywhere" feature
   - Minecraft sources are included in search results

### Understanding Minecraft's World Generation

Key classes to explore:
- `ChunkGenerator` - Main class for chunk generation
- `BiomeSource` - Provides biomes for coordinates
- `FixedBiomeSource` - Returns a single biome (used by Terrasect)
- `Biome` - Defines biome properties
- `Biomes` - Registry of vanilla biomes

## Testing Your Changes

### Unit Tests

Located in `src/test/java/`, unit tests validate logic without starting Minecraft.

**Run all tests**:
```bash
./gradlew test
```

**Run specific test class**:
```bash
./gradlew test --tests "com.terrasect.config.TerrasectConfigTest"
```

**Run with verbose output**:
```bash
./gradlew test --info
```

### Manual Testing (In-Game)

1. **Launch the client**:
   ```bash
   ./gradlew runClient
   ```

2. **Create a new world**:
   - Select "Create New World"
   - Choose any world type (the mod affects all)
   - Create and enter the world

3. **Verify the mod is working**:
   - Press F3 to open debug screen
   - Look at "Biome:" on the right side
   - Walk around - it should always show the same biome
   - Check logs in `run/client/logs/debug.log` for Terrasect messages

4. **Test different biomes**:
   - Edit `src/main/java/com/terrasect/config/TerrasectConfig.java`
   - Change `targetBiomeId` to a different biome
   - Rebuild: `./gradlew build`
   - Relaunch and create a new world

### Debugging

#### Enable Debug Logging

Already enabled in `build.gradle`:
- Mixin debug export: `.mixin.out/`
- Verbose mixin logging
- Registration markers
- Debug console level

#### Using IDE Debugger

1. Set breakpoints in your code:
   - In `ChunkGeneratorMixin.onInit()` to see when biome source is replaced
   - In `TerrasectConfig.getTargetBiome()` to see biome lookups

2. Launch in debug mode:
   - In IntelliJ: Click the bug icon next to "runClient"
   - In Eclipse: Right-click run configuration → "Debug As"
   - In VS Code: Use Debug panel with client configuration

3. Step through code:
   - Step into Minecraft methods to understand vanilla behavior
   - Watch variables to see biome values

#### Common Issues

**"Mod not loading"**:
- Check `logs/debug.log` for errors
- Verify `terrasect.mixins.json` is in resources
- Ensure mixin annotation processor ran (check for `terrasect.refmap.json` in build output)

**"Changes not reflecting"**:
- Run `./gradlew clean build` to rebuild completely
- Restart the client/server
- For mixin changes, always rebuild

**"Cannot find symbol" errors**:
- Run `./gradlew prepareRuns` to regenerate mappings
- Refresh Gradle project in IDE
- Invalidate caches and restart IDE

## Making Changes

### Adding New Features

1. **Plan your change**:
   - Identify which Minecraft classes/methods to hook
   - Determine if you need a mixin or event handler

2. **Write code**:
   - Follow existing code style
   - Add appropriate logging
   - Comment complex logic

3. **Test**:
   - Write unit tests if possible
   - Test in-game thoroughly
   - Check logs for warnings/errors

4. **Document**:
   - Update README.md if user-facing
   - Add code comments for maintainability
   - Update DEVELOPMENT.md if changing dev workflow

### Mixin Best Practices

1. **Use specific injection points**:
   ```java
   @Inject(method = "<init>", at = @At("RETURN"))
   ```

2. **Add descriptive names**:
   ```java
   private void onInit(...) { // Better than terrasect$init
   ```

3. **Handle errors gracefully**:
   ```java
   try {
       // Mixin logic
   } catch (Exception e) {
       LOGGER.error("Terrasect mixin failed", e);
   }
   ```

4. **Test with other mods**:
   - Mixins can conflict with other mods
   - Use specific targeting and priority if needed

## Build System Details

### Gradle Tasks

Common tasks:
- `./gradlew build` - Full build with tests
- `./gradlew assemble` - Build without tests
- `./gradlew clean` - Remove build artifacts
- `./gradlew test` - Run unit tests
- `./gradlew runClient` - Launch client
- `./gradlew runServer` - Launch server
- `./gradlew runData` - Generate data
- `./gradlew runGameTestServer` - Run game tests
- `./gradlew tasks` - List all available tasks

### Dependencies

Core dependencies (from `build.gradle`):
- **NeoForge**: Mod loader and API
- **Parchment**: Better parameter names and javadocs
- **Mixin**: Bytecode manipulation for hooks
- **JUnit 5**: Unit testing framework
- **Mockito**: Mocking for tests

### Source Sets

- `src/main/java` - Main mod code
- `src/main/resources` - Mod resources (metadata, mixins config)
- `src/test/java` - Unit tests
- `src/test/resources` - Test resources
- `src/generated/resources` - Auto-generated resources (gitignored)

## Troubleshooting

### Gradle Issues

**"Could not resolve dependencies"**:
```bash
./gradlew clean --refresh-dependencies
```

**"Task failed with an exception"**:
```bash
./gradlew clean build --stacktrace
```

### IDE Issues

**"Cannot resolve Minecraft classes"**:
1. Refresh Gradle project
2. Run `./gradlew prepareRuns`
3. Rebuild project in IDE

**"Run configurations not showing"**:
1. Re-run `./gradlew genIntellijRuns` (or Eclipse/VSCode equivalent)
2. Refresh Gradle project
3. Restart IDE

### Runtime Issues

**"Mixin failed to apply"**:
- Check `.mixin.out/` for exported mixins
- Verify target class exists (check Minecraft version)
- Review mixin syntax

**"ClassNotFoundException"**:
- Dependency not included in mod JAR
- Use `implementation` instead of `compileOnly` in `build.gradle`

## Resources

- [NeoForge Documentation](https://docs.neoforged.net/)
- [Mixin Documentation](https://github.com/SpongePowered/Mixin/wiki)
- [Parchment Documentation](https://parchmentmc.org/)
- [Minecraft Wiki](https://minecraft.wiki/)
- [Official Minecraft Mappings](https://github.com/ParchmentMC/Parchment)

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes with good commit messages
4. Test thoroughly
5. Submit a pull request

## Getting Help

- **Issues**: Open an issue on GitHub
- **Discussions**: Use GitHub Discussions
- **NeoForge Discord**: Join for mod development help
