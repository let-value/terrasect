package terrasect.gametest

import java.nio.file.Files
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.apache.commons.lang3.function.FailableFunction
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionBuilder
import terrasect.definition.RegionRegistry

fun testPreset(dimension: String = "minecraft:overworld", configure: RegionBuilder.() -> Unit) =
  RegionRegistry().apply {
    val root = dimension.substringAfter(':').removePrefix("the_") + "_root"
    setRoot(dimension, root)
    region(root).configure()
  }

fun <T> ClientGameTestContext.withTestWorld(
  seed: String,
  presetId: String = "__disabled__",
  preset: RegionRegistry? = null,
  action: (TestSingleplayerContext) -> T,
): T {
  val previousId = PresetRegistry.forcePresetId
  val previousPreset = PresetRegistry.presets[presetId]
  if (preset != null) PresetRegistry.presets[presetId] = preset
  PresetRegistry.forcePresetId = presetId
  try {
    return worldBuilder()
      .setUseConsistentSettings(false)
      .adjustSettings {
        it.seed = seed
        it.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
      }
      .create()
      .use(action)
  } finally {
    PresetRegistry.forcePresetId = previousId
    if (preset != null) {
      if (previousPreset == null) PresetRegistry.presets.remove(presetId)
      else PresetRegistry.presets[presetId] = previousPreset
    }
  }
}

fun <T> TestSingleplayerContext.onServer(action: (MinecraftServer) -> T): T =
  server.computeOnServer(FailableFunction<MinecraftServer, T, Exception>(action))

data class LocatedStructure(val id: String, val pos: BlockPos)

fun ServerLevel.locateStructure(filter: (String) -> Boolean): LocatedStructure? {
  val holders =
    server
      .registryAccess()
      .lookupOrThrow(Registries.STRUCTURE)
      .listElements()
      .filter { filter(it.unwrapKey().orElseThrow().identifier().toString()) }
      .toList()
  check(holders.isNotEmpty()) { "No registered structures match the locate filter" }
  val found =
    chunkSource.generator.findNearestMapStructure(
      this,
      HolderSet.direct(holders),
      BlockPos(0, 64, 0),
      1000,
      true,
    ) ?: return null
  return LocatedStructure(
    found.second.unwrapKey().orElseThrow().identifier().toString(),
    found.first,
  )
}

fun TestSingleplayerContext.screenshot(
  context: ClientGameTestContext,
  name: String,
  x: Int = 8,
  z: Int = 8,
  dimension: ResourceKey<Level> = Level.OVERWORLD,
  y: Double? = null,
) {
  onServer { server ->
    val level = checkNotNull(server.getLevel(dimension))
    level.getChunk(x shr 4, z shr 4)
    val offset = if (dimension == Level.NETHER) 16.0 else 40.0
    val height = y ?: (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + offset)
    val player = server.playerList.players.first()
    player.setGameMode(GameType.SPECTATOR)
    player.teleportTo(level, x + 0.5, height, z + 0.5, emptySet(), 0f, 90f, false)
  }
  context.waitTicks(20)
  clientLevel.waitForChunksDownload()
  clientLevel.waitForChunksRender()
  val hidden =
    context.computeOnClient(
      FailableFunction<Minecraft, Boolean, Exception> { client ->
        client.gui.hud.isHidden.also { if (!it) client.gui.hud.toggle() }
      }
    )
  try {
    val directory = e2eScreenshotsBase(object {}.javaClass).resolve(name.substringBeforeLast('/'))
    val path =
      context.takeScreenshot(
        TestScreenshotOptions.of(name.substringAfterLast('/')).withDestinationDir(directory)
      )
    check(Files.isRegularFile(path) && Files.size(path) > 0) { "Screenshot was not written: $path" }
  } finally {
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> {
        if (it.gui.hud.isHidden != hidden) it.gui.hud.toggle()
      }
    )
  }
}
