package terrasect

import java.nio.file.Path
import kotlin.math.sqrt
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.Heightmap
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory
import terrasect.definition.PresetRegistry
import terrasect.definition.RegionRegistry

private val log = LoggerFactory.getLogger("StructureConstraintGameTest")

private const val SEED = "structure-constraints"
private const val DISABLED_PRESET = "__disabled__"
private const val HIGH_DENSITY_PRESET = "structure_constraint_high_density"
private const val LOCATE_BLOCKED_PRESET = "structure_constraint_locate_blocked"
private const val RUINED_PORTAL_DISTANCE = 385

// Probe positions spaced ~512 blocks apart to sample different vanilla structure grid cells.
// Village spacing is 34 chunks = 544 blocks, so these probes hit distinct grid cells.
private val PROBE_POSITIONS = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private val SCREENSHOTS_BASE: Path by lazy {
  val classesRoot = Path.of(object {}.javaClass.protectionDomain.codeSource.location.toURI())
  classesRoot.parent.parent.parent.parent.resolve("build/gametest-screenshots")
}

private data class LocateResult(val structureId: String?, val pos: BlockPos?) {
  val distance: Int
    get() {
      if (pos == null) return -1
      val dx = pos.x.toLong()
      val dz = pos.z.toLong()
      return sqrt((dx * dx + dz * dz).toDouble()).toInt()
    }
}

private fun registerHighDensityPreset() {
  PresetRegistry.presets[HIGH_DENSITY_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures {
        // spacing=8 chunks (128 blocks), separation=4 chunks — ~18x denser than vanilla villages.
        // frequency=1.0 ensures every eligible grid cell actually places a structure.
        spacing(8)
        separation(4)
        frequency(1.0f)
      }
    }
}

private fun registerLocateBlockedPreset() {
  PresetRegistry.presets[LOCATE_BLOCKED_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { blockMods("minecraft") }
    }
}

private fun configureAerialCamera(client: Minecraft) {
  client.player?.let { player ->
    player.xRot = 60f
    player.yRot = 0f
    player.abilities.mayfly = true
    player.abilities.flying = true
    player.onUpdateAbilities()
  }
}

