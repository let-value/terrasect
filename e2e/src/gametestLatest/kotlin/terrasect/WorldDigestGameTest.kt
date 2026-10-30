package terrasect.gametest

import java.nio.file.Path
import kotlin.math.abs
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.level.levelgen.Heightmap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import terrasect.presets.Presets

private val PROBES = listOf(0 to 0, 512 to 0, 0 to 512, 512 to 512)

private fun parseColumns(text: String): Map<String, Pair<Int, Int>> =
  text.lineSequence().drop(1).filter(String::isNotBlank).associate { line ->
    val (x, z, floor, surface) = line.split(',')
    "$x,$z" to (floor.toInt() to surface.toInt())
  }

private fun verifyDigest(context: ClientGameTestContext, preset: String, owner: Class<*>) {
  val columns =
    context.withTestWorld("seed", preset) { game ->
      game
        .onServer { server ->
          val level = server.overworld()
          buildMap {
            for ((x, z) in PROBES) {
              level.getChunk(x shr 4, z shr 4)
              for (bx in x until x + 16) {
                for (bz in z until z + 16) {
                  put(
                    "$bx,$bz",
                    level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz) to
                      level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz),
                  )
                }
              }
            }
          }
        }
        .also {
          for ((x, z) in PROBES) game.screenshot(
            context,
            "${owner.simpleName}/probe_${x}_${z}",
            x + 8,
            z + 8,
          )
        }
    }
  val text = buildString {
    appendLine("x,z,ocean_floor,world_surface")
    for ((key, heights) in columns) appendLine("$key,${heights.first},${heights.second}")
  }
  val path: Path =
    e2eModuleDir(owner)
      .resolve("src/gametest/resources/terrasect/${owner.simpleName}_snapshots/columns.snapshot")
  val expected = parseColumns(testSnapshot(path, text, owner.name, "columns"))
  assertEquals(expected.keys, columns.keys, "Digest probe coordinates changed")
  for ((key, heights) in expected) {
    val actual = columns.getValue(key)
    assertTrue(
      abs(heights.first - actual.first) <= 1 && abs(heights.second - actual.second) <= 1,
      "$key: expected=$heights actual=$actual (height tolerance=1)",
    )
  }
}

@Suppress("UnstableApiUsage")
object VanillaWorldDigestTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    verifyDigest(context, "__disabled__", javaClass)
  }
}

@Suppress("UnstableApiUsage")
object TerrasectWorldDigestTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    verifyDigest(context, Presets.CLIMATE_DEBUG.toString(), javaClass)
  }
}
