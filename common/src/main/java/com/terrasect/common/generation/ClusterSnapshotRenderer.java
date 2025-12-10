package com.terrasect.common.generation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders a tiled cluster map into a PNG snapshot. The renderer repeats a single cluster tile across
 * the requested output dimensions to mimic how a chunk-based world would reuse cluster patterns.
 */
public final class ClusterSnapshotRenderer {
    private ClusterSnapshotRenderer() {
    }

    public static BufferedImage render(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        Objects.requireNonNull(pattern, "pattern");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int clusterSize = pattern.clusterSize();
        int[][] regionMap = pattern.regionMap();
        boolean[][] outlineMask = pattern.outlineMask();
        List<RegionDefinition> regions = pattern.regions();
        int[] regionColors = computeRegionColors(regions);
        for (int y = 0; y < height; y++) {
            int cy = Math.floorMod(y, clusterSize);
            for (int x = 0; x < width; x++) {
                int cx = Math.floorMod(x, clusterSize);
                int regionIndex = regionMap[cy][cx];
                int argb = outlineMask[cy][cx] ? Color.BLACK.getRGB() : regionColors[regionIndex];
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    public static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private static int[] computeRegionColors(List<RegionDefinition> regions) {
        int[] colors = new int[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            int hash = region.name().toLowerCase(Locale.ROOT).hashCode();
            int r = 64 + Math.abs(hash) % 128;
            int g = 64 + Math.abs(hash * 31) % 128;
            int b = 64 + Math.abs(hash * 17) % 128;
            colors[i] = new Color(r, g, b).getRGB();
        }
        return colors;
    }
}
