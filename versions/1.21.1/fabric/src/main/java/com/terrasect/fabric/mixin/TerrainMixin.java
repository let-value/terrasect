package com.terrasect.fabric.mixin;

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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Version-specific mixin for 1.21.1 that applies terrain height constraints.
 * 
 * <p>Note: In 1.21.1, NoiseRouter does NOT have a preliminarySurfaceLevel() method.
 * We use the depth function as an approximation for surface variation.
 */
@Mixin(NoiseChunk.class)
public class TerrainMixin {
    
    /** Pre-computed height lookup for this chunk */
    @Unique
    private TerrainHeightLookup terrasect$heightLookup;
    
    /** Fluid picker for determining water/air above height constraints */
    @Unique
    private Aquifer.FluidPicker terrasect$fluidPicker;
    
    /**
     * Build TerrainHeightLookup BEFORE Aquifer is constructed.
     * 
     * <p>This injection targets the write to {@code preliminarySurfaceLevel} field,
     * which happens right before Aquifer creation. We can't use HEAD because that's
     * before super() and 'this' isn't available. We can't use TAIL because Aquifer
     * is already built by then.
     * 
     * <p>By building the lookup before Aquifer construction, our clamping of
     * computePreliminarySurfaceLevel will be active when the Aquifer calculates
     * skipSamplingAboveY, ensuring carvers correctly place water instead of air
     * above our height constraints.
     * 
     * <p>Note: In 1.21.1, NoiseRouter does NOT have a preliminarySurfaceLevel() method.
     * We use the depth function as an approximation for surface variation.
     */
    @Inject(method = "<init>", at = @At(
        value = "FIELD",
        target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;preliminarySurfaceLevel:Lnet/minecraft/world/level/levelgen/DensityFunction;",
        opcode = Opcodes.PUTFIELD,
        shift = At.Shift.AFTER
    ))
    private void initHeightConstraintsEarly(
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
        
        // In 1.21.1, NoiseRouter doesn't have preliminarySurfaceLevel()
        // Use depth function as approximation for surface variation
        NoiseRouter router = randomState.router();
        DensityFunction depth = router.depth();
        
        terrasect$heightLookup = TerrainHeightLookup.build(ctx, chunkMinX, chunkMinZ, 
            (x, z) -> {
                // Approximate surface level from depth (depth=0 at surface, negative above)
                // Sample at Y=64 (sea level) and convert depth to height
                DensityFunction.SinglePointContext pointCtx = 
                    new DensityFunction.SinglePointContext(x, 64, z);
                double depthValue = depth.compute(pointCtx);
                // depth is roughly 0 at surface, so surface ≈ 64 + (depth * scale)
                return 64 + (int)(depthValue * 128);
            });
    }
    
    /**
     * For positions above height constraint, return fluid directly.
     */
    @Inject(method = "getInterpolatedState", at = @At("HEAD"), cancellable = true)
    private void constrainTerrainHeight(CallbackInfoReturnable<BlockState> cir) {
        if (terrasect$heightLookup == null) return;
        
        
        NoiseChunk self = (NoiseChunk)(Object)this;
        int blockX = self.blockX();
        int blockY = self.blockY();
        int blockZ = self.blockZ();
        
        int maxHeight = terrasect$heightLookup.getMaxHeight(blockX, blockZ);
        if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && blockY > maxHeight) {
            Aquifer.FluidStatus fluidStatus = terrasect$fluidPicker.computeFluid(blockX, blockY, blockZ);
            cir.setReturnValue(fluidStatus.at(blockY));
            return;
        }
    }
    
    /**
     * Clamp preliminary surface level to max height constraint.
     */
    @Inject(method = "computePreliminarySurfaceLevel", at = @At("RETURN"), cancellable = true)
    private void clampPreliminarySurfaceLevel(long packedPos, CallbackInfoReturnable<Integer> cir) {
        if (terrasect$heightLookup == null) return;
        
        
        int x = (int)(packedPos & 0xFFFFFFFFL);
        int z = (int)(packedPos >>> 32);
        int maxHeight = terrasect$heightLookup.getMaxHeight(x, z);
        
        if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT) {
            int original = cir.getReturnValue();
            if (original > maxHeight) {
                cir.setReturnValue(maxHeight);
                return;
            }
        }
    }
}
