package terrasect

import de.skuzzle.test.snapshots.SnapshotFile
import de.skuzzle.test.snapshots.SnapshotFile.SnapshotHeader
import java.nio.file.Path
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.presets.Presets

private val LOGGER = LoggerFactory.getLogger("WorldDigestGameTest")

private const val SEED = "seed"

private val PROBE_LOCATIONS = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private val GAME_TEST_MODULE_DIR: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.also { LOGGER.info("[WorldDigest] module dir = {}", it) }
}

private val GAME_TEST_SCREENSHOTS_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("build/gametest-screenshots")
}

private val GAME_TEST_RESOURCES_BASE: Path by lazy {
  GAME_TEST_MODULE_DIR.resolve("src/gametest/resources/terrasect")
}

private fun snapshotPath(testSimpleName: String): Path =
  GAME_TEST_RESOURCES_BASE.resolve("${testSimpleName}_snapshots/columns.snapshot")

private fun parseColumnsSnapshot(path: Path): Map<String, Pair<Int, Int>> {
  val text = SnapshotFile.fromSnapshotFile(path).snapshot()
  return text
    .lineSequence()
    .drop(1)
    .filter { it.isNotBlank() }
    .associate { line ->
      val parts = line.split(",")
      "${parts[0]},${parts[1]}" to (parts[2].toInt() to parts[3].toInt())
    }
}

private fun diffAgainstVanilla(terrasectSimpleName: String) {
  val vanillaPath = snapshotPath(VanillaWorldDigestTest::class.simpleName!!)
  val terrasectPath = snapshotPath(terrasectSimpleName)

  if (!vanillaPath.toFile().exists()) {
    LOGGER.warn("[WorldDigest] Vanilla snapshot not found at {} — skipping diff", vanillaPath)
    return
  }

  val vanilla = parseColumnsSnapshot(vanillaPath)
  val terrasect = parseColumnsSnapshot(terrasectPath)

  val diffText = buildString {
    append("x,z,vanilla_floor,vanilla_surface,terrasect_floor,terrasect_surface\n")
    for ((key, tVal) in terrasect) {
      val vVal = vanilla[key]
      if (vVal != tVal) {
        append("$key,${vVal?.first ?: -1},${vVal?.second ?: -1},${tVal.first},${tVal.second}\n")
      }
    }
  }

  val diffLines = diffText.lines().count { it.isNotBlank() } - 1
  if (diffLines <= 0) {
    LOGGER.warn(
      "[WorldDigest] Terrasect produced NO terrain differences vs vanilla — is the preset active?"
    )
  } else {
    LOGGER.info("[WorldDigest] {} column(s) differ vs vanilla", diffLines)
  }

  val diffSnapshotPath =
    GAME_TEST_RESOURCES_BASE.resolve("${terrasectSimpleName}_snapshots/diff_vs_vanilla.snapshot")
  val header =
    SnapshotHeader.fromMap(
      mapOf(
        SnapshotHeader.TEST_CLASS to TerrasectWorldDigestTest::class.qualifiedName!!,
        SnapshotHeader.TEST_METHOD to "runTest",
        SnapshotHeader.SNAPSHOT_NUMBER to "1",
        SnapshotHeader.SNAPSHOT_NAME to "diff_vs_vanilla",
        SnapshotHeader.DYNAMIC_DIRECTORY to "true",
      )
    )
  SnapshotFile.of(header, diffText).writeTo(diffSnapshotPath)
  LOGGER.info("[WorldDigest] Diff snapshot written to {}", diffSnapshotPath)
}

private fun snapshotColumns(
  columns: Map<String, Pair<Int, Int>>,
  snapshotDir: Path,
  testClassName: String,
) {
  val text = buildString {
    append("x,z,ocean_floor,world_surface\n")
    for ((key, value) in columns) {
      append("$key,${value.first},${value.second}\n")
    }
  }

  val snapshotFile = snapshotDir.resolve("columns.snapshot")
  val update = System.getProperty("updateSnapshots") != null

  if (update || !snapshotFile.toFile().exists()) {
    snapshotDir.toFile().mkdirs()
    val header =
      SnapshotHeader.fromMap(
        mapOf(
          SnapshotHeader.TEST_CLASS to testClassName,
          SnapshotHeader.TEST_METHOD to "runTest",
          SnapshotHeader.SNAPSHOT_NUMBER to "0",
          SnapshotHeader.SNAPSHOT_NAME to "columns",
          SnapshotHeader.DYNAMIC_DIRECTORY to "true",
        )
      )
    SnapshotFile.of(header, text).writeTo(snapshotFile)
    val verb = if (update) "updated" else "created initially — commit it to SCM"
    LOGGER.info("[WorldDigest] Snapshot {} at {}", verb, snapshotFile)
  } else {
    val stored = SnapshotFile.fromSnapshotFile(snapshotFile).snapshot()
    if (stored != text) {
      throw AssertionError(
        "Column snapshot mismatch at $snapshotFile\n" +
          "Rerun with -PupdateSnapshots to accept the new terrain, or investigate the diff:\n" +
          buildUnifiedDiff(stored, text)
      )
    }
    LOGGER.info("[WorldDigest] Snapshot matches: {}", snapshotFile)
  }
}

