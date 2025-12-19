package com.terrasect.neoforge.mixin;

import com.terrasect.neoforge.generation.NeoForgeNarrGenContext;
import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.mixin.BiomeHandler;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge mixin for MultiNoiseBiomeSource that filters the biome candidate list.
 * 
 * This mixin ONLY handles biome filtering based on SelectionRules.
 * Climate modifications are handled separately by ClimateMixin.
 * 
 * The region lookup and rule checking logic is in the common BiomeHandler class.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class BiomeMixin {

    /**
     * Cache of filtered parameter lists per SelectionRules.
     */
    @Unique
    private final ConcurrentHashMap<Integer, Climate.ParameterList<Holder<Biome>>> terrasect$filteredListCache = new ConcurrentHashMap<>();

    /**
     * Redirect the findValue call to use a filtered parameter list when biome rules are present.
     */
    @Redirect(
        method = "getNoiseBiome",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Climate$ParameterList;findValue(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Ljava/lang/Object;"
        )
    )
    private Object terrasect$filterBiomeCandidates(
            Climate.ParameterList<Holder<Biome>> parameterList,
            Climate.TargetPoint targetPoint,
            int x, int y, int z, Climate.Sampler sampler) {
        
        // Get platform-specific context
        Strategy context = NeoForgeNarrGenContext.get(sampler);
        
        // Use common handler to check if filtering is needed
        BiomeHandler.FilterContext filterContext = BiomeHandler.getFilterContext(context, x, z);
        
        if (!filterContext.shouldFilter()) {
            return parameterList.findValue(targetPoint);
        }
        
        // Get or build filtered parameter list
        int cacheKey = BiomeHandler.computeRulesCacheKey(filterContext.rules());
        Climate.ParameterList<Holder<Biome>> filteredList = terrasect$filteredListCache.computeIfAbsent(
            cacheKey,
            key -> terrasect$buildFilteredList(parameterList, filterContext)
        );
        
        return filteredList.findValue(targetPoint);
    }
    
    /**
     * Build a filtered parameter list containing only allowed biomes.
     */
    @Unique
    private Climate.ParameterList<Holder<Biome>> terrasect$buildFilteredList(
            Climate.ParameterList<Holder<Biome>> original, 
            BiomeHandler.FilterContext filterContext) {
        
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> originalValues = original.values();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> filteredValues = new ArrayList<>();
        
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : originalValues) {
            Holder<Biome> biome = entry.getSecond();
            String biomeId = terrasect$getBiomeId(biome);
            Set<String> biomeTags = terrasect$getBiomeTags(biome);
            
            if (BiomeHandler.isBiomeAllowed(filterContext.rules(), biomeId, biomeTags)) {
                filteredValues.add(entry);
            }
        }
        
        // If filtering removed all biomes, return the original list as fallback
        if (filteredValues.isEmpty()) {
            return original;
        }
        
        return new Climate.ParameterList<>(filteredValues);
    }
    
    @Unique
    private String terrasect$getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(key -> key.location().toString())
            .orElse("unknown");
    }
    
    @Unique
    private Set<String> terrasect$getBiomeTags(Holder<Biome> biome) {
        Set<String> tags = new HashSet<>();
        biome.tags().forEach(tag -> tags.add("#" + tag.location().toString()));
        return tags;
    }
}
