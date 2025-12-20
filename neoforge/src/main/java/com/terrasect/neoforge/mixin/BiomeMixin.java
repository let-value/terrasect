package com.terrasect.neoforge.mixin;

import com.terrasect.neoforge.generation.NeoForgeNarrGenContext;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.handler.BiomeHandler;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge mixin for MultiNoiseBiomeSource that filters the biome candidate list.
 * 
 * This mixin ONLY handles biome filtering based on SelectionRules.
 * Climate modifications are handled separately by ClimateMixin.
 * 
 * The region lookup and rule checking logic is in the common BiomeHandler class.
 * 
 * <p>Uses {@link BiomeLookup} from {@link NeoForgeNarrGenContext} for O(1) 
 * biome filtering. The lookup is pre-built during dimension initialization,
 * so this mixin has no initialization logic.
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
        
        // Get platform-specific context (contains pre-built biome lookup)
        NeoForgeNarrGenContext context = NeoForgeNarrGenContext.get(sampler);
        
        // Use common handler to check if filtering is needed
        BiomeHandler.FilterContext filterContext = BiomeHandler.getFilterContext(context, x, z);
        
        if (!filterContext.shouldFilter()) {
            Object result = parameterList.findValue(targetPoint);
            // Record unfiltered biome selection
            if (result instanceof Holder<?> holder) {
                @SuppressWarnings("unchecked")
                Holder<Biome> biomeHolder = (Holder<Biome>) holder;
                BiomeHandler.recordBiomeSelection(x, z, terrasect$getBiomeId(biomeHolder), 
                    filterContext.regionName(), false);
            }
            return result;
        }
        
        // Get pre-built biome lookup from context (built during dimension init)
        BiomeLookup<Holder<Biome>> biomeLookup = context != null ? context.getBiomeLookup() : null;
        
        // Get or build filtered parameter list using the lookup for O(1) filtering
        int cacheKey = BiomeHandler.computeRulesCacheKey(filterContext.rules());
        final BiomeLookup<Holder<Biome>> lookup = biomeLookup;
        Climate.ParameterList<Holder<Biome>> filteredList = terrasect$filteredListCache.computeIfAbsent(
            cacheKey,
            key -> terrasect$buildFilteredList(parameterList, filterContext, lookup)
        );
        
        Object result = filteredList.findValue(targetPoint);
        // Record filtered biome selection
        if (result instanceof Holder<?> holder) {
            @SuppressWarnings("unchecked")
            Holder<Biome> biomeHolder = (Holder<Biome>) holder;
            BiomeHandler.recordBiomeSelection(x, z, terrasect$getBiomeId(biomeHolder),
                filterContext.regionName(), true);
        }
        return result;
    }

    /**
     * Build a filtered parameter list containing only allowed biomes.
     * Uses BiomeMetadataLookup for O(1) biome checks when available.
     */
    @Unique
    private Climate.ParameterList<Holder<Biome>> terrasect$buildFilteredList(
            Climate.ParameterList<Holder<Biome>> original, 
            BiomeHandler.FilterContext filterContext,
            BiomeLookup<Holder<Biome>> biomeLookup) {
        
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> originalValues = original.values();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> filteredValues = new ArrayList<>();
        
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : originalValues) {
            Holder<Biome> biome = entry.getSecond();
            
            boolean allowed;
            if (biomeLookup != null) {
                // O(1) lookup using pre-computed metadata
                allowed = BiomeHandler.isBiomeAllowed(biomeLookup, biome, filterContext.rules());
            } else {
                // Fallback: extract metadata on demand (slower path)
                allowed = BiomeHandler.isBiomeAllowed(filterContext.rules(), 
                    terrasect$getBiomeId(biome), terrasect$getBiomeTags(biome));
            }
            
            if (allowed) {
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
            .map(key -> key.identifier().toString())
            .orElse("unknown");
    }
    
    @Unique
    private java.util.Set<String> terrasect$getBiomeTags(Holder<Biome> biome) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        biome.tags().forEach(tag -> tags.add("#" + tag.location().toString()));
        return tags;
    }
}
