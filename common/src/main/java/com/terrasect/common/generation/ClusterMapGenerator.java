package com.terrasect.common.generation;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Narrative-first cluster generator that treats each cluster as a looping journey pocket. Regions are
 * arranged along a softly warped ring so that players can migrate from cluster to cluster while
 * following repeatable story beats. The generator remains entirely math-driven so callers can resolve
 * cluster/region membership without chunk-level context, mirroring how Minecraft streams world data.
 */
public final class ClusterMapGenerator {
    private static final double EDGE_PADDING_RATIO = 0.1;
    private static final double JITTER_SCALE = 0.32;
    private static final double AREA_SCALAR = 1.55;
    private static final double CLUSTER_AREA_RANDOMNESS = 0.18;
    private static final double CELL_JITTER_RATIO = 0.22;
    private static final double INFLUENCE_RADIUS_RATIO = 1.75;
    private static final double INFLUENCE_SHARPNESS = 0.44;
    private static final double JOURNEY_RING_RATIO = 0.46;
    private static final double JOURNEY_RING_VARIANCE = 0.22;
    private static final double JOURNEY_ARC_BEND = 0.35;
    private static final double JOURNEY_BAND_STRENGTH = 0.38;
    private static final double REGION_ARC_NOISE = 0.18;
    private static final double REGION_RADIUS_WOBBLE = 0.2;
    private static final double WARP_STRENGTH = 0.14;
    private static final double WARP_SCALE = 6.0;
    private static final double ADJACENCY_PULL = 0.6;

    public ClusterPattern generate(ClusterDefinition definition, long seed) {
        Objects.requireNonNull(definition, "definition");
        int clusterSize = definition.clusterSize();
        if (clusterSize == 0) {
            clusterSize = autoSize(definition.regions());
        }
        Map<String, Integer> regionIndex = computeRegionIndex(definition.regions());
        double meanArea = meanTargetArea(definition.regions());
        return new ClusterPattern(clusterSize, definition.regions(), definition.totalTargetArea(), seed, regionIndex, meanArea);
    }

    private int autoSize(List<RegionDefinition> regions) {
        long total = regions.stream().mapToLong(RegionDefinition::targetArea).sum();
        int size = (int) Math.ceil(Math.sqrt(total * AREA_SCALAR));
        return Math.max(size, 96);
    }

    private double clusterRadiusScale(long targetClusterArea, int clusterSize, long seed, int cellX, int cellY) {
        double normalizedArea = targetClusterArea / (double) (clusterSize * clusterSize);
        double baseScale = Math.sqrt(Math.max(normalizedArea, 0.0001));
        double wobble = (valueNoise(seed ^ 0x9E3779B97F4A7C15L, cellX, cellY) - 0.5) * 2.0 * CLUSTER_AREA_RANDOMNESS;
        double scale = baseScale * (1.0 + wobble);
        return Math.max(0.35, scale);
    }

    private Point2D.Double anchorForRegion(RegionDefinition region, int regionIndex, ClusterSite site, int clusterSize,
                                           List<RegionDefinition> regions, Map<String, Integer> regionIndexByName,
                                           double meanArea) {
        double padding = clusterSize * EDGE_PADDING_RATIO;
        double maxRadius = clusterSize * 0.5 - padding;
        double angleStride = (Math.PI * 2.0) / regions.size();
        double baseAngle = site.journeyAngle() + angleStride * (regionIndex + site.chapterDrift());
        baseAngle += Math.sin(site.chapterPhase()) * JOURNEY_ARC_BEND;

        double pullX = 0.0;
        double pullY = 0.0;
        for (String adjacency : region.adjacentTo()) {
            Integer targetIndex = regionIndexByName.get(adjacency);
            if (targetIndex != null) {
                double targetAngle = site.journeyAngle() + angleStride * (targetIndex + site.chapterDrift());
                pullX += Math.cos(targetAngle);
                pullY += Math.sin(targetAngle);
            }
        }
        if (pullX != 0.0 || pullY != 0.0) {
            double adjacencyAngle = Math.atan2(pullY, pullX);
            baseAngle = lerp(baseAngle, adjacencyAngle, ADJACENCY_PULL);
        }

        long anchorSeed = site.siteSeed() ^ (long) region.name().hashCode() * 31L;
        double arcNoise = (valueNoise(anchorSeed, regionIndex, region.targetArea()) - 0.5) * REGION_ARC_NOISE * Math.PI;
        baseAngle += arcNoise;

        double areaRatio = Math.sqrt(region.targetArea() / Math.max(meanArea, 1.0));
        double ring = clusterSize * (JOURNEY_RING_RATIO + site.storyWeave() * JOURNEY_RING_VARIANCE);
        double radius = Math.min(maxRadius, padding + ring * areaRatio);
        double radialWobble = (valueNoise(anchorSeed ^ 0xA24BAEDL, site.cellX(), site.cellY()) - 0.5)
            * REGION_RADIUS_WOBBLE * clusterSize;
        radius = clampRadius(radius + radialWobble, padding, maxRadius);

        double x = site.centerX() + Math.cos(baseAngle) * radius;
        double y = site.centerY() + Math.sin(baseAngle) * radius;
        return clampToRadius(site.centerX(), site.centerY(), x, y, maxRadius);
    }

