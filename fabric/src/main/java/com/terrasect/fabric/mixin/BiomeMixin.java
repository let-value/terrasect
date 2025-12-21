package com.terrasect.fabric.mixin;

import com.terrasect.common.api.Region;
import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.runtime.handler.BiomeHandler;
import com.terrasect.common.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin for MultiNoiseBiomeSource that applies region-based biome filtering.
 * 
 * <p>Uses pre-filtered {@link Climate.ParameterList} instances cached in {@link MinecraftContext}.
 * The filtering is done once when a new SelectionRules is encountered, then cached.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class BiomeMixin {

    /**
     * Redirect the internal getNoiseBiome(TargetPoint) to use filtered parameter list.
     */
    @Redirect(
        method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource;getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;"
        )
    )
    private Holder<Biome> terrasect$filterBiome(
            MultiNoiseBiomeSource self,
            Climate.TargetPoint targetPoint,
            int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        
        // Early exit if no context
        var context = MinecraftContext.get(sampler);
        if (context == null) {
            return ((MultiNoiseBiomeSourceAccessor) self).getParameters()
                .map(list -> list, holder -> holder.value().parameters())
                .findValue(targetPoint);
        }
        
        // Get region and rules in single lookup
        Region region = BiomeHandler.getRegion(context, quartX, quartZ);
        SelectionRules rules = BiomeHandler.getRules(region);
        
        // Get filtered parameter list from context (cached internally)
        var parameterList = context.getFilteredParameterList(rules);
        boolean wasFiltered = rules != null && (rules.hasAllowRules() || rules.hasBlockRules());
        
        Holder<Biome> result = parameterList.findValue(targetPoint);
        BiomeHandler.recordResult(quartX, quartZ, 
            MinecraftContext.getBiomeId(result), region, wasFiltered);
        return result;
    }
}
