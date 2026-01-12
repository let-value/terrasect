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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

@SnapshotTests
public class SnapshotTest {

    @Test public void testRegionDistribution() {
        World.register(TestRegions.buildTestWorld(), World.OVERWORLD);

        var seed = 987654321L;
        var context = new MockStrategy(seed);

        var width = 100000;
        var height = 100000;
        var step = 100;

        System.out.println("Sampling Depth 1 (Children of ROOT):");
        var counts = RegionSampler.sample(0, 0, width, height, step, 1, context);
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

        @Override public long getSeed() {
            return seed;
        }

        @Override public long getInfluence(int x, int z) {
            var river = NoiseUtils.riverMask(x, z, seed);
            var ridge = NoiseUtils.ridgeMask(x, z, seed);
            return Packer.packPair(river, ridge);
        }
    }

    @Test public void generateSnapshots(Snapshot snapshot) throws IOException, NoSuchAlgorithmException {
        World.register(TestRegions.buildTestWorld(), World.OVERWORLD);
        var seed = 987654321L;
        var context = new MockStrategy(seed);

        World.initialize(context);
        var width = 512;
        var height = 512;
        var step = 4;

        var imgVoronoi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var imgEdge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var imgRiver = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var imgRidge = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var imgCombined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        var depthImages = new ArrayList<BufferedImage>();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {

                var wx = (x - width / 2) * step;
                var wz = (y - height / 2) * step;

                var influence = context.getInfluence(wx, wz);
                var river = Packer.unpackPairFirst(influence);
                var ridge = Packer.unpackPairSecond(influence);

                var regions = new ArrayList<Region>();
                var seeds = new ArrayList<Long>();

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

                var leafRegion = regions.get(regions.size() - 1);

                TraversalResult leafTraversal = World.traverse(context, wx, wz);
                float edgeDistance = leafTraversal != null ? leafTraversal.edgeDistance : 1.0f;
                var isEdge = false;

                var right = World.traverse(context, wx + step, wz).region;
                var down = World.traverse(context, wx, wz + step).region;
                if (!leafRegion.name().equals(right.name())
                        || !leafRegion.name().equals(down.name())) {
                    isEdge = true;
                }

                var leafRegionHash = leafRegion.name().hashCode();
                updateDigest(digest, leafRegionHash);
                updateDigest(digest, isEdge ? 1.0f : 0.0f);
                updateDigest(digest, river);
                updateDigest(digest, ridge);
                updateDigest(digest, leafRegionHash);

                var r = (int) (MathUtils.hash64(leafRegionHash, 1, 0, 0) & 0xFF);
                var g = (int) (MathUtils.hash64(leafRegionHash, 2, 0, 0) & 0xFF);
                var b = (int) (MathUtils.hash64(leafRegionHash, 3, 0, 0) & 0xFF);
                imgVoronoi.setRGB(x, y, (r << 16) | (g << 8) | b);

                for (var i = 0; i < regions.size(); i++) {
                    var region = regions.get(i);
                    var regionSeed = seeds.get(i);
                    var color = getGenericRegionColor(region);

                    TraversalResult rightTraversal = World.traverse(context, wx + step, wz, i + 1);
                    var layerRight = rightTraversal.region;
                    var seedRight = rightTraversal.seed;

                    TraversalResult downTraversal = World.traverse(context, wx, wz + step, i + 1);
                    var layerDown = downTraversal.region;
                    var seedDown = downTraversal.seed;

                    if (!region.name().equals(layerRight.name())
                            || regionSeed != seedRight
                            || !region.name().equals(layerDown.name())
                            || regionSeed != seedDown) {

                        var cr = (color >> 16) & 0xFF;
                        var cg = (color >> 8) & 0xFF;
                        var cb = color & 0xFF;
                        color = ((cr / 2) << 16) | ((cg / 2) << 8) | (cb / 2);
                    }

                    if (i > 0) {
                        var parent = regions.get(i - 1);
                        var parentSeed = seeds.get(i - 1);

                        TraversalResult parentRightTraversal = World.traverse(context, wx + step, wz, i);
                        var parentRight = parentRightTraversal.region;
                        var parentSeedRight = parentRightTraversal.seed;

                        TraversalResult parentDownTraversal = World.traverse(context, wx, wz + step, i);
                        var parentDown = parentDownTraversal.region;
                        var parentSeedDown = parentDownTraversal.seed;

                        if (!parent.name().equals(parentRight.name())
                                || parentSeed != parentSeedRight
                                || !parent.name().equals(parentDown.name())
                                || parentSeed != parentSeedDown) {
                            color = 0xFFFFFF;
                        }
                    }

                    depthImages.get(i).setRGB(x, y, color);
                }

                var edgeVal = (int) (edgeDistance * 255);
                imgEdge.setRGB(x, y, (edgeVal << 16) | (edgeVal << 8) | edgeVal);

                var riverVal = (int) (river * 255);
                imgRiver.setRGB(x, y, (riverVal << 16) | (riverVal << 8) | riverVal);

                var ridgeVal = (int) (ridge * 255);
                imgRidge.setRGB(x, y, (ridgeVal << 16) | (ridgeVal << 8) | ridgeVal);

                var archColor = getGenericRegionColor(leafRegion);

                var cr = (archColor >> 16) & 0xFF;
                var cg = (archColor >> 8) & 0xFF;
                var cb = archColor & 0xFF;

                cr = (int) (cr * edgeDistance);
                cg = (int) (cg * edgeDistance);
                cb = (int) (cb * edgeDistance);

                imgCombined.setRGB(x, y, (cr << 16) | (cg << 8) | cb);
            }
        }

        var actualDigest = HexFormat.of().formatHex(digest.digest());

        var outDir = new File("build/test-snapshots");
        outDir.mkdirs();
        ImageIO.write(imgVoronoi, "png", new File(outDir, "voronoi.png"));
        ImageIO.write(imgEdge, "png", new File(outDir, "edge.png"));
        ImageIO.write(imgRiver, "png", new File(outDir, "river.png"));
        ImageIO.write(imgRidge, "png", new File(outDir, "ridge.png"));
        ImageIO.write(imgCombined, "png", new File(outDir, "combined.png"));

        for (var i = 0; i < depthImages.size(); i++) {
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
        var color = getTestRegionColor(region);
        if (color != 0) return color;

        var h = region.name().hashCode();
        var r = 64 + (Math.abs(h) % 192);
        var g = 64 + (Math.abs(h >> 8) % 192);
        var b = 64 + (Math.abs(h >> 16) % 192);
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