    private int resolveRegion(int x, int y, int clusterSize, ClusterSite site, ResolvedSite resolved, long seed) {
        double best = Double.MAX_VALUE;
        int bestIndex = 0;
        double jitterX = jitter(seed, x, y);
        double jitterY = jitter(seed + 41, x, y);
        double warpX = organicWarp(seed + 73, x, y, clusterSize);
        double warpY = organicWarp(seed + 109, x, y, clusterSize);

        for (int i = 0; i < resolved.anchors().size(); i++) {
            Point2D anchor = resolved.anchors().get(i);
            double dx = x + jitterX + warpX - anchor.getX();
            double dy = y + jitterY + warpY - anchor.getY();
            double angleDelta = angularDifference(Math.atan2(dy, dx), site.journeyAngle());
            double corridorBias = 1.0 - Math.min(1.0, angleDelta / Math.PI) * JOURNEY_BAND_STRENGTH;
            double weight = corridorBias / Math.sqrt(resolved.regions().get(i).targetArea());
            double distance = (dx * dx + dy * dy) * weight;
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double jitter(long seed, int x, int y) {
        double scale = 0.18;
        double nx = x * scale;
        double ny = y * scale;
        double noise = fbm(seed, nx, ny, 3);
        return (noise - 0.5) * 2.0 * JITTER_SCALE;
    }

    private double organicWarp(long seed, int x, int y, int clusterSize) {
        double scale = Math.max(clusterSize / WARP_SCALE, 1.0);
        double noise = fbm(seed, x / scale, y / scale, 5);
        return (noise - 0.5) * clusterSize * WARP_STRENGTH;
    }

    private double fbm(long seed, double x, double y, int octaves) {
        double value = 0.0;
        double amplitude = 0.5;
        double frequency = 1.0;
        for (int i = 0; i < octaves; i++) {
            value += amplitude * smoothValueNoise(seed + i * 57L, x * frequency, y * frequency);
            amplitude *= 0.55;
            frequency *= 2.0;
        }
        return value;
    }

    private double valueNoise(long seed, int x, int y) {
        long hash = seed;
        hash ^= (long) x * 0x27d4eb2dL;
        hash ^= (long) y * 0x165667b1L;
        hash = (hash ^ (hash >> 15)) * 0xd168aae7L;
        hash ^= (hash >> 15);
        return (hash & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL;
    }

    private double smoothValueNoise(long seed, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        double tx = fade(x - x0);
        double ty = fade(y - y0);

        double n00 = valueNoise(seed, x0, y0);
        double n10 = valueNoise(seed, x1, y0);
        double n01 = valueNoise(seed, x0, y1);
        double n11 = valueNoise(seed, x1, y1);

        double nx0 = lerp(n00, n10, tx);
        double nx1 = lerp(n01, n11, tx);
        return lerp(nx0, nx1, ty);
    }

    private double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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
        double weight = 1.0 + (random.nextDouble() - 0.5) * 0.25;
        double journeyAngle = journeyOrientation(cellSeed, cellX, cellY);
        double chapterDrift = random.nextDouble();
        double chapterPhase = random.nextDouble() * Math.PI * 2.0;
        double storyWeave = random.nextDouble() * 2.0 - 1.0;
        return new ClusterSite(cellX, cellY, x, y, cellSeed, radiusScale, weight, journeyAngle, chapterDrift, chapterPhase, storyWeave);
    }

    private double journeyOrientation(long seed, int cellX, int cellY) {
        double gradient = Math.atan2(cellY, cellX == 0 ? 1.0 : cellX);
        double noise = valueNoise(seed ^ 0x632be5abL, cellX, cellY) * Math.PI * 2.0;
        return gradient * 0.35 + noise * 0.65;
    }

    private double influence(ClusterSite site, double x, double y, int clusterSize) {
        double dx = x - site.centerX();
        double dy = y - site.centerY();
        double radius = clusterSize * INFLUENCE_RADIUS_RATIO * site.radiusScale();
        double distance = Math.sqrt(dx * dx + dy * dy);
        double theta = Math.atan2(dy, dx) - site.journeyAngle();
        double band = 1.0 + Math.cos(theta * 2.0 + site.chapterPhase()) * JOURNEY_BAND_STRENGTH;
        double warpedRadius = radius * band;
        double softness = Math.exp(-Math.pow(distance / Math.max(warpedRadius, 0.001), INFLUENCE_SHARPNESS));
        double bandBlend = 0.65 + 0.35 * fade(clamp01(1.0 - distance / radius));
        double baseInfluence = site.weight() * softness * bandBlend;
        return Math.log(baseInfluence);
    }

    private ResolvedSite resolveSite(ClusterSite site, List<RegionDefinition> regions, int clusterSize,
                                     Map<String, Integer> regionIndexByName, double meanArea) {
        List<Point2D.Double> anchors = new ArrayList<>(regions.size());
        for (int i = 0; i < regions.size(); i++) {
            RegionDefinition region = regions.get(i);
            anchors.add(anchorForRegion(region, i, site, clusterSize, regions, regionIndexByName, meanArea));
        }
        return new ResolvedSite(site, anchors, regions);
    }

    private Map<String, Integer> computeRegionIndex(List<RegionDefinition> regions) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < regions.size(); i++) {
            index.put(regions.get(i).name(), i);
        }
        return index;
    }

