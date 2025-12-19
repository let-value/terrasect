package com.terrasect.fabric;

import com.terrasect.common.generation.*;
import com.terrasect.common.generation.definition.ClimateSettings;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BiomeTags;
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

/**
 * Visual tests for climate modification.
 * 
 * Generates side-by-side comparisons of:
 * 1. Vanilla Minecraft climate (temperature/humidity)
 * 2. Region-influenced climate using ClimateModifier
 * 3. Region boundaries overlay
 * 
 * Output is saved to build/climate-snapshots/
 */
public class ClimateVisualizationTest {

    private static final int WIDTH = 512;
    private static final int HEIGHT = 512;
    private static final int SCALE = 4; // blocks per pixel

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void visualizeClimateInfluence() throws IOException {
        long seed = 12345L;
        
        // Build region hierarchy with different climate settings
        Region root = buildClimateRegions();
        World.setRoot(root);
        
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
        
        Strategy context = createStrategy(seed, sampler, biomeSource);
        
        // Create images
        BufferedImage vanillaTemp = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage vanillaHumidity = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage modifiedTemp = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage modifiedHumidity = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage regionOverlay = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage tempDiff = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage edgeFactorMap = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage vanillaBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage modifiedBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage combinedView = new BufferedImage(WIDTH * 3, HEIGHT * 3, BufferedImage.TYPE_INT_RGB);
        
        // Track statistics
        long totalModified = 0;
        double totalTempDelta = 0;
        double totalHumidityDelta = 0;
        int biomesChanged = 0;
        
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                int blockX = px * SCALE;
                int blockZ = py * SCALE;
                int quartX = blockX >> 2;
                int quartZ = blockZ >> 2;
                
                // Get vanilla climate
                Climate.TargetPoint vanilla = sampler.sample(quartX, 16, quartZ);
                
                // Get region and calculate modified climate
                Region region = World.getRegion(blockX, blockZ, context);
                ClimateSettings climate = region != null ? region.definition().climate() : null;
                
                // Calculate edge factor (matching the FIXED logic in ClimateHandler)
                // edge value from unpackEdge is HIGH when deep inside region, LOW at boundaries
                // We invert it: edgeFactor=0 means center of region, edgeFactor=1 means at edge
                long regionData = RegionField.getRegionData(blockX, blockZ, seed, 512, 200.0f, 2048);
                float edge = RegionField.unpackEdge(regionData);
                float normalizedEdge = Math.min(1.0f, edge / Config.EDGE_SCALE);
                float edgeFactor = 1.0f - normalizedEdge; // INVERTED: 0 at center, 1 at boundary
                
                // Calculate offsets
                ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
                    climate, vanilla.temperature(), vanilla.humidity(), edgeFactor
                );
                
                long modTemp = ClimateModifier.applyTemperatureOffset(vanilla.temperature(), offset);
                long modHumid = ClimateModifier.applyHumidityOffset(vanilla.humidity(), offset);
                
                // Track stats
                if (offset.hasModifications()) {
                    totalModified++;
                    if (offset.temperatureOffset() != null) {
                        totalTempDelta += Math.abs(offset.temperatureOffset());
                    }
                    if (offset.humidityOffset() != null) {
                        totalHumidityDelta += Math.abs(offset.humidityOffset());
                    }
                }
                
                // Get biomes (for showing climate effect on biome selection)
                Holder<Biome> vanillaBiome = parameterList.findValue(vanilla);
                Climate.TargetPoint modifiedPoint = new Climate.TargetPoint(
                    modTemp, modHumid,
                    vanilla.continentalness(), vanilla.erosion(),
                    vanilla.depth(), vanilla.weirdness()
                );
                Holder<Biome> modifiedBiome = parameterList.findValue(modifiedPoint);
                
                if (!vanillaBiome.equals(modifiedBiome)) {
                    biomesChanged++;
                }
                
                // Draw images
                vanillaTemp.setRGB(px, py, climateToColor(vanilla.temperature(), true));
                vanillaHumidity.setRGB(px, py, climateToColor(vanilla.humidity(), false));
                modifiedTemp.setRGB(px, py, climateToColor(modTemp, true));
                modifiedHumidity.setRGB(px, py, climateToColor(modHumid, false));
                regionOverlay.setRGB(px, py, getRegionColor(region, edgeFactor));
                edgeFactorMap.setRGB(px, py, edgeFactorToColor(edgeFactor));
                vanillaBiomes.setRGB(px, py, biomeToColor(vanillaBiome));
                modifiedBiomes.setRGB(px, py, biomeToColor(modifiedBiome));
                
