package com.terrasect.fabric.mixin;

import com.mojang.datafixers.util.Pair;
import com.terrasect.common.generation.definition.SelectionRules;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.handler.BiomeHandler;
import com.terrasect.fabric.generation.MinecraftContext;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin for MultiNoiseBiomeSource that applies region-based biome filtering.
 * 
 * <p>Redirects getNoiseBiome() to filter biomes based on region rules.
 * Uses pre-built {@link BiomeLookup} for O(1) biome checking.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class BiomeMixin {

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
        
        Climate.ParameterList<Holder<Biome>> parameterList = 
            ((MultiNoiseBiomeSourceAccessor) self).getParameters().map(
                list -> list,
                holder -> holder.value().parameters()
            );
        
        MinecraftContext context = MinecraftContext.get(sampler);
        if (context == null) {
            return parameterList.findValue(targetPoint);
        }
        
        SelectionRules rules = BiomeHandler.getRules(context, quartX, quartZ);
        if (rules == null) {
            Holder<Biome> result = parameterList.findValue(targetPoint);
            return result;
        }
        
        // Filter biomes using O(1) lookup
        BiomeLookup<Holder<Biome>> lookup = context.getBiomeLookup();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> allowed = new ArrayList<>();
        
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : parameterList.values()) {
            if (BiomeHandler.isBiomeAllowed(lookup, entry.getSecond(), rules)) {
                allowed.add(entry);
            }
        }
        
        Holder<Biome> result = allowed.isEmpty() 
            ? parameterList.findValue(targetPoint)
            : new Climate.ParameterList<>(allowed).findValue(targetPoint);
        
        BiomeHandler.recordResult(context, quartX, quartZ, MinecraftContext.getBiomeId(result), true);
        return result;
    }
}
