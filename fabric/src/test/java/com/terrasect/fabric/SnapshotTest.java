package com.terrasect.fabric;

import com.terrasect.common.generation.Strategy;
import com.terrasect.common.generation.World;
import com.terrasect.common.generation.Region;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class SnapshotTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void generateRealNoiseSnapshots() throws IOException {
        // Setup NarrativeWorld regions for testing
        Region civilization = Region.builder("CIVILIZATION")
            .addChildren(
                Region.builder("RUINS").budget(50000).adjacentTo("PILGRIMAGE_PATH").build(),
                Region.builder("HARBOR").budget(30000).adjacentTo("PILGRIMAGE_PATH").build(),
                Region.builder("PILGRIMAGE_PATH").budget(20000).adjacentTo("RUINS", "HARBOR").build()
            ).build();

        Region wilderness = Region.builder("WILDERNESS")
            .addChildren(
                Region.builder("FORBIDDEN_WOODS").budget(60000).adjacentTo("PLAINS_OF_ASH").build(),
                Region.builder("PLAINS_OF_ASH").budget(40000).adjacentTo("FORBIDDEN_WOODS").build()
            ).build();

        Region highlands = Region.builder("HIGHLANDS")
            .addChildren(
                Region.builder("MOUNTAIN_PASS").budget(40000).adjacentTo("CRYSTAL_CANYON").build(),
                Region.builder("CRYSTAL_CANYON").budget(30000).adjacentTo("MOUNTAIN_PASS").build()
            ).build();

        Region root = Region.builder("ROOT")
            .addChildren(civilization, wilderness, highlands)
            .build();
            
        World.setRoot(root);

        long seed = 987654321L;
        
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
        var overworldParameters = parameterListLookup.getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldParameters);
        
        Strategy context = new Strategy() {
            @Override public long getSeed() { return seed; }
            @Override public float getRiverInfluence(int x, int z) {
                int qx = x >> 2;
                int qy = 16; 
                int qz = z >> 2;
                
                Holder<Biome> biome = biomeSource.getNoiseBiome(qx, qy, qz, sampler);
                
                boolean isRiver = biome.is(BiomeTags.IS_RIVER) 
                    || biome.is(net.minecraft.world.level.biome.Biomes.RIVER)
                    || biome.is(net.minecraft.world.level.biome.Biomes.FROZEN_RIVER);
                
                return isRiver ? 1.0f : 0.0f;
            }
            @Override public float getRidgeInfluence(int x, int z) {
                Climate.TargetPoint target = sampler.sample(x >> 2, 0, z >> 2);
                return (float) ((target.weirdness() + 10000) / 20000.0);
            }
        };
        
        int width = 512;
        int height = 512;
        int step = 4;

        BufferedImage imgRiver = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgRidge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgCombined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int riverCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int wx = x * step;
                int wz = y * step;
                
                float river = context.getRiverInfluence(wx, wz);
                if (river > 0) riverCount++;
                
                float ridge = context.getRidgeInfluence(wx, wz);
                
                Region region = World.getRegion(wx, wz, context);
                
                int riverVal = (int) (Math.max(0, Math.min(1, river)) * 255);
                imgRiver.setRGB(x, y, (riverVal << 16) | (riverVal << 8) | riverVal);

                int ridgeVal = (int) (Math.max(0, Math.min(1, ridge)) * 255);
                imgRidge.setRGB(x, y, (ridgeVal << 16) | (ridgeVal << 8) | ridgeVal);
                
                int color = getRegionColor(region);
                if (river > 0.6f) {
                    color = (color & 0xFEFEFE) >> 1;
                }
                if (ridge > 0.6f) {
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    r = Math.min(255, r + 50);
                    g = Math.min(255, g + 50);
                    b = Math.min(255, b + 50);
                    color = (r << 16) | (g << 8) | b;
                }
                imgCombined.setRGB(x, y, color);
            }
        }
        
        System.out.println("Total river pixels found: " + riverCount);
        
        File outDir = new File("build/fabric-snapshots");
        outDir.mkdirs();
        ImageIO.write(imgRiver, "png", new File(outDir, "real_river.png"));
        ImageIO.write(imgRidge, "png", new File(outDir, "real_ridge.png"));
        ImageIO.write(imgCombined, "png", new File(outDir, "real_combined.png"));
    }

    private int getRegionColor(Region region) {
        switch (region.name()) {
            case "RUINS": return 0x888888;
            case "PILGRIMAGE_PATH": return 0xFFFF00;
            case "HARBOR": return 0x0000FF;
            case "FORBIDDEN_WOODS": return 0x004400;
            case "MOUNTAIN_PASS": return 0xFFFFFF;
            case "PLAINS_OF_ASH": return 0x444444;
            case "CRYSTAL_CANYON": return 0x00FFFF;
            default: return 0x000000;
        }
    }
}