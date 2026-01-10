package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.compat.BiomeCompat;
import com.terrasect.common.definition.GenerationStrategyType;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.handler.ClimateHandler;
import com.terrasect.common.testing.SnapshotHashes;
import com.terrasect.common.util.Packer;
import de.skuzzle.test.snapshots.Snapshot;
import com.terrasect.common.testing.SnapshotTests;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
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

@SnapshotTests
public class ClimateVisualizationTest {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int SCALE = 8;

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void visualizeClimateInfluence(Snapshot snapshot) throws IOException {
        long seed = 12345L;

        Region root = buildClimateRegions();
        World.register(root, World.OVERWORLD);

        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = lookup.lookupOrThrow(Registries.NOISE);

        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                    .value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }

        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        Climate.Sampler sampler = randomState.sampler();

        var parameterListLookup = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldParameters = parameterListLookup.getOrThrow(
                net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldParameters);
        Climate.ParameterList<Holder<Biome>> parameterList =
                overworldParameters.value().parameters();

        Context context = createStrategy(seed, sampler, biomeSource);

        BufferedImage vanillaTemp = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage vanillaHumidity = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage modifiedTemp = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage modifiedHumidity = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage regionOverlay = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage tempDiff = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage regionBoundaries = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage vanillaBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage modifiedBiomes = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage combinedView = new BufferedImage(WIDTH * 3, HEIGHT * 3, BufferedImage.TYPE_INT_RGB);

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

                Climate.TargetPoint vanilla = sampler.sample(quartX, 16, quartZ);

                Region region = World.traverse(context, blockX, blockZ).region;

                Region rightRegion = World.traverse(context, blockX + SCALE, blockZ).region;
                Region downRegion = World.traverse(context, blockX, blockZ + SCALE).region;

                boolean isRightBoundary =
                        region != null && rightRegion != null && !region.name().equals(rightRegion.name());
                boolean isDownBoundary =
                        region != null && downRegion != null && !region.name().equals(downRegion.name());
                boolean isBoundary = isRightBoundary || isDownBoundary;

                long vanillaTempValue = vanilla.temperature();
                long vanillaHumidityValue = vanilla.humidity();
                long vanillaContinentalnessValue = vanilla.continentalness();
                long vanillaErosionValue = vanilla.erosion();
                long vanillaDepthValue = vanilla.depth();
                long vanillaWeirdnessValue = vanilla.weirdness();

                Holder<Biome> vanillaBiome = parameterList.findValue(vanilla);

                Climate.TargetPoint modifiedPoint =
                        ClimateHandler.modifyTargetPoint(context, quartX, 16, quartZ, vanilla);
                long modTemp = modifiedPoint.temperature();
                long modHumid = modifiedPoint.humidity();
                long modCont = modifiedPoint.continentalness();
                long modErosion = modifiedPoint.erosion();
                long modDepth = modifiedPoint.depth();
                long modWeirdness = modifiedPoint.weirdness();

                boolean modified = modTemp != vanillaTempValue
                        || modHumid != vanillaHumidityValue
                        || modCont != vanillaContinentalnessValue
                        || modErosion != vanillaErosionValue
                        || modDepth != vanillaDepthValue
                        || modWeirdness != vanillaWeirdnessValue;

                if (modified) {
                    totalModified++;
                }

                totalTempDelta += Math.abs(modTemp - vanillaTempValue);
                totalHumidityDelta += Math.abs(modHumid - vanillaHumidityValue);

                Holder<Biome> modifiedBiome = parameterList.findValue(modifiedPoint);

                if (!vanillaBiome.equals(modifiedBiome)) {
                    biomesChanged++;
                }

                vanillaTemp.setRGB(px, py, climateToColor(vanillaTempValue, true));
                vanillaHumidity.setRGB(px, py, climateToColor(vanillaHumidityValue, false));
                modifiedTemp.setRGB(px, py, climateToColor(modTemp, true));
                modifiedHumidity.setRGB(px, py, climateToColor(modHumid, false));

                int regionColor = getRegionColor(region);
                regionOverlay.setRGB(px, py, regionColor);

                int boundaryColor = isBoundary ? darkenColor(regionColor, 0.4f) : regionColor;
                regionBoundaries.setRGB(px, py, boundaryColor);

                vanillaBiomes.setRGB(px, py, biomeToColor(vanillaBiome));
                modifiedBiomes.setRGB(px, py, biomeToColor(modifiedBiome));

