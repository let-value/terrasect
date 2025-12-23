package com.terrasect.common.generation;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.Context;
import com.terrasect.common.api.Influence;
import com.terrasect.common.devtools.TestRegions;
import com.terrasect.common.runtime.Config;
import com.terrasect.common.runtime.RegionField;
import com.terrasect.common.runtime.World;
import com.terrasect.common.util.MathUtils;
import com.terrasect.common.util.NoiseUtils;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class SnapshotTest {

    // Single deterministic digest for the current snapshot generation
    // Updated for TestRegions (dev testing regions)
    private static final String EXPECTED_DIGEST = "6d049c23793036a9c1256013741b3c2212dd4096096b9ad46cda98235d7a8acd";

    @Test
    public void testRegionDistribution() {
        World.register(World.OVERWORLD, TestRegions.buildTestWorld());
        
        long seed = 987654321L;
        Context context = new MockStrategy(seed);
        
        // Sample a large area
        int width = 100000;
        int height = 100000;
        int step = 100;
        
        System.out.println("Sampling Depth 1 (Children of ROOT):");
        java.util.Map<String, Integer> counts = RegionSampler.sample(0, 0, width, height, step, 1, context);
        counts.forEach((name, count) -> System.out.println(name + ": " + count));
        
        System.out.println("\nSampling Depth 2 (Grandchildren of ROOT):");
        counts = RegionSampler.sample(0, 0, width, height, step, 2, context);
        counts.forEach((name, count) -> System.out.println(name + ": " + count));
    }

    static class MockStrategy implements Context {
        private final long seed;
        public MockStrategy(long seed) { this.seed = seed; }
        @Override public long getSeed() { return seed; }
        @Override public long getInfluence(int x, int z) {
            float river = NoiseUtils.riverMask(x, z, seed);
            float ridge = NoiseUtils.ridgeMask(x, z, seed);
            return Influence.pack(river, ridge);
        }
    }

    @Test
    public void generateSnapshots() throws IOException, NoSuchAlgorithmException {
        World.register(World.OVERWORLD, TestRegions.buildTestWorld());
        var seed = 987654321L;
        Context context = new MockStrategy(seed);
        
        // CRITICAL: Initialize to calculate anchor offset so SPAWN appears at origin
        World.initialize(context);
        int width = 512;
        int height = 512;
        int step = 4; // Sample every 4 blocks to cover 2048x2048 area

        BufferedImage imgVoronoi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgEdge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgRiver = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgRidge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgCombined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Dynamic depth images for nested regions
        List<BufferedImage> depthImages = new ArrayList<>();
        
        // We will combine all data into one digest for regression testing
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Center the sampling around origin so anchored region appears in center
                int wx = (x - width / 2) * step;
                int wz = (y - height / 2) * step;

                // Raw noise for background reference (default scale)
                long rawRegionData = RegionField.getRegionData(wx, wz, seed, 512, 200.0f, 2048);
                int rawRegionId = RegionField.unpackRegionId(rawRegionData);
                
                long influence = context.getInfluence(wx, wz);
                float river = Influence.unpackRiver(influence);
                float ridge = Influence.unpackRidge(influence);
                
                // Get full hierarchy
                List<Region> regions = new ArrayList<>();
                List<Long> seeds = new ArrayList<>();
                
                // We want to capture regions at each depth
                // Depth 0 (User): Root (Infinite Tiling) -> returns Root
                // Depth 1 (User): Children (Local Partitioning) -> returns Civ/Wild/High
                // Depth 2 (User): Grandchildren -> returns Ruins/Harbor etc.
                
                // We skip the actual root (Universe) which is just a container
                
                regions.add(World.getRegionAtDepth(context, wx, wz, 1));
                seeds.add(World.getRegionSeedAtDepth(context, wx, wz, 1));
                
                regions.add(World.getRegionAtDepth(context, wx, wz, 2));
                seeds.add(World.getRegionSeedAtDepth(context, wx, wz, 2));
                
                regions.add(World.getRegionAtDepth(context, wx, wz, 3));
                seeds.add(World.getRegionSeedAtDepth(context, wx, wz, 3));
                
                // Ensure we have enough images for the depth
                while (depthImages.size() < regions.size()) {
                    depthImages.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
                }
                
                Region leafRegion = regions.get(regions.size() - 1);
                
                // Edge detection by sampling neighbors
                float combinedEdge = 100.0f; // Dummy value if not edge
                boolean isEdge = false;
                
                // Check leaf edge
                Region right = World.getRegion(context, wx + step, wz);
                Region down = World.getRegion(context, wx, wz + step);
                if (!leafRegion.name().equals(right.name()) || !leafRegion.name().equals(down.name())) {
                    combinedEdge = 0.0f;
                    isEdge = true;
                }

                // Update digest with raw values to be independent of rendering colors
                updateDigest(digest, rawRegionId);
                updateDigest(digest, isEdge ? 1.0f : 0.0f);
                updateDigest(digest, river);
                updateDigest(digest, ridge);
                updateDigest(digest, leafRegion.name().hashCode());

                // Visualization (keep this for debug output)
                // Voronoi Cell ID visualization (random color from hash)
                int r = (int) (MathUtils.hash64(rawRegionId, 1, 0, 0) & 0xFF);
                int g = (int) (MathUtils.hash64(rawRegionId, 2, 0, 0) & 0xFF);
                int b = (int) (MathUtils.hash64(rawRegionId, 3, 0, 0) & 0xFF);
                imgVoronoi.setRGB(x, y, (r << 16) | (g << 8) | b);

                // Depth layers
                for (int i = 0; i < regions.size(); i++) {
                    Region region = regions.get(i);
                    long regionSeed = seeds.get(i);
                    int color = getGenericRegionColor(region);
                    
                    // Check neighbors for edge of current layer
                    // We check both region name AND seed to detect edges between identical regions (e.g. Root tiles)
                    Region layerRight = World.getRegionAtDepth(context, wx + step, wz, i + 1);
                    long seedRight = World.getRegionSeedAtDepth(context, wx + step, wz, i + 1);
                    
                    Region layerDown = World.getRegionAtDepth(context, wx, wz + step, i + 1);
                    long seedDown = World.getRegionSeedAtDepth(context, wx, wz + step, i + 1);
                    
                    if (!region.name().equals(layerRight.name()) || regionSeed != seedRight ||
                        !region.name().equals(layerDown.name()) || regionSeed != seedDown) {
                         // Darken color for edge
                         int cr = (color >> 16) & 0xFF;
                         int cg = (color >> 8) & 0xFF;
                         int cb = color & 0xFF;
                         color = ((cr/2) << 16) | ((cg/2) << 8) | (cb/2);
                    }
                    
                    // Overlay parent edges on child layers
                    if (i > 0) {
                        Region parent = regions.get(i - 1);
                        long parentSeed = seeds.get(i - 1);
                        
                        Region parentRight = World.getRegionAtDepth(context, wx + step, wz, i);
                        long parentSeedRight = World.getRegionSeedAtDepth(context, wx + step, wz, i);
                        
                        Region parentDown = World.getRegionAtDepth(context, wx, wz + step, i);
                        long parentSeedDown = World.getRegionSeedAtDepth(context, wx, wz + step, i);
                        
                        if (!parent.name().equals(parentRight.name()) || parentSeed != parentSeedRight ||
                            !parent.name().equals(parentDown.name()) || parentSeed != parentSeedDown) {
                            color = 0xFFFFFF; // White edge for parent
                        }
                    }
                    
                    depthImages.get(i).setRGB(x, y, color);
                }

                // Edge grayscale
                int edgeVal = (int) (MathUtils.clamp01(combinedEdge / Config.EDGE_SCALE) * 255);
                imgEdge.setRGB(x, y, (edgeVal << 16) | (edgeVal << 8) | edgeVal);

                // River grayscale
                int riverVal = (int) (river * 255);
                imgRiver.setRGB(x, y, (riverVal << 16) | (riverVal << 8) | riverVal);

                // Ridge grayscale
                int ridgeVal = (int) (ridge * 255);
                imgRidge.setRGB(x, y, (ridgeVal << 16) | (ridgeVal << 8) | ridgeVal);

                // Combined visualization (Leaf Region color + Edge darkening)
                int archColor = getGenericRegionColor(leafRegion);
                float edgeFactor = MathUtils.clamp01(combinedEdge / Config.EDGE_SCALE);
                // Make edges darker
                int cr = (archColor >> 16) & 0xFF;
                int cg = (archColor >> 8) & 0xFF;
                int cb = archColor & 0xFF;
                
                cr = (int) (cr * edgeFactor);
                cg = (int) (cg * edgeFactor);
                cb = (int) (cb * edgeFactor);
                
                imgCombined.setRGB(x, y, (cr << 16) | (cg << 8) | cb);
            }
        }
        
        String actualDigest = HexFormat.of().formatHex(digest.digest());
        
        File outDir = new File("build/test-snapshots");
        outDir.mkdirs();
        ImageIO.write(imgVoronoi, "png", new File(outDir, "voronoi.png"));
        ImageIO.write(imgEdge, "png", new File(outDir, "edge.png"));
        ImageIO.write(imgRiver, "png", new File(outDir, "river.png"));
        ImageIO.write(imgRidge, "png", new File(outDir, "ridge.png"));
        ImageIO.write(imgCombined, "png", new File(outDir, "combined.png"));
        
        for (int i = 0; i < depthImages.size(); i++) {
            ImageIO.write(depthImages.get(i), "png", new File(outDir, "depth_" + i + ".png"));
        }

        System.out.println("Snapshot digest: " + actualDigest);

        if (!EXPECTED_DIGEST.equals(actualDigest)) {
            fail("Snapshot digest mismatch! Actual: " + actualDigest);
        }
    }

    private void updateDigest(MessageDigest digest, int val) {
        digest.update((byte) (val >> 24));
        digest.update((byte) (val >> 16));
        digest.update((byte) (val >> 8));
        digest.update((byte) val);
    }
    
    private void updateDigest(MessageDigest digest, float val) {
        updateDigest(digest, Float.floatToRawIntBits(val));
    }

    private int getGenericRegionColor(Region region) {
        int color = getTestRegionColor(region);
        if (color != 0) return color;
        
        // Hash fallback with high saturation
        int h = region.name().hashCode();
        int r = 64 + (Math.abs(h) % 192);
        int g = 64 + (Math.abs(h >> 8) % 192);
        int b = 64 + (Math.abs(h >> 16) % 192);
        return (r << 16) | (g << 8) | b;
    }

    private int getTestRegionColor(Region region) {
        switch (region.name()) {
            // World level
            case "WORLD": return 0x333333;
            
            // Main areas
            case "SEASONS_HUB": return 0x88FF88;
            case "TEMPERATURE_LAB": return 0xFF8888;
            case "BIOME_LAB": return 0x8888FF;
            case "VANILLA": return 0xAAAAAA;
            case "BORDER": return 0x000044;
            
            // Seasons hub children
            case "SPAWN": return 0xFFFFFF;      // White - easy to spot
            case "SPRING": return 0x00FF00;     // Bright green
            case "SUMMER": return 0xFFFF00;     // Yellow
            case "AUTUMN": return 0xFF8800;     // Orange
            case "WINTER": return 0x00FFFF;     // Cyan
            
            // Temperature lab zones
            case "FREEZING": return 0x0000FF;   // Blue
            case "COLD": return 0x4488FF;       // Light blue
            case "MILD": return 0x44FF44;       // Green
            case "WARM": return 0xFFAA00;       // Orange
            case "HOT": return 0xFF0000;        // Red
            
            // Biome lab zones  
            case "OCEANS_ONLY": return 0x0044AA;
            case "FORESTS_ONLY": return 0x006600;
            case "MOUNTAINS_ONLY": return 0x888888;
            case "RIVERS_ONLY": return 0x4488FF;
            
            default: return 0x000000;
        }
    }
}
