package com.terrasect.common.strategy;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class TemplateStrategy {

    private TemplateStrategy() {
    }

    public static void query(
            long seed,
            List<Region> children,
            float dx,
            float dz,
            float radius,
            GenerationStrategy.Template template,
            QueryResult out) {
        if (children.isEmpty()) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 0.5f;
            return;
        }

        var count = children.size();
        if (count == 1) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 1.0f;
            return;
        }

        var nx = dx / radius;
        var nz = dz / radius;

        var totalBudget = 0F;
        for (var i = 0; i < count; i++) {
            totalBudget += children.get(i).areaBudget();
        }

        GenerationStrategy.TemplateType templateType = template != null ? template.type() : null;
        String centerRegionName = template != null ? template.centerRegionName() : null;

        TemplateType type = selectTemplate(templateType, children, totalBudget, count);

        int centerIndex =
                (type == TemplateType.CENTER_SURROUND) ? findCenterIndex(children, centerRegionName, totalBudget) : 0;

        var bestIndex = 0;
        var bestMetric = Float.MAX_VALUE;
        var secondBestMetric = Float.MAX_VALUE;
        float bestX = 0, bestZ = 0, bestR = 0;

        for (var i = 0; i < count; i++) {
            var budgetRatio = children.get(i).areaBudget() / totalBudget;
            var rNorm = (float) Math.sqrt(budgetRatio);

            float sx, sz;
            switch (type) {
                case BINARY -> {
                    var angle = hashToFloat(seed, 0, 0) * (float) Math.PI;
                    var offset = 0.35f;
                    if (i == 0) {
                        sx = (float) Math.cos(angle) * offset;
                        sz = (float) Math.sin(angle) * offset;
                    } else {
                        sx = (float) Math.cos(angle + Math.PI) * offset;
                        sz = (float) Math.sin(angle + Math.PI) * offset;
                    }
                }
                case TRIANGLE -> {
                    var baseAngle = hashToFloat(seed, 0, 1) * (float) Math.PI * 2;
                    var angle = baseAngle + i * (float) Math.PI * 2 / 3;
                    var dist = 0.4f + hashToFloat(seed, i, 2) * 0.1f;
                    sx = (float) Math.cos(angle) * dist;
                    sz = (float) Math.sin(angle) * dist;
                }
                case CENTER_SURROUND -> {
                    if (i == centerIndex) {
                        sx = 0;
                        sz = 0;
                    } else {
                        var baseAngle = hashToFloat(seed, 0, 3) * (float) Math.PI * 2;
                        var surroundCount = count - 1;
                        int surroundIdx = (i < centerIndex) ? i : i - 1;
                        var angle = baseAngle + surroundIdx * (float) Math.PI * 2 / surroundCount;
                        angle += (hashToFloat(seed, i, 4) - 0.5f) * 0.3f;
                        var dist = 0.45f + (hashToFloat(seed, i, 5) - 0.5f) * 0.1f;
                        sx = (float) Math.cos(angle) * dist;
                        sz = (float) Math.sin(angle) * dist;
                    }
                }
                case RADIAL -> {
                    var baseAngle = hashToFloat(seed, 0, 6) * (float) Math.PI * 2;
                    var angle = baseAngle + i * (float) Math.PI * 2 / count;
                    angle += (hashToFloat(seed, i, 7) - 0.5f) * 0.4f;
                    var dist = 0.4f + (i % 2) * 0.15f + (hashToFloat(seed, i, 8) - 0.5f) * 0.1f;
                    sx = (float) Math.cos(angle) * dist;
                    sz = (float) Math.sin(angle) * dist;
                }
                default -> {
                    sx = 0;
                    sz = 0;
                }
            }

            var ddx = nx - sx;
            var ddz = nz - sz;
            var distSq = ddx * ddx + ddz * ddz;
            var metric = distSq - rNorm * rNorm;

            if (metric < bestMetric) {
                secondBestMetric = bestMetric;
                bestMetric = metric;
                bestIndex = i;
                bestX = sx;
                bestZ = sz;
                bestR = rNorm;
            } else if (metric < secondBestMetric) {
                secondBestMetric = metric;
            }
        }

        out.childIndex = bestIndex;

        out.centerX = bestX;
        out.centerZ = bestZ;
        out.radius = Math.max(bestR, 0.1f);

        out.siteX = bestX;
        out.siteZ = bestZ;

        var rawEdge = secondBestMetric - bestMetric;
        out.edgeDistance = Math.min(1.0f, rawEdge * 2.0f);
    }

    private enum TemplateType {
        BINARY,
        TRIANGLE,
        CENTER_SURROUND,
        RADIAL
    }

    private static TemplateType selectTemplate(
            GenerationStrategy.TemplateType explicit, List<Region> children, float totalBudget, int count) {
        if (explicit != null) {
            return switch (explicit) {
                case BINARY -> TemplateType.BINARY;
                case TRIANGLE -> TemplateType.TRIANGLE;
                case CENTER_SURROUND -> TemplateType.CENTER_SURROUND;
                case RADIAL -> TemplateType.RADIAL;
            };
        }

        if (count == 2) return TemplateType.BINARY;

        var maxBudget = 0F;
        for (Region child : children) {
            maxBudget = Math.max(maxBudget, child.areaBudget());
        }
        if (maxBudget / totalBudget > 0.4f) return TemplateType.CENTER_SURROUND;

        if (count == 3) return TemplateType.TRIANGLE;

        return TemplateType.RADIAL;
    }

    private static int findCenterIndex(List<Region> children, String centerRegionName, float totalBudget) {

        if (centerRegionName != null) {
            for (var i = 0; i < children.size(); i++) {
                if (children.get(i).name().equals(centerRegionName)) {
                    return i;
                }
            }
        }

        var dominant = 0;
        var maxBudget = 0F;
        for (var i = 0; i < children.size(); i++) {
            var budget = children.get(i).areaBudget();
            if (budget > maxBudget) {
                maxBudget = budget;
                dominant = i;
            }
        }
        return dominant;
    }

    public static long getSeed(long parentSeed, int childIndex, Region region) {
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 888);
    }

    private static float hashToFloat(long seed, int a, int b) {
        var h = MathUtils.hash64(seed, a, b, 0);
        return (h & 0xFFFF) / 65536.0f;
    }
}