@Suppress("UnstableApiUsage")
private fun runStructureProbes(context: ClientGameTestContext, label: String, screenshotDir: Path) {
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
        server.playerList.players[0].teleportTo(8.0, 100.0, 8.0)
      }
    )
    context.waitTicks(10)
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
    )
    context.waitTicks(5)
    game.clientWorld.waitForChunksRender()

    for ((x, z) in PROBE_POSITIONS) {
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(x + 8.0, 100.0, z + 8.0)
        }
      )
      context.waitTicks(60)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()

      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          val level = server.overworld()
          val blockCounts = LinkedHashMap<String, Int>()
          val surfaces = mutableListOf<Int>()
          for (bx in x until x + 16) {
            for (bz in z until z + 16) {
              val s = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
              surfaces.add(s)
              val block = level.getBlockState(BlockPos(bx, s - 1, bz)).block
              val name = BuiltInRegistries.BLOCK.getKey(block).toString()
              blockCounts[name] = (blockCounts[name] ?: 0) + 1
            }
          }
          val top3 =
            blockCounts.entries
              .sortedByDescending { it.value }
              .take(3)
              .joinToString { "${it.key.substringAfterLast(':')}×${it.value}" }
          log.info(
            "[{}] probe ({},{}) surface={}-{} blocks=[{}]",
            label,
            x,
            z,
            surfaces.min(),
            surfaces.max(),
            top3,
          )
        }
      )

      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client -> configureAerialCamera(client) }
      )
      context.waitTicks(5)
      context.takeScreenshot(
        TestScreenshotOptions.of("probe_${x}_${z}").withDestinationDir(screenshotDir)
      )
    }

    log.info("[{}] done — screenshots in {}", label, screenshotDir)
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
private fun runLocateTest(
  context: ClientGameTestContext,
  label: String,
  screenshotDir: Path? = null,
  structureFilter: (String) -> Boolean = { "village" in it },
  screenshotName: String = "located_village",
): LocateResult {
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
    var result: LocateResult? = null
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val generator = level.getChunkSource().getGenerator()
        val structReg = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val holders =
          structReg
            .listElements()
            .filter { h ->
              h.unwrapKey().map { structureFilter(it.identifier().toString()) }.orElse(false)
            }
            .toList()
        if (holders.isEmpty()) {
          log.warn("[{}] no matching structure holders in registry", label)
          result = LocateResult(null, null)
          return@FailableConsumer
        }
        val holderSet = HolderSet.direct(holders)
        val found =
          generator.findNearestMapStructure(level, holderSet, BlockPos(0, 64, 0), 1000, true)
        if (found != null) {
          val id = found.second.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
          val pos = found.first
          log.info(
            "[{}] found type={} at ({},{}) distance={}",
            label,
            id,
            pos.x,
            pos.z,
            sqrt((pos.x.toLong().let { it * it } + pos.z.toLong().let { it * it }).toDouble())
              .toInt(),
          )
          result = LocateResult(id, pos)
        } else {
          log.info("[{}] not found within radius=1000", label)
          result = LocateResult(null, null)
        }
      }
    )
    val locateResult = result ?: LocateResult(null, null)
    if (screenshotDir != null && locateResult.pos != null) {
      val pos = locateResult.pos
      game.server.runOnServer(
        FailableConsumer<MinecraftServer, Exception> { server ->
          server.playerList.players[0].teleportTo(pos.x + 0.5, 100.0, pos.z + 0.5)
        }
      )
      context.waitTicks(10)
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          client.player?.let { player ->
            player.xRot = 90f
            player.yRot = 0f
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
          }
        }
      )
      context.waitTicks(5)
      game.clientWorld.waitForChunksDownload()
      game.clientWorld.waitForChunksRender()
      context.takeScreenshot(
        TestScreenshotOptions.of(screenshotName).withDestinationDir(screenshotDir)
      )
      log.info("[{}] screenshot saved to {}", label, screenshotDir)
    }
    return locateResult
  } finally {
    game.close()
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintVanillaGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    PresetRegistry.forcePresetId = DISABLED_PRESET
    try {
      runStructureProbes(
        context,
        "vanilla",
        SCREENSHOTS_BASE.resolve("StructureConstraintVanillaTest"),
      )
    } finally {
      PresetRegistry.forcePresetId = null
    }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintHighDensityGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    registerHighDensityPreset()
    PresetRegistry.forcePresetId = HIGH_DENSITY_PRESET
    try {
      runStructureProbes(
        context,
        "high_density",
        SCREENSHOTS_BASE.resolve("StructureConstraintHighDensityTest"),
      )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(HIGH_DENSITY_PRESET)
    }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintLocateGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val locateScreenshotsBase = SCREENSHOTS_BASE.resolve("StructureConstraintLocateTest")

    // Vanilla baseline: locate finds nearest village with no Terrasect constraints active.
    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", locateScreenshotsBase.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    // High-density: tighter grid (spacing=8, separation=4) produces a closer village than vanilla
    // for this seed — asserted below as highDensityResult.distance < vanillaResult.distance.
    registerHighDensityPreset()
    PresetRegistry.forcePresetId = HIGH_DENSITY_PRESET
    val highDensityResult: LocateResult
    try {
      highDensityResult =
        runLocateTest(context, "high_density", locateScreenshotsBase.resolve("high_density"))
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(HIGH_DENSITY_PRESET)
    }

    // Selection-blocked: all minecraft structures denied by region selection constraints.
    // findNearestMapStructure must return null, proving the locate mixin selection filter works.
    // No screenshot: the result is null by design.
    registerLocateBlockedPreset()
    PresetRegistry.forcePresetId = LOCATE_BLOCKED_PRESET
    val blockedResult: LocateResult
    try {
      blockedResult = runLocateTest(context, "blocked")
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(LOCATE_BLOCKED_PRESET)
    }

    log.info(
      " summary: vanilla={} dist={} | high_density={} dist={} | blocked={}",
      vanillaResult.structureId,
      vanillaResult.distance,
      highDensityResult.structureId,
      highDensityResult.distance,
      if (blockedResult.pos == null) "null (correct)"
      else "FOUND (unexpected: ${blockedResult.structureId})",
    )

    assertNotNull(
      vanillaResult.pos,
      "vanilla should find a village within radius=200 but got null — " +
        "check that village structures exist in the test world registry.",
    )
    assertNotNull(
      highDensityResult.pos,
      "high-density should find a village within radius=200 but got null — " +
        "placement-only preset must not suppress locate results (no selection constraint set).",
    )
    assertTrue(
      vanillaResult.structureId?.startsWith("minecraft:village_") == true,
      "vanilla should return a village-type structure id, got ${vanillaResult.structureId}",
    )
    assertTrue(
      highDensityResult.structureId?.startsWith("minecraft:village_") == true,
      "high-density should return a village-type structure id, got ${highDensityResult.structureId}",
    )
    assertEquals(
      vanillaResult.structureId,
      highDensityResult.structureId,
      "vanilla and high-density should locate the same closest village type for the fixed seed",
    )
    assertTrue(
      highDensityResult.distance < vanillaResult.distance,
      "high-density preset should find a closer village than vanilla " +
        "(vanilla dist=${vanillaResult.distance}, high_density dist=${highDensityResult.distance})",
    )
    assertTrue(
      vanillaResult.distance >= 0,
      "vanilla distance should be non-negative but was ${vanillaResult.distance}",
    )
    assertNull(
      blockedResult.pos,
      "selection-blocked preset (blockMods=minecraft) must not find any village " +
        "within radius=200, but found ${blockedResult.structureId} at ${blockedResult.pos} — " +
        "the locate mixin selection filter did not apply.",
    )
  }
}

