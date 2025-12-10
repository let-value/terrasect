package com.terrasect.common.generation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
        int clusterSize = pattern.clusterSize();
        List<RegionDefinition> regions = pattern.regions();
        int[] regionColors = computeRegionColors(regions);
        Map<Long, ClusterMapGenerator.ClusterSite> siteCache = new HashMap<>();
        Map<Long, ClusterMapGenerator.ResolvedSite> resolvedCache = new LinkedHashMap<>(128, 0.75f, true) {
            private static final int MAX_ENTRIES = 180;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ClusterMapGenerator.ResolvedSite> eldest) {
                return size() > MAX_ENTRIES;
            }
        };

        for (int y = 0; y < height; y++) {
            ClusterMapGenerator.ClusterSite[] currentRowSites = computeSitesForRow(pattern, siteCache, width, y);
            int[] currentRowRegions = computeRegionsForRow(pattern, siteCache, resolvedCache, width, y);

            ClusterMapGenerator.ClusterSite[] nextRowSites = y + 1 < height
                ? computeSitesForRow(pattern, siteCache, width, y + 1)
                : null;
            int[] nextRowRegions = y + 1 < height
                ? computeRegionsForRow(pattern, siteCache, resolvedCache, width, y + 1)
                : null;

            for (int x = 0; x < width; x++) {
                int regionIndex = currentRowRegions[x];
                boolean outline = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (!outline) {
                    if (x + 1 < width && currentRowRegions[x + 1] != regionIndex) {
                        outline = true;
                    }
                    if (nextRowRegions != null && nextRowRegions[x] != regionIndex) {
                        outline = true;
                    }
                    if (x + 1 < width && currentRowSites[x + 1] != currentRowSites[x]) {
                        outline = true;
                    }
                    if (nextRowSites != null && nextRowSites[x] != currentRowSites[x]) {
                        outline = true;
                    }
                }
                int argb = outline ? Color.BLACK.getRGB() : regionColors[regionIndex];
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static ClusterMapGenerator.ClusterSite[] computeSitesForRow(ClusterMapGenerator.ClusterPattern pattern,
                                                                        Map<Long, ClusterMapGenerator.ClusterSite> cache,
                                                                        int width, int y) {
        ClusterMapGenerator.ClusterSite[] row = new ClusterMapGenerator.ClusterSite[width];
        for (int x = 0; x < width; x++) {
            row[x] = pattern.siteForPoint(cache, x, y);
        }
        return row;
    }

    private static int[] computeRegionsForRow(ClusterMapGenerator.ClusterPattern pattern,
                                              Map<Long, ClusterMapGenerator.ClusterSite> siteCache,
                                              Map<Long, ClusterMapGenerator.ResolvedSite> resolvedCache,
                                              int width, int y) {
        int[] regions = new int[width];
        for (int x = 0; x < width; x++) {
            regions[x] = pattern.regionForPoint(siteCache, resolvedCache, x, y);
        }
        return regions;
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
