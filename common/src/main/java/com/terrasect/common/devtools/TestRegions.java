package com.terrasect.common.devtools;

import com.terrasect.common.Terrasect;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.StrategySettings;
import com.terrasect.common.runtime.World;

/**
 * Utilitarian test regions for development and visual testing.
 * 
 * <p>Designed to immediately show generation contrasts when running the client.
 * 
 * <h2>Structure</h2>
 * <pre>
 * WORLD (HEX grid with visible ring border)
 *   ├── SEASONS_HUB (at spawn)
 *   │     ├── SPAWN (tiny 1-chunk island, plains, anchored to origin)
 *   │     ├── SPRING (warm, flowers)
 *   │     ├── SUMMER (hot, dry)
 *   │     ├── AUTUMN (mild, forest)
 *   │     └── WINTER (cold, snowy)
 *   │
 *   ├── TEMPERATURE_LAB (pocket for testing climate manipulation)
 *   │     ├── FREEZING, COLD, MILD, WARM, HOT
 *   │
 *   ├── BIOME_LAB (pocket for testing biome filtering)
 *   │     ├── OCEANS_ONLY, FORESTS_ONLY, MOUNTAINS_ONLY
 *   │
 *   └── VANILLA (unmodified for comparison)
 * </pre>
 * 
 * <h2>Sizes</h2>
 * <ul>
 *   <li>SPAWN: 1 chunk (16 blocks)</li>
 *   <li>Season regions: 8 chunks diameter (128 blocks)</li>
 *   <li>Lab pockets: Small enough to see all variations quickly</li>
 * </ul>
 * 
 * @see World for registering dimension-specific roots
 */
public final class TestRegions {

    // Size constants (in blocks)
    private static final int CHUNK = 16;
    private static final int SEASON_SIZE = CHUNK * 4;      // 8 chunks diameter = 4 chunk radius
    private static final int LAB_SIZE = CHUNK * 8;         // Lab pocket
    private static final int LAB_ZONE_SIZE = CHUNK * 2;    // Individual lab zones

    private TestRegions() {
    }

    /**
     * Build and register test regions for all dimensions.
     * Call this from mod initialization when you want to test with sample regions.
     */
    public static void register() {
        Terrasect.LOGGER.info("Registering utilitarian test regions for development");
        
        // Build and register Overworld regions
        Region overworldRoot = buildTestWorld();
        World.register(World.OVERWORLD, overworldRoot);
        
        // Build and register End regions
        Region endRoot = buildEndWorld();
        World.register(World.THE_END, endRoot);
        
        Terrasect.LOGGER.info("Test regions registered:");
        Terrasect.LOGGER.info("  Overworld: {} child regions", overworldRoot.children().size());
        Terrasect.LOGGER.info("  The End: {} child regions", endRoot.children().size());
    }

    /**
     * Build the utilitarian test world with seasons hub and lab pockets.
     */
    public static Region buildTestWorld() {
        RegionRegistry registry = new RegionRegistry();
        
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .settings(StrategySettings.builder().hexRing("BORDER").build()) // Visible ring border
            .child("LANDMASS", root -> root
                .strategy(GenerationStrategyType.SUBDIVISION)
                // ===== SEASONS HUB (at spawn) =====
                .child("SEASONS_HUB", hub -> hub
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.CENTER_SURROUND)
                        .centerSurround("SPAWN")
                        .build())

                    // Tiny spawn island - flat plains, anchored to origin
                    .child("SPAWN", spawn -> spawn
                        .radius(SEASON_SIZE)
                        .anchoredToOrigin()
                        .biomes(b -> b.allowNames("minecraft:plains")))

                    // SPRING - Warm and lush, flowers (no plains - must be unique)
                    .child("SPRING", season -> season
                        .radius(SEASON_SIZE)
                        .climate(c -> c.temperature(0.6f).humidity(0.7f))
                        .biomes(b -> b.allowNames(
                            "minecraft:flower_forest",
                            "minecraft:meadow",
                            "minecraft:sunflower_plains")))
                        
                    // SUMMER - Hot and dry
                    .child("SUMMER", season -> season
                        .radius(SEASON_SIZE)
                        .climate(c -> c.temperature(1.0f).humidity(0.1f))
                        .biomes(b -> b.allowNames(
                            "minecraft:desert",
                            "minecraft:savanna",
                            "minecraft:savanna_plateau",
                            "minecraft:badlands")))
                        
                    // AUTUMN - Mild, forests
                    .child("AUTUMN", season -> season
                        .radius(SEASON_SIZE)
                        .climate(c -> c.temperature(0.5f).humidity(0.5f))
                        .biomes(b -> b.allowNames(
                            "minecraft:forest",
                            "minecraft:dark_forest",
                            "minecraft:birch_forest",
                            "minecraft:old_growth_birch_forest")))
                        
                    // WINTER - Cold and snowy
                    .child("WINTER", season -> season
                        .radius(SEASON_SIZE)
                        .climate(c -> c.temperature(0.0f).humidity(0.3f))
                        .biomes(b -> b.allowNames(
                            "minecraft:snowy_plains",
                            "minecraft:snowy_taiga",
                            "minecraft:ice_spikes",
                            "minecraft:frozen_river"))))
                        
