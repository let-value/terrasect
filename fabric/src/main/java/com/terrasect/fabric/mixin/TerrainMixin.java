package com.terrasect.fabric.mixin;

import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.lookup.TerrainHeightLookup;
import com.terrasect.common.util.MutablePointContext;
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
 * Mixin that applies terrain height constraints without allocating wrapper objects.
 *
 * <p>Instead of wrapping density functions, this mixin intercepts at two key points:
 * <ul>
 *   <li>{@code getInterpolatedState} - returns fluid (water/air) for positions above height constraint
 *   <li>{@code computePreliminarySurfaceLevel} - clamps surface level to max height
 * </ul>
 *
 * <p>By handling height constraints at {@code getInterpolatedState}, the Aquifer is never
 * involved for constrained positions. The fluid picker determines whether to place water
 * (below sea level) or air (above sea level).
 *
 * <p>The height lookup preserves natural terrain variation by mapping Minecraft's
 * preliminary surface level into the configured height range, creating interesting
 * ocean floors instead of flat planes.
 */
@Mixin(NoiseChunk.class)
public class TerrainMixin {

    /** Pre-computed height lookup for this chunk */
    @Unique private TerrainHeightLookup terrasect$heightLookup;

    /** Fluid picker for determining water/air above height constraints */
    @Unique private Aquifer.FluidPicker terrasect$fluidPicker;

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
     */
    @Inject(
            method = "<init>",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/NoiseChunk;preliminarySurfaceLevel:Lnet/minecraft/world/level/levelgen/DensityFunction;",
                            opcode = Opcodes.PUTFIELD,
                            shift = At.Shift.AFTER))
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

        // Get the preliminary surface level density function for natural terrain variation
        NoiseRouter router = randomState.router();
        DensityFunction surfaceLevel = router.preliminarySurfaceLevel();

        // Build lookup with surface sampler for interesting terrain
        MutablePointContext pointCtx = new MutablePointContext();
        terrasect$heightLookup = TerrainHeightLookup.build(ctx, chunkMinX, chunkMinZ, (x, z) -> {
            pointCtx.set(x, 0, z);
            return (int) Math.floor(surfaceLevel.compute(pointCtx));
        });
    }

    /**
     * For positions above height constraint, return fluid directly.
     *
     * <p>This is the single interception point for terrain height constraints.
     * The FluidPicker returns water below sea level and air above it.
     */
    @Inject(method = "getInterpolatedState", at = @At("HEAD"), cancellable = true)
    private void constrainTerrainHeight(CallbackInfoReturnable<BlockState> cir) {
        if (terrasect$heightLookup == null) return;

        // NoiseChunk implements FunctionContext, so we can read block coords directly
        NoiseChunk self = (NoiseChunk) (Object) this;
        int blockX = self.blockX();
        int blockY = self.blockY();
        int blockZ = self.blockZ();

        int maxHeight = terrasect$heightLookup.getMaxHeight(blockX, blockZ);
        if (maxHeight != TerrainHeightLookup.NO_CONSTRAINT && blockY > maxHeight) {
            // Above height constraint - use fluid picker to determine water or air
            Aquifer.FluidStatus fluidStatus = terrasect$fluidPicker.computeFluid(blockX, blockY, blockZ);
            cir.setReturnValue(fluidStatus.at(blockY));
            return;
        }
    }

    /**
     * Clamp preliminary surface level to max height constraint.
     *
     * <p>This helps the Aquifer and SurfaceRules know about the constrained surface,
     * enabling optimizations like skipSamplingAboveY.
     */
    @Inject(method = "computePreliminarySurfaceLevel", at = @At("RETURN"), cancellable = true)
    private void clampPreliminarySurfaceLevel(long packedPos, CallbackInfoReturnable<Integer> cir) {
        if (terrasect$heightLookup == null) return;

        int x = (int) (packedPos & 0xFFFFFFFFL);
        int z = (int) (packedPos >>> 32);
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