private fun buildUnifiedDiff(expected: String, actual: String): String {
  val exp = expected.lines()
  val act = actual.lines()
  val sb = StringBuilder()
  var matches = 0
  var diffs = 0
  for (i in 0 until maxOf(exp.size, act.size)) {
    val e = exp.getOrNull(i)
    val a = act.getOrNull(i)
    when {
      e == null -> {
        diffs++
        if (diffs <= 50) sb.appendLine("+ $a")
      }
      a == null -> {
        diffs++
        if (diffs <= 50) sb.appendLine("- $e")
      }
      e != a -> {
        diffs++
        if (diffs <= 50) {
          sb.appendLine("- $e")
          sb.appendLine("+ $a")
        }
      }
      else -> matches++
    }
  }
  if (diffs > 50) sb.appendLine("... (${diffs - 50} more differing lines)")
  sb.appendLine("Summary: $diffs differing line(s), $matches matching line(s)")
  return sb.toString()
}

@Suppress("UnstableApiUsage")
private fun computeWorldDigest(
  context: ClientGameTestContext,
  label: String,
  testSimpleName: String,
  testClassName: String,
) {
  val screenshotsDir = GAME_TEST_SCREENSHOTS_BASE.resolve(testSimpleName)
  val snapshotDir = GAME_TEST_RESOURCES_BASE.resolve("${testSimpleName}_snapshots")
  val game =
    context
      .worldBuilder()
      .setUseConsistentSettings(false)
      .adjustSettings { settings ->
        settings.seed = SEED
        settings.gameMode = WorldCreationUiState.SelectedGameMode.CREATIVE
      }
      .create()

  try {

    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        server.playerList.players[0].teleportTo(0.0, 400.0, 0.0)
      }
    )

    context.waitTicks(10)

    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client ->
        client.player?.let { player ->
          client.player?.xRot = 25f
          client.player?.yRot = 0f
          player.abilities.mayfly = true
          player.abilities.flying = true
          player.onUpdateAbilities()
        }
      }
    )

    game.clientWorld.waitForChunksRender()

    val allColumns = LinkedHashMap<String, Pair<Int, Int>>()

    for ((x, z) in PROBE_LOCATIONS) {
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(x + 8.0, 85.0, z + 8.0)
        }
      )

      context.waitTicks(60)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()

      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          val blockCounts = LinkedHashMap<String, Int>()
          val biomeCounts = LinkedHashMap<String, Int>()
          val surfaces = mutableListOf<Int>()
          for (bx in x until x + 16) {
            for (bz in z until z + 16) {
              val oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz)
              val worldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
              allColumns["$bx,$bz"] = oceanFloor to worldSurface
              surfaces.add(worldSurface)
              val surfaceY = worldSurface - 1
              val block = level.getBlockState(BlockPos(bx, surfaceY, bz)).block
              val blockName = BuiltInRegistries.BLOCK.getKey(block).toString()
              blockCounts[blockName] = (blockCounts[blockName] ?: 0) + 1
              val biome =
                level
                  .getBiome(BlockPos(bx, worldSurface, bz))
                  .unwrapKey()
                  .map { it.toString() }
                  .orElse("unknown")
              biomeCounts[biome] = (biomeCounts[biome] ?: 0) + 1
            }
          }
          val topBlocks =
            blockCounts.entries
              .sortedByDescending { it.value }
              .take(3)
              .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
          val topBiomes =
            biomeCounts.entries
              .sortedByDescending { it.value }
              .take(2)
              .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
          LOGGER.info(
            "[WorldDigest] probe ({},{}) surface={}-{} blocks=[{}] biomes=[{}]",
            x,
            z,
            surfaces.min(),
            surfaces.max(),
            topBlocks,
            topBiomes,
          )
        }
      )

      context.takeScreenshot(
        TestScreenshotOptions.of("probe_${x}_${z}").withDestinationDir(screenshotsDir)
      )
    }

    snapshotColumns(allColumns, snapshotDir, testClassName)
    LOGGER.info("[WorldDigest] {} complete", label)
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
object VanillaWorldDigestTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.forcePresetId = "__disabled__"
    try {
      computeWorldDigest(
        context,
        "vanilla",
        testSimpleName = this::class.simpleName!!,
        testClassName = this::class.qualifiedName!!,
      )
    } finally {
      PresetRegistry.forcePresetId = null
    }
  }
}

@Suppress("UnstableApiUsage")
object TerrasectWorldDigestTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    PresetRegistry.forcePresetId = Presets.CLIMATE_DEBUG.toString()
    try {
      computeWorldDigest(
        context,
        "terrasect",
        testSimpleName = this::class.simpleName!!,
        testClassName = this::class.qualifiedName!!,
      )
      diffAgainstVanilla(this::class.simpleName!!)
    } finally {
      PresetRegistry.forcePresetId = null
    }
  }
}
