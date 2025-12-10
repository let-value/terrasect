package com.terrasect.common.generation;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Produces organic cluster maps using deterministic pseudo-random noise. Cluster ownership is
 * derived from smooth radial influence fields rather than Voronoi distance checks to avoid hard
 * edges, while regions remain soft-edged blobs seeded from the owning cluster.
 *
 * <p>The cluster a coordinate belongs to is derived directly from math (soft influence from jittered
 * sites) so a caller can identify the owning cluster before doing any heavier per-cluster work,
 * which mirrors how chunk-based world generation streams in Minecraft.</p>
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.12;
    private static final double JITTER_SCALE = 0.5;
    private static final double AREA_SCALAR = 1.35;
    private static final double WARP_STRENGTH = 0.09;
    private static final double WARP_SCALE = 7.0;
    private static final double CELL_JITTER_RATIO = 0.38;
    private static final double INFLUENCE_RADIUS_RATIO = 1.8;
    private static final double INFLUENCE_SHARPNESS = 0.55;
    private static final double INFLUENCE_WEIGHT_NOISE = 0.35;
    private static final double INFLUENCE_ANISO_NOISE = 0.2;

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
        int cellX = Math.floorDiv(x, clusterSize);
        int cellY = Math.floorDiv(y, clusterSize);
        ClusterSite strongest = null;
        double strongestInfluence = Double.NEGATIVE_INFINITY;
        int radius = (int) Math.ceil(INFLUENCE_RADIUS_RATIO);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int neighborX = cellX + dx;
                int neighborY = cellY + dy;
                ClusterSite site = siteForCell(clusterSize, seed, neighborX, neighborY);
                double influence = influence(site, x, y, clusterSize);
                if (influence > strongestInfluence) {
                    strongestInfluence = influence;
                    strongest = site;
                }
            }
        }
        return Objects.requireNonNull(strongest);
    }

    private ClusterSite siteForCell(int clusterSize, long seed, int cellX, int cellY) {
        long cellSeed = mixSeed(seed, cellX, cellY);
        Random random = new Random(cellSeed * 6364136223846793005L + 1442695040888963407L);
        double jitter = clusterSize * CELL_JITTER_RATIO;
        double baseX = cellX * (double) clusterSize + clusterSize * 0.5;
        double baseY = cellY * (double) clusterSize + clusterSize * 0.5;
        double x = baseX + (random.nextDouble() - 0.5) * jitter;
        double y = baseY + (random.nextDouble() - 0.5) * jitter;
        double weight = 1.0 + (random.nextDouble() - 0.5) * INFLUENCE_WEIGHT_NOISE;
        double orientation = random.nextDouble() * Math.PI * 2.0;
        double anisotropy = 1.0 + (random.nextDouble() - 0.5) * INFLUENCE_ANISO_NOISE;
        return new ClusterSite(cellX, cellY, x, y, cellSeed, weight, orientation, anisotropy);
    }

    private double influence(ClusterSite site, double x, double y, int clusterSize) {
        double dx = x - site.centerX();
        double dy = y - site.centerY();
        double radius = clusterSize * INFLUENCE_RADIUS_RATIO;
        double cos = Math.cos(site.orientation());
        double sin = Math.sin(site.orientation());
        double rx = dx * cos + dy * sin;
        double ry = -dx * sin + dy * cos;
        double anisotropicRadius = radius * site.anisotropy();
        double distanceFactor = (rx * rx) / (anisotropicRadius * anisotropicRadius)
            + (ry * ry) / (radius * radius);
        double falloff = Math.exp(-distanceFactor * INFLUENCE_SHARPNESS);
        return Math.log(site.weight() * falloff);
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

    public record ClusterSite(int cellX, int cellY, double centerX, double centerY, long siteSeed,
                              double weight, double orientation, double anisotropy) {
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
