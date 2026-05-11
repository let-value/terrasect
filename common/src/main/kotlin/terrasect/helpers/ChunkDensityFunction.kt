package terrasect.helpers

import net.minecraft.util.KeyDispatchDataCodec
import net.minecraft.world.level.levelgen.DensityFunction
import terrasect.generation.ChunkContext
import terrasect.handler.NoiseHandler

class ChunkDensityFunction(
  val wrapped: DensityFunction,
  val key: String,
  val chunk: ChunkContext,
  val scale: Int = 1,
) : DensityFunction {
  override fun compute(context: DensityFunction.FunctionContext): Double {
    val previous = NoiseHandler.currentChunk.get()
    NoiseHandler.currentChunk.set(chunk)
    val original =
      try {
        wrapped.compute(context)
      } finally {
        if (previous == null) {
          NoiseHandler.currentChunk.remove()
        } else {
          NoiseHandler.currentChunk.set(previous)
        }
      }

    return NoiseHandler.modifyDensityValue(
      key,
      original,
      context.blockX() * scale,
      context.blockY(),
      context.blockZ() * scale,
      chunk,
    ) ?: original
  }

  override fun fillArray(array: DoubleArray, context: DensityFunction.ContextProvider) {
    val previous = NoiseHandler.currentChunk.get()
    NoiseHandler.currentChunk.set(chunk)
    try {
      wrapped.fillArray(array, context)
    } finally {
      if (previous == null) {
        NoiseHandler.currentChunk.remove()
      } else {
        NoiseHandler.currentChunk.set(previous)
      }
    }

    for (index in array.indices) {
      val sample = context.forIndex(index)
      val transformed =
        NoiseHandler.modifyDensityValue(
          key,
          array[index],
          sample.blockX() * scale,
          sample.blockY(),
          sample.blockZ() * scale,
          chunk,
        )
      if (transformed != null) {
        array[index] = transformed
      }
    }
  }

  override fun mapAll(visitor: DensityFunction.Visitor): DensityFunction {
    return ChunkDensityFunction(wrapped.mapAll(visitor), key, chunk, scale)
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
