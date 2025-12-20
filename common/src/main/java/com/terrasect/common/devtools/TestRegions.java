package com.terrasect.common.devtools;

import com.terrasect.common.Terrasect;
import com.terrasect.common.api.DimensionRoots;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.runtime.World;

/**
 * Pre-configured test regions for development and testing purposes.
 * 
 * <p>This demonstrates how to create different region hierarchies for different
 * dimensions using {@link DimensionRoots}.
 * 
 * <h2>Overworld Regions</h2>
 * <ul>
 *   <li>SCORCHED_WASTES: Hot desert climate, blocks ocean/river biomes</li>
 *   <li>FROZEN_REACHES: Cold tundra climate, allows only cold biomes</li>
 *   <li>VERDANT_HEART: Temperate forest climate, prefers forest biomes</li>
 *   <li>MYSTIC_JUNGLE: Hot and humid, allows jungle biomes</li>
 *   <li>ANCIENT_HIGHLANDS: Mild climate, allows mountain biomes</li>
 *   <li>WILDLANDS: No modifications - vanilla Minecraft behavior</li>
 * </ul>
 * 
 * <h2>End Regions</h2>
 * <ul>
 *   <li>CENTRAL_VOID: The main End island area</li>
 *   <li>OUTER_ISLANDS: The outer End islands</li>
 * </ul>
 * 
 * @see DimensionRoots for registering dimension-specific roots
 */
public final class TestRegions {

    private TestRegions() {
    }

    /**
     * Build and register test regions for all dimensions.
     * Call this from mod initialization when you want to test with sample regions.
     */
    public static void register() {
        Terrasect.LOGGER.info("Registering test regions for development");
        
        // Build and register Overworld regions
        Region overworldRoot = buildTestWorld();
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        
        // Build and register End regions
        Region endRoot = buildEndWorld();
        DimensionRoots.register(DimensionRoots.THE_END, endRoot);
        
        Terrasect.LOGGER.info("Test regions registered:");
        Terrasect.LOGGER.info("  Overworld: {} child regions", overworldRoot.children().size());
        Terrasect.LOGGER.info("  The End: {} child regions", endRoot.children().size());
    }
    
    /**
     * Register dimension roots using the new dimension-aware API.
     * This demonstrates the recommended way to register regions for multiple dimensions.
     */
    public static void registerForDimensions() {
        Terrasect.LOGGER.info("Registering dimension-aware test regions");
        
        RegionRegistry registry = new RegionRegistry();
        
        // ===== OVERWORLD =====
        registry.region("OVERWORLD_ROOT")
            .strategy(GenerationStrategyType.HEX)
            .child("CLIMATES", regions -> regions
                .strategy(GenerationStrategyType.SUBDIVISION)
                .child("SCORCHED_WASTES", region -> region
                    .radius(1000)
                    .climate(c -> c.temperature(1.0f).humidity(0.0f)))
                .child("FROZEN_REACHES", region -> region
                    .radius(1000)
                    .climate(c -> c.temperature(0.0f).humidity(0.3f)))
                .child("WILDLANDS", region -> region
                    .radius(1000)));
        
        // ===== THE END =====
        registry.region("END_ROOT")
            .strategy(GenerationStrategyType.VORONOI)
            .child("CENTRAL_VOID", region -> region
                .radius(500)
                .biomes(b -> b.allowNames("minecraft:the_end")))
            .child("OUTER_ISLANDS", region -> region
                .radius(2000)
                .biomes(b -> b.allowNames(
                    "minecraft:end_highlands",
                    "minecraft:end_midlands", 
                    "minecraft:end_barrens",
                    "minecraft:small_end_islands")));
        
        // Build all roots
        Region overworldRoot = registry.build("OVERWORLD_ROOT");
        Region endRoot = registry.build("END_ROOT");
        
        // Register for dimensions
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        DimensionRoots.register(DimensionRoots.THE_END, endRoot);
        
        // Example: A modded dimension could share the overworld config
        // DimensionRoots.register("mymod:overworld_copy", overworldRoot);
        
        Terrasect.LOGGER.info("Dimension roots registered: {}", 
            DimensionRoots.getRegisteredDimensions());
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
    
    /**
     * Build a test region hierarchy for The End dimension.
     * 
     * <p>The End has different biomes than the Overworld:
     * <ul>
     *   <li>the_end - The main central island</li>
     *   <li>end_highlands - Outer islands with chorus plants</li>
     *   <li>end_midlands - Transition areas</li>
     *   <li>end_barrens - Sparse outer regions</li>
     *   <li>small_end_islands - Small floating islands</li>
     * </ul>
     */
    public static Region buildEndWorld() {
        RegionRegistry registry = new RegionRegistry();
        
        registry.region("END_ROOT")
            .strategy(GenerationStrategyType.VORONOI)
            
            // Central island region - the main spawn area
            .child("CENTRAL_VOID", region -> region
                .radius(200)
                .biomes(b -> b.allowNames("minecraft:the_end")))
            
            // Outer islands - the explorable areas with cities
            .child("OUTER_ISLANDS", region -> region
                .radius(1500)
                .strategy(GenerationStrategyType.SUBDIVISION)
                
                // End highlands - rich with chorus plants and cities
                .child("END_HIGHLANDS", sub -> sub
                    .radius(800)
                    .biomes(b -> b.allowNames("minecraft:end_highlands")))
                    
                // End midlands - transition areas
                .child("END_MIDLANDS", sub -> sub
                    .radius(500)
                    .biomes(b -> b.allowNames("minecraft:end_midlands")))
                    
                // End barrens - sparse regions
                .child("END_BARRENS", sub -> sub
                    .radius(400)
                    .biomes(b -> b.allowNames("minecraft:end_barrens"))))
            
            // Small scattered islands
            .child("SMALL_ISLANDS", region -> region
                .radius(300)
                .biomes(b -> b.allowNames("minecraft:small_end_islands")));
        
        return registry.build("END_ROOT");
    }
}
