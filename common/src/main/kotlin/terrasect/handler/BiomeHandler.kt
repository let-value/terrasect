package terrasect.handler

import net.minecraft.core.Holder
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import terrasect.generation.DimensionContext
import terrasect.instrumentation.TerrasectInstr
import terrasect.instrumentation.TerrasectMetricEvent

private val instr = TerrasectInstr.biome
private val appliedCounter = instr.counter(TerrasectMetricEvent.BIOME_APPLIED)
private val rejectedCounter = instr.counter(TerrasectMetricEvent.BIOME_REJECTED)
private val fallbackCounter = instr.counter(TerrasectMetricEvent.BIOME_FALLBACK_APPLIED)
private val rejectedNoFallbackCounter =
  instr.counter(TerrasectMetricEvent.BIOME_REJECTED_NO_FALLBACK)

object BiomeHandler {
  @JvmStatic
  fun selectBiome(
    dimensionContext: DimensionContext?,
    quartX: Int,
    quartZ: Int,
    target: Climate.TargetPoint,
    base: Holder<Biome>,
  ): Holder<Biome> {
    val context = dimensionContext ?: return base
    val lookup = context.biomeLookup ?: return base
    val blockX = quartX shl 2
    val blockZ = quartZ shl 2
    val region = context.traverser.traverse(blockX, blockZ, context.cache).region
    if (!lookup.isConstrained(region)) return base

    appliedCounter.increment()
    if (lookup.isAdmitted(region, base.value())) return base

    rejectedCounter.increment()
    return lookup.select(region, target)?.also {
      fallbackCounter.increment()
    }
      ?: run {
        rejectedNoFallbackCounter.increment()
        base
      }
  }
}