                // Temperature difference
                long tempDelta = modTemp - vanilla.temperature();
                tempDiff.setRGB(px, py, deltaToColor(tempDelta));
            }
        }
        
        // Create combined view (3x3 grid)
        Graphics2D g = combinedView.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH * 3, HEIGHT * 3);
        
        // Row 1: Temperature
        g.drawImage(vanillaTemp, 0, 0, null);
        g.drawImage(modifiedTemp, WIDTH, 0, null);
        g.drawImage(tempDiff, WIDTH * 2, 0, null);
        
        // Row 2: Humidity and regions
        g.drawImage(vanillaHumidity, 0, HEIGHT, null);
        g.drawImage(modifiedHumidity, WIDTH, HEIGHT, null);
        g.drawImage(regionOverlay, WIDTH * 2, HEIGHT, null);
        
        // Row 3: Biomes and edge factor
        g.drawImage(vanillaBiomes, 0, HEIGHT * 2, null);
        g.drawImage(modifiedBiomes, WIDTH, HEIGHT * 2, null);
        g.drawImage(edgeFactorMap, WIDTH * 2, HEIGHT * 2, null);
        
        // Add labels
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Vanilla Temperature", 10, 20);
        g.drawString("Modified Temperature", WIDTH + 10, 20);
        g.drawString("Temperature Delta", WIDTH * 2 + 10, 20);
        g.drawString("Vanilla Humidity", 10, HEIGHT + 20);
        g.drawString("Modified Humidity", WIDTH + 10, HEIGHT + 20);
        g.drawString("Region Map", WIDTH * 2 + 10, HEIGHT + 20);
        g.drawString("Vanilla Biomes", 10, HEIGHT * 2 + 20);
        g.drawString("Climate-Modified Biomes", WIDTH + 10, HEIGHT * 2 + 20);
        g.drawString("Edge Factor (white=center)", WIDTH * 2 + 10, HEIGHT * 2 + 20);
        g.dispose();
        
        // Save images
        File outDir = new File("build/climate-snapshots");
        outDir.mkdirs();
        
        ImageIO.write(vanillaTemp, "png", new File(outDir, "vanilla_temperature.png"));
        ImageIO.write(vanillaHumidity, "png", new File(outDir, "vanilla_humidity.png"));
        ImageIO.write(modifiedTemp, "png", new File(outDir, "modified_temperature.png"));
        ImageIO.write(modifiedHumidity, "png", new File(outDir, "modified_humidity.png"));
        ImageIO.write(regionOverlay, "png", new File(outDir, "region_overlay.png"));
        ImageIO.write(tempDiff, "png", new File(outDir, "temperature_delta.png"));
        ImageIO.write(edgeFactorMap, "png", new File(outDir, "edge_factor.png"));
        ImageIO.write(vanillaBiomes, "png", new File(outDir, "vanilla_biomes.png"));
        ImageIO.write(modifiedBiomes, "png", new File(outDir, "climate_modified_biomes.png"));
        ImageIO.write(combinedView, "png", new File(outDir, "combined_climate_view.png"));
        
        // Print statistics
        System.out.println("=== Climate Modification Statistics ===");
        System.out.println("Total pixels: " + (WIDTH * HEIGHT));
        System.out.println("Pixels with climate modifications: " + totalModified);
        System.out.println("Modification coverage: " + String.format("%.1f%%", 100.0 * totalModified / (WIDTH * HEIGHT)));
        if (totalModified > 0) {
            System.out.println("Average temperature delta: " + String.format("%.1f", totalTempDelta / totalModified));
            System.out.println("Average humidity delta: " + String.format("%.1f", totalHumidityDelta / totalModified));
        }
        System.out.println("Biomes changed by climate: " + biomesChanged + 
            " (" + String.format("%.1f%%", 100.0 * biomesChanged / (WIDTH * HEIGHT)) + ")");
        System.out.println("\nImages saved to: " + outDir.getAbsolutePath());
    }
    
    /**
     * Build a region hierarchy with various climate settings to test.
     */
    private Region buildClimateRegions() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.VORONOI)
            // Hot desert region
            .child("BURNING_WASTES", region -> region
                .budget(80000)
                .climate(c -> c.temperature(1.0f).humidity(0.0f)))
            // Cold tundra region
            .child("FROZEN_NORTH", region -> region
                .budget(80000)
                .climate(c -> c.temperature(0.0f).humidity(0.3f)))
            // Tropical jungle region
            .child("JUNGLE_HEART", region -> region
                .budget(80000)
                .climate(c -> c.temperature(0.9f).humidity(1.0f)))
            // Temperate forest (mild climate)
            .child("VERDANT_WOODS", region -> region
                .budget(80000)
                .climate(c -> c.temperature(0.5f).humidity(0.6f)))
            // No climate setting - should use vanilla
            .child("UNTOUCHED_LANDS", region -> region
                .budget(80000));
        
        return registry.build("WORLD");
    }
    
    private Strategy createStrategy(long seed, Climate.Sampler sampler, MultiNoiseBiomeSource biomeSource) {
        return new Strategy() {
            @Override
            public long getSeed() { return seed; }
            
            @Override
            public float getRiverInfluence(int x, int z) {
                int qx = x >> 2;
                int qz = z >> 2;
                Holder<Biome> biome = biomeSource.getNoiseBiome(qx, 16, qz, sampler);
                return biome.is(BiomeTags.IS_RIVER) ? 1.0f : 0.0f;
            }
            
            @Override
            public float getRidgeInfluence(int x, int z) {
                Climate.TargetPoint target = sampler.sample(x >> 2, 0, z >> 2);
                return (float) ((target.weirdness() + 10000) / 20000.0);
            }
        };
    }
    
    /**
     * Convert climate value to a color for visualization.
     * Temperature: cold (blue) to hot (red)
     * Humidity: dry (yellow) to wet (blue)
     */
    private int climateToColor(long value, boolean isTemperature) {
        // Climate values are roughly in range [-10000, 10000]
        float normalized = (float) ((value + 10000.0) / 20000.0);
        normalized = Math.max(0, Math.min(1, normalized));
        
        if (isTemperature) {
            // Cold (blue) -> Neutral (white) -> Hot (red)
            if (normalized < 0.5f) {
                float t = normalized * 2;
                int r = (int) (t * 255);
                int g = (int) (t * 255);
                int b = 255;
                return (r << 16) | (g << 8) | b;
            } else {
                float t = (normalized - 0.5f) * 2;
                int r = 255;
                int g = (int) ((1 - t) * 255);
                int b = (int) ((1 - t) * 255);
                return (r << 16) | (g << 8) | b;
            }
        } else {
            // Dry (yellow/brown) -> Wet (blue/cyan)
            if (normalized < 0.5f) {
                float t = normalized * 2;
                int r = 200;
                int g = (int) (150 + t * 105);
                int b = (int) (50 + t * 150);
                return (r << 16) | (g << 8) | b;
            } else {
                float t = (normalized - 0.5f) * 2;
                int r = (int) (200 * (1 - t));
                int g = (int) (255 * (1 - t * 0.3));
                int b = (int) (200 + t * 55);
                return (r << 16) | (g << 8) | b;
            }
        }
    }
    
    /**
     * Convert delta value to color.
     * Negative (cooling) = blue, Zero = gray, Positive (warming) = red
     */
    private int deltaToColor(long delta) {
        if (delta == 0) return 0x808080;
        
        // Clamp to reasonable range
        float normalized = (float) delta / 10000f;
        normalized = Math.max(-1, Math.min(1, normalized));
        
        if (normalized < 0) {
            // Cooling - blue
            int intensity = (int) (Math.abs(normalized) * 255);
            return (128 - intensity/2) << 16 | (128 - intensity/2) << 8 | (128 + intensity);
        } else {
            // Warming - red
            int intensity = (int) (normalized * 255);
            return (128 + intensity) << 16 | (128 - intensity/2) << 8 | (128 - intensity/2);
        }
    }
    
    /**
     * Convert edge factor to grayscale.
     * 0 (center of region) = white, 1 (edge of region) = black
     */
    private int edgeFactorToColor(float edgeFactor) {
        int gray = (int) ((1.0f - edgeFactor) * 255);
        return (gray << 16) | (gray << 8) | gray;
    }
    
    /**
     * Get color for a region based on its name and edge proximity.
     */
    private int getRegionColor(Region region, float edgeFactor) {
        if (region == null) return 0x000000;
        
        int baseColor = switch (region.name()) {
            case "BURNING_WASTES" -> 0xFF4400;    // Orange-red for desert
            case "FROZEN_NORTH" -> 0x88CCFF;       // Light blue for tundra
            case "JUNGLE_HEART" -> 0x00AA00;       // Green for jungle
            case "VERDANT_WOODS" -> 0x44AA44;      // Lighter green for forest
            case "UNTOUCHED_LANDS" -> 0x888888;    // Gray for vanilla
            case "WORLD" -> 0x444444;
            default -> 0x666666;
        };
        
        // Darken at edges (edgeFactor high) to show boundaries
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
            .map(key -> key.location().toString())
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
