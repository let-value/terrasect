package com.terrasect.common.runtime.strategy;

import com.terrasect.common.util.MathUtils;
import com.terrasect.common.api.Region;
import com.terrasect.common.generation.definition.StrategySettings;

import java.util.List;

/**
 * Cache-free template-based layout strategy.
 * 
 * Uses pre-defined spatial templates like:
 * - BINARY: Two regions split along random axis
 * - TRIANGLE: Three regions in triangular formation  
 * - CENTER_SURROUND: One region in center, others distributed around
 * - RADIAL: Regions arranged in a ring
 * 
 * All positions are computed inline using deterministic formulas.
 * No caching needed - site positions derive directly from seed + index.
 */
public final class TemplateStrategy {

    private TemplateStrategy() {}

    /**
     * Query which child region contains the point, writing results to output buffer.
     * 
     * @param seed Parent seed for deterministic placement
     * @param children Child regions with budget weights
     * @param dx X offset from parent center
     * @param dz Z offset from parent center
     * @param radius Parent radius
     * @param templateType Explicit template type, or null for auto-selection
     * @param centerSettings Settings for CENTER_SURROUND template
     * @param out Output buffer with childIndex, centerX, centerZ, radius, siteX, siteZ
     */
    public static void query(long seed, List<Region> children, float dx, float dz, float radius,
                             StrategySettings.TemplateType templateType,
                             StrategySettings.CenterSurroundSettings centerSettings,
                             QueryResult out) {
        if (children.isEmpty()) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 0.5f;
            return;
        }

        int count = children.size();
        if (count == 1) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 1.0f;
            return;
        }

        // Normalize query point
        float nx = dx / radius;
        float nz = dz / radius;

        // Pre-compute budget radii
        float totalBudget = 0;
        for (int i = 0; i < count; i++) {
            totalBudget += children.get(i).areaBudget();
        }

        // Select template
        TemplateType type = selectTemplate(templateType, children, totalBudget, count);
        
        // Find center index for CENTER_SURROUND
        int centerIndex = (type == TemplateType.CENTER_SURROUND) 
            ? findCenterIndex(children, centerSettings, totalBudget) 
            : 0;

        // Find best cell by computing positions inline
        int bestIndex = 0;
        float bestMetric = Float.MAX_VALUE;
        float bestX = 0, bestZ = 0, bestR = 0;

        for (int i = 0; i < count; i++) {
            float budgetRatio = children.get(i).areaBudget() / totalBudget;
            float rNorm = (float) Math.sqrt(budgetRatio);

            // Compute position based on template
            float sx, sz;
            switch (type) {
                case BINARY -> {
                    float angle = hashToFloat(seed, 0, 0) * (float) Math.PI;
                    float offset = 0.35f;
                    if (i == 0) {
                        sx = (float) Math.cos(angle) * offset;
                        sz = (float) Math.sin(angle) * offset;
                    } else {
                        sx = (float) Math.cos(angle + Math.PI) * offset;
                        sz = (float) Math.sin(angle + Math.PI) * offset;
                    }
                }
                case TRIANGLE -> {
                    float baseAngle = hashToFloat(seed, 0, 1) * (float) Math.PI * 2;
                    float angle = baseAngle + i * (float) Math.PI * 2 / 3;
                    float dist = 0.4f + hashToFloat(seed, i, 2) * 0.1f;
                    sx = (float) Math.cos(angle) * dist;
                    sz = (float) Math.sin(angle) * dist;
                }
                case CENTER_SURROUND -> {
                    if (i == centerIndex) {
                        sx = 0;
                        sz = 0;
                    } else {
                        float baseAngle = hashToFloat(seed, 0, 3) * (float) Math.PI * 2;
                        int surroundCount = count - 1;
                        int surroundIdx = (i < centerIndex) ? i : i - 1;
                        float angle = baseAngle + surroundIdx * (float) Math.PI * 2 / surroundCount;
                        angle += (hashToFloat(seed, i, 4) - 0.5f) * 0.3f;
                        float dist = 0.45f + (hashToFloat(seed, i, 5) - 0.5f) * 0.1f;
                        sx = (float) Math.cos(angle) * dist;
                        sz = (float) Math.sin(angle) * dist;
                    }
                }
                case RADIAL -> {
                    float baseAngle = hashToFloat(seed, 0, 6) * (float) Math.PI * 2;
                    float angle = baseAngle + i * (float) Math.PI * 2 / count;
                    angle += (hashToFloat(seed, i, 7) - 0.5f) * 0.4f;
                    float dist = 0.4f + (i % 2) * 0.15f + (hashToFloat(seed, i, 8) - 0.5f) * 0.1f;
                    sx = (float) Math.cos(angle) * dist;
                    sz = (float) Math.sin(angle) * dist;
                }
                default -> {
                    sx = 0;
                    sz = 0;
                }
            }

            // Power diagram metric: d² - r²
            float ddx = nx - sx;
            float ddz = nz - sz;
            float distSq = ddx * ddx + ddz * ddz;
            float metric = distSq - rNorm * rNorm;

            if (metric < bestMetric) {
                bestMetric = metric;
                bestIndex = i;
                bestX = sx;
                bestZ = sz;
                bestR = rNorm;
            }
        }

        out.childIndex = bestIndex;
        // Template cells have explicit centers and radii. Transform to cell-local coordinates.
        out.centerX = bestX;    // Center at template cell
        out.centerZ = bestZ;
        out.radius = Math.max(bestR, 0.1f);    // Cell's normalized radius
        // Store cell center for seed uniqueness
        out.siteX = bestX;
        out.siteZ = bestZ;
    }

    private enum TemplateType { BINARY, TRIANGLE, CENTER_SURROUND, RADIAL }

    private static TemplateType selectTemplate(StrategySettings.TemplateType explicit, 
                                                List<Region> children, float totalBudget, int count) {
        if (explicit != null) {
            return switch (explicit) {
                case BINARY -> TemplateType.BINARY;
                case TRIANGLE -> TemplateType.TRIANGLE;
                case CENTER_SURROUND -> TemplateType.CENTER_SURROUND;
                case RADIAL -> TemplateType.RADIAL;
            };
        }

        // Auto-select based on count and budget distribution
        if (count == 2) return TemplateType.BINARY;
        
        // Check for dominant region (> 40% budget)
        float maxBudget = 0;
        for (Region child : children) {
            maxBudget = Math.max(maxBudget, child.areaBudget());
        }
        if (maxBudget / totalBudget > 0.4f) return TemplateType.CENTER_SURROUND;
        
        if (count == 3) return TemplateType.TRIANGLE;
        
        return TemplateType.RADIAL;
    }

    private static int findCenterIndex(List<Region> children, 
                                        StrategySettings.CenterSurroundSettings settings,
                                        float totalBudget) {
        // Check if user specified a center region by name
        if (settings != null && settings.centerRegionName() != null) {
            String targetName = settings.centerRegionName();
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).name().equals(targetName)) {
                    return i;
                }
            }
        }
        
        // Default: find region with highest budget
        int dominant = 0;
        float maxBudget = 0;
        for (int i = 0; i < children.size(); i++) {
            float budget = children.get(i).areaBudget();
            if (budget > maxBudget) {
                maxBudget = budget;
                dominant = i;
            }
        }
        return dominant;
    }

    /**
     * Compute child seed deterministically.
     */
    public static long getSeed(long parentSeed, int childIndex, Region region) {
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 888);
    }

    private static float hashToFloat(long seed, int a, int b) {
        long h = MathUtils.hash64(seed, a, b, 0);
        return (h & 0xFFFF) / 65536.0f;
    }
}