// --- Phase 3: per-level allow/ban coverage ---

private const val BAN_BY_MOD_PRESET = "structure_constraint_ban_by_mod"
private const val ALLOW_BY_MOD_PRESET = "structure_constraint_allow_by_mod"
private const val BAN_BY_TAG_PRESET = "structure_constraint_ban_by_tag"
private const val ALLOW_BY_TAG_PRESET = "structure_constraint_allow_by_tag"
private const val VILLAGE_TAG = "minecraft:village"

private fun registerBanByModPreset() {
  PresetRegistry.presets[BAN_BY_MOD_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { blockMods("minecraft") }
    }
}

private fun registerAllowByModPreset() {
  PresetRegistry.presets[ALLOW_BY_MOD_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { allowMods("minecraft") }
    }
}

private fun registerBanByTagPreset() {
  PresetRegistry.presets[BAN_BY_TAG_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures { blockTags(VILLAGE_TAG) }
    }
}

private fun registerAllowByTagPreset() {
  PresetRegistry.presets[ALLOW_BY_TAG_PRESET] =
    RegionRegistry().apply {
      setRoot("minecraft:overworld", "overworld_root")
      region("overworld_root").structures {
        allowTags(VILLAGE_TAG)
        blockMods("minecraft")
      }
    }
}

// Runs a locate with the current forcePresetId and always screenshots at screenshotPos
// regardless of whether locate found anything.
private fun runLocateAtPos(
  context: ClientGameTestContext,
  label: String,
  screenshotDir: Path,
  screenshotName: String,
  screenshotPos: BlockPos,
  structureFilter: (String) -> Boolean = { "village" in it },
): LocateResult {
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
    var result: LocateResult? = null
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        val level = server.overworld()
        val generator = level.getChunkSource().getGenerator()
        val structReg = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val holders =
          structReg
            .listElements()
            .filter { h ->
              h.unwrapKey().map { structureFilter(it.identifier().toString()) }.orElse(false)
            }
            .toList()
        if (holders.isEmpty()) {
          log.warn("[{}] no matching holders in registry", label)
          result = LocateResult(null, null)
          return@FailableConsumer
        }
        val holderSet = HolderSet.direct(holders)
        val found =
          generator.findNearestMapStructure(level, holderSet, BlockPos(0, 64, 0), 1000, true)
        if (found != null) {
          val id = found.second.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
          val pos = found.first
          log.info("[{}] found type={} at ({},{})", label, id, pos.x, pos.z)
          result = LocateResult(id, pos)
        } else {
          log.info("[{}] not found within radius=1000", label)
          result = LocateResult(null, null)
        }
      }
    )
    val locateResult = result ?: LocateResult(null, null)
    game.server.runOnServer(
      FailableConsumer<MinecraftServer, Exception> { server ->
        server.playerList.players[0].teleportTo(screenshotPos.x + 0.5, 100.0, screenshotPos.z + 0.5)
      }
    )
    context.waitTicks(10)
    context.runOnClient(
      FailableConsumer<Minecraft, Exception> { client ->
        client.player?.let { player ->
          player.xRot = 90f
          player.yRot = 0f
          player.abilities.mayfly = true
          player.abilities.flying = true
          player.onUpdateAbilities()
        }
      }
    )
    context.waitTicks(5)
    game.clientWorld.waitForChunksDownload()
    game.clientWorld.waitForChunksRender()
    context.takeScreenshot(
      TestScreenshotOptions.of(screenshotName).withDestinationDir(screenshotDir)
    )
    log.info("[{}] screenshot at {} done", label, screenshotDir)
    return locateResult
  } finally {
    game.close()
  }
}

