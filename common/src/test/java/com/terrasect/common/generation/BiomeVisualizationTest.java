package com.terrasect.common.generation;

import com.mojang.datafixers.util.Pair;
import com.terrasect.common.Context;
import com.terrasect.common.compat.BiomeCompat;
import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.definition.SelectionRules;
import com.terrasect.common.helpers.BiomeFilter;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.testing.SnapshotHashes;
import com.terrasect.common.testing.SnapshotOutputPaths;
import com.terrasect.common.util.Packer;
import de.skuzzle.test.snapshots.Snapshot;
import com.terrasect.common.testing.SnapshotTests;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SnapshotTests
public class BiomeVisualizationTest {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int SCALE = 8;

    @BeforeAll public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test public void visualizeBiomeFiltering(Snapshot snapshot) throws IOException {

        var seed = 12345L;

        var root = buildFilteredBiomeRegions();
        World.register(root, World.OVERWORLD);

        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        var noiseParams = lookup.lookupOrThrow(Registries.NOISE);

        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                    .value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }

        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        var sampler = randomState.sampler();

        var parameterListLookup = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldParameters = parameterListLookup.getOrThrow(
                net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldParameters);
        Climate.ParameterList<Holder<Biome>> parameterList =
                overworldParameters.value().parameters();

        var context = createStrategy(seed, sampler, biomeSource);

