package com.terrasect.common.generation;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Builds repeatable, narrative-focused cluster layouts. Each cluster acts like a pocket chapter in
 * the player's journey, with regions flowing along a soft "story spine" that repeats forever across
 * the world grid. Regions favor adjacency hints while still jittering into organic outlines so that
 * debugging snapshots remain informative.
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.1;
    private static final double AREA_SCALAR = 1.28;
    private static final double CLUSTER_AREA_RANDOMNESS = 0.16;
    private static final double CELL_JITTER_RATIO = 0.3;
    private static final double INFLUENCE_RADIUS_RATIO = 1.75;
    private static final double INFLUENCE_SHARPNESS = 0.65;
    private static final double STORY_BEND = 0.42;
    private static final double STORY_DEPTH = 0.55;
    private static final double STORY_WOBBLE = 0.22;
    private static final double STORY_REPEAT_ROTATION = 0.33;
    private static final double ANCHOR_SPREAD = 0.23;
    private static final double ANCHOR_FORWARD = 0.06;
    private static final double REGION_SOFTNESS = 0.35;
    private static final double FLOW_WARP_STRENGTH = 0.11;
    private static final double FLOW_WARP_SCALE = 6.0;
    private static final double CHAPTER_PULL = 1.05;
    private static final double OUTLINE_NOISE = 0.18;

    public ClusterPattern generate(ClusterDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        int clusterSize = definition.clusterSize();
        if (clusterSize == 0) {
            clusterSize = autoSize(definition.regions());
        }
        return new ClusterPattern(clusterSize, definition.regions(), definition.totalTargetArea(), seed);
    }

    private int autoSize(List<RegionDefinition> regions) {
        long total = regions.stream().mapToLong(RegionDefinition::targetArea).sum();
        int size = (int) Math.ceil(Math.sqrt(total * AREA_SCALAR));
        return Math.max(size, 96);
    }

    private ClusterSite locateSite(int clusterSize, long seed, long targetClusterArea, int x, int y) {
        int cellX = Math.floorDiv(x, clusterSize);
        int cellY = Math.floorDiv(y, clusterSize);
        ClusterSite strongest = null;
        double strongestInfluence = Double.NEGATIVE_INFINITY;
        int radius = (int) Math.ceil(INFLUENCE_RADIUS_RATIO);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int neighborX = cellX + dx;
                int neighborY = cellY + dy;
                ClusterSite site = siteForCell(clusterSize, seed, targetClusterArea, neighborX, neighborY);
                double influence = influence(site, x, y, clusterSize);
                if (influence > strongestInfluence) {
                    strongestInfluence = influence;
                    strongest = site;
                }
            }
        }
        return Objects.requireNonNull(strongest);
    }

    private ClusterSite siteForCell(int clusterSize, long seed, long targetClusterArea, int cellX, int cellY) {
        long cellSeed = mixSeed(seed, cellX, cellY);
        Random random = new Random(cellSeed * 6364136223846793005L + 1442695040888963407L);
        double jitter = clusterSize * CELL_JITTER_RATIO;
        double baseX = cellX * (double) clusterSize + clusterSize * 0.5;
        double baseY = cellY * (double) clusterSize + clusterSize * 0.5;
        double x = baseX + (random.nextDouble() - 0.5) * jitter;
        double y = baseY + (random.nextDouble() - 0.5) * jitter;
        double radiusScale = clusterRadiusScale(targetClusterArea, clusterSize, seed, cellX, cellY);
        double weight = 1.0 + (random.nextDouble() - 0.5) * 0.2;
        double orientation = random.nextDouble() * Math.PI * 2.0;
        return new ClusterSite(cellX, cellY, x, y, cellSeed, radiusScale, weight, orientation);
    }

    private double clusterRadiusScale(long targetClusterArea, int clusterSize, long seed, int cellX, int cellY) {
        double normalizedArea = targetClusterArea / (double) (clusterSize * clusterSize);
        double baseScale = Math.sqrt(Math.max(normalizedArea, 0.0001));
        double wobble = (valueNoise(seed ^ 0x9E3779B97F4A7C15L, cellX, cellY) - 0.5) * 2.0 * CLUSTER_AREA_RANDOMNESS;
        double scale = baseScale * (1.0 + wobble);
        return Math.max(0.35, scale);
    }

    private double influence(ClusterSite site, double x, double y, int clusterSize) {
        double dx = x - site.centerX();
        double dy = y - site.centerY();
        double radius = clusterSize * INFLUENCE_RADIUS_RATIO * site.radiusScale();
        double falloff = Math.exp(-(dx * dx + dy * dy) / (radius * radius) * INFLUENCE_SHARPNESS);
        return Math.log(site.weight() * falloff);
    }

    private ResolvedSite resolveSite(ClusterSite site, List<RegionDefinition> regions, int clusterSize) {
        long totalArea = regions.stream().mapToLong(RegionDefinition::targetArea).sum();
        StorySpine spine = buildStorySpine(site, clusterSize, regions.size());
        double usableRadius = clusterSize * 0.5 - clusterSize * EDGE_PADDING_RATIO;

        List<Point2D.Double> anchors = new ArrayList<>(regions.size());
        List<Double> chapterPositions = new ArrayList<>(regions.size());
        Map<String, Integer> nameToIndex = new HashMap<>();

        double cursor = 0.0;
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            double portion = region.targetArea() / (double) totalArea;
            double chapterCenter = cursor + portion * 0.5;
            double jitter = (valueNoise(site.siteSeed() ^ region.name().hashCode(), region.targetArea(), i) - 0.5)
                * STORY_WOBBLE * portion;
            double chapterPosition = clamp01(chapterCenter + jitter);
            Point2D.Double anchor = anchorForRegion(region, chapterPosition, spine, site, usableRadius);
            anchors.add(anchor);
            chapterPositions.add(chapterPosition);
            nameToIndex.put(region.name(), i);
            cursor += portion;
        }

        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            Point2D.Double anchor = anchors.get(i);
            for (String adjacency : region.adjacentTo()) {
                Integer idx = nameToIndex.get(adjacency);
                if (idx != null) {
                    anchor = mix(anchor, anchors.get(idx), 0.35 / Math.max(1, region.adjacentTo().size()));
                }
            }
            anchors.set(i, clampToRadius(site.centerX(), site.centerY(), anchor.getX(), anchor.getY(), usableRadius));
        }

        return new ResolvedSite(site, spine, anchors, chapterPositions, usableRadius);
    }

    private StorySpine buildStorySpine(ClusterSite site, int clusterSize, int chapterCount) {
        int points = Math.max(3, Math.min(7, chapterCount + 1));
        double padding = clusterSize * EDGE_PADDING_RATIO;
        double radius = clusterSize * 0.5 - padding;
        Random random = new Random(site.siteSeed() ^ 0x6a09e667f3bcc908L);
        double baseAngle = random.nextDouble() * Math.PI * 2.0;
        double rotation = baseAngle + STORY_REPEAT_ROTATION * (site.cellX() + site.cellY());
        double travel = radius * (0.9 + (random.nextDouble() - 0.5) * 0.1);

        List<Point2D.Double> controlPoints = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            double along = (t - 0.5) * 2.0 * travel;
            double lateralNoise = fbm(site.siteSeed() + i * 37L, t * 3.0, site.cellX() * 0.5 + site.cellY() * 0.5, 3);
            double lateral = (lateralNoise - 0.5) * STORY_BEND * travel;
            double depthNoise = fbm(site.siteSeed() ^ 0x1234abcdL + i, t * 2.5, site.cellY(), 2);
            double depth = (depthNoise - 0.5) * STORY_DEPTH * travel;
            Point2D.Double offset = offsetAlong(rotation, along, lateral);
            double x = site.centerX() + offset.getX() + Math.cos(rotation + Math.PI / 2.0) * depth;
            double y = site.centerY() + offset.getY() + Math.sin(rotation + Math.PI / 2.0) * depth;
            controlPoints.add(clampToRadius(site.centerX(), site.centerY(), x, y, radius));
        }

        return StorySpine.from(controlPoints);
    }

    private Point2D.Double offsetAlong(double rotation, double along, double lateral) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double x = cos * along - sin * lateral;
        double y = sin * along + cos * lateral;
        return new Point2D.Double(x, y);
    }

    private Point2D.Double anchorForRegion(RegionDefinition region, double chapterPosition, StorySpine spine,
                                           ClusterSite site, double usableRadius) {
        Point2D.Double spinePoint = spine.pointAt(chapterPosition);
        Point2D.Double tangent = spine.tangentAt(chapterPosition);
        double norm = Math.hypot(tangent.getX(), tangent.getY());
        double nx = norm == 0.0 ? 1.0 : -tangent.getY() / norm;
        double ny = norm == 0.0 ? 0.0 : tangent.getX() / norm;

        double lateralNoise = fbm(site.siteSeed() ^ region.name().hashCode() * 31L, chapterPosition * 8.0,
            region.targetArea() * 0.25, 3);
        double forwardNoise = fbm(site.siteSeed() ^ region.name().hashCode() * 17L, chapterPosition * 6.0,
            region.targetArea() * 0.17, 2);

        double lateral = (lateralNoise - 0.5) * 2.0 * usableRadius * ANCHOR_SPREAD;
        double forward = (forwardNoise - 0.5) * 2.0 * usableRadius * ANCHOR_FORWARD;

        double ax = spinePoint.getX() + nx * lateral + tangent.getX() * forward;
        double ay = spinePoint.getY() + ny * lateral + tangent.getY() * forward;
        return clampToRadius(site.centerX(), site.centerY(), ax, ay, usableRadius);
    }

    private int resolveRegion(int x, int y, int clusterSize, ClusterSite site, ResolvedSite resolved,
                              List<RegionDefinition> regions) {
        double warpX = organicWarp(site.siteSeed() + 211L, x, y, clusterSize) + flowWarp(site.siteSeed(), x, y, clusterSize);
        double warpY = organicWarp(site.siteSeed() + 443L, x, y, clusterSize) + flowWarp(site.siteSeed() + 17L, y, x, clusterSize);
        double px = x + warpX;
        double py = y + warpY;
        double storyT = resolved.story().project(px, py);

        double bestScore = Double.MAX_VALUE;
        int bestIndex = 0;

        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            Point2D anchor = resolved.anchors().get(i);
            double dx = px - anchor.getX();
            double dy = py - anchor.getY();
            double areaScale = Math.max(24.0, Math.sqrt(region.targetArea()) * REGION_SOFTNESS);
            double distanceScore = Math.hypot(dx, dy) / areaScale;

            double chapterDelta = Math.abs(storyT - resolved.chapterPositions().get(i));
            double chapterScore = chapterDelta * CHAPTER_PULL;

            double outline = fbm(site.siteSeed() ^ region.name().hashCode() * 13L, px / (clusterSize * FLOW_WARP_SCALE),
                py / (clusterSize * FLOW_WARP_SCALE), 3);
            double outlineScore = (outline - 0.5) * OUTLINE_NOISE;

            double radial = Math.hypot(x - site.centerX(), y - site.centerY()) / resolved.usableRadius();
            double edgePenalty = radial > 1.0 ? (radial - 1.0) * 3.5 : 0.0;

            double score = distanceScore + chapterScore + outlineScore + edgePenalty;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double flowWarp(long seed, double x, double y, int clusterSize) {
        double scale = Math.max(clusterSize / FLOW_WARP_SCALE, 1.0);
        double noise = fbm(seed + 91L, x / scale, y / scale, 4);
        return (noise - 0.5) * clusterSize * FLOW_WARP_STRENGTH;
    }

    private double organicWarp(long seed, double x, double y, int clusterSize) {
        double scale = Math.max(clusterSize / 5.0, 1.0);
        double noise = fbm(seed, x / scale, y / scale, 3);
        return (noise - 0.5) * clusterSize * 0.08;
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

    private static double lerp(double a, double b, double t) {
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Point2D.Double mix(Point2D.Double a, Point2D.Double b, double t) {
        return new Point2D.Double(lerp(a.getX(), b.getX(), t), lerp(a.getY(), b.getY(), t));
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

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, long targetClusterArea, long seed) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
        }

        public ClusterSite siteForPoint(int x, int y) {
            return new ClusterMapGenerator().locateSite(clusterSize, seed, targetClusterArea, x, y);
        }

        public int regionForPoint(int x, int y) {
            ClusterMapGenerator generator = new ClusterMapGenerator();
            ClusterSite site = generator.locateSite(clusterSize, seed, targetClusterArea, x, y);
            ResolvedSite resolved = generator.resolveSite(site, regions, clusterSize);
            return generator.resolveRegion(x, y, clusterSize, site, resolved, regions);
        }

        public ClusterLocation locateClusterAndRegion(int x, int y) {
            ClusterMapGenerator generator = new ClusterMapGenerator();
            ClusterSite site = generator.locateSite(clusterSize, seed, targetClusterArea, x, y);
            ResolvedSite resolved = generator.resolveSite(site, regions, clusterSize);
            int regionIndex = generator.resolveRegion(
                x,
                y,
                clusterSize,
                site,
                resolved,
                regions
            );
            double localX = x - site.centerX();
            double localY = y - site.centerY();
            return new ClusterLocation(site, resolved, regionIndex, localX, localY);
        }
    }

    public record ClusterSite(int cellX, int cellY, double centerX, double centerY, long siteSeed,
                              double radiusScale, double weight, double orientation) {
        public double squaredDistanceTo(double x, double y) {
            double dx = x - centerX;
            double dy = y - centerY;
            return dx * dx + dy * dy;
        }

        public long key() {
            return (((long) cellX) << 32) ^ (cellY & 0xFFFFFFFFL);
        }
    }

    public record ResolvedSite(ClusterSite site, StorySpine story, List<Point2D.Double> anchors,
                               List<Double> chapterPositions, double usableRadius) {
    }

    public record ClusterLocation(ClusterSite site, ResolvedSite resolvedSite, int regionIndex,
                                  double localX, double localY) {
    }

    private record StorySpine(List<Point2D.Double> points, double[] cumulativeLengths, double totalLength) {
        static StorySpine from(List<Point2D.Double> points) {
            double[] cumulative = new double[points.size()];
            double total = 0.0;
            for (int i = 1; i < points.size(); i++) {
                Point2D p0 = points.get(i - 1);
                Point2D p1 = points.get(i);
                total += p0.distance(p1);
                cumulative[i] = total;
            }
            return new StorySpine(points, cumulative, Math.max(total, 0.001));
        }

        Point2D.Double pointAt(double t) {
            double distance = clamp01(t) * totalLength;
            for (int i = 1; i < cumulativeLengths.length; i++) {
                double segmentLength = cumulativeLengths[i] - cumulativeLengths[i - 1];
                if (distance <= cumulativeLengths[i]) {
                    double local = segmentLength == 0.0 ? 0.0 : (distance - cumulativeLengths[i - 1]) / segmentLength;
                    Point2D a = points.get(i - 1);
                    Point2D b = points.get(i);
                    return new Point2D.Double(
                        lerp(a.getX(), b.getX(), local),
                        lerp(a.getY(), b.getY(), local)
                    );
                }
            }
            return new Point2D.Double(points.get(points.size() - 1).getX(), points.get(points.size() - 1).getY());
        }

        Point2D.Double tangentAt(double t) {
            double delta = 1e-3;
            Point2D.Double a = pointAt(Math.max(0.0, t - delta));
            Point2D.Double b = pointAt(Math.min(1.0, t + delta));
            return new Point2D.Double(b.getX() - a.getX(), b.getY() - a.getY());
        }

        double project(double x, double y) {
            double best = Double.MAX_VALUE;
            double bestT = 0.0;
            double accumulated = 0.0;
            for (int i = 1; i < points.size(); i++) {
                Point2D a = points.get(i - 1);
                Point2D b = points.get(i);
                double abx = b.getX() - a.getX();
                double aby = b.getY() - a.getY();
                double len2 = abx * abx + aby * aby;
                if (len2 == 0.0) {
                    continue;
                }
                double apx = x - a.getX();
                double apy = y - a.getY();
                double tOnSegment = clamp01((apx * abx + apy * aby) / len2);
                double projX = a.getX() + abx * tOnSegment;
                double projY = a.getY() + aby * tOnSegment;
                double dist = Math.hypot(x - projX, y - projY);
                if (dist < best) {
                    best = dist;
                    bestT = (accumulated + Math.sqrt(len2) * tOnSegment) / totalLength;
                }
                accumulated += Math.sqrt(len2);
            }
            return clamp01(bestT);
        }
    }
}
