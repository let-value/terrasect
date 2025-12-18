package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.definition.StrategySettings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Template-based layout strategy for narrative-driven region placement.
 * 
 * Uses pre-defined spatial templates like:
 * - CENTER_SURROUND: One region in center, others distributed around
 * - CARDINAL: Regions placed at N/S/E/W positions
 * - RADIAL: Regions arranged in concentric rings
 * 
 * Templates are selected based on child count and can be influenced by region hints.
 * All lookups are O(1) with pre-computed layouts.
 * 
 * Layout format: float[n * 5] where each region has:
 *   [centerX, centerZ, radius, childIndex, angle]
 * Coordinates normalized to parent radius = 1.0
 */
public final class TemplateStrategy {

    private static final int CACHE_SIZE = 4096;
    private static final Map<Long, float[]> LAYOUT_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
                return size() > CACHE_SIZE;
            }
        }
    );

    private TemplateStrategy() {}

    public static float[] getLayout(long seed, List<Region> children) {
        return getLayout(seed, children, null, null);
    }

    public static float[] getLayout(long seed, List<Region> children, 
                                     StrategySettings.TemplateType templateType,
                                     StrategySettings.CenterSurroundSettings centerSurroundSettings) {
        long cacheKey = computeCacheKey(seed, children, templateType, centerSurroundSettings);
        float[] layout = LAYOUT_CACHE.get(cacheKey);
        if (layout == null) {
            layout = computeLayout(children, seed, templateType, centerSurroundSettings);
            LAYOUT_CACHE.put(cacheKey, layout);
        }
        return layout;
    }

    private static long computeCacheKey(long seed, List<Region> children, 
                                          StrategySettings.TemplateType templateType,
                                          StrategySettings.CenterSurroundSettings centerSurroundSettings) {
        long hash = seed;
        for (Region child : children) {
            hash = hash * 31 + child.name().hashCode();
            hash = hash * 31 + child.areaBudget();
        }
        if (templateType != null) {
            hash = hash * 31 + templateType.ordinal();
        }
        if (centerSurroundSettings != null && centerSurroundSettings.centerRegionName() != null) {
            hash = hash * 31 + centerSurroundSettings.centerRegionName().hashCode();
        }
        return hash;
    }

    /**
     * Find which region contains the point. Uses modified Voronoi with organic edges.
     * Returns index into layout (multiple of 5), or -1 if outside.
     */
    public static int getCell(float[] layout, float dx, float dz, float radius) {
        float nx = dx / radius;
        float nz = dz / radius;

        // Quick bounds check
        float distSq = nx * nx + nz * nz;
        if (distSq > 1.5f * 1.5f) {
            // Find closest anyway for edge cases
            return findClosestCell(layout, nx, nz);
        }

        return findClosestCell(layout, nx, nz);
    }

    private static int findClosestCell(float[] layout, float nx, float nz) {
        int best = 0;
        float bestMetric = Float.MAX_VALUE;

        for (int i = 0; i < layout.length; i += 5) {
            float cx = layout[i];
            float cz = layout[i + 1];
            float r = layout[i + 2];

            float ddx = nx - cx;
            float ddz = nz - cz;
            float distSq = ddx * ddx + ddz * ddz;

            // Power diagram metric: d² - r²
            float metric = distSq - r * r;

            if (metric < bestMetric) {
                bestMetric = metric;
                best = i;
            }
        }

        return best;
    }

    public static Region getRegion(List<Region> children, float[] layout, int index) {
        if (index < 0 || index >= layout.length) return children.get(0);
        int childIndex = (int) layout[index + 3];
        return children.get(Math.min(childIndex, children.size() - 1));
    }

    public static long getSeed(long parentSeed, float[] layout, int index, Region region) {
        if (index < 0) return MathUtils.hash64(parentSeed, region.name().hashCode(), 0, 888);
        int childIndex = (int) layout[index + 3];
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 888);
    }

    public static float getNextCx(float cx, float radius, float[] layout, int index) {
        if (index < 0) return cx;
        return cx + layout[index] * radius;
    }

    public static float getNextCz(float cz, float radius, float[] layout, int index) {
        if (index < 0) return cz;
        return cz + layout[index + 1] * radius;
    }

    public static float getNextRadius(float radius, float[] layout, int index) {
        if (index < 0) return radius * 0.5f;
        return radius * layout[index + 2];
    }

    // ========== Layout Computation ==========

    private static float[] computeLayout(List<Region> children, long seed, 
                                          StrategySettings.TemplateType templateType,
                                          StrategySettings.CenterSurroundSettings centerSurroundSettings) {
        if (children.isEmpty()) return new float[0];

        int count = children.size();
        float[] budgets = new float[count];
        float totalBudget = 0;

        for (int i = 0; i < count; i++) {
            budgets[i] = children.get(i).areaBudget();
            totalBudget += budgets[i];
        }

        // Normalize to area fractions, then compute radius (sqrt of area)
        float[] radii = new float[count];
        for (int i = 0; i < count; i++) {
            radii[i] = (float) Math.sqrt(budgets[i] / totalBudget);
        }

        // Use explicit template if specified, otherwise auto-select
        if (templateType != null) {
            return applyExplicitTemplate(templateType, count, radii, budgets, children, 
                                         centerSurroundSettings, seed);
        }
        
        return selectAndApplyTemplate(count, radii, budgets, totalBudget, seed);
    }

    private static float[] applyExplicitTemplate(StrategySettings.TemplateType templateType, 
                                                   int count, float[] radii, float[] budgets,
                                                   List<Region> children,
                                                   StrategySettings.CenterSurroundSettings centerSurroundSettings,
                                                   long seed) {
        return switch (templateType) {
            case BINARY -> templateBinary(radii, seed);
            case TRIANGLE -> templateTriangle(radii, seed);
            case CENTER_SURROUND -> {
                int centerIndex = findCenterIndex(children, budgets, centerSurroundSettings);
                yield templateCenterSurround(count, radii, centerIndex, seed);
            }
            case RADIAL -> templateRadial(radii, seed);
        };
    }

    /**
     * Find the index of the center region for CENTER_SURROUND template.
     * If centerSurroundSettings specifies a region name, use that.
     * Otherwise, fall back to highest budget region.
     */
    private static int findCenterIndex(List<Region> children, float[] budgets,
                                        StrategySettings.CenterSurroundSettings settings) {
        // Check if user specified a center region by name
        if (settings != null && settings.centerRegionName() != null) {
            String targetName = settings.centerRegionName();
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).name().equals(targetName)) {
                    return i;
                }
            }
            // Name not found, fall through to budget-based selection
        }
        
        // Default: find region with highest budget
        int dominant = 0;
        float maxBudget = 0;
        for (int i = 0; i < budgets.length; i++) {
            if (budgets[i] > maxBudget) {
                maxBudget = budgets[i];
                dominant = i;
            }
        }
        return dominant;
    }

    private static float[] selectAndApplyTemplate(int count, float[] radii, float[] budgets, 
                                                   float totalBudget, long seed) {
        if (count == 1) {
            return new float[] { 0, 0, 1.0f, 0, 0 };
        }

        if (count == 2) {
            return templateBinary(radii, seed);
        }

        // Check if one is dominant (> 40% budget) - use center-surround
        int dominant = -1;
        float maxBudget = 0;
        for (int i = 0; i < count; i++) {
            if (budgets[i] > maxBudget) {
                maxBudget = budgets[i];
                dominant = i;
            }
        }

        // Use center-surround if dominant region has > 40% of total
        if (maxBudget / totalBudget > 0.4f) {
            return templateCenterSurround(count, radii, dominant, seed);
        }

        if (count == 3) {
            return templateTriangle(radii, seed);
        }

        // Default: radial distribution
        return templateRadial(radii, seed);
    }

    /**
     * Two regions split along a random axis.
     */
    private static float[] templateBinary(float[] radii, long seed) {
        float[] layout = new float[10];
        float angle = hashToFloat(seed, 0, 0) * (float) Math.PI;

        // Offset based on radii
        float offset = 0.35f + (radii[0] + radii[1]) * 0.2f;

        layout[0] = (float) Math.cos(angle) * offset;
        layout[1] = (float) Math.sin(angle) * offset;
        layout[2] = radii[0];
        layout[3] = 0;
        layout[4] = angle;

        layout[5] = (float) Math.cos(angle + Math.PI) * offset;
        layout[6] = (float) Math.sin(angle + Math.PI) * offset;
        layout[7] = radii[1];
        layout[8] = 1;
        layout[9] = angle + (float) Math.PI;

        return layout;
    }

    /**
     * Three regions in a triangle formation.
     */
    private static float[] templateTriangle(float[] radii, long seed) {
        float[] layout = new float[15];
        float baseAngle = hashToFloat(seed, 0, 1) * (float) Math.PI * 2;

        for (int i = 0; i < 3; i++) {
            float angle = baseAngle + i * (float) Math.PI * 2 / 3;
            float dist = 0.4f + hashToFloat(seed, i, 2) * 0.1f;

            layout[i * 5] = (float) Math.cos(angle) * dist;
            layout[i * 5 + 1] = (float) Math.sin(angle) * dist;
            layout[i * 5 + 2] = radii[i];
            layout[i * 5 + 3] = i;
            layout[i * 5 + 4] = angle;
        }

        return layout;
    }

    /**
     * Narrative layout: dominant region in center with others around it.
     * This creates the classic "city surrounded by farmland" or "castle surrounded by villages" pattern.
     * 
     * Note: This prioritizes spatial relationships over exact budget matching.
     * Use SubdivisionStrategy if precise area ratios are critical.
     */
    private static float[] templateCenterSurround(int count, float[] radii, int dominantIdx, long seed) {
        float[] layout = new float[count * 5];
        
        // Dominant region at center
        layout[dominantIdx * 5] = 0;
        layout[dominantIdx * 5 + 1] = 0;
        layout[dominantIdx * 5 + 2] = radii[dominantIdx];
        layout[dominantIdx * 5 + 3] = dominantIdx;
        layout[dominantIdx * 5 + 4] = 0;

        // Other regions in a ring around center
        // Distribute evenly with some jitter for organic feel
        float baseAngle = hashToFloat(seed, 0, 3) * (float) Math.PI * 2;
        int surroundCount = count - 1;
        int surroundIdx = 0;

        for (int i = 0; i < count; i++) {
            if (i == dominantIdx) continue;

            // Angle: evenly distributed with jitter
            float angle = baseAngle + surroundIdx * (float) Math.PI * 2 / surroundCount;
            float jitter = (hashToFloat(seed, i, 4) - 0.5f) * 0.3f;
            angle += jitter;

            // Distance: place at edge of a circle, with some variation
            // Use a moderate distance so power diagram naturally creates a ring
            float dist = 0.45f + (hashToFloat(seed, i, 5) - 0.5f) * 0.1f;

            layout[i * 5] = (float) Math.cos(angle) * dist;
            layout[i * 5 + 1] = (float) Math.sin(angle) * dist;
            layout[i * 5 + 2] = radii[i];
            layout[i * 5 + 3] = i;
            layout[i * 5 + 4] = angle;

            surroundIdx++;
        }

        return layout;
    }

    /**
     * Radial distribution for many equal-ish regions.
     */
    private static float[] templateRadial(float[] radii, long seed) {
        int count = radii.length;
        float[] layout = new float[count * 5];

        float baseAngle = hashToFloat(seed, 0, 6) * (float) Math.PI * 2;

        for (int i = 0; i < count; i++) {
            float angle = baseAngle + i * (float) Math.PI * 2 / count;
            float jitter = (hashToFloat(seed, i, 7) - 0.5f) * 0.4f;
            angle += jitter;

            // Distance based on index for slight spiral effect
            float baseDist = 0.4f + (i % 2) * 0.15f;
            float dist = baseDist + (hashToFloat(seed, i, 8) - 0.5f) * 0.1f;

            layout[i * 5] = (float) Math.cos(angle) * dist;
            layout[i * 5 + 1] = (float) Math.sin(angle) * dist;
            layout[i * 5 + 2] = radii[i];
            layout[i * 5 + 3] = i;
            layout[i * 5 + 4] = angle;
        }

        return layout;
    }

    // ========== Utilities ==========

    private static float hashToFloat(long seed, int a, int b) {
        long h = MathUtils.hash64(seed, a, b, 0);
        return (h & 0xFFFF) / 65536.0f;
    }
}
