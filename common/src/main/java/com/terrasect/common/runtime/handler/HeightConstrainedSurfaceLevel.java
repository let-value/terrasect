package com.terrasect.common.runtime.handler;

import com.terrasect.common.devtools.Profiler;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps preliminarySurfaceLevel() to clamp surface level to maxHeight.
 * Ensures aquifer fills water correctly above constrained terrain.
 * 
 * <p>Uses pre-computed {@link TerrainHeightLookup} for O(1) height lookups,
 * avoiding repeated region traversal during terrain generation.
 */
public record HeightConstrainedSurfaceLevel(
    DensityFunction wrapped,
    @Nullable TerrainHeightLookup lookup
) implements DensityFunction {
    
    @Override
    public double compute(FunctionContext ctx) {
        long t0 = Profiler.begin();
        double surface = wrapped.compute(ctx);
        
        // Fast path: no constraints in this chunk
        if (lookup == null) {
            Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
            return surface;
        }
        
        int maxHeight = lookup.getMaxHeight(ctx.blockX(), ctx.blockZ());
        Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
        
        // Clamp surface level to max height if constrained
        return (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && surface > maxHeight) 
            ? maxHeight : surface;
    }
    
    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        long t0 = Profiler.begin();
        wrapped.fillArray(array, contextProvider);
        
        // Fast path: no constraints in this chunk
        if (lookup == null) {
            Profiler.end(Profiler.TERRAIN_FILL_ARRAY, t0);
            return;
        }
        
        for (int i = 0; i < array.length; i++) {
            FunctionContext ctx = contextProvider.forIndex(i);
            int maxHeight = lookup.getMaxHeight(ctx.blockX(), ctx.blockZ());
            
            if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && array[i] > maxHeight) {
                array[i] = maxHeight;
            }
        }
        Profiler.end(Profiler.TERRAIN_FILL_ARRAY, t0);
    }
    
    @Override
    public double minValue() { return wrapped.minValue(); }
    
    @Override
    public double maxValue() { return wrapped.maxValue(); }
    
    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return new HeightConstrainedSurfaceLevel(wrapped.mapAll(visitor), lookup);
    }
    
    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return wrapped.codec(); }
}
