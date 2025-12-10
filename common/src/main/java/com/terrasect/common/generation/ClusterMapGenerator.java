package com.terrasect.common.generation;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Produces tiled cluster maps using deterministic pseudo-random noise. Each cluster is treated as a
 * square tile that can be repeated across a larger surface while keeping region adjacency and blob
 * like shapes based on the world seed.
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.12;
    private static final double ADJACENT_DISTANCE_RATIO = 0.22;
    private static final double JITTER_SCALE = 0.5;
    private static final double AREA_SCALAR = 1.35;
    private static final double WARP_STRENGTH = 0.09;
    private static final double WARP_SCALE = 7.0;

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

    private List<Point2D.Double> placeAnchors(List<RegionDefinition> regions, int clusterSize, long seed) {
        Map<String, Point2D.Double> lookup = new HashMap<>();
        List<Point2D.Double> anchors = new ArrayList<>(regions.size());
        Random random = new Random(seed * 31 + 17);
        double padding = clusterSize * EDGE_PADDING_RATIO;
        for (RegionDefinition region : regions) {
            Point2D.Double anchor = null;
            for (String adjacency : region.adjacentTo()) {
                if (lookup.containsKey(adjacency)) {
                    Point2D.Double base = lookup.get(adjacency);
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = clusterSize * ADJACENT_DISTANCE_RATIO;
                    double x = clamp(base.x + Math.cos(angle) * radius, padding, clusterSize - padding);
                    double y = clamp(base.y + Math.sin(angle) * radius, padding, clusterSize - padding);
                    anchor = new Point2D.Double(x, y);
                    break;
                }
            }
            if (anchor == null) {
                double x = padding + random.nextDouble() * (clusterSize - padding * 2);
                double y = padding + random.nextDouble() * (clusterSize - padding * 2);
                anchor = new Point2D.Double(x, y);
            }
            lookup.put(region.name(), anchor);
            anchors.add(anchor);
        }
        return anchors;
    }

    private int resolveRegion(int x, int y, int clusterSize, List<Point2D.Double> anchors,
                              List<RegionDefinition> regions, long seed) {
        double best = Double.MAX_VALUE;
        int bestIndex = 0;
        double jitterX = jitter(seed, x, y);
        double jitterY = jitter(seed + 41, x, y);
        double warpX = organicWarp(seed + 73, x, y, clusterSize);
        double warpY = organicWarp(seed + 109, x, y, clusterSize);
        for (int i = 0; i < anchors.size(); i++) {
            Point2D anchor = anchors.get(i);
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

    private boolean[][] computeOutlines(int[][] map) {
        int size = map.length;
        boolean[][] outline = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int region = map[y][x];
                if (x == 0 || y == 0 || x == size - 1 || y == size - 1) {
                    outline[y][x] = true;
                    continue;
                }
                if (map[y - 1][x] != region || map[y + 1][x] != region || map[y][x - 1] != region
                    || map[y][x + 1] != region) {
                    outline[y][x] = true;
                }
            }
        }
        return outline;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public TilePattern generateTilePattern(List<RegionDefinition> regions, int clusterSize, long seed,
                                           int tileX, int tileY) {
        long tileSeed = mixSeed(seed, tileX, tileY);
        List<Point2D.Double> anchors = placeAnchors(regions, clusterSize, tileSeed);
        int[][] map = new int[clusterSize][clusterSize];
        for (int y = 0; y < clusterSize; y++) {
            for (int x = 0; x < clusterSize; x++) {
                map[y][x] = resolveRegion(x, y, clusterSize, anchors, regions, tileSeed);
            }
        }
        boolean[][] outlines = computeOutlines(map);
        return new TilePattern(map, outlines);
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

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, long seed) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
        }

        public TilePattern tile(int tileX, int tileY) {
            return new ClusterMapGenerator().generateTilePattern(regions, clusterSize, seed, tileX, tileY);
        }

        public Point clusterForPoint(int x, int y) {
            int cx = Math.floorMod(x, clusterSize);
            int cy = Math.floorMod(y, clusterSize);
            return new Point(cx, cy);
        }
    }

    public record TilePattern(int[][] regionMap, boolean[][] outlineMask) {
        public TilePattern {
            Objects.requireNonNull(regionMap, "regionMap");
            Objects.requireNonNull(outlineMask, "outlineMask");
        }
    }
}
