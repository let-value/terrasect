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

    // Placeholder digest - update after first run
    private static final String EXPECTED_DIGEST = "a48633a0d7d029827b4f65d4d3d3cd7c7e5090bc52e2165a511cce2b3d688bc2"; 

    private static class MockContext implements Strategy {
        private final long seed;
        public MockContext(long seed) { this.seed = seed; }
        @Override public long getSeed() { return seed; }
        @Override public float getRiverInfluence(int x, int z) { return NoiseUtils.riverMask(x, z, seed); }
        @Override public float getRidgeInfluence(int x, int z) { return NoiseUtils.ridgeMask(x, z, seed); }
    }

    @Test
    public void generateSnapshots() throws IOException, NoSuchAlgorithmException {
        // Setup NarrativeWorld regions for testing
        Region civilization = Region.builder("CIVILIZATION")
            .budget(100)
            .addChildren(
                Region.builder("RUINS").budget(50000).adjacentTo("PILGRIMAGE_PATH").build(),
                Region.builder("HARBOR").budget(30000).adjacentTo("PILGRIMAGE_PATH").build(),
                Region.builder("PILGRIMAGE_PATH").budget(20000).adjacentTo("RUINS", "HARBOR").build()
            ).build();

        Region wilderness = Region.builder("WILDERNESS")
            .budget(100)
            .addChildren(
                Region.builder("FORBIDDEN_WOODS").budget(60000).adjacentTo("PLAINS_OF_ASH").build(),
                Region.builder("PLAINS_OF_ASH").budget(40000).adjacentTo("FORBIDDEN_WOODS").build()
            ).build();

        Region highlands = Region.builder("HIGHLANDS")
            .budget(100)
            .addChildren(
                Region.builder("MOUNTAIN_PASS").budget(40000).adjacentTo("CRYSTAL_CANYON").build(),
                Region.builder("CRYSTAL_CANYON").budget(30000).adjacentTo("MOUNTAIN_PASS").build()
            ).build();

        Region root = Region.builder("ROOT")
            .addChildren(civilization, wilderness, highlands)
            .build();
            
        World.setRoot(root);

        long seed = 987654321L;
        Strategy context = new MockContext(seed);
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
                long rawRegionData = RegionField.getRegionData(wx, wz, seed);
                int rawRegionId = RegionField.unpackRegionId(rawRegionData);
                
                float river = context.getRiverInfluence(wx, wz);
                float ridge = context.getRidgeInfluence(wx, wz);
                
                // Get full hierarchy
                List<World.TraversalStep> steps = World.getRegionHierarchy(wx, wz, context);
                
                // Ensure we have enough images for the depth
                while (depthImages.size() < steps.size()) {
                    depthImages.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
                }
                
                Region leafRegion = steps.get(steps.size() - 1).region();
                
                // Combined edge distance (min of all layers to show all boundaries)
                float combinedEdge = Float.MAX_VALUE;
                for (World.TraversalStep s : steps) {
                    combinedEdge = Math.min(combinedEdge, s.edgeDistance());
                }

                // Update digest with raw values to be independent of rendering colors
                updateDigest(digest, rawRegionId);
                updateDigest(digest, combinedEdge);
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
                for (int i = 0; i < steps.size(); i++) {
                    Region region = steps.get(i).region();
                    int color = getGenericRegionColor(region);
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
            default: return 0x000000;
        }
    }
}
