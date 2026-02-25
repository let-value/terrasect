package terrasect.helpers

import net.minecraft.util.KeyDispatchDataCodec
import net.minecraft.world.level.levelgen.DensityFunction
import terrasect.NoiseChunkExtender
import terrasect.handler.NoiseHandler

class ChunkDensityFunction(
    val wrapped: DensityFunction,
    val key: String,
    val noiseChunk: NoiseChunkExtender,
) : DensityFunction {
  override fun compute(context: DensityFunction.FunctionContext): Double {
    val original = wrapped.compute(context)
    return NoiseHandler.modifyDensityValue(
        key,
        original,
        context.blockX(),
        context.blockZ(),
        noiseChunk.`terrasect$getChunk`().`terrasect$getCache`(),
    ) ?: original
  }

  override fun fillArray(array: DoubleArray, context: DensityFunction.ContextProvider) {
    return context.fillAllDirectly(array, this)
  }

  override fun mapAll(visitor: DensityFunction.Visitor): DensityFunction {
    return ChunkDensityFunction(wrapped.mapAll(visitor), key, noiseChunk)
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