                long tempDelta = modTemp - vanillaTempValue;
                tempDiff.setRGB(px, py, deltaToColor(tempDelta));
            }
        }

        Graphics2D g = combinedView.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH * 3, HEIGHT * 3);

        g.drawImage(vanillaTemp, 0, 0, null);
        g.drawImage(modifiedTemp, WIDTH, 0, null);
        g.drawImage(tempDiff, WIDTH * 2, 0, null);

        g.drawImage(vanillaHumidity, 0, HEIGHT, null);
        g.drawImage(modifiedHumidity, WIDTH, HEIGHT, null);
        g.drawImage(regionBoundaries, WIDTH * 2, HEIGHT, null);

        g.drawImage(vanillaBiomes, 0, HEIGHT * 2, null);
        g.drawImage(modifiedBiomes, WIDTH, HEIGHT * 2, null);
        g.drawImage(regionOverlay, WIDTH * 2, HEIGHT * 2, null);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Vanilla Temperature", 10, 20);
        g.drawString("Modified Temperature", WIDTH + 10, 20);
        g.drawString("Temperature Delta", WIDTH * 2 + 10, 20);
        g.drawString("Vanilla Humidity", 10, HEIGHT + 20);
        g.drawString("Modified Humidity", WIDTH + 10, HEIGHT + 20);
        g.drawString("Region Boundaries", WIDTH * 2 + 10, HEIGHT + 20);
        g.drawString("Vanilla Biomes", 10, HEIGHT * 2 + 20);
        g.drawString("Climate-Modified Biomes", WIDTH + 10, HEIGHT * 2 + 20);
        g.drawString("Region Colors", WIDTH * 2 + 10, HEIGHT * 2 + 20);
        g.dispose();

        File outDir = new File("build/climate-snapshots");
        outDir.mkdirs();

        ImageIO.write(vanillaTemp, "png", new File(outDir, "vanilla_temperature.png"));
        ImageIO.write(vanillaHumidity, "png", new File(outDir, "vanilla_humidity.png"));
        ImageIO.write(modifiedTemp, "png", new File(outDir, "modified_temperature.png"));
        ImageIO.write(modifiedHumidity, "png", new File(outDir, "modified_humidity.png"));
        ImageIO.write(regionOverlay, "png", new File(outDir, "region_overlay.png"));
        ImageIO.write(tempDiff, "png", new File(outDir, "temperature_delta.png"));
        ImageIO.write(regionBoundaries, "png", new File(outDir, "region_boundaries.png"));
        ImageIO.write(vanillaBiomes, "png", new File(outDir, "vanilla_biomes.png"));
        ImageIO.write(modifiedBiomes, "png", new File(outDir, "climate_modified_biomes.png"));
        ImageIO.write(combinedView, "png", new File(outDir, "combined_climate_view.png"));

        System.out.println("=== Climate Modification Statistics ===");
        System.out.println("Total pixels: " + (WIDTH * HEIGHT));
        System.out.println("Pixels with climate modifications: " + totalModified);
        System.out.println(
                "Modification coverage: " + String.format("%.1f%%", 100.0 * totalModified / (WIDTH * HEIGHT)));
        if (totalModified > 0) {
            System.out.println("Average temperature delta: " + String.format("%.1f", totalTempDelta / totalModified));
            System.out.println("Average humidity delta: " + String.format("%.1f", totalHumidityDelta / totalModified));
        }
        System.out.println("Biomes changed by climate: " + biomesChanged + " ("
            + String.format("%.1f%%", 100.0 * biomesChanged / (WIDTH * HEIGHT)) + ")");
        System.out.println("\nImages saved to: " + outDir.getAbsolutePath());
        StringBuilder snapshotText = new StringBuilder(256);
        snapshotText.append("totalModified=").append(totalModified).append('\n');
        snapshotText.append("totalTempDelta=").append(String.format(Locale.ROOT, "%.4f", totalTempDelta)).append('\n');
        snapshotText.append("totalHumidityDelta=").append(String.format(Locale.ROOT, "%.4f", totalHumidityDelta))
            .append('\n');
        snapshotText.append("biomesChanged=").append(biomesChanged).append('\n');
        appendImageHash(snapshotText, "vanilla_temperature", vanillaTemp);
        appendImageHash(snapshotText, "modified_temperature", modifiedTemp);
        appendImageHash(snapshotText, "temperature_delta", tempDiff);
        appendImageHash(snapshotText, "vanilla_humidity", vanillaHumidity);
        appendImageHash(snapshotText, "modified_humidity", modifiedHumidity);
        appendImageHash(snapshotText, "region_boundaries", regionBoundaries);
        appendImageHash(snapshotText, "vanilla_biomes", vanillaBiomes);
        appendImageHash(snapshotText, "climate_modified_biomes", modifiedBiomes);
        appendImageHash(snapshotText, "region_overlay", regionOverlay);
        appendImageHash(snapshotText, "combined_climate_view", combinedView);
        snapshot.assertThat(snapshotText.toString()).asText().matchesSnapshotText();
    }

    private Region buildClimateRegions() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REGIONS", regions -> regions.strategy(
                        GenerationStrategyType.VORONOI)
                .child("BURNING_WASTES", region -> region.radius(500)
                        .climate(c -> c.temperature(1.0f).humidity(0.0f)))
                .child("FROZEN_NORTH", region -> region.radius(100)
                        .climate(c -> c.temperature(0.0f).humidity(0.3f)))
                .child("JUNGLE_HEART", region -> region.radius(200)
                        .climate(c -> c.temperature(0.9f).humidity(1.0f)))
                .child("VERDANT_WOODS", region -> region.radius(300)
                        .climate(c -> c.temperature(0.5f).humidity(0.6f)))
                .child("UNTOUCHED_LANDS", region -> region.radius(100)));

        return registry.build("WORLD");
    }

    private Context createStrategy(long seed, Climate.Sampler sampler, MultiNoiseBiomeSource biomeSource) {
        return new Context() {
            @Override
            public long getSeed() {
                return seed;
            }

            @Override
            public long getInfluence(int x, int z) {
                int qx = x >> 2;
                int qz = z >> 2;
                Holder<Biome> biome = biomeSource.getNoiseBiome(qx, 16, qz, sampler);

                String biomeId = BiomeCompat.getBiomeId(biome);
                float river = biomeId.contains("river") ? 1.0f : 0.0f;

                Climate.TargetPoint target = sampler.sample(x >> 2, 0, z >> 2);
                float ridge = (float) ((target.weirdness() + 10000) / 20000.0);

                return Packer.packPair(river, ridge);
            }
        };
    }

    private int climateToColor(long value, boolean isTemperature) {

        float normalized = (float) ((value + 10000.0) / 20000.0);
        normalized = Math.max(0, Math.min(1, normalized));

        if (isTemperature) {

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

    private int deltaToColor(long delta) {
        if (delta == 0) return 0x808080;

        float normalized = (float) delta / 10000f;
        normalized = Math.max(-1, Math.min(1, normalized));

        if (normalized < 0) {

            int intensity = (int) (Math.abs(normalized) * 255);
            return (128 - intensity / 2) << 16 | (128 - intensity / 2) << 8 | (128 + intensity);
        } else {

            int intensity = (int) (normalized * 255);
            return (128 + intensity) << 16 | (128 - intensity / 2) << 8 | (128 - intensity / 2);
        }
    }

    private int darkenColor(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private int getRegionColor(Region region) {
        if (region == null) return 0x000000;
        return getRegionColor(region.name());
    }

    private int getRegionColor(String name) {
        return switch (name) {
            case "BURNING_WASTES" -> 0xFF4400;
            case "FROZEN_NORTH" -> 0x88CCFF;
            case "JUNGLE_HEART" -> 0x00AA00;
            case "VERDANT_WOODS" -> 0x44AA44;
            case "UNTOUCHED_LANDS" -> 0x888888;
            case "WORLD", "ROOT" -> 0x444444;
            default -> {
                int hash = name.hashCode();
                int r = ((hash >> 16) & 0x7F) + 0x40;
                int g = ((hash >> 8) & 0x7F) + 0x40;
                int b = (hash & 0x7F) + 0x40;
                yield (r << 16) | (g << 8) | b;
            }
        };
    }

    private int biomeToColor(Holder<Biome> biome) {
        String name = BiomeCompat.getBiomeId(biome);

        int hash = name.hashCode();
        int r = ((hash >> 16) & 0xFF);
        int g = ((hash >> 8) & 0xFF);
        int b = (hash & 0xFF);

        int brightness = (r + g + b) / 3;
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
