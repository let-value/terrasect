package com.terrasect.common.generation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Renders an organic cluster map into a PNG snapshot. The renderer resolves Voronoi cluster sites
 * and samples region assignment per pixel so clusters flow together instead of repeating rigid
 * square tiles.
 */
public final class ClusterSnapshotRenderer {
    private ClusterSnapshotRenderer() {
    }

    public static BufferedImage render(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        Objects.requireNonNull(pattern, "pattern");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<RegionDefinition> regions = pattern.regions();
        int[] regionColors = computeRegionColors(regions);
        Map<Long, ClusterMapGenerator.ClusterSite> siteCache = new HashMap<>();
        Map<Long, List<Point2D.Double>> anchorCache = new HashMap<>();
        ClusterMapGenerator.ClusterSite[] nextRowSites = computeSitesForRow(pattern, siteCache, width, 0);
        ClusterMapGenerator.ClusterSite[] currentRowSites = new ClusterMapGenerator.ClusterSite[width];
        int[] nextRowRegions = computeRegionsForRow(pattern, anchorCache, nextRowSites, width, 0);
        int[] currentRowRegions = new int[width];

        for (int y = 0; y < height; y++) {
            currentRowSites = nextRowSites;
            currentRowRegions = nextRowRegions;
            if (y < height - 1) {
                nextRowSites = computeSitesForRow(pattern, siteCache, width, y + 1);
                nextRowRegions = computeRegionsForRow(pattern, anchorCache, nextRowSites, width, y + 1);
            } else {
                nextRowSites = new ClusterMapGenerator.ClusterSite[width];
                nextRowRegions = new int[width];
            }

            for (int x = 0; x < width; x++) {
                ClusterMapGenerator.ClusterSite site = currentRowSites[x];
                int regionIndex = currentRowRegions[x];
                boolean outline = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (!outline && x + 1 < width) {
                    if (currentRowSites[x + 1] != site || currentRowRegions[x + 1] != regionIndex) {
                        outline = true;
                    }
                }
                if (!outline && y + 1 < height) {
                    if (nextRowSites[x] != site || nextRowRegions[x] != regionIndex) {
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
                                              Map<Long, List<Point2D.Double>> anchorCache,
                                              ClusterMapGenerator.ClusterSite[] sites,
                                              int width, int y) {
        int[] regions = new int[width];
        for (int x = 0; x < width; x++) {
            ClusterMapGenerator.ClusterSite site = sites[x];
            regions[x] = pattern.regionForSite(anchorCache, site, x, y);
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
