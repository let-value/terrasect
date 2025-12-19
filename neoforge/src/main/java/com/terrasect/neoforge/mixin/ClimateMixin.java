package com.terrasect.neoforge.mixin;

import com.terrasect.neoforge.generation.NeoForgeNarrGenContext;
import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.mixin.ClimateHandler;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeoForge mixin for MultiNoiseBiomeSource that applies region-based climate modifications.
 * 
 * This mixin ONLY handles climate (temperature/humidity) adjustments based on region settings.
 * Biome filtering is handled separately by BiomeMixin.
 * 
 * The actual climate calculation logic is in the common ClimateHandler class.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class ClimateMixin {

    /**
     * Redirect climate sampling to apply region-based climate modifications.
     */
    @Redirect(
        method = "getNoiseBiome",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Climate$Sampler;sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;"
        )
    )
    private Climate.TargetPoint terrasect$modifyClimate(Climate.Sampler sampler, int x, int y, int z) {
        Climate.TargetPoint original = sampler.sample(x, y, z);

        // Get platform-specific context
        Strategy context = NeoForgeNarrGenContext.get(sampler);
        
        // Use common handler for climate modification
        ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
            context, x, y, z,
            original.temperature(),
            original.humidity()
        );
        
        if (!result.modified()) {
            return original;
        }
        
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
