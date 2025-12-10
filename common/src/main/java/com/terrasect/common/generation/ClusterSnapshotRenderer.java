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
        Map<Long, ClusterMapGenerator.TilePattern> tileCache = new LinkedHashMap<>(32, 0.75f, true) {
            private static final int MAX_ENTRIES = 48;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ClusterMapGenerator.TilePattern> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
        Map<Long, ClusterMapGenerator.ClusterSite> siteCache = new HashMap<>();
        ClusterMapGenerator.ClusterSite[] nextRowSites = new ClusterMapGenerator.ClusterSite[width];
        ClusterMapGenerator.ClusterSite[] currentRowSites = new ClusterMapGenerator.ClusterSite[width];

        for (int y = 0; y < height; y++) {
            currentRowSites = nextRowSites;
            if (y < height - 1) {
                nextRowSites = computeSitesForRow(pattern, siteCache, width, y + 1);
            } else {
                nextRowSites = new ClusterMapGenerator.ClusterSite[width];
            }
            if (currentRowSites[0] == null) {
                currentRowSites = computeSitesForRow(pattern, siteCache, width, y);
            }

            for (int x = 0; x < width; x++) {
                ClusterMapGenerator.ClusterSite site = currentRowSites[x];
                ClusterMapGenerator.TilePattern tile = tileCache.computeIfAbsent(site.key(), key -> pattern.tileForSite(site));
                int localX = clampToTile(x, site.centerX(), clusterSize);
                int localY = clampToTile(y, site.centerY(), clusterSize);
                int regionIndex = tile.regionMap()[localY][localX];
                boolean outline = tile.outlineMask()[localY][localX];
                if (!outline) {
                    if (x + 1 < width && currentRowSites[x + 1] != site) {
                        outline = true;
                    }
                    if (y + 1 < height && nextRowSites[x] != null && nextRowSites[x] != site) {
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

    private static int clampToTile(int worldCoord, double centerCoord, int clusterSize) {
        double local = worldCoord - centerCoord + clusterSize / 2.0;
        int coord = (int) Math.floor(local);
        if (coord < 0) {
            return 0;
        }
        if (coord >= clusterSize) {
            return clusterSize - 1;
        }
        return coord;
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
