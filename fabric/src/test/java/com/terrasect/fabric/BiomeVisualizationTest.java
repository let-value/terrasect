package com.terrasect.fabric;

import com.terrasect.common.api.DimensionRoots;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.api.Context;
import com.terrasect.common.devtools.PerfTracker;
import com.terrasect.common.lookup.BiomeLookup;
import com.terrasect.common.runtime.BiomeFilter;
import com.terrasect.common.runtime.Config;
import com.terrasect.common.runtime.RegionField;
import com.terrasect.common.runtime.World;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.SelectionRules;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;


public class BiomeVisualizationTest {

    private static final int WIDTH = 256;  // Reduced from 512 for faster tests
    private static final int HEIGHT = 256;
    private static final int SCALE = 8;    // Increased from 4 - fewer samples

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void visualizeBiomeFiltering() throws IOException {
        // Enable performance tracking
        PerfTracker.enable();
        
        long seed = 12345L;
        
        PerfTracker.start("buildRegionHierarchy");
        // Build region hierarchy with biome filtering rules
        Region root = buildFilteredBiomeRegions();
        DimensionRoots.register(DimensionRoots.OVERWORLD, root);
        PerfTracker.stop("buildRegionHierarchy");
        
        PerfTracker.start("setupMinecraft");
        // Setup Minecraft vanilla climate sampler
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
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldParameters);
        Climate.ParameterList<Holder<Biome>> parameterList = overworldParameters.value().parameters();
        
        Context context = createStrategy(seed, sampler, biomeSource);
        PerfTracker.stop("setupMinecraft");
        
