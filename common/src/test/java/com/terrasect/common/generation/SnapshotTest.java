package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.TestRegions;
import com.terrasect.common.definition.Region;
import com.terrasect.common.util.Packer;
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
    private static final String EXPECTED_DIGEST = "ac5557559bf804851cf974e40cbcabfe4c86e77ccd1e52e7b346c0585a112831";

    @Test
    public void testRegionDistribution() {
        World.register(TestRegions.buildTestWorld(), World.OVERWORLD);
        
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
            return Packer.packPair(river, ridge);
        }
    }

    @Test
    public void generateSnapshots() throws IOException, NoSuchAlgorithmException {
        World.register(TestRegions.buildTestWorld(), World.OVERWORLD);
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

                long influence = context.getInfluence(wx, wz);
                float river = Packer.unpackPairFirst(influence);
                float ridge = Packer.unpackPairSecond(influence);
                
                // Get full hierarchy
                List<Region> regions = new ArrayList<>();
                List<Long> seeds = new ArrayList<>();
                
                // We want to capture regions at each depth
                // Depth 0 (User): Root (Infinite Tiling) -> returns Root
                // Depth 1 (User): Children (Local Partitioning) -> returns Civ/Wild/High
                // Depth 2 (User): Grandchildren -> returns Ruins/Harbor etc.
                
                // We skip the actual root (Universe) which is just a container
                
                TraversalResult traversal = World.traverse(context, wx, wz, 1);
                regions.add(traversal.region);
                seeds.add(traversal.seed);
                
                traversal = World.traverse(context, wx, wz, 2);
                regions.add(traversal.region);
                seeds.add(traversal.seed);
                
                traversal = World.traverse(context, wx, wz, 3);
                regions.add(traversal.region);
                seeds.add(traversal.seed);
                
                // Ensure we have enough images for the depth
                while (depthImages.size() < regions.size()) {
                    depthImages.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
                }
                
                Region leafRegion = regions.get(regions.size() - 1);
                
                // Get edge distance from the traversal result (1 = center, 0 = edge)
                TraversalResult leafTraversal = World.traverse(context, wx, wz);
                float edgeDistance = leafTraversal != null ? leafTraversal.edgeDistance : 1.0f;
                boolean isEdge = false;
                
                // Check leaf edge
                Region right = World.traverse(context, wx + step, wz).region;
                Region down = World.traverse(context, wx, wz + step).region;
                if (!leafRegion.name().equals(right.name()) || !leafRegion.name().equals(down.name())) {
                    isEdge = true;
                }

                // Update digest with values to be independent of rendering colors
                // Use leaf region hash instead of old RegionField ID
                int leafRegionHash = leafRegion.name().hashCode();
                updateDigest(digest, leafRegionHash);
                updateDigest(digest, isEdge ? 1.0f : 0.0f);
                updateDigest(digest, river);
                updateDigest(digest, ridge);
                updateDigest(digest, leafRegionHash);

                // Visualization (keep this for debug output)
                // Region visualization (random color from hash)
                int r = (int) (MathUtils.hash64(leafRegionHash, 1, 0, 0) & 0xFF);
                int g = (int) (MathUtils.hash64(leafRegionHash, 2, 0, 0) & 0xFF);
                int b = (int) (MathUtils.hash64(leafRegionHash, 3, 0, 0) & 0xFF);
                imgVoronoi.setRGB(x, y, (r << 16) | (g << 8) | b);

                // Depth layers
                for (int i = 0; i < regions.size(); i++) {
                    Region region = regions.get(i);
                    long regionSeed = seeds.get(i);
                    int color = getGenericRegionColor(region);
                    
                    // Check neighbors for edge of current layer
                    // We check both region name AND seed to detect edges between identical regions (e.g. Root tiles)
                    TraversalResult rightTraversal = World.traverse(context, wx + step, wz, i + 1);
                    Region layerRight = rightTraversal.region;
                    long seedRight = rightTraversal.seed;
                    
                    TraversalResult downTraversal = World.traverse(context, wx, wz + step, i + 1);
                    Region layerDown = downTraversal.region;
                    long seedDown = downTraversal.seed;
                    
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
                        
                        TraversalResult parentRightTraversal = World.traverse(context, wx + step, wz, i);
                        Region parentRight = parentRightTraversal.region;
                        long parentSeedRight = parentRightTraversal.seed;
                        
                        TraversalResult parentDownTraversal = World.traverse(context, wx, wz + step, i);
                        Region parentDown = parentDownTraversal.region;
                        long parentSeedDown = parentDownTraversal.seed;
                        
                        if (!parent.name().equals(parentRight.name()) || parentSeed != parentSeedRight ||
                            !parent.name().equals(parentDown.name()) || parentSeed != parentSeedDown) {
                            color = 0xFFFFFF; // White edge for parent
                        }
                    }
                    
                    depthImages.get(i).setRGB(x, y, color);
                }

                // Edge grayscale (edgeDistance: 1 = center = white, 0 = edge = black)
                int edgeVal = (int) (edgeDistance * 255);
                imgEdge.setRGB(x, y, (edgeVal << 16) | (edgeVal << 8) | edgeVal);

                // River grayscale
                int riverVal = (int) (river * 255);
                imgRiver.setRGB(x, y, (riverVal << 16) | (riverVal << 8) | riverVal);

                // Ridge grayscale
                int ridgeVal = (int) (ridge * 255);
                imgRidge.setRGB(x, y, (ridgeVal << 16) | (ridgeVal << 8) | ridgeVal);

                // Combined visualization (Leaf Region color + Edge darkening)
                int archColor = getGenericRegionColor(leafRegion);
                // Make edges darker (edgeDistance is 0 at edge, 1 at center)
                int cr = (archColor >> 16) & 0xFF;
                int cg = (archColor >> 8) & 0xFF;
                int cb = archColor & 0xFF;
                
                cr = (int) (cr * edgeDistance);
                cg = (int) (cg * edgeDistance);
                cb = (int) (cb * edgeDistance);
                
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
