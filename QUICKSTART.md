# Quick Start Guide

Get started developing Terrasect in 5 minutes!

## Setup

### 1. Clone and Build
```bash
git clone https://github.com/let-value/terrasect.git
cd terrasect
./gradlew build
```

Wait for dependencies to download (~5-10 minutes first time).

### 2. Open in IDE

**IntelliJ IDEA** (recommended):
```bash
./gradlew idea
```
Then: File → Open → Select `terrasect` folder

**Eclipse**:
```bash
./gradlew eclipse
```
Then: Import → Existing Projects into Workspace

**VS Code**:
Open the `terrasect` folder directly. The Java extension will auto-detect the Gradle project.

### 3. Verify Minecraft Source Access

Open any file in `src/main/java/com/terrasect/mixin/`

Click on `ChunkGenerator` (Ctrl+Click or Cmd+Click)

You should see decompiled Minecraft source code with readable parameter names!

## Run the Mod

### Launch Minecraft Client
```bash
./gradlew runClient
```

This opens Minecraft with your mod loaded.

**Test it**:
1. Create a new world
2. Press F3 to show debug screen
3. Look at "Biome:" field on right side
4. Walk around - should always show "Plains"

### Make a Change

1. **Edit the biome**:
   - Open `src/main/java/com/terrasect/config/TerrasectConfig.java`
   - Change line 30: `private static String targetBiomeId = "minecraft:desert";`

2. **Rebuild**:
   ```bash
   ./gradlew build
   ```

3. **Test again**:
   ```bash
   ./gradlew runClient
   ```
   Create a new world - should be all desert now!

## Run Tests

```bash
./gradlew test
```

See test results in: `build/reports/tests/test/index.html`

## Debug

### In IntelliJ:
1. Set breakpoint in `ChunkGeneratorMixin.onInit()`
2. Click debug icon next to "runClient" configuration
3. Create a world - debugger should stop at your breakpoint

### Check Logs:
- While running: Check console output
- After running: Check `run/client/logs/debug.log`

## Common Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build mod JAR |
| `./gradlew test` | Run unit tests |
| `./gradlew runClient` | Launch Minecraft client |
| `./gradlew runServer` | Launch dedicated server |
| `./gradlew clean` | Clean build artifacts |
| `./gradlew tasks` | Show all available tasks |

## Next Steps

- **Read DEVELOPMENT.md** for detailed development guide
- **Browse Minecraft source** by navigating to Minecraft classes in your IDE
- **Make changes** to the mixin and test them
- **Write tests** in `src/test/java/`

## Troubleshooting

**Build fails?**
```bash
./gradlew clean build --refresh-dependencies
```

**IDE not seeing Minecraft classes?**
```bash
./gradlew prepareRuns
```
Then refresh your Gradle project in IDE.

**Game won't launch?**
Check `run/client/logs/debug.log` for errors.

## Getting Help

- Check DEVELOPMENT.md for detailed info
- Open an issue on GitHub
- Join NeoForge Discord for community help

Happy modding! 🎮