                // ===== TEMPERATURE LAB =====
                .child("TEMPERATURE_LAB", lab -> lab
                    .radius(LAB_SIZE)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                        
                    .child("FREEZING", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .climate(c -> c.temperature(0.0f).humidity(0.5f)))
                        
                    .child("COLD", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .climate(c -> c.temperature(0.25f).humidity(0.5f)))
                        
                    .child("MILD", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .climate(c -> c.temperature(0.5f).humidity(0.5f)))
                        
                    .child("WARM", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .climate(c -> c.temperature(0.75f).humidity(0.5f)))
                        
                    .child("HOT", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .climate(c -> c.temperature(1.0f).humidity(0.5f))))
                        
                // ===== BIOME LAB =====
                .child("BIOME_LAB", lab -> lab
                    .radius(LAB_SIZE)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                        
                    .child("OCEANS_ONLY", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .biomes(b -> b.allowTags("#minecraft:is_ocean")))
                        
                    .child("FORESTS_ONLY", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .biomes(b -> b.allowTags("#minecraft:is_forest")))
                        
                    .child("MOUNTAINS_ONLY", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .biomes(b -> b.allowTags("#minecraft:is_mountain")))
                        
                    .child("RIVERS_ONLY", zone -> zone
                        .radius(LAB_ZONE_SIZE)
                        .biomes(b -> b.allowTags("#minecraft:is_river"))))
                        
                // ===== VANILLA (unmodified for comparison) =====
                .child("VANILLA", vanilla -> vanilla
                    .radius(LAB_SIZE)))
            // ===== BORDER (hex ring - visible edge between cells) =====
            .child("BORDER", border -> border
                .radius(CHUNK * 20)
                // Ocean terrain: constrain height BELOW sea level for actual ocean floor
                // height(40, 55) means ocean floor varies from Y=40 to Y=55
                // Natural terrain variation is mapped into this range for interesting topography
                // Sea level in Minecraft is 63 (water at 62 and below)
                .climate(c -> c
                    .height(40, 55)                 // Ocean floor with variation
                    .depth(-1.5f, -0.5f)            // Force underwater depth (ocean floor)
                    .continentalness(-1.0f, -0.7f)  // Deep ocean biome selection
                    .erosion(0.5f, 1.0f))           // Flat terrain
                .biomes(b -> b.allowNames("minecraft:deep_ocean")));
        
        return registry.build("WORLD");
    }
    
    /**
     * Build a test region hierarchy for The End dimension.
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
                
                .child("END_HIGHLANDS", sub -> sub
                    .radius(800)
                    .biomes(b -> b.allowNames("minecraft:end_highlands")))
                    
                .child("END_MIDLANDS", sub -> sub
                    .radius(500)
                    .biomes(b -> b.allowNames("minecraft:end_midlands")))
                    
                .child("END_BARRENS", sub -> sub
                    .radius(400)
                    .biomes(b -> b.allowNames("minecraft:end_barrens"))))
            
            // Small scattered islands
            .child("SMALL_ISLANDS", region -> region
                .radius(300)
                .biomes(b -> b.allowNames("minecraft:small_end_islands")));
        
        return registry.build("END_ROOT");
    }
    
    /**
     * @deprecated Use {@link #register()} instead.
     */
    @Deprecated
    public static void registerForDimensions() {
        register();
    }
}
