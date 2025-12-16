package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.MathUtils;
import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.RegionField;

import java.util.List;

public class HexGenerationStrategy implements RegionGenerationStrategy {

    @Override
    public void traverse(List<Region> children, TraversalScratch scratch) {
        float hexSize = scratch.currentRadius();
        long hexPacked = RegionField.getHexCell(scratch.warpedX(), scratch.warpedZ(), hexSize);
        int q = (int) (hexPacked >> 32);
        int r = (int) hexPacked;

        float hx = hexSize * ((float) Math.sqrt(3) * q + (float) Math.sqrt(3) / 2.0f * r);
        float hz = hexSize * (3.0f / 2.0f * r);

        int hexDist = (Math.abs(q) + Math.abs(q + r) + Math.abs(r)) / 2;

        long hexSeed = MathUtils.hash64(scratch.currentSeed(), q, r, 9999);
        Region next;
        int index = 0;
        if (hexDist == 0) {
            next = children.get(0);
            hexSeed = MathUtils.hash64(scratch.currentSeed(), 0, 0, 9999);
        } else {
            next = pickChildWeighted(children, hexSeed);
            index = children.indexOf(next);
        }

        scratch.select(next, index, hexSeed, hx, hz, scratch.currentRadius());
    }

    private Region pickChildWeighted(List<Region> children, long seed) {
        float randomVal = (MathUtils.hash64(seed, 0, 0, 0) & 0xFFFF) / 65536.0f;

        float totalWeight = 0;
        for (Region r : children) {
            totalWeight += r.areaBudget();
        }

        float target = randomVal * totalWeight;
        float currentW = 0;
        for (Region r : children) {
            currentW += r.areaBudget();
            if (currentW >= target) return r;
        }
        return children.get(0);
    }
}
