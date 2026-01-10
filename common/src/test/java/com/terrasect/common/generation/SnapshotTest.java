package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.TestRegions;
import com.terrasect.common.definition.Region;
import com.terrasect.common.helpers.RegionSampler;
import com.terrasect.common.util.MathUtils;
import com.terrasect.common.util.NoiseUtils;
import com.terrasect.common.util.Packer;
import de.skuzzle.test.snapshots.Snapshot;
import com.terrasect.common.testing.SnapshotTests;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

@SnapshotTests
public class SnapshotTest {

    @Test
    public void testRegionDistribution() {
        World.register(TestRegions.buildTestWorld(), World.OVERWORLD);

        long seed = 987654321L;
        Context context = new MockStrategy(seed);

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

        public MockStrategy(long seed) {
            this.seed = seed;
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public long getInfluence(int x, int z) {
            float river = NoiseUtils.riverMask(x, z, seed);
            float ridge = NoiseUtils.ridgeMask(x, z, seed);
            return Packer.packPair(river, ridge);
        }
    }

    @Test
    public void generateSnapshots(Snapshot snapshot) throws IOException, NoSuchAlgorithmException {
        World.register(TestRegions.buildTestWorld(), World.OVERWORLD);
        var seed = 987654321L;
        Context context = new MockStrategy(seed);

        World.initialize(context);
        int width = 512;
        int height = 512;
        int step = 4;

        BufferedImage imgVoronoi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgEdge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgRiver = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgRidge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage imgCombined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        List<BufferedImage> depthImages = new ArrayList<>();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int wx = (x - width / 2) * step;
                int wz = (y - height / 2) * step;

                long influence = context.getInfluence(wx, wz);
                float river = Packer.unpackPairFirst(influence);
                float ridge = Packer.unpackPairSecond(influence);

                List<Region> regions = new ArrayList<>();
                List<Long> seeds = new ArrayList<>();

                TraversalResult traversal = World.traverse(context, wx, wz, 1);
                regions.add(traversal.region);
                seeds.add(traversal.seed);

                traversal = World.traverse(context, wx, wz, 2);
                regions.add(traversal.region);
                seeds.add(traversal.seed);

                traversal = World.traverse(context, wx, wz, 3);
                regions.add(traversal.region);
                seeds.add(traversal.seed);

                while (depthImages.size() < regions.size()) {
                    depthImages.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
                }

                Region leafRegion = regions.get(regions.size() - 1);

                TraversalResult leafTraversal = World.traverse(context, wx, wz);
                float edgeDistance = leafTraversal != null ? leafTraversal.edgeDistance : 1.0f;
                boolean isEdge = false;

                Region right = World.traverse(context, wx + step, wz).region;
                Region down = World.traverse(context, wx, wz + step).region;
                if (!leafRegion.name().equals(right.name())
                        || !leafRegion.name().equals(down.name())) {
                    isEdge = true;
                }

                int leafRegionHash = leafRegion.name().hashCode();
                updateDigest(digest, leafRegionHash);
                updateDigest(digest, isEdge ? 1.0f : 0.0f);
                updateDigest(digest, river);
                updateDigest(digest, ridge);
                updateDigest(digest, leafRegionHash);

                int r = (int) (MathUtils.hash64(leafRegionHash, 1, 0, 0) & 0xFF);
                int g = (int) (MathUtils.hash64(leafRegionHash, 2, 0, 0) & 0xFF);
                int b = (int) (MathUtils.hash64(leafRegionHash, 3, 0, 0) & 0xFF);
                imgVoronoi.setRGB(x, y, (r << 16) | (g << 8) | b);

                for (int i = 0; i < regions.size(); i++) {
                    Region region = regions.get(i);
                    long regionSeed = seeds.get(i);
                    int color = getGenericRegionColor(region);

                    TraversalResult rightTraversal = World.traverse(context, wx + step, wz, i + 1);
                    Region layerRight = rightTraversal.region;
                    long seedRight = rightTraversal.seed;

                    TraversalResult downTraversal = World.traverse(context, wx, wz + step, i + 1);
                    Region layerDown = downTraversal.region;
                    long seedDown = downTraversal.seed;

                    if (!region.name().equals(layerRight.name())
                            || regionSeed != seedRight
                            || !region.name().equals(layerDown.name())
                            || regionSeed != seedDown) {

                        int cr = (color >> 16) & 0xFF;
                        int cg = (color >> 8) & 0xFF;
                        int cb = color & 0xFF;
                        color = ((cr / 2) << 16) | ((cg / 2) << 8) | (cb / 2);
                    }

                    if (i > 0) {
                        Region parent = regions.get(i - 1);
                        long parentSeed = seeds.get(i - 1);

                        TraversalResult parentRightTraversal = World.traverse(context, wx + step, wz, i);
                        Region parentRight = parentRightTraversal.region;
                        long parentSeedRight = parentRightTraversal.seed;

                        TraversalResult parentDownTraversal = World.traverse(context, wx, wz + step, i);
                        Region parentDown = parentDownTraversal.region;
                        long parentSeedDown = parentDownTraversal.seed;

                        if (!parent.name().equals(parentRight.name())
                                || parentSeed != parentSeedRight
                                || !parent.name().equals(parentDown.name())
                                || parentSeed != parentSeedDown) {
                            color = 0xFFFFFF;
                        }
                    }

                    depthImages.get(i).setRGB(x, y, color);
                }

                int edgeVal = (int) (edgeDistance * 255);
                imgEdge.setRGB(x, y, (edgeVal << 16) | (edgeVal << 8) | edgeVal);

                int riverVal = (int) (river * 255);
                imgRiver.setRGB(x, y, (riverVal << 16) | (riverVal << 8) | riverVal);

                int ridgeVal = (int) (ridge * 255);
                imgRidge.setRGB(x, y, (ridgeVal << 16) | (ridgeVal << 8) | ridgeVal);

                int archColor = getGenericRegionColor(leafRegion);

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

        snapshot.assertThat(actualDigest).asText().matchesSnapshotText();
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

        int h = region.name().hashCode();
        int r = 64 + (Math.abs(h) % 192);
        int g = 64 + (Math.abs(h >> 8) % 192);
        int b = 64 + (Math.abs(h >> 16) % 192);
        return (r << 16) | (g << 8) | b;
    }

    private int getTestRegionColor(Region region) {
        switch (region.name()) {
            case "WORLD":
                return 0x333333;

            case "SEASONS_HUB":
                return 0x88FF88;
            case "TEMPERATURE_LAB":
                return 0xFF8888;
            case "BIOME_LAB":
                return 0x8888FF;
            case "VANILLA":
                return 0xAAAAAA;
            case "BORDER":
                return 0x000044;

            case "SPAWN":
                return 0xFFFFFF;
            case "SPRING":
                return 0x00FF00;
            case "SUMMER":
                return 0xFFFF00;
            case "AUTUMN":
                return 0xFF8800;
            case "WINTER":
                return 0x00FFFF;

            case "FREEZING":
                return 0x0000FF;
            case "COLD":
                return 0x4488FF;
            case "MILD":
                return 0x44FF44;
            case "WARM":
                return 0xFFAA00;
            case "HOT":
                return 0xFF0000;

            case "OCEANS_ONLY":
                return 0x0044AA;
            case "FORESTS_ONLY":
                return 0x006600;
            case "MOUNTAINS_ONLY":
                return 0x888888;
            case "RIVERS_ONLY":
                return 0x4488FF;

            default:
                return 0x000000;
        }
    }
}
