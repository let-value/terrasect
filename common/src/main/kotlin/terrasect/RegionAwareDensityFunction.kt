package terrasect

import net.minecraft.resources.ResourceKey
import net.minecraft.util.KeyDispatchDataCodec
import net.minecraft.world.level.levelgen.DensityFunction
import terrasect.handler.NoiseHandler

class RegionAwareDensityFunction(
    private val original: DensityFunction,
    private val key: ResourceKey<DensityFunction>,
    private val noiseChunk: NoiseChunkExtender,
) : DensityFunction {

  override fun compute(ctx: DensityFunction.FunctionContext): Double {
    val value = original.compute(ctx)
    val cache = noiseChunk.`terrasect$getChunk`()?.`terrasect$getCache`() ?: return value
    return NoiseHandler.modifyDensityValue(key, value, ctx.blockX(), ctx.blockZ(), cache)
  }

  override fun fillArray(ds: DoubleArray, contextProvider: DensityFunction.ContextProvider) {
    contextProvider.fillAllDirectly(ds, this)
  }

  override fun mapAll(visitor: DensityFunction.Visitor): DensityFunction {
    return visitor.apply(RegionAwareDensityFunction(original.mapAll(visitor), key, noiseChunk))
  }

  override fun minValue(): Double = original.minValue()

  override fun maxValue(): Double = original.maxValue()

  override fun codec(): KeyDispatchDataCodec<out DensityFunction> = original.codec()
}
