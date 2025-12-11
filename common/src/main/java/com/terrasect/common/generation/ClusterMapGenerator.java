package com.terrasect.common.generation;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Produces tiled cluster maps using deterministic pseudo-random noise. Each cluster is treated as a
 * narrative pocket where regions form chapters that spiral out from the center and repeat endlessly
 * when the tile is tiled across the world. Adjacency hints pull chapters toward each other while the
 * generator still leaves room for semi-random geography.
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.16;
    private static final double JOURNEY_RING_STEP = 0.2;
    private static final double JOURNEY_START_RADIUS = 0.2;
    private static final double ADJACENT_PULL_RATIO = 0.45;
    private static final double JITTER_SCALE = 0.45;
    private static final double FLOW_FALLOFF = 0.8;
    private static final double WARP_AMPLITUDE = 0.32;
    private static final double AREA_SCALAR = 1.6;

    public ClusterPattern generate(ClusterDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        int clusterSize = definition.clusterSize();
        if (clusterSize == 0) {
            clusterSize = autoSize(definition.regions());
        }
        List<RegionDefinition> regions = definition.regions();
        List<Point2D.Double> anchors = placeAnchors(regions, clusterSize, seed);
        int[][] map = paintCluster(regions, anchors, clusterSize, seed);
        boolean[][] outlines = computeOutlines(map);
        Map<String, RegionStatistics> statistics = computeStatistics(map, regions, anchors);
        return new ClusterPattern(clusterSize, regions, map, outlines, anchors, statistics);
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
        double center = clusterSize / 2.0;
        double ringStep = clusterSize * JOURNEY_RING_STEP;
        double startRadius = clusterSize * JOURNEY_START_RADIUS;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        double baseAngle = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            double radius = startRadius + ringStep * (i * 0.65 + random.nextDouble() * 0.45);
            double angle = baseAngle + goldenAngle * i;
            for (String adjacency : region.adjacentTo()) {
                if (lookup.containsKey(adjacency)) {
                    Point2D.Double base = lookup.get(adjacency);
                    double pullRadius = Point2D.distance(center, center, base.x, base.y);
                    double targetAngle = Math.atan2(base.y - center, base.x - center);
                    radius = blend(radius, pullRadius, ADJACENT_PULL_RATIO);
                    angle = blendAngle(angle, targetAngle, ADJACENT_PULL_RATIO + random.nextDouble() * 0.15);
                    break;
                }
            }
            double jitterRadius = 0.18 * ringStep;
            radius += (random.nextDouble() - 0.5) * jitterRadius;
            angle += (random.nextDouble() - 0.5) * 0.4;
            double x = clamp(center + Math.cos(angle) * radius, padding, clusterSize - padding);
            double y = clamp(center + Math.sin(angle) * radius, padding, clusterSize - padding);
            Point2D.Double anchor = new Point2D.Double(x, y);
            lookup.put(region.name(), anchor);
            anchors.add(anchor);
        }
        return anchors;
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

    private double warp(long seed, int x, int y) {
        double nx = (x + seed * 0.73) * 0.035;
        double ny = (y - seed * 0.41) * 0.035;
        double sin = Math.sin(nx) + Math.sin(ny * 0.8 + nx * 0.6);
        double cos = Math.cos(ny) + Math.cos(nx * 0.6 - ny * 0.4);
        return (sin + cos) * WARP_AMPLITUDE;
    }

    private double flowPenalty(Point2D anchor, int clusterSize, int x, int y) {
        double center = clusterSize / 2.0;
        double anchorRadius = Point2D.distance(center, center, anchor.getX(), anchor.getY());
        double sampleRadius = Point2D.distance(center, center, x, y);
        double delta = Math.abs(sampleRadius - anchorRadius);
        return (delta / clusterSize) * FLOW_FALLOFF;
    }

    private int[][] paintCluster(List<RegionDefinition> regions, List<Point2D.Double> anchors, int clusterSize, long seed) {
        int[][] map = new int[clusterSize][clusterSize];
        for (int y = 0; y < clusterSize; y++) {
            for (int x = 0; x < clusterSize; x++) {
                map[y][x] = resolveRegion(x, y, anchors, regions, clusterSize, seed);
            }
        }
        return map;
    }

    private int resolveRegion(int x, int y, List<Point2D.Double> anchors, List<RegionDefinition> regions, int clusterSize,
                              long seed) {
        double best = Double.MAX_VALUE;
        int bestIndex = 0;
        double jitterX = jitter(seed, x, y);
        double jitterY = jitter(seed + 41, x, y);
        double warp = warp(seed, x, y);
        for (int i = 0; i < anchors.size(); i++) {
            Point2D anchor = anchors.get(i);
            double dx = x + jitterX - anchor.getX();
            double dy = y + jitterY - anchor.getY();
            double weight = 1.0 / Math.sqrt(regions.get(i).targetArea());
            double distance = (dx * dx + dy * dy) * weight;
            double flow = flowPenalty(anchor, clusterSize, x, y);
            double score = distance + flow + warp;
            if (score < best) {
                best = score;
                bestIndex = i;
            }
        }
        return bestIndex;
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

    private double blend(double a, double b, double factor) {
        return a * (1 - factor) + b * factor;
    }

    private double blendAngle(double a, double b, double factor) {
        double diff = Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return a + diff * factor;
    }

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, int[][] regionMap,
                                 boolean[][] outlineMask, List<Point2D.Double> anchors,
                                 Map<String, RegionStatistics> statistics) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
            Objects.requireNonNull(regionMap, "regionMap");
            Objects.requireNonNull(outlineMask, "outlineMask");
            Objects.requireNonNull(anchors, "anchors");
            Objects.requireNonNull(statistics, "statistics");
        }

        public Point clusterForPoint(int x, int y) {
            int cx = Math.floorMod(x, clusterSize);
            int cy = Math.floorMod(y, clusterSize);
            return new Point(cx, cy);
        }
    }

    public record RegionStatistics(String name, int cells, double coverageRatio, Point anchor) {
        public RegionStatistics {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    private Map<String, RegionStatistics> computeStatistics(int[][] map, List<RegionDefinition> regions,
                                                            List<Point2D.Double> anchors) {
        int size = map.length;
        int[] counts = new int[regions.size()];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int region = map[y][x];
                counts[region]++;
            }
        }
        int total = size * size;
        Map<String, RegionStatistics> statistics = new LinkedHashMap<>();
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            Point2D.Double anchor = anchors.get(i);
            Point anchorPoint = new Point((int) Math.round(anchor.x), (int) Math.round(anchor.y));
            statistics.put(region.name(), new RegionStatistics(region.name(), counts[i], counts[i] / (double) total,
                anchorPoint));
        }
        return statistics;
    }
}
