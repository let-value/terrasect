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
 * narrative pocket that repeats infinitely, encouraging players to migrate from one clustered
 * chapter to the next while still respecting adjacency and target areas.
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.10;
    private static final double JOURNEY_RADIUS_RATIO = 0.38;
    private static final double JOURNEY_INNER_RATIO = 0.18;
    private static final double ADJACENT_PULL_RATIO = 0.25;
    private static final double JITTER_SCALE = 0.45;
    private static final double FRACTAL_NOISE_SCALE = 0.018;
    private static final double NOISE_INTENSITY = 11.0;
    private static final double AREA_SCALAR = 1.40;

    public ClusterPattern generate(ClusterDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        int clusterSize = definition.clusterSize();
        if (clusterSize == 0) {
            clusterSize = autoSize(definition.regions());
        }
        List<RegionDefinition> regions = definition.regions();
        List<RegionAnchor> anchors = planNarrativeAnchors(regions, clusterSize, seed);
        int[][] map = new int[clusterSize][clusterSize];
        int[] regionArea = new int[regions.size()];
        for (int y = 0; y < clusterSize; y++) {
            for (int x = 0; x < clusterSize; x++) {
                int region = resolveRegion(x, y, anchors, regions, seed, clusterSize);
                map[y][x] = region;
                regionArea[region]++;
            }
        }
        boolean[][] outlines = computeOutlines(map);
        Map<String, Integer> statistics = collectRegionStatistics(regions, regionArea);
        return new ClusterPattern(clusterSize, regions, map, outlines, statistics);
    }

    private int autoSize(List<RegionDefinition> regions) {
        long total = regions.stream().mapToLong(RegionDefinition::targetArea).sum();
        int size = (int) Math.ceil(Math.sqrt(total * AREA_SCALAR));
        return Math.max(size, 96);
    }

    private List<RegionAnchor> planNarrativeAnchors(List<RegionDefinition> regions, int clusterSize, long seed) {
        Map<String, Integer> lookup = new HashMap<>();
        List<RegionAnchor> anchors = new ArrayList<>(regions.size());
        Random random = new Random(seed * 131 + 7);
        double padding = clusterSize * EDGE_PADDING_RATIO;
        double center = clusterSize / 2.0;
        double maxRadius = clusterSize * JOURNEY_RADIUS_RATIO;
        double minRadius = clusterSize * JOURNEY_INNER_RATIO;
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            double t = (double) i / regions.size();
            double angle = t * Math.PI * 2 + random.nextDouble() * 0.35;
            double narrativeRadius = minRadius + (maxRadius - minRadius) * (0.6 + 0.4 * Math.sin(t * Math.PI * 2));
            double jitter = (random.nextDouble() - 0.5) * clusterSize * 0.12;
            double x = clamp(center + Math.cos(angle) * narrativeRadius + jitter, padding, clusterSize - padding);
            double y = clamp(center + Math.sin(angle) * narrativeRadius + jitter, padding, clusterSize - padding);
            RegionAnchor anchor = new RegionAnchor(new Point2D.Double(x, y), angle);
            anchors.add(anchor);
            lookup.put(region.name(), i);
        }

        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            RegionAnchor anchor = anchors.get(i);
            for (String adjacency : region.adjacentTo()) {
                Integer idx = lookup.get(adjacency);
                if (idx == null) {
                    continue;
                }
                RegionAnchor neighbor = anchors.get(idx);
                double nx = anchor.point.x + (neighbor.point.x - anchor.point.x) * ADJACENT_PULL_RATIO;
                double ny = anchor.point.y + (neighbor.point.y - anchor.point.y) * ADJACENT_PULL_RATIO;
                anchor = new RegionAnchor(new Point2D.Double(clamp(nx, padding, clusterSize - padding),
                    clamp(ny, padding, clusterSize - padding)), anchor.narrativeAngle);
            }
            anchors.set(i, anchor);
        }
        return anchors;
    }

    private int resolveRegion(int x, int y, List<RegionAnchor> anchors, List<RegionDefinition> regions, long seed,
        int clusterSize) {
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestIndex = 0;
        double jitterX = jitter(seed, x, y);
        double jitterY = jitter(seed + 41, x, y);
        double flowAngle = flowAngle(seed, x, y, clusterSize);
        for (int i = 0; i < anchors.size(); i++) {
            RegionAnchor anchor = anchors.get(i);
            double dx = toroidalDelta(x + jitterX, anchor.point.x, clusterSize);
            double dy = toroidalDelta(y + jitterY, anchor.point.y, clusterSize);
            double distance = Math.sqrt(dx * dx + dy * dy);
            double areaWeight = Math.sqrt(regions.get(i).targetArea());
            double narrativeAlignment = Math.cos(flowAngle - anchor.narrativeAngle) * 0.25 + 0.75;
            double warpedDistance = distance / areaWeight;
            double noise = ridgeNoise(seed + i * 13L, x, y);
            double score = -warpedDistance + noise * NOISE_INTENSITY + narrativeAlignment;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double flowAngle(long seed, int x, int y, int clusterSize) {
        double cx = clusterSize / 2.0;
        double cy = clusterSize / 2.0;
        double dx = toroidalDelta(x, cx, clusterSize);
        double dy = toroidalDelta(y, cy, clusterSize);
        double base = Math.atan2(dy, dx);
        return base + ridgeNoise(seed * 3 + 11, x, y) * 0.8;
    }

    private double ridgeNoise(long seed, int x, int y) {
        double accum = 0;
        double amplitude = 1.0;
        double frequency = FRACTAL_NOISE_SCALE;
        for (int i = 0; i < 4; i++) {
            double value = valueNoise(seed + i * 19, x * frequency, y * frequency);
            accum += (1.0 - Math.abs(value * 2 - 1)) * amplitude;
            amplitude *= 0.5;
            frequency *= 2.1;
        }
        return accum;
    }

    private double valueNoise(long seed, double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        double topLeft = hash(seed, xi, yi);
        double topRight = hash(seed, xi + 1, yi);
        double bottomLeft = hash(seed, xi, yi + 1);
        double bottomRight = hash(seed, xi + 1, yi + 1);
        double top = lerp(topLeft, topRight, fade(xf));
        double bottom = lerp(bottomLeft, bottomRight, fade(xf));
        return lerp(top, bottom, fade(yf));
    }

    private double hash(long seed, int x, int y) {
        long h = seed;
        h ^= (long) x * 341873128712L;
        h ^= (long) y * 132897987541L;
        h ^= (h << 13);
        h ^= (h >>> 7);
        h ^= (h << 17);
        return (h & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL;
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double jitter(long seed, int x, int y) {
        return (hash(seed * 17 + 5, x, y) - 0.5) * JITTER_SCALE;
    }

    private double toroidalDelta(double coordinate, double anchor, int size) {
        double delta = coordinate - anchor;
        if (Math.abs(delta) > size / 2.0) {
            delta = delta - Math.copySign(size, delta);
        }
        return delta;
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

    private Map<String, Integer> collectRegionStatistics(List<RegionDefinition> regions, int[] regionArea) {
        Map<String, Integer> stats = new HashMap<>();
        for (int i = 0; i < regions.size(); i++) {
            stats.put(regions.get(i).name(), regionArea[i]);
        }
        return Map.copyOf(stats);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RegionAnchor(Point2D.Double point, double narrativeAngle) {
    }

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, int[][] regionMap,
                                 boolean[][] outlineMask, Map<String, Integer> regionStatistics) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
            Objects.requireNonNull(regionMap, "regionMap");
            Objects.requireNonNull(outlineMask, "outlineMask");
            Objects.requireNonNull(regionStatistics, "regionStatistics");
        }

        public Point clusterForPoint(int x, int y) {
            int cx = Math.floorMod(x, clusterSize);
            int cy = Math.floorMod(y, clusterSize);
            return new Point(cx, cy);
        }
    }
}
