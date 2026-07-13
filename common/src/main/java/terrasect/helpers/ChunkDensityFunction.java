package terrasect.helpers;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import terrasect.generation.ChunkContext;
import terrasect.handler.NoiseHandler;

public final class ChunkDensityFunction implements DensityFunction {
  public final DensityFunction wrapped;
  public final String key;
  public final ChunkContext chunk;
  public final int scale;

  public ChunkDensityFunction(DensityFunction wrapped, String key, ChunkContext chunk, int scale) {
    this.wrapped = wrapped;
    this.key = key;
    this.chunk = chunk;
    this.scale = scale;
  }

  @Override
  public double compute(DensityFunction.FunctionContext context) {
    double original = wrapped.compute(context);
    Double transformed =
        NoiseHandler.modifyDensityValue(
            key,
            original,
            context.blockX() * scale,
            context.blockY(),
            context.blockZ() * scale,
            chunk);
    return transformed != null ? transformed : original;
  }

  @Override
  public void fillArray(double[] array, DensityFunction.ContextProvider context) {
    wrapped.fillArray(array, context);
    for (int index = 0; index < array.length; index++) {
      DensityFunction.FunctionContext sample = context.forIndex(index);
      Double transformed =
          NoiseHandler.modifyDensityValue(
              key,
              array[index],
              sample.blockX() * scale,
              sample.blockY(),
              sample.blockZ() * scale,
              chunk);
      if (transformed != null) {
        array[index] = transformed;
      }
    }
  }

  public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
    return new ChunkDensityFunction(visitor.apply(wrapped), key, chunk, scale);
  }

  @Override
  public DensityFunction mapAll(DensityFunction.Visitor visitor) {
    return new ChunkDensityFunction(wrapped.mapAll(visitor), key, chunk, scale);
  }

  @Override
  public double minValue() {
    return wrapped.minValue();
  }

  @Override
  public double maxValue() {
    return wrapped.maxValue();
  }

  @Override
  public KeyDispatchDataCodec<? extends DensityFunction> codec() {
    return wrapped.codec();
  }
}
