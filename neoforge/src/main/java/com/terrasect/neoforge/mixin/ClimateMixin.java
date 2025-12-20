package com.terrasect.neoforge.mixin;

import com.terrasect.neoforge.generation.MinecraftContext;
import com.terrasect.common.runtime.handler.ClimateHandler;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeoForge mixin for MultiNoiseBiomeSource that applies region-based climate modifications.
 * 
 * <p>This mixin is intentionally thin - it only redirects the sampler.sample() call
 * to {@link ClimateHandler#modifyClimate}. All logic, debug logging, and statistics
 * tracking are handled in the common handler.
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
        // Get the original sample first
        Climate.TargetPoint original = sampler.sample(x, y, z);

        // Get context and delegate to common handler
        MinecraftContext context = MinecraftContext.get(sampler);
        
        ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
            context, x, y, z,
            original.temperature(),
            original.humidity()
        );
        
        // If not modified, return original
        if (!result.modified()) {
            return original;
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
