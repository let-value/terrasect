package com.terrasect.common.generation;

import com.terrasect.common.Terrasect;
import com.terrasect.common.generation.definition.GenerationStrategyType;

/**
 * Pre-configured test regions for development and testing purposes.
 * 
 * This creates a world with multiple distinct regions showcasing different
 * climate settings and biome filtering rules.
 * 
 * Region layout (using Voronoi partitioning):
 * - SCORCHED_WASTES: Hot desert climate, blocks ocean/river biomes
 * - FROZEN_REACHES: Cold tundra climate, allows only cold biomes
 * - VERDANT_HEART: Temperate forest climate, prefers forest biomes
 * - MYSTIC_JUNGLE: Hot and humid, allows jungle biomes
 * - ANCIENT_HIGHLANDS: Mild climate, allows mountain biomes
 * - WILDLANDS: No modifications - vanilla Minecraft behavior
 */
public final class TestRegions {

    private TestRegions() {
    }

    /**
     * Build and register test regions for development.
     * Call this from mod initialization when you want to test with sample regions.
     */
    public static void register() {
        Terrasect.LOGGER.info("Registering test regions for development");
        
        Region root = buildTestWorld();
        World.setRoot(root);
        
        Terrasect.LOGGER.info("Test regions registered: {} child regions under WORLD", 
            root.children().size());
    }

    /**
     * Build a test world with diverse regions for testing climate and biome features.
     */
    public static Region buildTestWorld() {
        RegionRegistry registry = new RegionRegistry();
        
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("ROOT", regions -> regions
                .strategy(GenerationStrategyType.SUBDIVISION)
                // ===== Hot Desert Region =====
                // Extreme heat, no moisture. Blocks water biomes.
                .child("SCORCHED_WASTES", region -> region
                    .radius(100)
                    .climate(c -> c
                        .temperature(1.0f)   // Maximum heat
                        .humidity(0.0f))     // Bone dry
                    .biomes(b -> b
                        .blockTags("#minecraft:is_ocean", "#minecraft:is_river")
                        .blockNames("minecraft:swamp", "minecraft:mangrove_swamp")))
                
                // ===== Frozen Tundra Region =====
                // Extreme cold. Prefers cold biomes.
                .child("FROZEN_REACHES", region -> region
                    .radius(1000)
                    .climate(c -> c
                        .temperature(0.0f)   // Maximum cold
                        .humidity(0.3f))     // Some snow
                    .biomes(b -> b
                        .allowTags("#minecraft:is_taiga")
                        .allowNames(
                            "minecraft:snowy_plains", 
                            "minecraft:snowy_taiga",
                            "minecraft:snowy_beach",
                            "minecraft:snowy_slopes",
                            "minecraft:frozen_peaks",
                            "minecraft:jagged_peaks",
                            "minecraft:frozen_river",
                            "minecraft:ice_spikes",
                            "minecraft:grove")))
                
                // ===== Temperate Forest Region =====
                // Mild temperature, moderate humidity. Prefers forests.
                .child("VERDANT_HEART", region -> region
                    .radius(1000)
                    .climate(c -> c
                        .temperature(0.5f)   // Mild
                        .humidity(0.6f))     // Moist
                    .biomes(b -> b
                        .allowTags("#minecraft:is_forest")
                        .allowNames(
                            "minecraft:forest",
                            "minecraft:flower_forest", 
                            "minecraft:birch_forest",
                            "minecraft:old_growth_birch_forest",
                            "minecraft:dark_forest",
                            "minecraft:plains",
                            "minecraft:meadow")))
                
                // ===== Tropical Jungle Region =====
                // Hot and very humid. Allows jungle biomes.
                .child("MYSTIC_JUNGLE", region -> region
                    .radius(1000)
                    .climate(c -> c
                        .temperature(0.9f)   // Hot
                        .humidity(1.0f))     // Maximum humidity
                    .biomes(b -> b
                        .allowTags("#minecraft:is_jungle")
                        .allowNames(
                            "minecraft:jungle",
                            "minecraft:sparse_jungle",
                            "minecraft:bamboo_jungle",
                            "minecraft:swamp",
                            "minecraft:mangrove_swamp")))
                
                // ===== Mountain Highlands Region =====
                // Cool and dry. Allows mountain biomes.
                .child("ANCIENT_HIGHLANDS", region -> region
                    .radius(1000)
                    .climate(c -> c
                        .temperature(0.3f)   // Cool
                        .humidity(0.4f))     // Semi-dry
                    .biomes(b -> b
                        .allowTags("#minecraft:is_mountain")
                        .allowNames(
                            "minecraft:meadow",
                            "minecraft:stony_peaks",
                            "minecraft:frozen_peaks",
                            "minecraft:jagged_peaks",
                            "minecraft:snowy_slopes",
                            "minecraft:grove",
                            "minecraft:windswept_hills",
                            "minecraft:windswept_gravelly_hills",
                            "minecraft:windswept_forest")))
                
                // ===== Wildlands (Vanilla) =====
                // No climate or biome modifications - pure vanilla behavior.
                .child("WILDLANDS", region -> region
                    .radius(1000)));
        
        return registry.build("WORLD");
    }
    
    /**
     * Build a simple test world with fewer regions for quick testing.
     */
    public static Region buildSimpleTestWorld() {
        RegionRegistry registry = new RegionRegistry();
        
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("ROOT", regions -> regions
                .strategy(GenerationStrategyType.SUBDIVISION)
                
                // Hot region
                .child("HOT_ZONE", region -> region
                    .radius(1000)
                    .climate(c -> c.temperature(1.0f).humidity(0.2f)))
                
                // Cold region
                .child("COLD_ZONE", region -> region
                    .radius(1000)
                    .climate(c -> c.temperature(0.0f).humidity(0.5f)))
                
                // Vanilla region
                .child("VANILLA", region -> region
                    .radius(1000)));
        
        return registry.build("WORLD");
    }
}