    private double meanTargetArea(List<RegionDefinition> regions) {
        return regions.stream().mapToLong(RegionDefinition::targetArea).average().orElse(1.0);
    }

    private double angularDifference(double a, double b) {
        double diff = a - b;
        while (diff > Math.PI) diff -= Math.PI * 2.0;
        while (diff < -Math.PI) diff += Math.PI * 2.0;
        return Math.abs(diff);
    }

    private double clampRadius(double radius, double min, double max) {
        return Math.max(min, Math.min(max, radius));
    }

    public record ClusterPattern(int clusterSize, List<RegionDefinition> regions, long targetClusterArea, long seed,
                                 Map<String, Integer> regionIndexByName, double meanArea) {
        public ClusterPattern {
            Objects.requireNonNull(regions, "regions");
            Objects.requireNonNull(regionIndexByName, "regionIndexByName");
        }

        public ClusterSite siteForPoint(int x, int y) {
            return new ClusterMapGenerator().locateSite(clusterSize, seed, targetClusterArea, x, y);
        }

        public int regionForPoint(int x, int y) {
            ClusterMapGenerator generator = new ClusterMapGenerator();
            ClusterSite site = generator.locateSite(clusterSize, seed, targetClusterArea, x, y);
            ResolvedSite resolved = generator.resolveSite(site, regions, clusterSize, regionIndexByName, meanArea);
            return generator.resolveRegion(x, y, clusterSize, site, resolved, site.siteSeed());
        }

        public ClusterLocation locateClusterAndRegion(int x, int y) {
            ClusterMapGenerator generator = new ClusterMapGenerator();
            ClusterSite site = generator.locateSite(clusterSize, seed, targetClusterArea, x, y);
            ResolvedSite resolved = generator.resolveSite(site, regions, clusterSize, regionIndexByName, meanArea);
            int regionIndex = generator.resolveRegion(
                x,
                y,
                clusterSize,
                site,
                resolved,
                site.siteSeed()
            );
            double localX = x - site.centerX();
            double localY = y - site.centerY();
            return new ClusterLocation(site, resolved, regionIndex, localX, localY);
        }
    }

    public record ClusterSite(int cellX, int cellY, double centerX, double centerY, long siteSeed,
                              double radiusScale, double weight, double journeyAngle, double chapterDrift,
                              double chapterPhase, double storyWeave) {
        public double squaredDistanceTo(double x, double y) {
            double dx = x - centerX;
            double dy = y - centerY;
            return dx * dx + dy * dy;
        }

        public long key() {
            return (((long) cellX) << 32) ^ (cellY & 0xFFFFFFFFL);
        }
    }

    public record ResolvedSite(ClusterSite site, List<Point2D.Double> anchors, List<RegionDefinition> regions) {
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
