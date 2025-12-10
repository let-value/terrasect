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
    private static final double JITTER_SCALE = 0.35;
    private static final double AREA_SCALAR = 1.35;

    public ClusterPattern generate(ClusterDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        int clusterSize = definition.clusterSize();
        if (clusterSize == 0) {
            clusterSize = autoSize(definition.regions());
        }
        List<RegionDefinition> regions = definition.regions();
        List<Point2D.Double> anchors = placeAnchors(regions, clusterSize, seed);
        int[][] map = new int[clusterSize][clusterSize];
        for (int y = 0; y < clusterSize; y++) {
            for (int x = 0; x < clusterSize; x++) {
                map[y][x] = resolveRegion(x, y, anchors, regions, seed);
            }
        }
        boolean[][] outlines = computeOutlines(map);
        return new ClusterPattern(clusterSize, regions, map, outlines);
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

    private int resolveRegion(int x, int y, List<Point2D.Double> anchors, List<RegionDefinition> regions, long seed) {
        double best = Double.MAX_VALUE;
        int bestIndex = 0;
        double jitterX = jitter(seed, x, y);
        double jitterY = jitter(seed + 41, x, y);
        for (int i = 0; i < anchors.size(); i++) {
            Point2D anchor = anchors.get(i);
            double dx = x + jitterX - anchor.getX();
            double dy = y + jitterY - anchor.getY();
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

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, int[][] regionMap,
                                 boolean[][] outlineMask) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
            Objects.requireNonNull(regionMap, "regionMap");
            Objects.requireNonNull(outlineMask, "outlineMask");
        }

        public Point clusterForPoint(int x, int y) {
            int cx = Math.floorMod(x, clusterSize);
            int cy = Math.floorMod(y, clusterSize);
            return new Point(cx, cy);
        }
    }
}
