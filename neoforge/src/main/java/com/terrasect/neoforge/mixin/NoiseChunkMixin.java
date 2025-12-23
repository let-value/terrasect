package com.terrasect.neoforge.mixin;

import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.runtime.handler.TerrainHeightLookup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that applies terrain height constraints without allocating wrapper objects.
 * 
 * <p>Instead of wrapping density functions, this mixin intercepts at two key points:
 * <ul>
 *   <li>{@code getInterpolatedState} - returns fluid (water/air) for positions above height constraint
 *   <li>{@code computePreliminarySurfaceLevel} - clamps surface level to max height
 * </ul>
 */
@Mixin(NoiseChunk.class)
public class NoiseChunkMixin {
    
    /** Pre-computed height lookup for this chunk */
    @Unique
    private TerrainHeightLookup terrasect$heightLookup;
    
    /** Fluid picker for determining water/air above height constraints */
    @Unique
    private Aquifer.FluidPicker terrasect$fluidPicker;
    
    /**
     * Capture FluidPicker and build TerrainHeightLookup at construction.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void initHeightConstraints(
            int cellCountXZ,
            RandomState randomState,
            int chunkMinX,
            int chunkMinZ,
            NoiseSettings noiseSettings,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings generatorSettings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender,
            CallbackInfo ci) {
        this.terrasect$fluidPicker = fluidPicker;
        MinecraftContext ctx = MinecraftContext.get(randomState.sampler());
        
        NoiseRouter router = randomState.router();
        DensityFunction surfaceLevel = router.preliminarySurfaceLevel();
        
        terrasect$heightLookup = TerrainHeightLookup.build(ctx, chunkMinX, chunkMinZ, 
            (x, z) -> {
                DensityFunction.SinglePointContext pointCtx = 
                    new DensityFunction.SinglePointContext(x, 0, z);
                return (int) Math.floor(surfaceLevel.compute(pointCtx));
            });
    }
    
    /**
     * For positions above height constraint, return fluid directly.
     */
    @Inject(method = "getInterpolatedState", at = @At("HEAD"), cancellable = true)
    private void constrainTerrainHeight(CallbackInfoReturnable<BlockState> cir) {
        if (terrasect$heightLookup == null) return;
        
        long t0 = Profiler.begin();
        
        NoiseChunk self = (NoiseChunk)(Object)this;
        int blockX = self.blockX();
        int blockY = self.blockY();
        int blockZ = self.blockZ();
        
        int maxHeight = terrasect$heightLookup.getMaxHeight(blockX, blockZ);
        if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && blockY > maxHeight) {
            Aquifer.FluidStatus fluidStatus = terrasect$fluidPicker.computeFluid(blockX, blockY, blockZ);
            Profiler.end(Profiler.TERRAIN_HEIGHT_CHECK, t0);
            cir.setReturnValue(fluidStatus.at(blockY));
            return;
        }
        Profiler.end(Profiler.TERRAIN_HEIGHT_CHECK, t0);
    }
    
    /**
     * Clamp preliminary surface level to max height constraint.
     */
    @Inject(method = "computePreliminarySurfaceLevel", at = @At("RETURN"), cancellable = true)
    private void clampPreliminarySurfaceLevel(long packedPos, CallbackInfoReturnable<Integer> cir) {
        if (terrasect$heightLookup == null) return;
        
        long t0 = Profiler.begin();
        
        int x = (int)(packedPos & 0xFFFFFFFFL);
        int z = (int)(packedPos >>> 32);
        int maxHeight = terrasect$heightLookup.getMaxHeight(x, z);
        
        if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT) {
            int original = cir.getReturnValue();
            if (original > maxHeight) {
                Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
                cir.setReturnValue(maxHeight);
                return;
            }
        }
        Profiler.end(Profiler.TERRAIN_DENSITY_COMPUTE, t0);
    }
}