// Ban by mod: blockMods("minecraft") suppresses all vanilla village locate results.
// Screenshots: vanilla (village visible) vs banned (same location, no village generated).
@Suppress("UnstableApiUsage")
object StructureConstraintBanByModGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintBanByModTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", screenshotDir.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    registerBanByModPreset()
    PresetRegistry.forcePresetId = BAN_BY_MOD_PRESET
    val bannedResult: LocateResult
    try {
      bannedResult =
        runLocateAtPos(
          context,
          "ban_by_mod",
          screenshotDir.resolve("banned"),
          "village_absent",
          vanillaResult.pos ?: BlockPos(0, 64, 0),
        )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(BAN_BY_MOD_PRESET)
    }

    assertNotNull(vanillaResult.pos, "[ban_by_mod] vanilla must find a village within radius=200")
    assertNull(
      bannedResult.pos,
      "[ban_by_mod] blockMods(minecraft) must suppress all village locate results, " +
        "but found ${bannedResult.structureId} at ${bannedResult.pos}",
    )
  }
}

// Allow by mod: allowMods("minecraft") is a pass-through — all vanilla structures remain.
// Screenshots: vanilla (village visible) vs allowMods-constrained (village still visible).
@Suppress("UnstableApiUsage")
object StructureConstraintAllowByModGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintAllowByModTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", screenshotDir.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    registerAllowByModPreset()
    PresetRegistry.forcePresetId = ALLOW_BY_MOD_PRESET
    val allowedResult: LocateResult
    try {
      allowedResult =
        runLocateAtPos(
          context,
          "allow_by_mod",
          screenshotDir.resolve("allowed"),
          "village_present",
          vanillaResult.pos ?: BlockPos(0, 64, 0),
        )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(ALLOW_BY_MOD_PRESET)
    }

    assertNotNull(vanillaResult.pos, "[allow_by_mod] vanilla must find a village within radius=200")
    assertNotNull(
      allowedResult.pos,
      "[allow_by_mod] allowMods(minecraft) must not suppress village locate — " +
        "it is a pass-through that leaves all minecraft structures allowed",
    )
  }
}

// Ban by tag: blockTags("minecraft:village") suppresses all village-tagged structures.
// Screenshots: vanilla (village visible) vs banned (same location, village absent).
@Suppress("UnstableApiUsage")
object StructureConstraintBanByTagGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintBanByTagTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", screenshotDir.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    registerBanByTagPreset()
    PresetRegistry.forcePresetId = BAN_BY_TAG_PRESET
    val bannedResult: LocateResult
    try {
      bannedResult =
        runLocateAtPos(
          context,
          "ban_by_tag",
          screenshotDir.resolve("banned"),
          "village_absent",
          vanillaResult.pos ?: BlockPos(0, 64, 0),
        )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(BAN_BY_TAG_PRESET)
    }

    assertNotNull(vanillaResult.pos, "[ban_by_tag] vanilla must find a village within radius=200")
    assertNull(
      bannedResult.pos,
      "[ban_by_tag] blockTags($VILLAGE_TAG) must suppress all village locate results, " +
        "but found ${bannedResult.structureId} at ${bannedResult.pos}",
    )
  }
}

// Allow by tag: allowTags("minecraft:village") + blockMods("minecraft") lets village-tagged
// structures through the mod block. Screenshots: vanilla vs constrained (village still visible).
@Suppress("UnstableApiUsage")
object StructureConstraintAllowByTagGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintAllowByTagTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", screenshotDir.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    registerAllowByTagPreset()
    PresetRegistry.forcePresetId = ALLOW_BY_TAG_PRESET
    val allowedResult: LocateResult
    try {
      allowedResult =
        runLocateAtPos(
          context,
          "allow_by_tag",
          screenshotDir.resolve("allowed"),
          "village_present",
          vanillaResult.pos ?: BlockPos(0, 64, 0),
        )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(ALLOW_BY_TAG_PRESET)
    }

    assertNotNull(vanillaResult.pos, "[allow_by_tag] vanilla must find a village within radius=200")
    assertNotNull(
      allowedResult.pos,
      "[allow_by_tag] allowTags($VILLAGE_TAG)+blockMods(minecraft) must still locate a village — " +
        "tag-level allow overrides mod-level block",
    )
  }
}

