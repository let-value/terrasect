package terrasect.gametest

import kotlin.math.hypot
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import terrasect.definition.RegionRegistry
import terrasect.definition.StructureConstraints

private const val SEED = "structure-constraints"

private fun locate(
  context: ClientGameTestContext,
  name: String,
  preset: RegionRegistry? = null,
  at: BlockPos? = null,
  captureProbes: Boolean = false,
  filter: (String) -> Boolean = { it.startsWith("minecraft:village_") },
): LocatedStructure? =
  context.withTestWorld(SEED, "structure_constraint_test", preset) { game ->
    game
      .onServer { it.overworld().locateStructure(filter) }
      .also { result ->
        val position = at ?: result?.pos
        if (position != null)
          game.screenshot(context, "StructureConstraintTest/$name", position.x, position.z)
        if (captureProbes) {
          for ((x, z) in listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)) {
            game.screenshot(context, "StructureConstraintTest/$name/probe_${x}_${z}", x + 8, z + 8)
          }
        }
      }
  }

private fun selectionTest(
  context: ClientGameTestContext,
  name: String,
  allowed: Boolean,
  exactName: Boolean = false,
  configure: StructureConstraints.Builder.(String) -> Unit,
) {
  val vanilla = checkNotNull(locate(context, "$name/vanilla")) { "Vanilla must locate a village" }
  val preset = testPreset { structures { configure(vanilla.id) } }
  val result =
    locate(context, "$name/constrained", preset, vanilla.pos) {
      if (exactName) it == vanilla.id else it.startsWith("minecraft:village_")
    }
  if (allowed) {
    assertNotNull(result, "Allowed villages must remain locatable")
    if (exactName) assertEquals(vanilla.id, result!!.id)
  } else {
    assertNull(result, "Blocked villages must not be locatable")
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintLocateGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val vanilla =
      checkNotNull(locate(context, "locate/vanilla", captureProbes = true)) {
        "Vanilla must locate a village"
      }
    assertNotNull(
      locate(
        context,
        "locate/dense",
        testPreset {
          structures {
            spacing(8)
            separation(4)
            frequency(1f)
          }
        },
        captureProbes = true,
      ),
      "Placement-only constraints must not suppress locate",
    )
    assertNull(
      locate(
        context,
        "locate/blocked",
        testPreset { structures { blockMods("minecraft") } },
        vanilla.pos,
      )
    )
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintBanByModGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    selectionTest(context, "ban_by_mod", allowed = false) { blockMods("minecraft") }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintAllowByModGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    selectionTest(context, "allow_by_mod", allowed = true) { allowMods("minecraft") }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintBanByTagGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    selectionTest(context, "ban_by_tag", allowed = false) { blockTags("minecraft:village") }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintAllowByTagGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    selectionTest(context, "allow_by_tag", allowed = true) {
      allowTags("minecraft:village")
      blockMods("minecraft")
    }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintBanByNameGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    selectionTest(context, "ban_by_name", allowed = false, exactName = true) { blockNames(it) }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintAllowByNameGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    selectionTest(context, "allow_by_name", allowed = true, exactName = true) {
      allowNames(it)
      blockMods("minecraft")
    }
  }
}

@Suppress("UnstableApiUsage")
object StructureConstraintLocateRuinedPortalGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val result = checkNotNull(locate(context, "ruined_portal") { "ruined_portal" in it })
    assertEquals(385, hypot(result.pos.x.toDouble(), result.pos.z.toDouble()).toInt())
  }
}
