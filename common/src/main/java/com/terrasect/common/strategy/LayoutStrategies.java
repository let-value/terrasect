package com.terrasect.common.strategy;

import com.terrasect.common.definition.GenerationStrategyType;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.StrategySettings;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class LayoutStrategies {
    private static final ThreadLocal<QueryResult> RESULT = ThreadLocal.withInitial(QueryResult::new);

    private LayoutStrategies() {}

    /**
     * Query a generation strategy for the cell containing the point (dx, dz).
     * Returns the thread-local QueryResult with all fields populated.
     *
     * @param parent Parent region (provides strategy type and settings)
     * @param seed Parent region seed
     * @param dx X offset from parent center (in world units)
     * @param dz Z offset from parent center (in world units)
     * @param radius Parent radius
     * @return QueryResult with childIndex, centerX, centerZ, radius, and seed fields
     */
    public static QueryResult query(Region parent, long seed, float dx, float dz, float radius) {
        QueryResult result = RESULT.get();
        result.isRing = false;

        GenerationStrategyType type = parent.definition().generationStrategy();
        StrategySettings settings = parent.definition().strategySettings();
        List<Region> children = parent.children();

        switch (type) {
            case HEX -> HexStrategy.query(seed, children, dx, dz, radius, settings, result);
            case SUBDIVISION -> querySubdivision(seed, children, dx, dz, radius, settings, result);
            case TEMPLATE -> queryTemplate(seed, children, dx, dz, radius, settings, result);
            default -> queryVoronoi(seed, parent, dx, dz, radius, settings, result); // VORONOI
        }

        // Compute child seed and store in result
        result.childSeed = computeSeed(type, seed, result);

        return result;
    }

    /**
     * Compute seed for the child region based on strategy type and query result.
     */
    private static long computeSeed(GenerationStrategyType type, long parentSeed, QueryResult result) {
        if (type == GenerationStrategyType.HEX) {
            // HEX uses q,r coords stored in result during query
            int q = (int) result.siteX;
            int r = (int) result.siteZ;
            return HexStrategy.getSeed(parentSeed, q, r);
        }

        // For voronoi/subdivision/template, use site position from result
        // to make each cell's children unique
        int siteX = Float.floatToIntBits(result.siteX);
        int siteZ = Float.floatToIntBits(result.siteZ);

        int salt =
                switch (type) {
                    case SUBDIVISION -> 777;
                    case TEMPLATE -> 888;
                    default -> 999; // VORONOI
                };
        return MathUtils.hash64(parentSeed, siteX ^ siteZ, result.childIndex, salt);
    }

    // ========== Strategy-specific query implementations ==========

    private static void queryVoronoi(
            long seed, Region parent, float dx, float dz, float radius, StrategySettings settings, QueryResult out) {
        int relaxationIterations = 5; // Default relaxation
        if (settings != null && settings.voronoi() != null) {
            relaxationIterations = settings.voronoi().relaxationIterations();
        }
        VoronoiStrategy.query(
                seed, parent.children(), dx, dz, radius, parent.childrenTotalBudget(), relaxationIterations, out);
    }

    private static void querySubdivision(
            long seed,
            List<Region> children,
            float dx,
            float dz,
            float radius,
            StrategySettings settings,
            QueryResult out) {
        float jitter = 0.05f; // Default jitter
        if (settings != null && settings.subdivision() != null) {
            jitter = settings.subdivision().jitter();
        }
        SubdivisionStrategy.query(seed, children, dx, dz, radius, jitter, out);
    }

    private static void queryTemplate(
            long seed,
            List<Region> children,
            float dx,
            float dz,
            float radius,
            StrategySettings settings,
            QueryResult out) {
        StrategySettings.TemplateType templateType = null;
        StrategySettings.CenterSurroundSettings centerSurroundSettings = null;
        if (settings != null && settings.template() != null) {
            templateType = settings.template().type();
            centerSurroundSettings = settings.template().centerSurround();
        }
        TemplateStrategy.query(seed, children, dx, dz, radius, templateType, centerSurroundSettings, out);
    }
}
