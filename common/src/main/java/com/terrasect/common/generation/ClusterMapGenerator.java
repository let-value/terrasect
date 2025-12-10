package com.terrasect.common.generation;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces organic cluster maps using deterministic pseudo-random noise. Clusters are carved out of
 * flowing basins in a ridged noise field instead of rigid Voronoi cells, then populated with
 * soft-edged regional blobs derived from the world seed.
 *
 * <p>The cluster a coordinate belongs to is derived directly from math (noise basins resolved on
 * the fly) so a caller can identify the owning cluster before doing any heavier per-cluster work,
 * which mirrors how chunk-based world generation streams in Minecraft.</p>
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.12;
    private static final double JITTER_SCALE = 0.12;
    private static final double AREA_SCALAR = 1.35;
    private static final double WARP_STRENGTH = 0.025;
    private static final double WARP_SCALE = 7.0;
    private static final double CELL_JITTER_RATIO = 0.38;
    private static final double BASIN_STEP_RATIO = 0.9;
    private static final double BASIN_STEP_FALLOFF = 0.6;
    private static final double BASIN_PULL = 0.9;
    private static final double BASIN_FIELD_SCALE = 0.05;
    private static final int BASIN_RELAX_STEPS = 4;

    public ClusterPattern generate(ClusterDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        int clusterSize = definition.clusterSize();
        if (clusterSize == 0) {
            clusterSize = autoSize(definition.regions());
        }
        return new ClusterPattern(clusterSize, definition.regions(), seed);
    }

    private int autoSize(List<RegionDefinition> regions) {
        long total = regions.stream().mapToLong(RegionDefinition::targetArea).sum();
        int size = (int) Math.ceil(Math.sqrt(total * AREA_SCALAR));
        return Math.max(size, 96);
    }

    private Point2D.Double anchorForRegion(RegionDefinition region, ClusterSite site, int clusterSize,
                                           List<RegionDefinition> regions) {
        double padding = clusterSize * EDGE_PADDING_RATIO;
        double maxRadius = clusterSize * 0.5 - padding;
        long nameHash = region.name().hashCode();
        double baseAngle = Math.PI * 2 * valueNoise(site.siteSeed() ^ nameHash * 31L, site.cellX(), site.cellY());
        double biasX = 0.0;
        double biasY = 0.0;
        for (String adjacency : region.adjacentTo()) {
            int adjacencyHash = adjacency.hashCode();
            double adjacencyAngle = Math.PI * 2 * valueNoise(site.siteSeed() ^ adjacencyHash * 17L ^ nameHash, region.targetArea(), adjacencyHash);
            biasX += Math.cos(adjacencyAngle);
            biasY += Math.sin(adjacencyAngle);
        }
        double combinedAngle = baseAngle;
        if (biasX != 0.0 || biasY != 0.0) {
            double adjacencyAngle = Math.atan2(biasY, biasX);
            combinedAngle = lerp(baseAngle, adjacencyAngle, 0.65);
        }
        double baseRadiusNoise = valueNoise(site.siteSeed() ^ nameHash * 47L, region.targetArea(), regions.size());
        double radius = padding + baseRadiusNoise * (maxRadius - padding);
        double x = site.centerX() + Math.cos(combinedAngle) * radius;
        double y = site.centerY() + Math.sin(combinedAngle) * radius;
        return clampToRadius(site.centerX(), site.centerY(), x, y, maxRadius);
    }

    private int resolveRegion(int x, int y, int clusterSize, ClusterSite site,
                              List<RegionDefinition> regions, long seed) {
        double best = Double.MAX_VALUE;
        int bestIndex = 0;
        double jitterX = jitter(seed, x, y);
        double jitterY = jitter(seed + 41, x, y);
        double warpX = organicWarp(seed + 73, x, y, clusterSize);
        double warpY = organicWarp(seed + 109, x, y, clusterSize);
        for (int i = 0; i < regions.size(); i++) {
            Point2D anchor = anchorForRegion(regions.get(i), site, clusterSize, regions);
            double dx = x + jitterX + warpX - anchor.getX();
            double dy = y + jitterY + warpY - anchor.getY();
            double weight = 1.0 / Math.sqrt(regions.get(i).targetArea());
            double distance = (dx * dx + dy * dy) * weight;
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double jitter(long seed, int x, int y) {
        long hash = seed;
        hash ^= (long) x * 341873128712L;
        hash ^= (long) y * 132897987541L;
        hash ^= (hash << 13);
        hash ^= (hash >>> 7);
        hash ^= (hash << 17);
        double normalized = (hash & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL;
        return (normalized - 0.5) * JITTER_SCALE;
    }

    private double organicWarp(long seed, int x, int y, int clusterSize) {
        double scale = Math.max(clusterSize / WARP_SCALE, 1.0);
        double noise = fbm(seed, x / scale, y / scale, 3);
        return (noise - 0.5) * clusterSize * WARP_STRENGTH;
    }

    private double fbm(long seed, double x, double y, int octaves) {
        double value = 0.0;
        double amplitude = 0.5;
        double frequency = 1.0;
        for (int i = 0; i < octaves; i++) {
            value += amplitude * smoothNoise(seed + i * 19, x * frequency, y * frequency);
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return value;
    }

    private double smoothNoise(long seed, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double sx = x - x0;
        double sy = y - y0;
        double n00 = valueNoise(seed, x0, y0);
        double n10 = valueNoise(seed, x1, y0);
        double n01 = valueNoise(seed, x0, y1);
        double n11 = valueNoise(seed, x1, y1);
        double ix0 = lerp(n00, n10, fade(sx));
        double ix1 = lerp(n01, n11, fade(sx));
        return lerp(ix0, ix1, fade(sy));
    }

    private double valueNoise(long seed, int x, int y) {
        long hash = seed;
        hash ^= (long) x * 0x27d4eb2dL;
        hash ^= (long) y * 0x165667b1L;
        hash = (hash ^ (hash >> 15)) * 0xd168aae7L;
        hash ^= (hash >> 15);
        return (hash & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private long mixSeed(long seed, int tileX, int tileY) {
        long hash = seed;
        hash ^= (long) tileX * 0x9e3779b97f4a7c15L;
        hash ^= (long) tileY * 0xc2b2ae3d27d4eb4fL;
        hash ^= (hash << 17);
        hash ^= (hash >>> 31);
        hash ^= (hash << 11);
        return hash;
    }

    private ClusterSite locateSite(int clusterSize, long seed, int x, int y) {
        double step = Math.max(clusterSize * BASIN_STEP_RATIO, 8.0);
        double cx = x;
        double cy = y;
        for (int i = 0; i < BASIN_RELAX_STEPS; i++) {
            double bestX = cx;
            double bestY = cy;
            double bestValue = basinNoise(seed, clusterSize, cx, cy);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) != 1) {
                        continue;
                    }
                    double nx = cx + dx * step;
                    double ny = cy + dy * step;
                    double candidate = basinNoise(seed, clusterSize, nx, ny);
                    if (candidate < bestValue) {
                        bestValue = candidate;
                        bestX = nx;
                        bestY = ny;
                    }
                }
            }
            cx = lerp(cx, bestX, BASIN_PULL);
            cy = lerp(cy, bestY, BASIN_PULL);
            step *= BASIN_STEP_FALLOFF;
        }
        int anchorX = (int) Math.round(cx);
        int anchorY = (int) Math.round(cy);
        long siteSeed = mixSeed(seed, anchorX, anchorY);
        int cellX = Math.floorDiv(anchorX, clusterSize);
        int cellY = Math.floorDiv(anchorY, clusterSize);
        return new ClusterSite(cellX, cellY, anchorX, anchorY, siteSeed);
    }

    private double basinNoise(long seed, int clusterSize, double x, double y) {
        double scale = BASIN_FIELD_SCALE / clusterSize;
        double nx = x * scale;
        double ny = y * scale;
        double ridge = 1.0 - Math.abs(fbm(seed + 211, nx * 1.7, ny * 1.7, 3) * 2.0 - 1.0);
        double base = fbm(seed + 389, nx, ny, 4);
        double flow = fbm(seed - 947, nx * 0.6, ny * 0.6, 2);
        return base * 0.55 + ridge * 0.35 + flow * 0.10;
    }

    private ResolvedSite resolveSite(ClusterSite site, List<RegionDefinition> regions, int clusterSize) {
        List<Point2D.Double> anchors = new ArrayList<>(regions.size());
        for (RegionDefinition region : regions) {
            anchors.add(anchorForRegion(region, site, clusterSize, regions));
        }
        return new ResolvedSite(site, anchors);
    }

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, long seed) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
        }

        public ClusterSite siteForPoint(int x, int y) {
            return new ClusterMapGenerator().locateSite(clusterSize, seed, x, y);
        }

        public int regionForPoint(int x, int y) {
            ClusterMapGenerator generator = new ClusterMapGenerator();
            ClusterSite site = generator.locateSite(clusterSize, seed, x, y);
            return generator.resolveRegion(x, y, clusterSize, site, regions, site.siteSeed());
        }

        public ClusterLocation locateClusterAndRegion(int x, int y) {
            ClusterMapGenerator generator = new ClusterMapGenerator();
            ClusterSite site = generator.locateSite(clusterSize, seed, x, y);
            ResolvedSite resolved = generator.resolveSite(site, regions, clusterSize);
            int regionIndex = generator.resolveRegion(
                x,
                y,
                clusterSize,
                site,
                regions,
                site.siteSeed()
            );
            double localX = x - site.centerX();
            double localY = y - site.centerY();
            return new ClusterLocation(site, resolved, regionIndex, localX, localY);
        }
    }

    public record ClusterSite(int cellX, int cellY, double centerX, double centerY, long siteSeed) {
        public double squaredDistanceTo(double x, double y) {
            double dx = x - centerX;
            double dy = y - centerY;
            return dx * dx + dy * dy;
        }

        public long key() {
            return (((long) cellX) << 32) ^ (cellY & 0xFFFFFFFFL);
        }
    }

    public record ResolvedSite(ClusterSite site, List<Point2D.Double> anchors) {
    }

    public record ClusterLocation(ClusterSite site, ResolvedSite resolvedSite, int regionIndex,
                                  double localX, double localY) {
    }

    private Point2D.Double clampToRadius(double centerX, double centerY, double x, double y, double maxRadius) {
        double dx = x - centerX;
        double dy = y - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= maxRadius) {
            return new Point2D.Double(x, y);
        }
        double scale = maxRadius / distance;
        double clampedX = centerX + dx * scale;
        double clampedY = centerY + dy * scale;
        return new Point2D.Double(clampedX, clampedY);
    }
}
