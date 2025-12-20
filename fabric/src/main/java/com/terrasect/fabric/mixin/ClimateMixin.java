package com.terrasect.fabric.mixin;

import com.terrasect.fabric.generation.MinecraftContext;
import com.terrasect.common.runtime.handler.ClimateHandler;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin for MultiNoiseBiomeSource that applies region-based climate modifications.
 * 
 * <p>This mixin is intentionally thin - it only redirects the sampler.sample() call
 * to {@link ClimateHandler#modifyClimate}. All logic, debug logging, and statistics
 * tracking are handled in the common handler.
 * 
 * <p>Target method structure (from Minecraft 1.21.x):
 * <pre>
 * public Holder&lt;Biome&gt; getNoiseBiome(int i, int j, int k, Climate.Sampler sampler) {
 *     return this.getNoiseBiome(sampler.sample(i, j, k));
 * }
 * </pre>
 */
@Mixin(MultiNoiseBiomeSource.class)
public class ClimateMixin {

    /**
     * Redirect the sampler.sample() call to apply region-based climate modifications.
     * The coordinates (x, y, z) are in quart coordinates (block >> 2).
     */
    @Redirect(
        method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
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
