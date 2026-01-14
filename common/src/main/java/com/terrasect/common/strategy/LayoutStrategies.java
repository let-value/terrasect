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

        dispatchQuery(strategy, parent, children, seed, dx, dz, radius, result);

        result.childSeed = computeSeed(strategy, seed, result);

        return result;
    }

    private static void dispatchQuery(
            GenerationStrategy strategy,
            Region parent,
            List<Region> children,
            long seed,
            float dx,
            float dz,
            float radius,
            QueryResult out) {
        if (strategy instanceof GenerationStrategy.Hex hex) {
            HexStrategy.query(seed, children, dx, dz, radius, hex, out);
            return;
        }

        if (strategy instanceof GenerationStrategy.Subdivision subdivision) {
            SubdivisionStrategy.query(seed, children, dx, dz, radius, subdivision, out);
            return;
        }

        if (strategy instanceof GenerationStrategy.Template template) {
            TemplateStrategy.query(seed, children, dx, dz, radius, template, out);
            return;
        }

        if (strategy instanceof GenerationStrategy.Voronoi voronoi) {
            queryVoronoi(seed, parent, dx, dz, radius, voronoi, out);
            return;
        }

        throw new IllegalStateException("Unknown generation strategy: " + strategy);
    }

    private static long computeSeed(GenerationStrategy strategy, long parentSeed, QueryResult result) {
        if (strategy instanceof GenerationStrategy.Hex) {
            var q = (int) result.siteX;
            var r = (int) result.siteZ;
            return HexStrategy.getSeed(parentSeed, q, r);
        }

        final int salt;
        if (strategy instanceof GenerationStrategy.Subdivision) {
            salt = 777;
        } else if (strategy instanceof GenerationStrategy.Template) {
            salt = 888;
        } else {
            salt = 999;
        }

        return hashChildSeed(parentSeed, result, salt);
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
