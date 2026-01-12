package com.terrasect.common.strategy;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.util.MathUtils;
import java.util.List;

public final class TemplateStrategy {

    private TemplateStrategy() {}

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

        int count = children.size();
        if (count == 1) {
            out.childIndex = 0;
            out.centerX = 0;
            out.centerZ = 0;
            out.radius = 1.0f;
            return;
        }

        float nx = dx / radius;
        float nz = dz / radius;

        float totalBudget = 0;
        for (int i = 0; i < count; i++) {
            totalBudget += children.get(i).areaBudget();
        }

        GenerationStrategy.TemplateType templateType = template != null ? template.type() : null;
        String centerRegionName = template != null ? template.centerRegionName() : null;

        TemplateType type = selectTemplate(templateType, children, totalBudget, count);

        int centerIndex =
            (type == TemplateType.CENTER_SURROUND) ? findCenterIndex(children, centerRegionName, totalBudget) : 0;

        int bestIndex = 0;
        float bestMetric = Float.MAX_VALUE;
        float secondBestMetric = Float.MAX_VALUE;
        float bestX = 0, bestZ = 0, bestR = 0;

        for (int i = 0; i < count; i++) {
            float budgetRatio = children.get(i).areaBudget() / totalBudget;
            float rNorm = (float) Math.sqrt(budgetRatio);

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

            float ddx = nx - sx;
            float ddz = nz - sz;
            float distSq = ddx * ddx + ddz * ddz;
            float metric = distSq - rNorm * rNorm;

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

        float rawEdge = secondBestMetric - bestMetric;
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

        float maxBudget = 0;
        for (Region child : children) {
            maxBudget = Math.max(maxBudget, child.areaBudget());
        }
        if (maxBudget / totalBudget > 0.4f) return TemplateType.CENTER_SURROUND;

        if (count == 3) return TemplateType.TRIANGLE;

        return TemplateType.RADIAL;
    }

    private static int findCenterIndex(List<Region> children, String centerRegionName, float totalBudget) {

        if (centerRegionName != null) {
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).name().equals(centerRegionName)) {
                    return i;
                }
            }
        }

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

    public static long getSeed(long parentSeed, int childIndex, Region region) {
        return MathUtils.hash64(parentSeed, region.name().hashCode(), childIndex, 888);
    }

    private static float hashToFloat(long seed, int a, int b) {
        long h = MathUtils.hash64(seed, a, b, 0);
        return (h & 0xFFFF) / 65536.0f;
    }
}
