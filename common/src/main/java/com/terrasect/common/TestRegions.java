package com.terrasect.common;

import com.terrasect.common.definition.GenerationStrategyType;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.definition.StrategySettings;
import com.terrasect.common.generation.World;

public final class TestRegions {

    private static final int CHUNK = 16;
    private static final int SEASON_SIZE = CHUNK * 4;
    private static final int LAB_SIZE = CHUNK * 8;
    private static final int LAB_ZONE_SIZE = CHUNK * 2;

    private TestRegions() {}

    public static void register() {
        Terrasect.LOGGER.info("Registering utilitarian test regions for development");

        Region overworldRoot = buildTestWorld();
        World.register(overworldRoot, World.OVERWORLD);

        Region endRoot = buildEndWorld();
        World.register(endRoot, World.THE_END);

        Terrasect.LOGGER.info("Test regions registered:");
        Terrasect.LOGGER.info(
                "  Overworld: {} child regions", overworldRoot.children().size());
        Terrasect.LOGGER.info("  The End: {} child regions", endRoot.children().size());
    }

    public static Region buildTestWorld() {
        RegionRegistry registry = new RegionRegistry();

        registry.region("WORLD")
                .strategy(GenerationStrategyType.HEX)
                .settings(StrategySettings.builder().hexRing("BORDER").build())
                .child("LANDMASS", root -> root.strategy(GenerationStrategyType.SUBDIVISION)
                        .child("SEASONS_HUB", hub -> hub.strategy(GenerationStrategyType.TEMPLATE)
                                .climate(c -> c.continentalness(0.8f, 1.0f))
                                .settings(StrategySettings.builder()
                                        .template(StrategySettings.TemplateType.CENTER_SURROUND)
                                        .centerSurround("SPAWN")
                                        .build())
                                .child("SPAWN", spawn -> spawn.radius(SEASON_SIZE)
                                        .anchoredToOrigin()
                                        .biomes(b -> b.allowNames("minecraft:plains")))
                                .child("SPRING", season -> season.radius(SEASON_SIZE)
                                        .climate(c -> c.temperature(0.6f).humidity(0.7f))
                                        .biomes(b -> b.allowNames(
                                                "minecraft:flower_forest",
                                                "minecraft:meadow",
                                                "minecraft:sunflower_plains")))
                                .child("SUMMER", season -> season.radius(SEASON_SIZE)
                                        .climate(c -> c.temperature(1.0f).humidity(0.1f))
                                        .biomes(b -> b.allowNames(
                                                "minecraft:desert",
                                                "minecraft:savanna",
                                                "minecraft:savanna_plateau",
                                                "minecraft:badlands")))
                                .child("AUTUMN", season -> season.radius(SEASON_SIZE)
                                        .climate(c -> c.temperature(0.5f).humidity(0.5f))
                                        .biomes(b -> b.allowNames(
                                                "minecraft:forest",
                                                "minecraft:dark_forest",
                                                "minecraft:birch_forest",
                                                "minecraft:old_growth_birch_forest")))
                                .child("WINTER", season -> season.radius(SEASON_SIZE)
                                        .climate(c -> c.temperature(0.0f).humidity(0.3f))
                                        .biomes(b -> b.allowNames(
                                                "minecraft:snowy_plains",
                                                "minecraft:snowy_taiga",
                                                "minecraft:ice_spikes",
                                                "minecraft:frozen_river"))))
                        .child("TEMPERATURE_LAB", lab -> lab.radius(LAB_SIZE)
                                .strategy(GenerationStrategyType.SUBDIVISION)
                                .child("FREEZING", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.temperature(0.0f).humidity(0.5f)))
                                .child("COLD", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.temperature(0.25f).humidity(0.5f)))
                                .child("MILD", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.temperature(0.5f).humidity(0.5f)))
                                .child("WARM", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.temperature(0.75f).humidity(0.5f)))
                                .child("HOT", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.temperature(1.0f).humidity(0.5f))))
                        .child("BIOME_LAB", lab -> lab.radius(LAB_SIZE)
                                .strategy(GenerationStrategyType.SUBDIVISION)
                                .child("OCEANS_ONLY", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.continentalness(-1.0f, -0.7f))
                                        .biomes(b -> b.allowTags("#minecraft:is_ocean")))
                                .child("FORESTS_ONLY", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .biomes(b -> b.allowTags("#minecraft:is_forest")))
                                .child("MOUNTAINS_ONLY", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .climate(c -> c.continentalness(0.8f, 1.0f))
                                        .biomes(b -> b.allowTags("#minecraft:is_mountain")))
                                .child("RIVERS_ONLY", zone -> zone.radius(LAB_ZONE_SIZE)
                                        .biomes(b -> b.allowTags("#minecraft:is_river"))))
                        .child("VANILLA", vanilla -> vanilla.radius(LAB_SIZE)))
                .child("BORDER", border -> border.radius(CHUNK * 20)
                        .height(40, 55)
                        .climate(c -> c.depth(-1.5f, -0.5f)
                                .continentalness(-1.0f, -0.7f)
                                .erosion(0.5f, 1.0f))
                        .biomes(b -> b.allowNames("minecraft:deep_ocean")));

        return registry.build("WORLD");
    }

    public static Region buildEndWorld() {
        RegionRegistry registry = new RegionRegistry();

        registry.region("END_ROOT")
                .strategy(GenerationStrategyType.VORONOI)
                .child("CENTRAL_VOID", region -> region.radius(200).biomes(b -> b.allowNames("minecraft:the_end")))
                .child("OUTER_ISLANDS", region -> region.radius(1500)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .child("END_HIGHLANDS", sub -> sub.radius(800)
                                .biomes(b -> b.allowNames("minecraft:end_highlands")))
                        .child("END_MIDLANDS", sub -> sub.radius(500)
                                .biomes(b -> b.allowNames("minecraft:end_midlands")))
                        .child("END_BARRENS", sub -> sub.radius(400)
                                .biomes(b -> b.allowNames("minecraft:end_barrens"))))
                .child("SMALL_ISLANDS", region -> region.radius(300)
                        .biomes(b -> b.allowNames("minecraft:small_end_islands")));

        return registry.build("END_ROOT");
    }

    @Deprecated
    public static void registerForDimensions() {
        register();
    }
}
