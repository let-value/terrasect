package com.terrasect.common.generation;

import com.terrasect.common.definition.Region;

public final class TraversalResult {

    public Region region;

    public long seed;

    public float edgeDistance;

    public float edgeInfluence;

    public void reset() {
        region = null;
        seed = 0;
        edgeDistance = 1.0f;
        edgeInfluence = 0.0f;
    }
}
