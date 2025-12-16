package com.terrasect.common.generation.strategy;

import com.terrasect.common.generation.Region;
import com.terrasect.common.generation.Strategy;

/**
 * Mutable traversal state reused across region lookups to avoid per-call allocations.
 */
public final class TraversalScratch {
    private float warpedX;
    private float warpedZ;
    private float centerX;
    private float centerZ;
    private float radius;
    private long seed;
    private Strategy worldContext;
    private Region selectedRegion;
    private int selectedIndex;

    public void reset(float warpedX, float warpedZ, float radius, long seed, Strategy worldContext, Region root) {
        this.warpedX = warpedX;
        this.warpedZ = warpedZ;
        this.centerX = 0;
        this.centerZ = 0;
        this.radius = radius;
        this.seed = seed;
        this.worldContext = worldContext;
        this.selectedRegion = root;
        this.selectedIndex = -1;
    }

    public void select(Region region, int index, long nextSeed, float nextCenterX, float nextCenterZ, float nextRadius) {
        this.selectedRegion = region;
        this.selectedIndex = index;
        this.seed = nextSeed;
        this.centerX = nextCenterX;
        this.centerZ = nextCenterZ;
        this.radius = nextRadius;
    }

    public float warpedX() {
        return warpedX;
    }

    public float warpedZ() {
        return warpedZ;
    }

    public float centerX() {
        return centerX;
    }

    public float centerZ() {
        return centerZ;
    }

    public float currentRadius() {
        return radius;
    }

    public long currentSeed() {
        return seed;
    }

    public Strategy worldContext() {
        return worldContext;
    }

    public Region selectedRegion() {
        return selectedRegion;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

}