        var metadata = new IdentityHashMap<Holder<Biome>, BiomeLookup.Entry>();
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : parameterList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (metadata.containsKey(biome)) {
                continue;
            }
            metadata.put(biome, new BiomeLookup.Entry(getBiomeId(biome), getBiomeTags(biome)));
        }
        BiomeLookup biomeLookup = BiomeLookup.metadataOnly(metadata);

        var vanillaBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var filteredBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var filterOverlay = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var regionMap = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var combinedView = new BufferedImage(WIDTH * 2, HEIGHT * 2, BufferedImage.TYPE_INT_RGB);

        var blockedCount = 0;
        var replacedCount = 0;
        var totalWithRules = 0;

        for (var py = 0; py < HEIGHT; py++) {
            for (var px = 0; px < WIDTH; px++) {
                var blockX = px * SCALE;
                var blockZ = py * SCALE;
                var quartX = blockX >> 2;
                var quartZ = blockZ >> 2;

                var vanilla = sampler.sample(quartX, 16, quartZ);

                var vanillaBiome = parameterList.findValue(vanilla);

                var vanillaEntry = biomeLookup.get(vanillaBiome);
                String vanillaBiomeId = vanillaEntry != null ? vanillaEntry.id() : getBiomeId(vanillaBiome);
                Set<String> vanillaTags = vanillaEntry != null ? vanillaEntry.tags() : getBiomeTags(vanillaBiome);

                TraversalResult traversal = World.traverse(context, blockX, blockZ);
                Region region = traversal != null ? traversal.region : null;

                SelectionRules biomeRules = region != null ? region.definition().biomes() : null;

                float edgeFactor = traversal != null ? 1.0f - traversal.edgeDistance : 0.5f;

                BiomeFilter.FilterResult filterResult = BiomeFilter.checkBiome(biomeRules, vanillaBiomeId, vanillaTags);

                Holder<Biome> finalBiome = vanillaBiome;
                var overlayColor = 0x444444;

                if (BiomeFilter.hasRules(biomeRules)) {
                    totalWithRules++;
                }

                if (filterResult == BiomeFilter.FilterResult.BLOCKED) {
                    blockedCount++;

                    finalBiome = findAllowedBiomeFallback(biomeLookup, vanilla, biomeRules, parameterList);

                    if (finalBiome != null && !finalBiome.equals(vanillaBiome)) {
                        replacedCount++;
                        overlayColor = 0xFF0000;
                    } else {
                        overlayColor = 0xFF8800;
                        finalBiome = vanillaBiome;
                    }
                } else if (BiomeFilter.hasRules(biomeRules)) {
                    overlayColor = 0x00FF00;
                }

                vanillaBiomes.setRGB(px, py, biomeToColor(vanillaBiome));
                filteredBiomes.setRGB(px, py, biomeToColor(finalBiome));
                filterOverlay.setRGB(px, py, overlayColor);
                regionMap.setRGB(px, py, getRegionColor(region, edgeFactor));
            }
        }

        var g = combinedView.createGraphics();
        g.drawImage(vanillaBiomes, 0, 0, null);
        g.drawImage(filteredBiomes, WIDTH, 0, null);
        g.drawImage(regionMap, 0, HEIGHT, null);
        g.drawImage(filterOverlay, WIDTH, HEIGHT, null);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Vanilla Biomes", 10, 20);
        g.drawString("Filtered Biomes", WIDTH + 10, 20);
        g.drawString("Region Map", 10, HEIGHT + 20);
        g.drawString("Filter Overlay (red=replaced, green=allowed)", WIDTH + 10, HEIGHT + 20);
        g.dispose();

        var outDir = SnapshotOutputPaths.forTestClass(BiomeVisualizationTest.class);
        outDir.mkdirs();

        ImageIO.write(vanillaBiomes, "png", new File(outDir, "vanilla_biomes.png"));
        ImageIO.write(filteredBiomes, "png", new File(outDir, "filtered_biomes.png"));
        ImageIO.write(filterOverlay, "png", new File(outDir, "filter_overlay.png"));
        ImageIO.write(regionMap, "png", new File(outDir, "region_map.png"));
        ImageIO.write(combinedView, "png", new File(outDir, "combined_biome_view.png"));

        System.out.println("=== Biome Filtering Statistics ===");
        System.out.println("Total pixels: " + (WIDTH * HEIGHT));
        System.out.println("Pixels with filtering rules: " + totalWithRules);
        System.out.println("Biomes blocked: " + blockedCount);
        System.out.println("Biomes replaced with fallback: " + replacedCount);
        System.out.println("Block rate (of filtered area): "
                + String.format("%.2f%%", totalWithRules > 0 ? 100.0 * blockedCount / totalWithRules : 0));
        System.out.println("Biomes changed: " + replacedCount + " ("
                + String.format("%.1f%%", 100.0 * replacedCount / (WIDTH * HEIGHT)) + " of total)");
        System.out.println("\nImages saved to: " + outDir.getAbsolutePath());
        var snapshotText = new StringBuilder(256);
        snapshotText.append("blockedCount=").append(blockedCount).append('\n');
        snapshotText.append("replacedCount=").append(replacedCount).append('\n');
        snapshotText.append("totalWithRules=").append(totalWithRules).append('\n');
        appendImageHash(snapshotText, "vanilla_biomes", vanillaBiomes);
        appendImageHash(snapshotText, "filtered_biomes", filteredBiomes);
        appendImageHash(snapshotText, "filter_overlay", filterOverlay);
        appendImageHash(snapshotText, "region_map", regionMap);
        appendImageHash(snapshotText, "combined_biome_view", combinedView);
        snapshot.assertThat(snapshotText.toString()).asText().matchesSnapshotText();
    }

    private Region buildFilteredBiomeRegions() {
        var registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategy.voronoi())
                .child("ETERNAL_FOREST", region -> region.radius(150).biomes(b -> b.allowTags("#minecraft:is_forest")))
                .child("LANDLOCKED", region -> region.radius(200)
                        .biomes(b -> b.blockTags("#minecraft:is_ocean", "#minecraft:is_river")))
                .child("PURIST_LANDS", region -> region.radius(500).biomes(b -> b.allowMods("minecraft")))
                .child("CURATED_ZONE", region -> region.radius(300)
                        .biomes(b -> b.allowNames(
                                "minecraft:plains",
                                "minecraft:sunflower_plains",
                                "minecraft:meadow",
                                "minecraft:flower_forest")))
                .child("WILDERNESS", region -> region.radius(400));

        return registry.build("WORLD");
    }

    private Holder<Biome> findAllowedBiomeFallback(
            BiomeLookup biomeLookup,
            Climate.TargetPoint target,
            SelectionRules rules,
            Climate.ParameterList<Holder<Biome>> parameterList) {

        if (rules == null || !BiomeFilter.hasRules(rules)) {
            return null;
        }

        Holder<Biome> bestFallback = null;
        var bestDistance = Long.MAX_VALUE;

        for (var entry : parameterList.values()) {
            Holder<Biome> candidate = entry.getSecond();

            if (biomeLookup.isAllowed(candidate, rules)) {
                var distance = calculateClimateDistance(target, entry.getFirst());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestFallback = candidate;
                }
            }
        }

        return bestFallback;
    }

    private long calculateClimateDistance(Climate.TargetPoint target, Climate.ParameterPoint params) {
        var tempDist = Math.abs(target.temperature() - params.temperature().min());
        var humidDist = Math.abs(target.humidity() - params.humidity().min());
        var contDist =
                Math.abs(target.continentalness() - params.continentalness().min());
        var erosionDist = Math.abs(target.erosion() - params.erosion().min());
        return tempDist + humidDist + contDist + erosionDist;
    }

    private String getBiomeId(Holder<Biome> biome) {
        return BiomeCompat.getBiomeId(biome);
    }

    private Set<String> getBiomeTags(Holder<Biome> biome) {
        var tags = new HashSet<String>();

        try {
            BiomeCompat.getTags(biome)
                    .forEach(tag -> tags.add("#" + tag.location().toString()));
        } catch (IllegalStateException e) {

        }

        var biomeId = getBiomeId(biome);
        tags.addAll(inferTagsFromBiomeName(biomeId));

        return tags;
    }

    private Set<String> inferTagsFromBiomeName(String biomeId) {
        var tags = new HashSet<String>();

        if (biomeId.contains("forest") || biomeId.contains("grove") || biomeId.contains("taiga")) {
            tags.add("#minecraft:is_forest");
        }

        if (biomeId.contains("ocean")) {
            tags.add("#minecraft:is_ocean");
        }

        if (biomeId.contains("river")) {
            tags.add("#minecraft:is_river");
        }

        if (biomeId.contains("mountain") || biomeId.contains("peak") || biomeId.contains("slope")) {
            tags.add("#minecraft:is_mountain");
        }

        if (biomeId.contains("beach")) {
            tags.add("#minecraft:is_beach");
        }

        if (biomeId.contains("badlands")) {
            tags.add("#minecraft:is_badlands");
        }

        if (biomeId.contains("jungle")) {
            tags.add("#minecraft:is_jungle");
        }

        return tags;
    }

    private Context createStrategy(long seed, Climate.Sampler sampler, MultiNoiseBiomeSource biomeSource) {
        return new Context() {
            @Override public long getSeed() {
                return seed;
            }

            @Override public long getInfluence(int x, int z) {
                var qx = x >> 2;
                var qz = z >> 2;
                var biome = biomeSource.getNoiseBiome(qx, 16, qz, sampler);

                String biomeId = BiomeCompat.getBiomeId(biome);
                float river = biomeId.contains("river") ? 1.0f : 0.0f;

                var target = sampler.sample(x >> 2, 0, z >> 2);
                var ridge = (float) ((target.weirdness() + 10000) / 20000.0);

                return Packer.packPair(river, ridge);
            }
        };
    }

    private int getRegionColor(Region region, float edgeFactor) {
        if (region == null) return 0x000000;

        var baseColor =
                switch (region.name()) {
                    case "ETERNAL_FOREST" -> 0x228B22;
                    case "LANDLOCKED" -> 0xDEB887;
                    case "PURIST_LANDS" -> 0x9370DB;
                    case "CURATED_ZONE" -> 0xFFD700;
                    case "WILDERNESS" -> 0x808080;
                    case "WORLD" -> 0x444444;
                    default -> 0x666666;
                };

        if (edgeFactor > 0.5f) {
            var darken = (edgeFactor - 0.5f) * 2;
            var r = (int) (((baseColor >> 16) & 0xFF) * (1 - darken * 0.5));
            var g = (int) (((baseColor >> 8) & 0xFF) * (1 - darken * 0.5));
            var b = (int) ((baseColor & 0xFF) * (1 - darken * 0.5));
            return (r << 16) | (g << 8) | b;
        }

        return baseColor;
    }

    private int biomeToColor(Holder<Biome> biome) {
        String name = BiomeCompat.getBiomeId(biome);

        var hash = name.hashCode();
        var r = ((hash >> 16) & 0xFF);
        var g = ((hash >> 8) & 0xFF);
        var b = (hash & 0xFF);

        var brightness = (r + g + b) / 3;
        if (brightness < 80) {
            r = Math.min(255, r + 80);
            g = Math.min(255, g + 80);
            b = Math.min(255, b + 80);
        }

        return (r << 16) | (g << 8) | b;
    }

    private static void appendImageHash(StringBuilder sb, String name, BufferedImage image) {
        sb.append(name).append('=').append(SnapshotHashes.imageSha256(image)).append('\n');
    }
}