        // Build BiomeMetadataLookup - pre-computes biome IDs and tags for O(1) lookups
        // This is the production-ready pattern for fast biome filtering
        PerfTracker.start("buildBiomeMetadataLookup");
        BiomeLookup.Builder<Holder<Biome>, Void> lookupBuilder = BiomeLookup.builder();
        Set<Holder<Biome>> seenBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : parameterList.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (seenBiomes.add(biome)) {
                String biomeId = getBiomeId(biome);
                Set<String> tags = getBiomeTags(biome);
                lookupBuilder.add(biome, biomeId, tags);
            }
        }
        BiomeLookup<Holder<Biome>, Void> biomeLookup = lookupBuilder.buildSimple();
        PerfTracker.stop("buildBiomeMetadataLookup");
        
        BufferedImage vanillaBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage filteredBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage filterOverlay = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage regionMap = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage combinedView = new BufferedImage(WIDTH * 2, HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        
        int blockedCount = 0;
        int replacedCount = 0;
        int totalWithRules = 0;
        
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                int blockX = px * SCALE;
                int blockZ = py * SCALE;
                int quartX = blockX >> 2;
                int quartZ = blockZ >> 2;
                
                // Get vanilla biome
                PerfTracker.start("climateSample");
                Climate.TargetPoint vanilla = sampler.sample(quartX, 16, quartZ);
                PerfTracker.stop("climateSample");
                
                PerfTracker.start("parameterListFind");
                Holder<Biome> vanillaBiome = parameterList.findValue(vanilla);
                PerfTracker.stop("parameterListFind");
                
                // Use BiomeMetadataLookup for O(1) lookup
                PerfTracker.start("lookupBiomeMetadata");
                BiomeLookup.Entry vanillaEntry = biomeLookup.get(vanillaBiome);
                String vanillaBiomeId = vanillaEntry != null ? vanillaEntry.id() : getBiomeId(vanillaBiome);
                Set<String> vanillaTags = vanillaEntry != null ? vanillaEntry.tags() : getBiomeTags(vanillaBiome);
                PerfTracker.stop("lookupBiomeMetadata");
                
                // Get region and its biome rules
                PerfTracker.start("regionLookup");
                Region region = World.getRegion(DimensionRoots.OVERWORLD, blockX, blockZ, context);
                PerfTracker.stop("regionLookup");
                
                SelectionRules biomeRules = region != null ? region.definition().biomes() : null;
                
                // Calculate edge for region map
                PerfTracker.start("regionFieldData");
                long regionData = RegionField.getRegionData(blockX, blockZ, seed, 512, 200.0f, 2048);
                float edge = RegionField.unpackEdge(regionData);
                float normalizedEdge = Math.min(1.0f, edge / Config.EDGE_SCALE);
                float edgeFactor = 1.0f - normalizedEdge;
                PerfTracker.stop("regionFieldData");
                
                // Check if vanilla biome is allowed
                PerfTracker.start("biomeFilterCheck");
                BiomeFilter.FilterResult filterResult = BiomeFilter.checkBiome(biomeRules, vanillaBiomeId, vanillaTags);
                PerfTracker.stop("biomeFilterCheck");
                
                Holder<Biome> finalBiome = vanillaBiome;
                int overlayColor = 0x444444; // Default gray
                
                if (BiomeFilter.hasRules(biomeRules)) {
                    totalWithRules++;
                }
                
                if (filterResult == BiomeFilter.FilterResult.BLOCKED) {
                    blockedCount++;
                    // Find fallback biome using BiomeMetadataLookup
                    PerfTracker.start("findFallbackBiome");
                    finalBiome = findAllowedBiomeFallback(biomeLookup, vanilla, biomeRules, parameterList);
                    PerfTracker.stop("findFallbackBiome");
                    if (finalBiome != null && !finalBiome.equals(vanillaBiome)) {
                        replacedCount++;
                        overlayColor = 0xFF0000; // Red for replaced
                    } else {
                        overlayColor = 0xFF8800; // Orange for blocked but no fallback
                        finalBiome = vanillaBiome;
                    }
                } else if (BiomeFilter.hasRules(biomeRules)) {
                    overlayColor = 0x00FF00; // Green for allowed by rules
                }
                
                PerfTracker.start("imageSetRGB");
                vanillaBiomes.setRGB(px, py, biomeToColor(vanillaBiome));
                filteredBiomes.setRGB(px, py, biomeToColor(finalBiome));
                filterOverlay.setRGB(px, py, overlayColor);
                regionMap.setRGB(px, py, getRegionColor(region, edgeFactor));
                PerfTracker.stop("imageSetRGB");
            }
        }
        
        // Create combined view (2x2 grid)
        Graphics2D g = combinedView.createGraphics();
        g.drawImage(vanillaBiomes, 0, 0, null);
        g.drawImage(filteredBiomes, WIDTH, 0, null);
        g.drawImage(regionMap, 0, HEIGHT, null);
        g.drawImage(filterOverlay, WIDTH, HEIGHT, null);
        
        // Add labels
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Vanilla Biomes", 10, 20);
        g.drawString("Filtered Biomes", WIDTH + 10, 20);
        g.drawString("Region Map", 10, HEIGHT + 20);
        g.drawString("Filter Overlay (red=replaced, green=allowed)", WIDTH + 10, HEIGHT + 20);
        g.dispose();
        
        // Save images
        File outDir = new File("build/biome-snapshots");
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
        System.out.println("Block rate (of filtered area): " + 
            String.format("%.2f%%", totalWithRules > 0 ? 100.0 * blockedCount / totalWithRules : 0));
        System.out.println("Biomes changed: " + replacedCount + 
            " (" + String.format("%.1f%%", 100.0 * replacedCount / (WIDTH * HEIGHT)) + " of total)");
        System.out.println("\nImages saved to: " + outDir.getAbsolutePath());
        
        // Print performance summary
        PerfTracker.printSummary();
        PerfTracker.disable();
    }
    
    /**
     * Build a region hierarchy with biome filtering rules to test.
     */
    private Region buildFilteredBiomeRegions() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.VORONOI)
            // Forest-only region - blocks everything except forests
            .child("ETERNAL_FOREST", region -> region
                .radius(150)
                .biomes(b -> b.allowTags("#minecraft:is_forest")))
            // No-ocean region - blocks all ocean biomes
            .child("LANDLOCKED", region -> region
                .radius(200)
                .biomes(b -> b.blockTags("#minecraft:is_ocean", "#minecraft:is_river")))
            // Vanilla-only region - only minecraft biomes allowed
            .child("PURIST_LANDS", region -> region
                .radius(500)
                .biomes(b -> b.allowMods("minecraft")))
            // Specific biome allowlist
            .child("CURATED_ZONE", region -> region
                .radius(300)
                .biomes(b -> b.allowNames("minecraft:plains", "minecraft:sunflower_plains", 
                                          "minecraft:meadow", "minecraft:flower_forest")))
            // No filtering - vanilla behavior
            .child("WILDERNESS", region -> region
                .radius(400));
        
        return registry.build("WORLD");
    }
    
    /**
     * Find the best allowed fallback biome using BiomeMetadataLookup for fast filtering.
     * Uses O(1) biome metadata retrieval.
     */
    private Holder<Biome> findAllowedBiomeFallback(
            BiomeLookup<Holder<Biome>, Void> biomeLookup,
            Climate.TargetPoint target,
            SelectionRules rules,
            Climate.ParameterList<Holder<Biome>> parameterList) {
        
        if (rules == null || !BiomeFilter.hasRules(rules)) {
            return null;
        }
        
        Holder<Biome> bestFallback = null;
        long bestDistance = Long.MAX_VALUE;
        
        // Iterate through parameter list entries to get climate params
        for (var entry : parameterList.values()) {
            Holder<Biome> candidate = entry.getSecond();
            
            // Use BiomeMetadataLookup for O(1) allow check - no allocation!
            if (biomeLookup.isAllowed(candidate, rules)) {
                long distance = calculateClimateDistance(target, entry.getFirst());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestFallback = candidate;
                }
            }
        }
        
        return bestFallback;
    }
    
    private long calculateClimateDistance(Climate.TargetPoint target, Climate.ParameterPoint params) {
        long tempDist = Math.abs(target.temperature() - params.temperature().min());
        long humidDist = Math.abs(target.humidity() - params.humidity().min());
        long contDist = Math.abs(target.continentalness() - params.continentalness().min());
        long erosionDist = Math.abs(target.erosion() - params.erosion().min());
        return tempDist + humidDist + contDist + erosionDist;
    }
    
    private String getBiomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("unknown");
    }
    
    private Set<String> getBiomeTags(Holder<Biome> biome) {
        Set<String> tags = new HashSet<>();
        // In test environment, biome.tags() may throw because tags aren't bound
        // So we use try-catch and fall back to inferring tags from biome name
        try {
            biome.tags().forEach(tag -> tags.add("#" + tag.location().toString()));
        } catch (IllegalStateException e) {
            // Tags not bound in this test environment - fall through to inference
        }
        
        // Add inferred tags based on biome name for test purposes
        String biomeId = getBiomeId(biome);
        tags.addAll(inferTagsFromBiomeName(biomeId));
        
        return tags;
    }
    
    /**
     * Infer biome tags from the biome name for testing purposes.
     * In a real Minecraft environment, these would come from the biome's actual tag bindings.
     */
    private Set<String> inferTagsFromBiomeName(String biomeId) {
        Set<String> tags = new HashSet<>();
        
        // Forest biomes
        if (biomeId.contains("forest") || biomeId.contains("grove") || biomeId.contains("taiga")) {
            tags.add("#minecraft:is_forest");
        }
        
        // Ocean biomes
        if (biomeId.contains("ocean")) {
            tags.add("#minecraft:is_ocean");
        }
        
        // River biomes
        if (biomeId.contains("river")) {
            tags.add("#minecraft:is_river");
        }
        
        // Mountain biomes
        if (biomeId.contains("mountain") || biomeId.contains("peak") || biomeId.contains("slope")) {
            tags.add("#minecraft:is_mountain");
        }
        
        // Beach biomes
        if (biomeId.contains("beach")) {
            tags.add("#minecraft:is_beach");
        }
        
        // Badlands
        if (biomeId.contains("badlands")) {
            tags.add("#minecraft:is_badlands");
        }
        
        // Jungle
        if (biomeId.contains("jungle")) {
            tags.add("#minecraft:is_jungle");
        }
        
        return tags;
    }
    
    private Context createStrategy(long seed, Climate.Sampler sampler, MultiNoiseBiomeSource biomeSource) {
        return new Context() {
            @Override
            public long getSeed() { return seed; }
            
            @Override
            public float getRiverInfluence(int x, int z) {
                int qx = x >> 2;
                int qz = z >> 2;
                Holder<Biome> biome = biomeSource.getNoiseBiome(qx, 16, qz, sampler);
                // Use biome ID check instead of tags - tags aren't bound in test environment
                String biomeId = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("");
                return biomeId.contains("river") ? 1.0f : 0.0f;
            }
            
            @Override
            public float getRidgeInfluence(int x, int z) {
                Climate.TargetPoint target = sampler.sample(x >> 2, 0, z >> 2);
                return (float) ((target.weirdness() + 10000) / 20000.0);
            }
        };
    }
    
    /**
     * Get color for a region based on its name and edge proximity.
     */
    private int getRegionColor(Region region, float edgeFactor) {
        if (region == null) return 0x000000;
        
        int baseColor = switch (region.name()) {
            case "ETERNAL_FOREST" -> 0x228B22;    // Forest green
            case "LANDLOCKED" -> 0xDEB887;        // Burlywood (land)
            case "PURIST_LANDS" -> 0x9370DB;      // Medium purple
            case "CURATED_ZONE" -> 0xFFD700;      // Gold
            case "WILDERNESS" -> 0x808080;         // Gray
            case "WORLD" -> 0x444444;
            default -> 0x666666;
        };
        
        // Darken at edges to show boundaries
        if (edgeFactor > 0.5f) {
            float darken = (edgeFactor - 0.5f) * 2;
            int r = (int) (((baseColor >> 16) & 0xFF) * (1 - darken * 0.5));
            int g = (int) (((baseColor >> 8) & 0xFF) * (1 - darken * 0.5));
            int b = (int) ((baseColor & 0xFF) * (1 - darken * 0.5));
            return (r << 16) | (g << 8) | b;
        }
        
        return baseColor;
    }
    
    /**
     * Get a deterministic color for a biome based on its registry name.
     */
    private int biomeToColor(Holder<Biome> biome) {
        String name = biome.unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("unknown");
        
        // Create a hash-based color
        int hash = name.hashCode();
        int r = ((hash >> 16) & 0xFF);
        int g = ((hash >> 8) & 0xFF);
        int b = (hash & 0xFF);
        
        // Ensure reasonable brightness
        int brightness = (r + g + b) / 3;
        if (brightness < 80) {
            r = Math.min(255, r + 80);
            g = Math.min(255, g + 80);
            b = Math.min(255, b + 80);
        }
        
        return (r << 16) | (g << 8) | b;
    }
}
