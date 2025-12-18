package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.StrategySettings;

import java.util.List;

/**
 * Unified facade for all generation strategies (Hex, Voronoi, Subdivision, Template).
 * 
 * Uses thread-local scratch buffer to return multiple values without allocation.
 * The query() method returns the region index, and results are stored in a scratch
 * buffer accessible via getLastCenterX(), getLastCenterZ(), getLastRadius().
 * 
 * This keeps NarrativeSpace clean while maintaining zero-allocation traversal
 * with full float precision.
 */
public final class LayoutStrategies {

    // Thread-local scratch space: [regionIndex, centerX, centerZ, radiusScale, hexQ, hexR, isRing]
    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[7]);

    private LayoutStrategies() {}

    /**
     * Query a generation strategy for the cell containing the point (dx, dz).
     * Results are stored in thread-local scratch - retrieve with getLastXxx() methods.
     * 
     * @param parent Parent region (provides strategy type and settings)
     * @param seed Parent region seed
     * @param dx X offset from parent center (in world units)
     * @param dz Z offset from parent center (in world units)
     * @param radius Parent radius
     * @return Region index into children list, or -1 for ring region (HEX only)
     */
    public static int query(Region parent, long seed, float dx, float dz, float radius) {
        float[] scratch = SCRATCH.get();
        scratch[6] = 0; // Reset isRing flag
        
        GenerationStrategyType type = parent.definition().generationStrategy();
        StrategySettings settings = parent.definition().strategySettings();
        List<Region> children = parent.children();
        
        switch (type) {
            case HEX -> HexStrategy.query(seed, children, dx, dz, radius, settings, scratch);
            case SUBDIVISION -> querySubdivision(seed, children, dx, dz, radius, scratch);
            case TEMPLATE -> queryTemplate(seed, children, dx, dz, radius, settings, scratch);
            default -> queryVoronoi(seed, children, dx, dz, radius, scratch); // VORONOI
        }
        
        return (int) scratch[0];
    }

    /** Get the normalized center X from the last query (relative to parent, in [-1,1] range) */
    public static float getLastCenterX() {
        return SCRATCH.get()[1];
    }

    /** Get the normalized center Z from the last query (relative to parent, in [-1,1] range) */
    public static float getLastCenterZ() {
        return SCRATCH.get()[2];
    }

    /** Get the radius scale from the last query (multiply by parent radius) */
    public static float getLastRadius() {
        return SCRATCH.get()[3];
    }

    /**
     * Compute seed for the child region.
     */
    public static long getSeed(GenerationStrategyType type, long parentSeed, int regionIndex, Region region) {
        if (type == GenerationStrategyType.HEX) {
            // HEX uses q,r coords stored in scratch during query
            float[] scratch = SCRATCH.get();
            int q = (int) scratch[4];
            int r = (int) scratch[5];
            return HexStrategy.getSeed(parentSeed, q, r);
        }
        int salt = switch (type) {
            case SUBDIVISION -> 777;
            case TEMPLATE -> 888;
            default -> 999; // VORONOI
        };
        return MathUtils.hash64(parentSeed, region.name().hashCode(), regionIndex, salt);
    }

    /**
     * Check if the last HEX query returned a ring region (between hex cells).
     */
    public static boolean isRingRegion() {
        return SCRATCH.get()[6] != 0;
    }

    // ========== Strategy-specific query implementations ==========

    private static void queryVoronoi(long seed, List<Region> children, float dx, float dz, 
                                      float radius, float[] out) {
        float[] layout = VoronoiStrategy.getLayout(seed, children);
        int index = VoronoiStrategy.getCell(layout, dx, dz, radius);
        
        if (index < 0 || index >= layout.length) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0.5f;
            return;
        }
        
        int childIndex = (int) layout[index + 3];
        Region region = children.get(childIndex);
        
        out[0] = childIndex;
        out[1] = layout[index];      // normalized centerX
        out[2] = layout[index + 1];  // normalized centerZ
        
        // Voronoi uses budget-based radius
        float totalBudget = VoronoiStrategy.getTotalWeight(children);
        out[3] = (float) Math.sqrt(region.areaBudget() / totalBudget);
    }

    private static void querySubdivision(long seed, List<Region> children, float dx, float dz, 
                                          float radius, float[] out) {
        float[] layout = SubdivisionStrategy.getLayout(seed, children);
        int index = SubdivisionStrategy.getCell(layout, dx, dz, radius);
        
        if (index < 0 || index >= layout.length) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0.5f;
            return;
        }
        
        int childIndex = (int) layout[index + 4];
        float minX = layout[index];
        float minZ = layout[index + 1];
        float maxX = layout[index + 2];
        float maxZ = layout[index + 3];
        
        out[0] = childIndex;
        out[1] = (minX + maxX) / 2.0f;  // normalized centerX
        out[2] = (minZ + maxZ) / 2.0f;  // normalized centerZ
        
        // Use smaller dimension as radius
        float width = (maxX - minX) / 2.0f;
        float height = (maxZ - minZ) / 2.0f;
        out[3] = Math.min(width, height);
    }

    private static void queryTemplate(long seed, List<Region> children, float dx, float dz, 
                                       float radius, StrategySettings settings, float[] out) {
        // Extract template settings
        StrategySettings.TemplateType templateType = null;
        StrategySettings.CenterSurroundSettings centerSurroundSettings = null;
        if (settings != null && settings.template() != null) {
            templateType = settings.template().type();
            centerSurroundSettings = settings.template().centerSurround();
        }
        
        float[] layout = TemplateStrategy.getLayout(seed, children, templateType, centerSurroundSettings);
        int index = TemplateStrategy.getCell(layout, dx, dz, radius);
        
        if (index < 0 || index >= layout.length) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0.5f;
            return;
        }
        
        int childIndex = (int) layout[index + 3];
        
        out[0] = childIndex;
        out[1] = layout[index];      // normalized centerX
        out[2] = layout[index + 1];  // normalized centerZ
        out[3] = layout[index + 2];  // normalized radius
    }
}
