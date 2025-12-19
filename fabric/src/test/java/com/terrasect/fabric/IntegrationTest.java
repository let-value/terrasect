package com.terrasect.fabric;

import com.terrasect.common.generation.*;
import com.terrasect.common.generation.mixin.ClimateHandler;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify the mod's climate and biome modifications
 * work correctly with Minecraft's world generation systems.
 * 
 * These tests run without a full game client, using Minecraft's Bootstrap
 * to initialize the required registries.
 */
public class IntegrationTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testRegionsAreConfigured() {
        // Register test regions
        TestRegions.register();
        
        Region root = World.getRoot();
        assertNotNull(root, "Root region should be set");
        assertEquals("WORLD", root.name());
        assertFalse(root.children().isEmpty(), "World should have child regions");
        
        System.out.println("Root region: " + root.name());
        System.out.println("Child regions: " + root.children().size());
        for (Region child : root.children()) {
            System.out.println("  - " + child.name() + " (climate: " + child.definition().climate() + ")");
        }
    }

    @Test
    public void testClimateModificationWorks() {
        long seed = 12345L;
        
        // Register test regions
        TestRegions.register();
        
        // Setup Minecraft climate system
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = lookup.lookupOrThrow(Registries.NOISE);
        
        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }
        
        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        Climate.Sampler sampler = randomState.sampler();
        
        // Create a test strategy
        Strategy context = new Strategy() {
            @Override
            public long getSeed() { return seed; }
            
            @Override
            public float getRiverInfluence(int x, int z) { return 0.0f; }
            
            @Override
            public float getRidgeInfluence(int x, int z) { return 0.5f; }
        };
        
        // Test climate modification at various locations
        int modified = 0;
        int total = 0;
        
        for (int x = -1000; x <= 1000; x += 100) {
            for (int z = -1000; z <= 1000; z += 100) {
                int quartX = x >> 2;
                int quartZ = z >> 2;
                
                Climate.TargetPoint original = sampler.sample(quartX, 16, quartZ);
                
                ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
                    context, x, 64, z,
                    original.temperature(),
                    original.humidity()
                );
                
                total++;
                if (result.modified()) {
                    modified++;
                }
            }
        }
        
        System.out.println("Climate modification test:");
        System.out.println("  Total samples: " + total);
        System.out.println("  Modified: " + modified);
        System.out.println("  Modification rate: " + (100.0 * modified / total) + "%");
        
        assertTrue(modified > 0, "Some climate samples should be modified");
    }

    @Test
    public void testRegionLookup() {
        long seed = 12345L;
        
        // Register test regions
        TestRegions.register();
        
        Strategy context = new Strategy() {
            @Override
            public long getSeed() { return seed; }
            
            @Override
            public float getRiverInfluence(int x, int z) { return 0.0f; }
            
            @Override
            public float getRidgeInfluence(int x, int z) { return 0.5f; }
        };
        
        // Test region lookup at various locations
        java.util.Map<String, Integer> regionCounts = new java.util.HashMap<>();
        
        for (int x = -2000; x <= 2000; x += 50) {
            for (int z = -2000; z <= 2000; z += 50) {
                Region region = World.getRegion(x, z, context);
                String name = region != null ? region.name() : "null";
                regionCounts.merge(name, 1, Integer::sum);
            }
        }
        
        System.out.println("Region distribution:");
        regionCounts.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));
        
        // Verify we find multiple different regions
        assertTrue(regionCounts.size() > 1, "Should find multiple different regions");
    }

    @Test
    public void testBiomeSelectionWithClimate() {
        long seed = 12345L;
        
        // Register test regions
        TestRegions.register();
        
        // Setup Minecraft biome system
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = lookup.lookupOrThrow(Registries.NOISE);
        
        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }
        
        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        Climate.Sampler sampler = randomState.sampler();
        
        var parameterListLookup = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldParameters = parameterListLookup.getOrThrow(
            net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        Climate.ParameterList<Holder<Biome>> parameterList = overworldParameters.value().parameters();
        
        Strategy context = new Strategy() {
            @Override
            public long getSeed() { return seed; }
            
            @Override
            public float getRiverInfluence(int x, int z) { return 0.0f; }
            
            @Override
            public float getRidgeInfluence(int x, int z) { return 0.5f; }
        };
        
        // Compare vanilla vs modified biome selection
        int biomesChanged = 0;
        int total = 0;
        
        for (int x = -1000; x <= 1000; x += 100) {
            for (int z = -1000; z <= 1000; z += 100) {
                int quartX = x >> 2;
                int quartZ = z >> 2;
                
                Climate.TargetPoint original = sampler.sample(quartX, 16, quartZ);
                Holder<Biome> vanillaBiome = parameterList.findValue(original);
                
                ClimateHandler.ClimateResult result = ClimateHandler.modifyClimate(
                    context, x, 64, z,
                    original.temperature(),
                    original.humidity()
                );
                
                Climate.TargetPoint modified = new Climate.TargetPoint(
                    result.temperature(),
                    result.humidity(),
                    original.continentalness(),
                    original.erosion(),
                    original.depth(),
                    original.weirdness()
                );
                
                Holder<Biome> modifiedBiome = parameterList.findValue(modified);
                
                total++;
                if (!vanillaBiome.equals(modifiedBiome)) {
                    biomesChanged++;
                }
            }
        }
        
        System.out.println("Biome selection test:");
        System.out.println("  Total samples: " + total);
        System.out.println("  Biomes changed: " + biomesChanged);
        System.out.println("  Change rate: " + (100.0 * biomesChanged / total) + "%");
        
        assertTrue(biomesChanged > 0, "Some biomes should change due to climate modification");
    }
}
