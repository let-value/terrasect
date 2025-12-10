package com.terrasect.common.generation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Renders a procedural cluster map into a PNG snapshot using Voronoi-shaped cluster ownership and
 * organic in-cluster region layouts.
 */
public final class ClusterSnapshotRenderer {
    private ClusterSnapshotRenderer() {
    }

    public static BufferedImage render(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        Objects.requireNonNull(pattern, "pattern");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<RegionDefinition> regions = pattern.regions();
        int[] regionColors = computeRegionColors(regions);
        int[] pixels = new int[width * height];
        int[] regionGrid = new int[width * height];
        long[] siteKeys = new long[width * height];
        int outlineColor = Color.BLACK.getRGB();

        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                ClusterMapGenerator.ClusterSite site = pattern.siteForPoint(x, y);
                siteKeys[idx] = site.key();
                regionGrid[idx] = pattern.regionForPoint(x, y);
            }
        });

        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int regionIndex = regionGrid[idx];
                boolean outline = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (!outline) {
                    int rightIdx = idx + 1;
                    int downIdx = idx + width;
                    outline = regionGrid[rightIdx] != regionIndex
                        || regionGrid[downIdx] != regionIndex
                        || siteKeys[rightIdx] != siteKeys[idx]
                        || siteKeys[downIdx] != siteKeys[idx];
                }
                pixels[idx] = outline ? outlineColor : regionColors[regionIndex];
            }
        });

        image.setRGB(0, 0, width, height, pixels, 0, width);
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
