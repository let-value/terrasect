package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;

import java.util.List;

public class VoronoiGenerationStrategy implements RegionGenerationStrategy {

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        if (children.isEmpty()) return;

        float totalBudget = Math.max(getTotalWeight(children), 1.0f);

        float dx = scratch.warpedX() - scratch.centerX();
        float dz = scratch.warpedZ() - scratch.centerZ();
        float angle = (float) Math.atan2(dz, dx);
        float normalizedAngle = (angle + (float) Math.PI) / ((float) Math.PI * 2.0f);

        float angleOffset = (MathUtils.hash64(scratch.currentSeed(), children.size(), 123, 456) & 0xFFFF) / 65536.0f;
        normalizedAngle = (normalizedAngle + angleOffset) % 1.0f;

        Region chosen = children.get(0);
        int chosenIndex = 0;
        float accumulator = 0.0f;

        for (int i = 0; i < children.size(); i++) {
            Region child = children.get(i);
            float fraction = child.areaBudget() / totalBudget;
            accumulator += fraction;
            if (normalizedAngle <= accumulator || i == children.size() - 1) {
                chosen = child;
                chosenIndex = i;
                break;
            }
        }

        long nextSeed = MathUtils.hash64(scratch.currentSeed(), chosen.name().hashCode(), chosenIndex, 999);
        float childRadius = scratch.currentRadius() * (float) Math.sqrt(chosen.areaBudget() / totalBudget);

        scratch.select(chosen, chosenIndex, nextSeed, scratch.centerX(), scratch.centerZ(), childRadius);
    }

    private float getTotalWeight(List<Region> regions) {
        float sum = 0;
        for (Region r : regions) sum += r.areaBudget();
        return sum;
    }
}

