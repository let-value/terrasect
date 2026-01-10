package com.terrasect.common.strategy;

public final class QueryResult {

    public int childIndex;

    public float centerX;

    public float centerZ;

    public float radius;

    public float siteX;

    public float siteZ;

    public boolean isRing;

    public long childSeed;

    public float edgeDistance;

    public void reset() {
        childIndex = 0;
        centerX = 0;
        centerZ = 0;
        radius = 0.5f;
        siteX = 0;
        siteZ = 0;
        isRing = false;
        childSeed = 0;
        edgeDistance = 1.0f;
    }
}
