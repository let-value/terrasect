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
        List<RegionDefinition> regions = pattern.regions();
        int[] regionColors = computeRegionColors(regions);
        int tilesX = (int) Math.ceil(width / (double) clusterSize);
        int tilesY = (int) Math.ceil(height / (double) clusterSize);
        for (int tileY = 0; tileY < tilesY; tileY++) {
            for (int tileX = 0; tileX < tilesX; tileX++) {
                ClusterMapGenerator.TilePattern tile = pattern.tile(tileX, tileY);
                int startX = tileX * clusterSize;
                int startY = tileY * clusterSize;
                int maxX = Math.min(startX + clusterSize, width);
                int maxY = Math.min(startY + clusterSize, height);
                for (int y = startY; y < maxY; y++) {
                    int cy = y - startY;
                    for (int x = startX; x < maxX; x++) {
                        int cx = x - startX;
                        int regionIndex = tile.regionMap()[cy][cx];
                        int argb = tile.outlineMask()[cy][cx] ? Color.BLACK.getRGB() : regionColors[regionIndex];
                        image.setRGB(x, y, argb);
                    }
                }
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
