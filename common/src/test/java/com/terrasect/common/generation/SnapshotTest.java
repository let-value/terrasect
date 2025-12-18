package com.terrasect.common.generation;

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
    private static final String EXPECTED_DIGEST = "3ac7a372cc407fb2757682413c3890cef7722b0972735c4052cd558390a23329";

    @Test
    public void testRegionDistribution() {
        World.setRoot(buildUniverse());
        
        long seed = 987654321L;
        Strategy context = new MockStrategy(seed);
        
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

    static class MockStrategy implements Strategy {
        private final long seed;
        public MockStrategy(long seed) { this.seed = seed; }
        @Override public long getSeed() { return seed; }
        @Override public float getRiverInfluence(int x, int z) { return NoiseUtils.riverMask(x, z, seed); }
        @Override public float getRidgeInfluence(int x, int z) { return NoiseUtils.ridgeMask(x, z, seed); }
    }

    @Test
    public void generateSnapshots() throws IOException, NoSuchAlgorithmException {
        World.setRoot(buildUniverse());

        long seed = 987654321L;
        Strategy context = new MockStrategy(seed);
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
                int wx = x * step;
                int wz = y * step;

                // Raw noise for background reference (default scale)
                long rawRegionData = RegionField.getRegionData(wx, wz, seed, 512, 200.0f, 2048);
                int rawRegionId = RegionField.unpackRegionId(rawRegionData);
                
                float river = context.getRiverInfluence(wx, wz);
                float ridge = context.getRidgeInfluence(wx, wz);
                
                // Get full hierarchy
                List<Region> regions = new ArrayList<>();
                List<Long> seeds = new ArrayList<>();
                
                // We want to capture regions at each depth
                // Depth 0 (User): Root (Infinite Tiling) -> returns Root
                // Depth 1 (User): Children (Local Partitioning) -> returns Civ/Wild/High
                // Depth 2 (User): Grandchildren -> returns Ruins/Harbor etc.
                
                // We skip the actual root (Universe) which is just a container
                
                regions.add(World.getRegionAtDepth(wx, wz, context, 1));
                seeds.add(World.getRegionSeedAtDepth(wx, wz, context, 1));
                
                regions.add(World.getRegionAtDepth(wx, wz, context, 2));
                seeds.add(World.getRegionSeedAtDepth(wx, wz, context, 2));
                
                regions.add(World.getRegionAtDepth(wx, wz, context, 3));
                seeds.add(World.getRegionSeedAtDepth(wx, wz, context, 3));
                
                // Ensure we have enough images for the depth
                while (depthImages.size() < regions.size()) {
                    depthImages.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
                }
                
                Region leafRegion = regions.get(regions.size() - 1);
                
                // Edge detection by sampling neighbors
                float combinedEdge = 100.0f; // Dummy value if not edge
                boolean isEdge = false;
                
                // Check leaf edge
                Region right = World.getRegion(wx + step, wz, context);
                Region down = World.getRegion(wx, wz + step, context);
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
                    Region layerRight = World.getRegionAtDepth(wx + step, wz, context, i + 1);
                    long seedRight = World.getRegionSeedAtDepth(wx + step, wz, context, i + 1);
                    
                    Region layerDown = World.getRegionAtDepth(wx, wz + step, context, i + 1);
                    long seedDown = World.getRegionSeedAtDepth(wx, wz + step, context, i + 1);
                    
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
                        
                        Region parentRight = World.getRegionAtDepth(wx + step, wz, context, i);
                        long parentSeedRight = World.getRegionSeedAtDepth(wx + step, wz, context, i);
                        
                        Region parentDown = World.getRegionAtDepth(wx, wz + step, context, i);
                        long parentSeedDown = World.getRegionSeedAtDepth(wx, wz + step, context, i);
                        
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

    private Region buildUniverse() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("UNIVERSE")
            .child("ROOT", root -> root
                .child("CIVILIZATION", civ -> civ
                    .child("RUINS", ruins -> ruins.adjacentTo("PILGRIMAGE_PATH")
                        .child("SHRINE", shrine -> shrine.budget(25))
                        .child("CATACOMBS", catacombs -> catacombs.budget(100)))
                    .child("HARBOR", harbor -> harbor.budget(75).adjacentTo("PILGRIMAGE_PATH"))
                    .child("PILGRIMAGE_PATH", path -> path.budget(50).adjacentTo("RUINS", "HARBOR")))
                .child("WILDERNESS", wild -> wild
                    .child("FORBIDDEN_WOODS", woods -> woods.budget(150).adjacentTo("PLAINS_OF_ASH"))
                    .child("PLAINS_OF_ASH", plains -> plains.budget(100).adjacentTo("FORBIDDEN_WOODS")))
                .child("HIGHLANDS", high -> high
                    .child("MOUNTAIN_PASS", pass -> pass.budget(100).adjacentTo("CRYSTAL_CANYON"))
                    .child("CRYSTAL_CANYON", canyon -> canyon.budget(75).adjacentTo("MOUNTAIN_PASS")))
                .child("FACTORY", high -> high
                    .child("MEKA", pass -> pass.budget(200).adjacentTo("CRYSTAL_CANYON"))
                    .child("CREATE", canyon -> canyon.budget(50).adjacentTo("MOUNTAIN_PASS")))
                );

        return registry.build("UNIVERSE");
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
        int color = getRegionColor(region);
        if (color != 0) return color;
        color = getClusterColor(region);
        if (color != 0) return color;
        
        // Hash fallback
        int h = region.name().hashCode();
        int r = (h & 0xFF0000) >> 16;
        int g = (h & 0x00FF00) >> 8;
        int b = (h & 0x0000FF);
        return (r << 16) | (g << 8) | b;
    }

    private int getClusterColor(Region cluster) {
        switch (cluster.name()) {
            case "CIVILIZATION": return 0xFF0000; // Red
            case "WILDERNESS": return 0x00FF00; // Green
            case "HIGHLANDS": return 0x0000FF; // Blue
            default: return 0x000000;
        }
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
            case "SHRINE": return 0xFF00FF; // Magenta
            case "CATACOMBS": return 0x440044; // Dark Magenta
            case "ROOT": return 0x555555; // Dark Gray
            case "UNIVERSE": return 0xFFFFFF; // White
            default: return 0x000000;
        }
    }
}
