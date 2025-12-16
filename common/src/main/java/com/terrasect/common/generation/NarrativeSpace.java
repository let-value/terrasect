package com.terrasect.common.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates the math for traversing the region hierarchy. Separating this from
 * {@link World} makes it easier to reason about the generation math independently
 * from the global static state.
 */
final class NarrativeSpace {

    private static final float WORLD_ORIGIN_DAMP_RADIUS = 600.0f;
    private static final int MACRO_WARP_NOISE_SCALE = 4000;
    private static final float DOMAIN_WARP_AMPLITUDE = 1500.0f;
    private static final int DOMAIN_WARP_NOISE_SCALE = 600;
    private static final int MICRO_WARP_NOISE_SCALE = 150;
    private static final int EDGE_MICRO_NOISE_SCALE = 8;
    private static final float BASE_WARP_AMPLITUDE = 42.0f;
    private static final float MICRO_WARP_AMPLITUDE = 15.0f;
    private static final float INFLUENCE_WARP_AMPLITUDE = 55.0f;
    private static final int WARP_ANGLE_NOISE_SCALE = 350;
    private static final float NARRATIVE_AREA_SCALE = 0.06f;
    private static final float SHELL_PLACEMENT_RADIUS_SCALE = 0.70f;
    private static final float ANGLE_JITTER_SCALE = 0.5f;
    private static final float RADIUS_JITTER_SCALE = 0.2f;
    private static final float COARSE_SCALE_FACTOR = 0.5f;
    private static final float COARSE_EDGE_AMPLITUDE_SCALE = 2.5f;

    private static final ThreadLocal<WarpResult> WARP_SCRATCH = ThreadLocal.withInitial(WarpResult::new);

    private final EdgeStatistics edgeStats = EdgeStatistics.vanillaOverworld();

    Region getRegionAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        Region current = root;

        WarpResult warped = getWarpedCoordinates(x, z, context);
        float wx = warped.x;
        float wz = warped.z;

        long currentSeed = context.getSeed();
        int currentDepth = 0;
        boolean infiniteTilingDone = false;

        float centerX = 0;
        float centerZ = 0;
        float currentRadius = (float) current.areaBudget();

