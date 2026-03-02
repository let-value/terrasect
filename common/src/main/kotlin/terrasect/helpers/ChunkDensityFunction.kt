package terrasect.helpers

import net.minecraft.util.KeyDispatchDataCodec
import net.minecraft.world.level.levelgen.DensityFunction
import terrasect.extender.NoiseChunkExtender
import terrasect.handler.NoiseHandler

class ChunkDensityFunction(
    val wrapped: DensityFunction,
    val key: String,
    val noiseChunk: NoiseChunkExtender,
    val scale: Int = 1,
) : DensityFunction {
  override fun compute(context: DensityFunction.FunctionContext): Double {
    val original = wrapped.compute(context)
    val chunk = noiseChunk.`terrasect$getChunk`()?.`terrasect$getCache`() ?: return original
    return NoiseHandler.modifyDensityValue(
        key,
        original,
        context.blockX() * scale,
        context.blockZ() * scale,
        chunk,
    ) ?: original
  }

  override fun fillArray(array: DoubleArray, context: DensityFunction.ContextProvider) {
    return context.fillAllDirectly(array, this)
  }

  override fun mapAll(visitor: DensityFunction.Visitor): DensityFunction {
    return ChunkDensityFunction(wrapped.mapAll(visitor), key, noiseChunk, scale)
  }

  override fun minValue(): Double {
    return wrapped.minValue()
  }

  override fun maxValue(): Double {
    return wrapped.maxValue()
  }

  override fun codec(): KeyDispatchDataCodec<out DensityFunction> {
    return wrapped.codec()
  }
}
