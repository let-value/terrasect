package com.terrasect.fabric.mixin;

import com.terrasect.common.Terrasect;
import com.terrasect.fabric.generation.FabricNarrGenContext;
import com.terrasect.common.api.Strategy;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.runtime.handler.ClimateHandler;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin for MultiNoiseBiomeSource that applies region-based climate modifications.
 * 
 * Target method structure (from Minecraft 1.21.1):
 * <pre>
 * public Holder<Biome> getNoiseBiome(int i, int j, int k, Climate.Sampler sampler) {
 *     return this.getNoiseBiome(sampler.sample(i, j, k));
 * }
 * </pre>
 * 
 * We redirect the sampler.sample() call to apply climate modifications.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class ClimateMixin {

    @Unique
    private static int terrasect$logCounter = 0;
    
    @Unique
    private static int terrasect$modifiedCount = 0;
    
    @Unique
    private static int terrasect$noContextCount = 0;
    
    @Unique
    private static int terrasect$totalCalls = 0;

    /**
     * Redirect the sampler.sample() call to apply region-based climate modifications.
     * The coordinates (i, j, k) are in quart coordinates (block >> 2).
     */
    @Redirect(
        method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Climate$Sampler;sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;"
        )
    )
    private Climate.TargetPoint terrasect$modifyClimate(Climate.Sampler sampler, int x, int y, int z) {
        terrasect$totalCalls++;
        
        // Log first call to confirm mixin is working
        if (terrasect$totalCalls == 1) {
            Terrasect.LOGGER.info("ClimateMixin: FIRST CALL - Mixin is active! Quart coords: ({}, {}, {})", x, y, z);
        }
        
        // Log calls near spawn (within 50 quarts = 200 blocks)
        int blockX = x << 2;
        int blockZ = z << 2;
        int distSq = blockX * blockX + blockZ * blockZ;
        if (distSq < 200 * 200) {
            Terrasect.LOGGER.info("ClimateMixin: NEAR SPAWN! B({}, {}) from Q({}, {}, {})", blockX, blockZ, x, y, z);
        }
        
        // Log every 5000 calls for debugging
        if (terrasect$totalCalls % 5000 == 0) {
            Terrasect.LOGGER.info("ClimateMixin: Stats - total={}, modified={}, noContext={}", 
                terrasect$totalCalls, terrasect$modifiedCount, terrasect$noContextCount);
        }
        
        // Get the original sample
        Climate.TargetPoint original = sampler.sample(x, y, z);
        
        // Get platform-specific context
        Strategy context = FabricNarrGenContext.get(sampler);
        
        if (context == null) {
            terrasect$noContextCount++;
            // Record unmodified sample
            MixinSampler.recordMixinIO(x, y, z,
                original.temperature(), original.humidity(),
                original.continentalness(), original.erosion(), original.depth(), original.weirdness(),
                original.temperature(), original.humidity(),
                null, false);
            // Log occasionally
            if (terrasect$logCounter++ % 10000 == 0) {
                Terrasect.LOGGER.warn("ClimateMixin: No context for sampler! noContext={}, modified={}", 
                    terrasect$noContextCount, terrasect$modifiedCount);
            }
            return original; // No context, use vanilla
        }
        
        // Apply climate modifications using common handler
        // Pass quart coordinates - ClimateHandler will convert to block coords
        ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
            context, x, y, z,
            original.temperature(),
            original.humidity()
        );
        
        if (!result.modified()) {
            // Record unmodified sample with region info
            MixinSampler.recordMixinIO(x, y, z,
                original.temperature(), original.humidity(),
                original.continentalness(), original.erosion(), original.depth(), original.weirdness(),
                original.temperature(), original.humidity(),
                result.regionName(), false);
            return original;
        }
        
        terrasect$modifiedCount++;
        
        // Record modified sample
        MixinSampler.recordMixinIO(x, y, z,
            original.temperature(), original.humidity(),
            original.continentalness(), original.erosion(), original.depth(), original.weirdness(),
            result.temperature(), result.humidity(),
            result.regionName(), true);
        
        // Log occasionally when we actually modify
        if (terrasect$modifiedCount % 1000 == 1) {
            Terrasect.LOGGER.info("ClimateMixin: Modified climate at ({}, {}) - temp: {} -> {}, humid: {} -> {}",
                blockX, blockZ, original.temperature(), result.temperature(),
                original.humidity(), result.humidity());
        }
        
        // Return modified climate point
        return new Climate.TargetPoint(
            result.temperature(),
            result.humidity(),
            original.continentalness(),
            original.erosion(),
            original.depth(),
            original.weirdness()
        );
    }
}
