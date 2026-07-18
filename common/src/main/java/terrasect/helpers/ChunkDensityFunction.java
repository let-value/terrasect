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

  // Region rarely changes between adjacent samples, so remember the last resolution to skip the
  // per-sample IdentityHashMap lookup for runs of the same region.
  private Region cachedRegion;
  private ResolvedNoise cachedResolved;

  private double apply(double original, int blockX, int blockZ) {
    Region region;
    float distance;
    // Read region and distance off a single grid index; both are populated together per cell.
    Region[] regions = chunk.regions;
    int localX = blockX - chunk.originX;
    int localZ = blockZ - chunk.originZ;
    if (regions != null
        && localX >= 0
        && localX < chunk.width
        && localZ >= 0
        && localZ < chunk.height) {
      int cell = localX * chunk.height + localZ;
      region = regions[cell];
      distance = chunk.distances[cell];
    } else {
      region = chunk.getRegion(blockX, blockZ);
      distance = chunk.getDistance(blockX, blockZ);
    }
    if (region == null) {
      return original;
    }
    ResolvedNoise resolved;
    if (region == cachedRegion) {
      resolved = cachedResolved;
    } else {
      resolved = binding.get(region);
      cachedRegion = region;
      cachedResolved = resolved;
    }
    if (resolved == null) {
      return original;
    }
    float strength = NoiseHandler.getStrength(resolved.blendWidth, distance);
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
