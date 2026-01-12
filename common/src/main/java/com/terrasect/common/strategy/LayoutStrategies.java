package com.terrasect.common.strategy;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class LayoutStrategies {
    private static final ThreadLocal<QueryResult> RESULT = ThreadLocal.withInitial(QueryResult::new);

    private LayoutStrategies() {
    }

    public static QueryResult query(Region parent, long seed, float dx, float dz, float radius) {
        var result = RESULT.get();

        var strategy = parent.definition().generationStrategy();
        List<Region> children = parent.children();

        switch (strategy) {
            case GenerationStrategy.Hex hex -> HexStrategy.query(seed, children, dx, dz, radius, hex, result);
            case GenerationStrategy.Subdivision subdivision ->
                SubdivisionStrategy.query(seed, children, dx, dz, radius, subdivision, result);
            case GenerationStrategy.Template template ->
                TemplateStrategy.query(seed, children, dx, dz, radius, template, result);
            case GenerationStrategy.Voronoi voronoi ->
                queryVoronoi(seed, parent, dx, dz, radius, voronoi, result);
        }

        result.childSeed = computeSeed(strategy, seed, result);

        return result;
    }

    private static long computeSeed(GenerationStrategy strategy, long parentSeed, QueryResult result) {
        return switch (strategy) {
            case GenerationStrategy.Hex _ -> {
                var q = (int) result.siteX;
                var r = (int) result.siteZ;
                yield HexStrategy.getSeed(parentSeed, q, r);
            }
            case GenerationStrategy.Subdivision _ -> hashChildSeed(parentSeed, result, 777);
            case GenerationStrategy.Template _ -> hashChildSeed(parentSeed, result, 888);
            case GenerationStrategy.Voronoi _ -> hashChildSeed(parentSeed, result, 999);
        };
    }

    private static long hashChildSeed(long parentSeed, QueryResult result, int salt) {
        var siteX = Float.floatToIntBits(result.siteX);
        var siteZ = Float.floatToIntBits(result.siteZ);
        return MathUtils.hash64(parentSeed, siteX ^ siteZ, result.childIndex, salt);
    }

    private static void queryVoronoi(
            long seed,
            Region parent,
            float dx,
            float dz,
            float radius,
            GenerationStrategy.Voronoi voronoi,
            QueryResult out) {
        var relaxationIterations = voronoi.relaxationIterations();
        VoronoiStrategy.query(
                seed, parent.children(), dx, dz, radius, parent.childrenTotalBudget(), relaxationIterations, out);
    }
}
