package terrasect.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.levelgen.Heightmap
import org.junit.jupiter.api.Assertions.assertTrue
import terrasect.definition.RegionRegistry

private data class Column(
  val floor: Int,
  val surface: Int,
  val ground: String,
  val cover: String,
  val biome: String,
)

private fun sample(
  context: ClientGameTestContext,
  name: String,
  preset: RegionRegistry? = null,
): List<Column> =
  context.withTestWorld("noise-narrative", "noise_narrative_client_test", preset) { game ->
    game
      .onServer { server ->
        val level = server.overworld()
        (0 until 16).flatMap { x ->
          (0 until 16).map { z ->
            val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
            fun block(y: Int) =
              BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos(x, y, z)).block)
                .toString()
            Column(
              level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z),
              surface,
              block(surface - 1),
              block(surface),
              level
                .getBiome(BlockPos(x, surface, z))
                .unwrapKey()
                .orElseThrow()
                .identifier()
                .toString(),
            )
          }
        }
      }
      .also { game.screenshot(context, "NoiseConstraintTest/$name") }
  }

@Suppress("UnstableApiUsage")
object NoiseConstraintGameTest : FabricClientGameTest {
  override fun runTest(context: ClientGameTestContext) {
    if (!GameTestFilter.shouldRun(this::class)) return
    val scenarios =
      mapOf(
        "desert" to
          testPreset {
            noise {
              noise("temperature") {
                it.multiply(0.0).add(0.8)
              }
              densityFunction("continents") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("ridges") {
                it.multiply(0.0).add(-0.2)
              }
              densityFunction("finalDensity") { it.add(0.02) }
            }
          },
        "ocean" to
          testPreset {
            noise {
              densityFunction("continents") {
                it.multiply(0.0).add(-0.8)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("ridges") {
                it.multiply(0.0).add(0.0)
              }
              densityFunction("fluidLevelFloodednessNoise") {
                it.multiply(0.0).add(1.0)
              }
              densityFunction("fluidLevelSpreadNoise") {
                it.multiply(0.0).add(1.0)
              }
              densityFunction("lavaNoise") {
                it.multiply(0.0).add(-1.0)
              }
              densityFunction("finalDensity") { it.add(-1.2) }
            }
          },
        "highlands" to
          testPreset {
            noise {
              densityFunction("continents") {
                it.multiply(0.0).add(0.35)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(-0.65)
              }
              densityFunction("ridges") {
                it.multiply(0.0).add(0.6)
              }
              densityFunction("preliminarySurfaceLevel") { it.add(16.0) }
              densityFunction("finalDensity") { it.add(0.02) }
            }
          },
        "hilly_plains" to
          testPreset {
            noise {
              noise("temperature") {
                it.multiply(0.0).add(0.0)
              }
              noise("vegetation") {
                it.multiply(0.0).add(-0.5)
              }
              densityFunction("continents") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("ridges") {
                it.multiply(0.0).add(0.25)
              }
              densityFunction("finalDensity") { it.add(-0.07) }
            }
          },
        "flat_plains" to
          testPreset {
            noise {
              noise("temperature") {
                it.multiply(0.0).add(0.0)
              }
              noise("vegetation") {
                it.multiply(0.0).add(-0.5)
              }
              densityFunction("continents") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(0.3)
              }
              densityFunction("ridges") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("finalDensity") { it.add(-0.15) }
            }
          },
        "lowlands" to
          testPreset {
            noise {
              densityFunction("continents") {
                it.multiply(0.0).add(0.2)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(0.25)
              }
              densityFunction("finalDensity") { it.add(-0.07) }
            }
          },
        "ridge_valley" to
          testPreset {
            noise {
              densityFunction("continents") {
                it.multiply(0.0).add(0.1)
              }
              densityFunction("erosion") {
                it.multiply(0.0).add(0.8)
              }
              densityFunction("ridges") {
                it.multiply(0.0).add(0.0)
              }
              densityFunction("finalDensity") { it.add(-0.08) }
            }
          },
      )

    val vanilla = sample(context, "vanilla")
    val baselineHeight = vanilla.map { it.surface }.average()
    for ((name, preset) in scenarios) {
      val columns = sample(context, name, preset)
      val average = columns.map { it.surface }.average()
      val relief = columns.maxOf { it.surface } - columns.minOf { it.surface }
      val water = columns.count { "water" in it.ground || "water" in it.cover }
      val evidence =
        "$name: average=$average (vanilla=$baselineHeight), relief=$relief, water=$water/${columns.size}"
      assertTrue(
        columns.indices.any { i ->
          val actual = columns[i]
          val expected = vanilla[i]
          actual.floor != expected.floor ||
            actual.surface != expected.surface ||
            actual.ground != expected.ground ||
            actual.cover != expected.cover
        },
        "$evidence: noise must change terrain, not only biomes",
      )
      when (name) {
        "desert" -> {
          assertTrue(columns.count { "sand" in it.ground } > columns.size / 2, evidence)
          assertTrue(columns.count { it.biome == "minecraft:desert" } > columns.size / 2, evidence)
        }
        "ocean" -> {
          assertTrue(
            columns.count { it.biome == "minecraft:deep_ocean" } > columns.size / 2,
            evidence,
          )
          assertTrue(water > columns.size / 2, evidence)
        }
        "highlands" -> assertTrue(average >= baselineHeight + 2, evidence)
        "hilly_plains" -> {
          assertTrue(average <= baselineHeight - 2, evidence)
          assertTrue(relief <= 18, evidence)
          assertTrue(water < columns.size / 3, evidence)
        }
        "flat_plains" -> {
          assertTrue(average <= baselineHeight - 6, evidence)
          assertTrue(relief <= 15, evidence)
          assertTrue(water < columns.size / 4, evidence)
        }
        "lowlands" -> {
          assertTrue(average <= baselineHeight - 4, evidence)
          assertTrue(water < columns.size / 2, evidence)
        }
        "ridge_valley" -> assertTrue(average <= baselineHeight - 2, evidence)
        else -> error("Unasserted noise scenario: $name")
      }
    }
  }
}
