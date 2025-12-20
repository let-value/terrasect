package com.terrasect.common.runtime;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.Strategy;

/**
 * Facade for locating narrative regions in the hierarchy while keeping the
 * heavy math contained in {@link NarrativeSpace}. Mods can depend on this
 * class to ask "what region am I in?" without touching the lower-level math
 * utilities in {@link World} or {@link RegionField}.
 */
public final class RegionLocator {

    private final NarrativeSpace narrativeSpace;
    private Region root;

    public RegionLocator(NarrativeSpace narrativeSpace) {
        this.narrativeSpace = narrativeSpace;
    }

    public void setRoot(Region newRoot) {
        this.root = newRoot;
    }

    public Region getRoot() {
        if (root == null) {
            throw new IllegalStateException("NarrativeWorld root not initialized!");
        }
        return root;
    }

    public Region getRegion(int x, int z, Strategy context) {
        return getRegionAtDepth(x, z, context, 100);
    }

    public Region getRegionAtDepth(int x, int z, Strategy context, int targetDepth) {
        return narrativeSpace.getRegionAtDepth(getRoot(), x, z, context, targetDepth);
    }

    public long getRegionSeedAtDepth(int x, int z, Strategy context, int targetDepth) {
        return narrativeSpace.getRegionSeedAtDepth(getRoot(), x, z, context, targetDepth);
    }
}