// Ban by name: blockNames(T) suppresses exactly the village type T that vanilla would find.
// Dynamic: T is derived from vanilla locate so the exact structure is always targeted.
// Screenshots: vanilla (structure T visible) vs banned (same location, T absent).
@Suppress("UnstableApiUsage")
object StructureConstraintBanByNameGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintBanByNameTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", screenshotDir.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    assertNotNull(
      vanillaResult.pos,
      "[ban_by_name] vanilla must find a village within radius=200 to derive blockNames target",
    )
    val targetId = vanillaResult.structureId!!

    val banPresetId = "structure_constraint_ban_by_name"
    PresetRegistry.presets[banPresetId] =
      RegionRegistry().apply {
        setRoot("minecraft:overworld", "overworld_root")
        region("overworld_root").structures { blockNames(targetId) }
      }
    PresetRegistry.forcePresetId = banPresetId
    val bannedResult: LocateResult
    try {
      bannedResult =
        runLocateAtPos(
          context,
          "ban_by_name",
          screenshotDir.resolve("banned"),
          "village_absent",
          vanillaResult.pos!!,
          structureFilter = { it == targetId },
        )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(banPresetId)
    }

    assertNull(
      bannedResult.pos,
      "[ban_by_name] blockNames($targetId) must suppress that exact structure, " +
        "but found ${bannedResult.structureId} at ${bannedResult.pos}",
    )
  }
}

// Allow by name: allowNames(T)+blockMods("minecraft") lets exactly structure T through the
// mod block. Dynamic: T is derived from vanilla locate. Screenshots: vanilla vs constrained
// (same structure T visible despite mod block).
@Suppress("UnstableApiUsage")
object StructureConstraintAllowByNameGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintAllowByNameTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val vanillaResult: LocateResult
    try {
      vanillaResult = runLocateTest(context, "vanilla", screenshotDir.resolve("vanilla"))
    } finally {
      PresetRegistry.forcePresetId = null
    }

    assertNotNull(
      vanillaResult.pos,
      "[allow_by_name] vanilla must find a village within radius=200 to derive allowNames target",
    )
    val targetId = vanillaResult.structureId!!

    val allowPresetId = "structure_constraint_allow_by_name"
    PresetRegistry.presets[allowPresetId] =
      RegionRegistry().apply {
        setRoot("minecraft:overworld", "overworld_root")
        region("overworld_root").structures {
          allowNames(targetId)
          blockMods("minecraft")
        }
      }
    PresetRegistry.forcePresetId = allowPresetId
    val allowedResult: LocateResult
    try {
      allowedResult =
        runLocateAtPos(
          context,
          "allow_by_name",
          screenshotDir.resolve("allowed"),
          "village_present",
          vanillaResult.pos!!,
          structureFilter = { it == targetId },
        )
    } finally {
      PresetRegistry.forcePresetId = null
      PresetRegistry.presets.remove(allowPresetId)
    }

    assertNotNull(
      allowedResult.pos,
      "[allow_by_name] allowNames($targetId)+blockMods(minecraft) must still locate $targetId — " +
        "name-level allow overrides mod-level block",
    )
    assertEquals(
      targetId,
      allowedResult.structureId,
      "[allow_by_name] constrained locate must return the same structure type as vanilla",
    )
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintLocateRuinedPortalGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return

    val screenshotDir = SCREENSHOTS_BASE.resolve("StructureConstraintLocateRuinedPortalTest")

    PresetRegistry.forcePresetId = DISABLED_PRESET
    val result: LocateResult
    try {
      result =
        runLocateTest(
          context,
          "ruined_portal",
          screenshotDir,
          structureFilter = { "ruined_portal" in it },
          screenshotName = "located_ruined_portal",
        )
    } finally {
      PresetRegistry.forcePresetId = null
    }

    log.info("[ruined_portal] id={} dist={}", result.structureId, result.distance)

    assertNotNull(
      result.pos,
      "[ruined_portal] expected a ruined portal within radius=200 but got null",
    )
    assertTrue(
      result.structureId?.contains("ruined_portal") == true,
      "[ruined_portal] expected id containing ruined_portal, got ${result.structureId}",
    )
    assertEquals(
      RUINED_PORTAL_DISTANCE,
      result.distance,
      "[ruined_portal] horizontal distance from origin mismatch for fixed seed",
    )
  }
}
