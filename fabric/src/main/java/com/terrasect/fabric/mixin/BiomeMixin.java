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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin for MultiNoiseBiomeSource that applies region-based biome filtering.
 * 
 * <p>Uses pre-filtered {@link Climate.ParameterList} instances cached per SelectionRules.
 * The filtering is done once when a new SelectionRules is encountered, then cached.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class BiomeMixin {
    
    // Cache filtered ParameterLists per SelectionRules
    // This is the MC-specific cache that wraps BiomeLookup's filtered biome sets
    private static final Map<SelectionRules, Climate.ParameterList<Holder<Biome>>> FILTERED_LISTS = 
        new ConcurrentHashMap<>();

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
        
        // Get the original parameter list
        Climate.ParameterList<Holder<Biome>> originalList = 
            ((MultiNoiseBiomeSourceAccessor) self).getParameters().map(
                list -> list,
                holder -> holder.value().parameters()
            );
        
        // Early exit if no context
        MinecraftContext context = MinecraftContext.get(sampler);
        if (context == null) {
            return originalList.findValue(targetPoint);
        }
        
        // Get selection rules for this position
        SelectionRules rules = BiomeHandler.getRules(context, quartX, quartZ);
        if (rules == null || (!rules.hasAllowRules() && !rules.hasBlockRules())) {
            return originalList.findValue(targetPoint);
        }
        
        // Get or create filtered parameter list for these rules
        Climate.ParameterList<Holder<Biome>> filteredList = FILTERED_LISTS.computeIfAbsent(
            rules,
            r -> buildFilteredParameterList(originalList, context.getBiomeLookup(), r)
        );
        
        return filteredList.findValue(targetPoint);
    }
    
    /**
     * Build a filtered ParameterList for the given rules.
     * Called once per unique SelectionRules, then cached.
     */
    private static Climate.ParameterList<Holder<Biome>> buildFilteredParameterList(
            Climate.ParameterList<Holder<Biome>> original,
            BiomeLookup<Holder<Biome>> lookup,
            SelectionRules rules) {
        
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> originalEntries = original.values();
        
        // Use BiomeLookup's filtering which caches the allowed biome set
        BiomeLookup.FilteredEntries<Pair<Climate.ParameterPoint, Holder<Biome>>> filtered =
            lookup.filterEntries(originalEntries, Pair::getSecond, rules);
        
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> filteredEntries = filtered.entries();
        
        // If everything was filtered out, keep at least the first biome as fallback
        if (filteredEntries.isEmpty() && !originalEntries.isEmpty()) {
            filteredEntries = List.of(originalEntries.get(0));
        }
        
        return new Climate.ParameterList<>(filteredEntries);
    }
}
