package com.terrasect.common.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class World {

    private static final ThreadLocal<WarpResult> WARP_SCRATCH = ThreadLocal.withInitial(WarpResult::new);
    private static Region root;

    public static void setRoot(Region newRoot) {
        root = newRoot;
    }

    public static Region getRoot() {
        if (root == null) {
            throw new IllegalStateException("NarrativeWorld root not initialized!");
        }
        return root;
    }

    /**
     * Get the leaf Region at a given location by traversing the hierarchy.
     */
    public static Region getRegion(int x, int z, Strategy context) {
        // Default to max depth
        return getRegionAtDepth(x, z, context, 100);
    }

    /**
     * Get the Region at a specific depth in the hierarchy.
     */
    public static Region getRegionAtDepth(int x, int z, Strategy context, int targetDepth) {
        Region current = getRoot();
        
        // 1. Warping
        WarpResult warped = getWarpedCoordinates(x, z, context);
        float wx = warped.x;
        float wz = warped.z;
        
        long currentSeed = context.getSeed();
        int currentDepth = 0;
        boolean infiniteTilingDone = false;
        
        // Track the center of the current region for relative coordinates
        float centerX = 0;
        float centerZ = 0;
        float currentRadius = (float) current.areaBudget();

        while (current.hasChildren() && currentDepth < targetDepth) {
            List<Region> children = current.children();
            
            if (!infiniteTilingDone) {
                // Depth 1: Warped Hex Grid Tiling
                float hexSize = currentRadius; 
                long hexPacked = RegionField.getHexCell(wx, wz, hexSize);
                int q = (int) (hexPacked >> 32);
                int r = (int) hexPacked;
                
                // Calculate center of this hex in warped space
                float hx = hexSize * ((float)Math.sqrt(3) * q + (float)Math.sqrt(3)/2.0f * r);
                float hz = hexSize * (3.0f/2.0f * r);
                
                centerX = hx;
                centerZ = hz;
                
                // Hex Distance from Center (0,0)
                int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;
                
                if (hexDist == 0) {
                    // Center Region: Always the first child (e.g., CIVILIZATION)
                    // This ensures the player starts in a specific narrative zone.
                    current = children.get(0);
                    // Use a fixed seed for the center to ensure stability
                    long hexSeed = MathUtils.hash64(currentSeed, 0, 0, 9999);
                    currentSeed = hexSeed;
                } else {
                    // Outer Regions: Procedural
                    long hexSeed = MathUtils.hash64(currentSeed, q, r, 9999);
                    current = pickChildWeighted(children, hexSeed, (int)centerX, (int)centerZ, context);
                    currentSeed = hexSeed;
                }
                
                infiniteTilingDone = true;
            } else {
                 // Recursive Narrative Layout for all depths > 0
                 List<Site> sites = computeNarrativeLayout(children, currentSeed, currentRadius);
                 
                 float totalBudget = getTotalWeight(children);
                 // Reduced areaScale to prevent central hub from consuming neighbors
                 float areaScale = currentRadius * currentRadius * 0.06f;
                 
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
                     // Fallback site finding
                     for(Site s : sites) {
                         if(s.region == bestChild) {
                             bestSite = s;
                             break;
                         }
                     }
                 }
                 
                 current = bestChild;
                 currentSeed = MathUtils.hash64(currentSeed, current.name().hashCode(), bestChildIndex, 999);
                 
                 // Update for next depth
                 if (bestSite != null) {
                     centerX += bestSite.x;
                     centerZ += bestSite.z;
                     // Scale radius based on area share
                     currentRadius = currentRadius * (float)Math.sqrt(bestChild.areaBudget() / totalBudget);
                 }
            }
            currentDepth++;
        }
        return current;
    }

    public static long getRegionSeedAtDepth(int x, int z, Strategy context, int targetDepth) {
        Region current = getRoot();
        
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
                 // Recursive Narrative Layout for all depths > 0
                 List<Site> sites = computeNarrativeLayout(children, currentSeed, currentRadius);
                 
                 float totalBudget = getTotalWeight(children);
                 float areaScale = currentRadius * currentRadius * 0.06f;
                 
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

    private static float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }

    private static Region pickChildWeighted(List<Region> children, long seed, int x, int z, Strategy context) {
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

    private static WarpResult getWarpedCoordinates(int x, int z, Strategy context) {
        WarpResult result = WARP_SCRATCH.get();

        float river = context.getRiverInfluence(x, z);
        float ridge = context.getRidgeInfluence(x, z);
        long seed = context.getSeed();

        EdgeStatistics stats = EdgeStatistics.vanillaOverworld();

        float dist = (float) Math.sqrt(x * x + z * z);
        float dampFactor = Math.min(1.0f, dist / 600.0f);

        float m1 = NoiseUtils.valueNoise(x, z, seed, 10001, 4000);
        float m2 = NoiseUtils.valueNoise(x, z, seed, 10002, 4000);

        float mx = x + (m1 - 0.5f) * 1500.0f * dampFactor;
        float mz = z + (m2 - 0.5f) * 1500.0f * dampFactor;

        float n1 = NoiseUtils.warpNoise1((int) mx, (int) mz, seed, 600);
        float n2 = NoiseUtils.warpNoise2((int) mx, (int) mz, seed, 600);

        float r1 = NoiseUtils.valueNoise((int) mx, (int) mz, seed, 5001, 150);
        float r2 = NoiseUtils.valueNoise((int) mx, (int) mz, seed, 5002, 150);

        float baseAmp = 42.0f;
        float microAmp = 15.0f;
        float influenceAmp = 55.0f;

        float warpAngle = NoiseUtils.valueNoise(x, z, seed, 9999, 350) * (float) Math.PI * 2.0f;
        float riverWarpX = (float) Math.cos(warpAngle) * (river + ridge) * influenceAmp;
        float riverWarpZ = (float) Math.sin(warpAngle) * (river + ridge) * influenceAmp;

        float coarseScale = stats.coarseAverageRunBlocks() * 0.5f;
        float coarseAmplitude = stats.coarseTransitionDensity() * Config.EDGE_SCALE * 2.5f;

        float macroEdgeX = (NoiseUtils.valueNoise(x, z, seed, 9201, (int) coarseScale) - 0.5f) * coarseAmplitude;
        float macroEdgeZ = (NoiseUtils.valueNoise(z, x, seed, 9202, (int) coarseScale) - 0.5f) * coarseAmplitude;

        float microEdgeX = (NoiseUtils.valueNoise(x, z, seed, 9203, 8) - 0.5f) * stats.fineHorizontalJitter();
        float microEdgeZ = (NoiseUtils.valueNoise(z, x, seed, 9204, 8) - 0.5f) * stats.fineVerticalJitter();

        result.x = mx + ((n1 - 0.5f) * baseAmp + (r1 - 0.5f) * microAmp) * dampFactor + riverWarpX * dampFactor + macroEdgeX + microEdgeX;
        result.z = mz + ((n2 - 0.5f) * baseAmp + (r2 - 0.5f) * microAmp) * dampFactor + riverWarpZ * dampFactor + macroEdgeZ + microEdgeZ;

        return result;
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

    private static List<Site> computeNarrativeLayout(List<Region> children, long seed, float hexRadius) {
        List<Site> sites = new ArrayList<>();
        if (children.isEmpty()) return sites;

        // 1. Find Hub
        Region hub = children.get(0);
        int maxScore = -1;
        
        for (Region r : children) {
            int score = r.adjacentTo().size();
            // Hub is determined purely by connectivity (most connected region)
            if (score > maxScore) {
                maxScore = score;
                hub = r;
            }
        }

        // 2. BFS for Shells
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
                    // Find the region object
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
        
        // Handle disconnected regions (put them in outer shell)
        int maxShell = 0;
        for (int s : shells.values()) maxShell = Math.max(maxShell, s);
        
        for (Region r : children) {
            if (!shells.containsKey(r)) {
                shells.put(r, maxShell + 1);
            }
        }

        // 3. Assign Positions
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
            
            // Calculate outer radius for this shell
            float nextInnerRadius = (float) Math.sqrt(currentInnerRadius * currentInnerRadius + hexRadius * hexRadius * budgetFraction);
            
            float placementRadius;
            if (shell == 0) {
                placementRadius = 0;
            } else {
                // Place sites closer to the center of their area share to ensure they are within the hex
                // Using 0.70 of the outer radius of the shell seems to work well for hex containment
                placementRadius = nextInnerRadius * 0.70f;
            }
            
            float angleStep = (float) (2 * Math.PI / shellRegions.size());
            float angleOffset = (MathUtils.hash64(seed, shell, 0, 0) & 0xFFFF) / 65536.0f * (float)Math.PI;
            
            for (int i = 0; i < shellRegions.size(); i++) {
                Region r = shellRegions.get(i);
                
                // Add jitter to angle (up to 50% of the step)
                float angleJitter = ((MathUtils.hash64(seed, shell, i, 1) & 0xFFFF) / 65536.0f - 0.5f) * angleStep * 0.5f;
                float angle = angleOffset + i * angleStep + angleJitter;
                
                // Add jitter to radius (up to 20% of the shell width)
                float radiusJitter = ((MathUtils.hash64(seed, shell, i, 2) & 0xFFFF) / 65536.0f - 0.5f) * (nextInnerRadius - currentInnerRadius) * 0.2f;
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
}
