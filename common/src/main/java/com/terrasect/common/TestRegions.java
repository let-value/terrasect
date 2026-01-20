package com.terrasect.common;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.generation.World;

public final class TestRegions {

  private static final int CHUNK = 16;
  private static final int SEASON_SIZE = CHUNK * 4;
  private static final int LAB_ZONE_SIZE = CHUNK * 6;
  private static final int STRUCTURE_ZONE_SIZE = CHUNK * 8;

  private TestRegions() {}

  public static void register() {
    Terrasect.LOGGER.info("Registering utilitarian test regions for development");

    Region overworldRoot = buildTestWorld();
    World.register(overworldRoot, World.OVERWORLD);

    Region endRoot = buildEndWorld();
    World.register(endRoot, World.THE_END);

    Terrasect.LOGGER.info("Test regions registered:");
    Terrasect.LOGGER.info("  Overworld: {} child regions", overworldRoot.children().size());
    Terrasect.LOGGER.info("  The End: {} child regions", endRoot.children().size());
  }

  public static Region buildTestWorld() {
    var registry = new RegionRegistry();

    registry.region("WORLD").strategy(GenerationStrategy.hex("BORDER"));

    registry.region("LANDMASS").parent("WORLD").strategy(GenerationStrategy.subdivision());

    registry
        .region("SEASONS_HUB")
        .parent("LANDMASS")
        .strategy(GenerationStrategy.centerSurround("SPAWN"))
        .climate(c -> c.continentalness(0.8f, 1.0f));
    registry
        .region("SPAWN")
        .parent("SEASONS_HUB")
        .radius(SEASON_SIZE)
        .anchoredToOrigin()
        .biomes(b -> b.allowNames("minecraft:plains"));
    registry
        .region("SPRING")
        .parent("SEASONS_HUB")
        .radius(SEASON_SIZE)
        .climate(c -> c.temperature(0.6f).humidity(0.7f))
        .biomes(
            b ->
                b.allowNames(
                    "minecraft:flower_forest", "minecraft:meadow", "minecraft:sunflower_plains"));
    registry
        .region("SUMMER")
        .parent("SEASONS_HUB")
        .radius(SEASON_SIZE)
        .climate(c -> c.temperature(1.0f).humidity(0.1f))
        .biomes(
            b ->
                b.allowNames(
                    "minecraft:desert",
                    "minecraft:savanna",
                    "minecraft:savanna_plateau",
                    "minecraft:badlands"));
    registry
        .region("AUTUMN")
        .parent("SEASONS_HUB")
        .radius(SEASON_SIZE)
        .climate(c -> c.temperature(0.5f).humidity(0.5f))
        .biomes(
            b ->
                b.allowNames(
                    "minecraft:forest",
                    "minecraft:dark_forest",
                    "minecraft:birch_forest",
                    "minecraft:old_growth_birch_forest"));
    registry
        .region("WINTER")
        .parent("SEASONS_HUB")
        .radius(SEASON_SIZE)
        .climate(c -> c.temperature(0.0f).humidity(0.3f))
        .biomes(
            b ->
                b.allowNames(
                    "minecraft:snowy_plains",
                    "minecraft:snowy_taiga",
                    "minecraft:ice_spikes",
                    "minecraft:frozen_river"));

    registry
        .region("TEMPERATURE_LAB")
        .parent("LANDMASS")
        .strategy(GenerationStrategy.subdivision());
    registry
        .region("FREEZING")
        .parent("TEMPERATURE_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.temperature(0.0f).humidity(0.5f));
    registry
        .region("COLD")
        .parent("TEMPERATURE_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.temperature(0.25f).humidity(0.5f));
    registry
        .region("MILD")
        .parent("TEMPERATURE_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.temperature(0.5f).humidity(0.5f));
    registry
        .region("WARM")
        .parent("TEMPERATURE_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.temperature(0.75f).humidity(0.5f));
    registry
        .region("HOT")
        .parent("TEMPERATURE_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.temperature(1.0f).humidity(0.5f));

    registry.region("BIOME_LAB").parent("LANDMASS").strategy(GenerationStrategy.subdivision());
    registry
        .region("OCEANS_ONLY")
        .parent("BIOME_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.continentalness(-1.0f, -0.7f))
        .biomes(b -> b.allowTags("#minecraft:is_ocean"));
    registry
        .region("FORESTS_ONLY")
        .parent("BIOME_LAB")
        .radius(LAB_ZONE_SIZE)
        .biomes(b -> b.allowTags("#minecraft:is_forest"));
    registry
        .region("MOUNTAINS_ONLY")
        .parent("BIOME_LAB")
        .radius(LAB_ZONE_SIZE)
        .climate(c -> c.continentalness(0.8f, 1.0f))
        .biomes(b -> b.allowTags("#minecraft:is_mountain"));
    registry
        .region("RIVERS_ONLY")
        .parent("BIOME_LAB")
        .radius(LAB_ZONE_SIZE)
        .biomes(b -> b.allowTags("#minecraft:is_river"));

    registry.region("NOISE_LAB").parent("LANDMASS").strategy(GenerationStrategy.subdivision());
    registry
        .region("NO_OCEAN")
        .parent("NOISE_LAB")
        .radius(LAB_ZONE_SIZE)
        .noise(
            n ->
                n.noise("minecraft:continentalness", t -> t.multiply(0.0).add(0.8))
                    .noise("minecraft:continentalness_large", t -> t.multiply(0.0).add(0.8))
                    .noise("minecraft:erosion", t -> t.multiply(0.0).add(0.0))
                    .noise("minecraft:erosion_large", t -> t.multiply(0.0).add(0.0))
                    .noise("minecraft:ridge", t -> t.multiply(0.0))
                    .densityFunction("minecraft:overworld/offset", t -> t.multiply(0.0).add(0.6))
                    .densityFunction("minecraft:overworld/factor", t -> t.multiply(0.0).add(1.0)));
    registry
        .region("ONLY_OCEAN")
        .parent("NOISE_LAB")
        .radius(LAB_ZONE_SIZE)
        .noise(
            n ->
                n.noise("minecraft:continentalness", t -> t.multiply(0.0).add(-0.9))
                    .noise("minecraft:continentalness_large", t -> t.multiply(0.0).add(-0.9))
                    .noise("minecraft:erosion", t -> t.multiply(0.0).add(0.9))
                    .noise("minecraft:erosion_large", t -> t.multiply(0.0).add(0.9))
                    .noise("minecraft:ridge", t -> t.multiply(0.0))
                    .densityFunction("minecraft:overworld/offset", t -> t.multiply(0.0).add(-0.7))
                    .densityFunction("minecraft:overworld/factor", t -> t.multiply(0.0).add(0.2)));
    registry
        .region("CONTINENTALNESS_LOCKED")
        .parent("NOISE_LAB")
        .radius(LAB_ZONE_SIZE)
        .noise(
            n ->
                n.noise("minecraft:continentalness", t -> t.multiply(0.0).add(0.6))
                    .noise("minecraft:continentalness_large", t -> t.multiply(0.0).add(0.6)));
    registry
        .region("EROSION_LOCKED")
        .parent("NOISE_LAB")
        .radius(LAB_ZONE_SIZE)
        .noise(
            n ->
                n.noise("minecraft:erosion", t -> t.multiply(0.0))
                    .noise("minecraft:erosion_large", t -> t.multiply(0.0)));
    registry
        .region("RIDGE_LOCKED")
        .parent("NOISE_LAB")
        .radius(LAB_ZONE_SIZE)
        .noise(n -> n.noise("minecraft:ridge", t -> t.multiply(0.0)));

    registry.region("STRUCTURE_LAB").parent("LANDMASS").strategy(GenerationStrategy.subdivision());
    registry
        .region("SETTLEMENTS")
        .parent("STRUCTURE_LAB")
        .radius(STRUCTURE_ZONE_SIZE)
        .structures(
            structures ->
                structures.allowNames(
                    "minecraft:village_plains",
                    "minecraft:village_desert",
                    "minecraft:village_savanna",
                    "minecraft:village_snowy",
                    "minecraft:village_taiga",
                    "minecraft:pillager_outpost"));
    registry
        .region("RUINS")
        .parent("STRUCTURE_LAB")
        .radius(STRUCTURE_ZONE_SIZE)
        .structures(
            structures ->
                structures.allowNames(
                    "minecraft:ruined_portal_standard",
                    "minecraft:ruined_portal_desert",
                    "minecraft:ruined_portal_jungle",
                    "minecraft:ruined_portal_swamp",
                    "minecraft:ruined_portal_mountain",
                    "minecraft:ruined_portal_ocean",
                    "minecraft:shipwreck",
                    "minecraft:shipwreck_beached"));
    registry
        .region("NO_SETTLEMENTS")
        .parent("STRUCTURE_LAB")
        .radius(STRUCTURE_ZONE_SIZE)
        .structures(
            structures ->
                structures.blockNames(
                    "minecraft:village_plains",
                    "minecraft:village_desert",
                    "minecraft:village_savanna",
                    "minecraft:village_snowy",
                    "minecraft:village_taiga",
                    "minecraft:pillager_outpost",
                    "minecraft:mineshaft",
                    "minecraft:mineshaft_mesa"));

    registry
        .region("BORDER")
        .parent("WORLD")
        .radius(CHUNK * 20)
        .height(height -> height.range(40, 55))
        .climate(c -> c.depth(-1.5f, -0.5f).continentalness(-1.0f, -0.7f).erosion(0.5f, 1.0f))
        .biomes(b -> b.allowNames("minecraft:deep_ocean"));

    return registry.build("WORLD");
  }

  public static Region buildEndWorld() {
    var registry = new RegionRegistry();

    registry
        .region("END_ROOT")
        .strategy(GenerationStrategy.voronoi())
        .child(
            "CENTRAL_VOID",
            region -> region.radius(200).biomes(b -> b.allowNames("minecraft:the_end")))
        .child(
            "OUTER_ISLANDS",
            region ->
                region
                    .radius(1500)
                    .strategy(GenerationStrategy.subdivision())
                    .child(
                        "END_HIGHLANDS",
                        sub -> sub.radius(800).biomes(b -> b.allowNames("minecraft:end_highlands")))
                    .child(
                        "END_MIDLANDS",
                        sub -> sub.radius(500).biomes(b -> b.allowNames("minecraft:end_midlands")))
                    .child(
                        "END_BARRENS",
                        sub -> sub.radius(400).biomes(b -> b.allowNames("minecraft:end_barrens"))))
        .child(
            "SMALL_ISLANDS",
            region -> region.radius(300).biomes(b -> b.allowNames("minecraft:small_end_islands")));

    return registry.build("END_ROOT");
  }

  @Deprecated
  public static void registerForDimensions() {
    register();
  }
}