        while (current.hasChildren() && currentDepth < targetDepth) {
            List<Region> children = current.children();

            if (!infiniteTilingDone) {
                float hexSize = currentRadius;
                long hexPacked = RegionField.getHexCell(wx, wz, hexSize);
                int q = (int) (hexPacked >> 32);
                int r = (int) hexPacked;

                float hx = hexSize * ((float)Math.sqrt(3) * q + (float)Math.sqrt(3)/2.0f * r);
                float hz = hexSize * (3.0f/2.0f * r);

                centerX = hx;
                centerZ = hz;

                int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;

                if (hexDist == 0) {
                    current = children.get(0);
                    long hexSeed = MathUtils.hash64(currentSeed, 0, 0, 9999);
                    currentSeed = hexSeed;
                } else {
                    long hexSeed = MathUtils.hash64(currentSeed, q, r, 9999);
                    current = pickChildWeighted(children, hexSeed, (int)centerX, (int)centerZ, context);
                    currentSeed = hexSeed;
                }

                infiniteTilingDone = true;
            } else {
                 List<Site> sites = computeNarrativeLayout(children, currentSeed, currentRadius);

                 float totalBudget = getTotalWeight(children);
                 float areaScale = currentRadius * currentRadius * NARRATIVE_AREA_SCALE;

                 Region bestChild = null;
                 float minMetric = Float.MAX_VALUE;
                 int bestChildIndex = -1;
                 Site bestSite = null;

                 for (Site site : sites) {
                     float distSq = (wx - (centerX + site.x))*(wx - (centerX + site.x)) + (wz - (centerZ + site.z))*(wz - (centerZ + site.z));
                     float weight = (site.region.areaBudget() / totalBudget) * areaScale;
                     float metric = distSq - weight;

                     if (metric < minMetric) {
                         minMetric = metric;
                         bestChild = site.region;
                         bestChildIndex = site.index;
                         bestSite = site;
                     }
                 }

                 if (bestChild == null) {
                     bestChild = children.get(0);
                     for(Site s : sites) {
                         if(s.region == bestChild) {
                             bestSite = s;
                             break;
                         }
                     }
                 }

                 current = bestChild;
                 currentSeed = MathUtils.hash64(currentSeed, current.name().hashCode(), bestChildIndex, 999);

                 if (bestSite != null) {
                     centerX += bestSite.x;
                     centerZ += bestSite.z;
                     currentRadius = currentRadius * (float)Math.sqrt(bestChild.areaBudget() / totalBudget);
                 }
            }
            currentDepth++;
        }
        return current;
    }

    long getRegionSeedAtDepth(Region root, int x, int z, Strategy context, int targetDepth) {
        Region current = root;

        WarpResult warped = getWarpedCoordinates(x, z, context);
        float wx = warped.x;
        float wz = warped.z;

        long currentSeed = context.getSeed();
        int currentDepth = 0;
        boolean infiniteTilingDone = false;

        float centerX = 0;
        float centerZ = 0;
        float currentRadius = (float) current.areaBudget();

        while (current.hasChildren() && currentDepth < targetDepth) {
            List<Region> children = current.children();

            if (!infiniteTilingDone) {
                float hexSize = currentRadius;
                long hexPacked = RegionField.getHexCell(wx, wz, hexSize);
                int q = (int) (hexPacked >> 32);
                int r = (int) hexPacked;

                float hx = hexSize * ((float)Math.sqrt(3) * q + (float)Math.sqrt(3)/2.0f * r);
                float hz = hexSize * (3.0f/2.0f * r);
                centerX = hx;
                centerZ = hz;

                int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;

                if (hexDist == 0) {
                    current = children.get(0);
                    long hexSeed = MathUtils.hash64(currentSeed, 0, 0, 9999);
                    currentSeed = hexSeed;
                } else {
                    long hexSeed = MathUtils.hash64(currentSeed, q, r, 9999);
                    current = pickChildWeighted(children, hexSeed, (int)centerX, (int)centerZ, context);
                    currentSeed = hexSeed;
                }
                infiniteTilingDone = true;
            } else {
                 List<Site> sites = computeNarrativeLayout(children, currentSeed, currentRadius);

                 float totalBudget = getTotalWeight(children);
                 float areaScale = currentRadius * currentRadius * NARRATIVE_AREA_SCALE;

                 Region bestChild = null;
                 float minMetric = Float.MAX_VALUE;
                 int bestChildIndex = -1;
                 Site bestSite = null;

                 for (Site site : sites) {
                     float distSq = (wx - (centerX + site.x))*(wx - (centerX + site.x)) + (wz - (centerZ + site.z))*(wz - (centerZ + site.z));
                     float weight = (site.region.areaBudget() / totalBudget) * areaScale;
                     float metric = distSq - weight;

                     if (metric < minMetric) {
                         minMetric = metric;
                         bestChild = site.region;
                         bestChildIndex = site.index;
                         bestSite = site;
                     }
                 }

                 if (bestChild == null) {
                     bestChild = children.get(0);
                     for(Site s : sites) {
                         if(s.region == bestChild) {
                             bestSite = s;
                             break;
                         }
                     }
                 }

                 current = bestChild;
                 currentSeed = MathUtils.hash64(currentSeed, current.name().hashCode(), bestChildIndex, 999);

                 if (bestSite != null) {
                     centerX += bestSite.x;
                     centerZ += bestSite.z;
                     currentRadius = currentRadius * (float)Math.sqrt(bestChild.areaBudget() / totalBudget);
                 }
            }
            currentDepth++;
        }
        return currentSeed;
    }

    private float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    private Region pickChildWeighted(List<Region> children, long seed, int x, int z, Strategy context) {
        float randomVal = (MathUtils.hash64(seed, 0, 0, 0) & 0xFFFF) / 65536.0f;

        float totalWeight = 0;
        float[] weights = new float[children.size()];
        float river = context.getRiverInfluence(x, z);
        float ridge = context.getRidgeInfluence(x, z);

        for (int i = 0; i < children.size(); i++) {
            Region r = children.get(i);
            float weight = r.areaBudget();

            weights[i] = weight;
            totalWeight += weight;
        }

        float target = randomVal * totalWeight;
        float currentW = 0;
        for (int i = 0; i < children.size(); i++) {
            currentW += weights[i];
            if (currentW >= target) {
                return children.get(i);
            }
        }
        return children.get(0);
    }

    private WarpResult getWarpedCoordinates(int x, int z, Strategy context) {
        WarpResult result = WARP_SCRATCH.get();

        float river = context.getRiverInfluence(x, z);
        float ridge = context.getRidgeInfluence(x, z);
        long seed = context.getSeed();

        float dist = (float) Math.sqrt(x * x + z * z);
        float dampFactor = Math.min(1.0f, dist / WORLD_ORIGIN_DAMP_RADIUS);

        float m1 = NoiseUtils.valueNoise(x, z, seed, 10001, MACRO_WARP_NOISE_SCALE);
        float m2 = NoiseUtils.valueNoise(x, z, seed, 10002, MACRO_WARP_NOISE_SCALE);

        float mx = x + (m1 - 0.5f) * DOMAIN_WARP_AMPLITUDE * dampFactor;
        float mz = z + (m2 - 0.5f) * DOMAIN_WARP_AMPLITUDE * dampFactor;

        float n1 = NoiseUtils.warpNoise1((int) mx, (int) mz, seed, DOMAIN_WARP_NOISE_SCALE);
        float n2 = NoiseUtils.warpNoise2((int) mx, (int) mz, seed, DOMAIN_WARP_NOISE_SCALE);

        float r1 = NoiseUtils.valueNoise((int) mx, (int) mz, seed, 5001, MICRO_WARP_NOISE_SCALE);
        float r2 = NoiseUtils.valueNoise((int) mx, (int) mz, seed, 5002, MICRO_WARP_NOISE_SCALE);

        float baseAmp = BASE_WARP_AMPLITUDE;
        float microAmp = MICRO_WARP_AMPLITUDE;
        float influenceAmp = INFLUENCE_WARP_AMPLITUDE;

        float warpAngle = NoiseUtils.valueNoise(x, z, seed, 9999, WARP_ANGLE_NOISE_SCALE) * (float) Math.PI * 2.0f;
        float riverWarpX = (float) Math.cos(warpAngle) * (river + ridge) * influenceAmp;
        float riverWarpZ = (float) Math.sin(warpAngle) * (river + ridge) * influenceAmp;

        float coarseScale = edgeStats.coarseAverageRunBlocks() * COARSE_SCALE_FACTOR;
        float coarseAmplitude = edgeStats.coarseTransitionDensity() * Config.EDGE_SCALE * COARSE_EDGE_AMPLITUDE_SCALE;

        float macroEdgeX = (NoiseUtils.valueNoise(x, z, seed, 9201, (int) coarseScale) - 0.5f) * coarseAmplitude;
        float macroEdgeZ = (NoiseUtils.valueNoise(z, x, seed, 9202, (int) coarseScale) - 0.5f) * coarseAmplitude;

        float microEdgeX = (NoiseUtils.valueNoise(x, z, seed, 9203, EDGE_MICRO_NOISE_SCALE) - 0.5f) * edgeStats.fineHorizontalJitter();
        float microEdgeZ = (NoiseUtils.valueNoise(z, x, seed, 9204, EDGE_MICRO_NOISE_SCALE) - 0.5f) * edgeStats.fineVerticalJitter();

        result.x = mx + ((n1 - 0.5f) * baseAmp + (r1 - 0.5f) * microAmp) * dampFactor + riverWarpX * dampFactor + macroEdgeX + microEdgeX;
        result.z = mz + ((n2 - 0.5f) * baseAmp + (r2 - 0.5f) * microAmp) * dampFactor + riverWarpZ * dampFactor + macroEdgeZ + microEdgeZ;

        return result;
    }

    private List<Site> computeNarrativeLayout(List<Region> children, long seed, float hexRadius) {
        List<Site> sites = new ArrayList<>();
        if (children.isEmpty()) return sites;

        Region hub = children.get(0);
        int maxScore = -1;

        for (Region r : children) {
            int score = r.adjacentTo().size();
            if (score > maxScore) {
                maxScore = score;
                hub = r;
            }
        }

        Map<Region, Integer> shells = new HashMap<>();
        shells.put(hub, 0);

        List<Region> queue = new ArrayList<>();
        queue.add(hub);

        Set<String> visited = new HashSet<>();
        visited.add(hub.name());

        while(!queue.isEmpty()) {
            Region current = queue.remove(0);
            int currentShell = shells.get(current);

            for (String neighborName : current.adjacentTo()) {
                if (!visited.contains(neighborName)) {
                    for (Region r : children) {
                        if (r.name().equals(neighborName)) {
                            visited.add(neighborName);
                            shells.put(r, currentShell + 1);
                            queue.add(r);
                            break;
                        }
                    }
                }
            }
        }

        int maxShell = 0;
        for (int s : shells.values()) maxShell = Math.max(maxShell, s);

        for (Region r : children) {
            if (!shells.containsKey(r)) {
                shells.put(r, maxShell + 1);
            }
        }

        Map<Integer, List<Region>> byShell = new HashMap<>();
        for (Map.Entry<Region, Integer> entry : shells.entrySet()) {
            byShell.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        float totalBudget = getTotalWeight(children);
        float currentInnerRadius = 0;

        List<Integer> sortedShells = new ArrayList<>(byShell.keySet());
        Collections.sort(sortedShells);

        for (int shell : sortedShells) {
            List<Region> shellRegions = byShell.get(shell);

            float shellBudget = 0;
            for (Region r : shellRegions) shellBudget += r.areaBudget();

            float budgetFraction = shellBudget / totalBudget;

            float nextInnerRadius = (float) Math.sqrt(currentInnerRadius * currentInnerRadius + hexRadius * hexRadius * budgetFraction);

            float placementRadius;
            if (shell == 0) {
                placementRadius = 0;
            } else {
                placementRadius = nextInnerRadius * SHELL_PLACEMENT_RADIUS_SCALE;
            }

            float angleStep = (float) (2 * Math.PI / shellRegions.size());
            float angleOffset = (MathUtils.hash64(seed, shell, 0, 0) & 0xFFFF) / 65536.0f * (float)Math.PI;

            for (int i = 0; i < shellRegions.size(); i++) {
                Region r = shellRegions.get(i);

                float angleJitter = ((MathUtils.hash64(seed, shell, i, 1) & 0xFFFF) / 65536.0f - 0.5f) * angleStep * ANGLE_JITTER_SCALE;
                float angle = angleOffset + i * angleStep + angleJitter;

                float radiusJitter = ((MathUtils.hash64(seed, shell, i, 2) & 0xFFFF) / 65536.0f - 0.5f) * (nextInnerRadius - currentInnerRadius) * RADIUS_JITTER_SCALE;
                float rRadius = placementRadius + radiusJitter;

                Site site = new Site();
                if (shell == 0) {
                    site.x = 0;
                    site.z = 0;
                } else {
                    site.x = (float)Math.cos(angle) * rRadius;
                    site.z = (float)Math.sin(angle) * rRadius;
                }
                site.region = r;
                site.index = children.indexOf(r);
                sites.add(site);
            }

            currentInnerRadius = nextInnerRadius;
        }

        return sites;
    }

    private static class WarpResult {
        float x;
        float z;
    }

    private static class Site {
        float x, z;
        Region region;
        int index;
    }
}
