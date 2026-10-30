package terrasect.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import org.apache.commons.lang3.function.FailableConsumer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import terrasect.generation.DimensionContext

private const val SEED = "other-dimensions"

private fun ServerLevel.surfaces(x: Int, z: Int): IntRange {
  for (cx in (x shr 4)..((x + 15) shr 4)) {
    for (cz in (z shr 4)..((z + 15) shr 4)) getChunk(cx, cz)
  }
  val heights =
    (x until x + 16).flatMap { bx ->
      (z until z + 16).map { bz -> getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) }
    }
  return heights.min()..heights.max()
}

private fun noiseTest(
  context: ClientGameTestContext,
  dimension: ResourceKey<Level>,
  probes: List<Pair<Int, Int>>,
  baselineMin: Int,
  constrainedMax: Int,
) {
  val id = dimension.identifier().toString()
  val vanilla =
    context.withTestWorld(SEED) { game ->
      assertNull(DimensionContext.get(id), "Disabled preset must not register $id")
      game
        .onServer { server ->
          probes.map { (x, z) -> checkNotNull(server.getLevel(dimension)).surfaces(x, z) }
        }
        .also {
          for ((x, z) in probes) game.screenshot(
            context,
            "${dimension.identifier().path}/vanilla_${x}_${z}",
            x + 8,
            z + 8,
            dimension,
          )
        }
    }
  val preset =
    testPreset(id) {
      noise { densityFunction("finalDensity") { it.multiply(0.0).add(-1.0) } }
      structures { blockMods("minecraft") }
    }
  val constrained =
    context.withTestWorld(SEED, "dimension_noise_test", preset) { game ->
      assertNotNull(DimensionContext.get(id), "Preset must register $id")
      game
        .onServer { server ->
          probes.map { (x, z) -> checkNotNull(server.getLevel(dimension)).surfaces(x, z) }
        }
        .also {
          for ((x, z) in probes) game.screenshot(
            context,
            "${dimension.identifier().path}/constrained_${x}_${z}",
            x + 8,
            z + 8,
            dimension,
          )
        }
    }
  for (index in probes.indices) {
    assertTrue(
      vanilla[index].last >= baselineMin,
      "$id ${probes[index]}: invalid vanilla surface ${vanilla[index]}",
    )
    assertTrue(
      constrained[index].last <= constrainedMax,
      "$id ${probes[index]}: density constraint did not carve terrain: ${constrained[index]}",
    )
  }
}

@Suppress("UnstableApiUsage")
object NetherNoiseConstraintGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    noiseTest(context, Level.NETHER, listOf(0 to 0, 700 to 700), 100, 60)
  }
}

@Suppress("UnstableApiUsage")
object EndNoiseConstraintGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    noiseTest(context, Level.END, listOf(64 to 64), 50, 10)
  }
}

@Suppress("UnstableApiUsage")
object NetherMobConstraintGameTest : FabricClientGameTest {
  private fun spawns(context: ClientGameTestContext, blocked: Boolean): Map<String, Int> {
    val preset =
      if (blocked) testPreset("minecraft:the_nether") { mobs { blockMods("minecraft") } } else null
    return context.withTestWorld(SEED, "nether_mob_test", preset) { game ->
      game.server.runCommand("execute in minecraft:the_nether run tp @a 8 90 8")
      context.waitTicks(20)
      context.runOnClient(
        FailableConsumer<Minecraft, Exception> { client ->
          checkNotNull(client.player).apply {
            abilities.mayfly = true
            abilities.flying = true
            onUpdateAbilities()
          }
        }
      )
      game.clientLevel.waitForChunksDownload()
      context.waitTicks(300)
      game
        .onServer { server ->
          checkNotNull(server.getLevel(Level.NETHER))
            .getEntitiesOfClass(Mob::class.java, AABB(-120.0, 0.0, -120.0, 136.0, 256.0, 136.0)) {
              true
            }
            .groupingBy { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() }
            .eachCount()
        }
        .also {
          game.screenshot(
            context,
            "NetherMobConstraintTest/${if (blocked) "blocked" else "vanilla"}",
            dimension = Level.NETHER,
            y = 90.0,
          )
        }
    }
  }

  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val vanilla = spawns(context, false)
    val blocked = spawns(context, true)
    assertTrue(vanilla.values.sum() > 0, "Vanilla Nether must naturally spawn mobs")
    assertTrue(blocked.isEmpty(), "Blocked Nether spawned $blocked")
  }
}

@Suppress("UnstableApiUsage")
object NetherStructureConstraintGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    fun locate(blocked: Boolean, at: net.minecraft.core.BlockPos? = null) =
      context.withTestWorld(
        SEED,
        "nether_structure_test",
        if (blocked) testPreset("minecraft:the_nether") { structures { blockMods("minecraft") } }
        else null,
      ) { game ->
        game
          .onServer { server ->
            checkNotNull(server.getLevel(Level.NETHER)).locateStructure {
              "fortress" in it || "bastion" in it
            }
          }
          .also { result ->
            val position = at ?: result?.pos
            game.screenshot(
              context,
              "NetherStructureConstraintTest/${if (blocked) "blocked" else "vanilla"}",
              position?.x ?: 8,
              position?.z ?: 8,
              Level.NETHER,
              90.0,
            )
          }
      }
    val vanilla = checkNotNull(locate(false)) { "Vanilla must locate a fortress or bastion" }
    assertNull(locate(true, vanilla.pos), "Blocked Nether must not locate a fortress or bastion")
  }
}

@Suppress("UnstableApiUsage")
object DimensionContextIsolationGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    for (dimension in listOf("minecraft:overworld", "minecraft:the_nether")) {
      val preset = testPreset(dimension) { mobs { blockNames("minecraft:creeper") } }
      context.withTestWorld(SEED, "dimension_isolation_test", preset) {
        assertNotNull(DimensionContext.get(dimension))
        val other =
          if (dimension == "minecraft:overworld") "minecraft:the_nether" else "minecraft:overworld"
        assertNull(DimensionContext.get(other), "Stale $other context leaked into $dimension world")
      }
    }
  }
}
