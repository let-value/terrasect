package terrasect.helpers;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import terrasect.definition.Region;
import terrasect.generation.ChunkContext;
import terrasect.handler.NoiseHandler;
import terrasect.lookup.NoiseBinding;
import terrasect.lookup.ResolvedNoise;

public final class ChunkDensityFunction implements DensityFunction {
  public final DensityFunction wrapped;
  public final NoiseBinding binding;
  public final ChunkContext chunk;
  public final int scale;

  public ChunkDensityFunction(
      DensityFunction wrapped, NoiseBinding binding, ChunkContext chunk, int scale) {
    this.wrapped = wrapped;
    this.binding = binding;
    this.chunk = chunk;
    this.scale = scale;
  }

  private double apply(double original, int blockX, int blockZ) {
    Region region = chunk.getRegion(blockX, blockZ);
    if (region == null) {
      return original;
    }
    ResolvedNoise resolved = binding.get(region);
    if (resolved == null) {
      return original;
    }
    float strength =
        NoiseHandler.getStrength(resolved.blendWidth, chunk.getDistance(blockX, blockZ));
    if (strength <= 0f) {
      return original;
    }
    double transformed = resolved.transform.apply(original);
    if (strength >= 1f) {
      return transformed;
    }
    return original + (transformed - original) * strength;
  }

  @Override
  public double compute(DensityFunction.FunctionContext context) {
    double original = wrapped.compute(context);
    return apply(original, context.blockX() * scale, context.blockZ() * scale);
  }

  @Override
  public void fillArray(double[] array, DensityFunction.ContextProvider context) {
    wrapped.fillArray(array, context);
    for (int index = 0; index < array.length; index++) {
      DensityFunction.FunctionContext sample = context.forIndex(index);
      array[index] = apply(array[index], sample.blockX() * scale, sample.blockZ() * scale);
    }
  }

  public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
    return new ChunkDensityFunction(visitor.apply(wrapped), binding, chunk, scale);
  }

  @Override
  public DensityFunction mapAll(DensityFunction.Visitor visitor) {
    return new ChunkDensityFunction(wrapped.mapAll(visitor), binding, chunk, scale);
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
