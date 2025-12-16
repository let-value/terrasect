package com.terrasect.common.generation;

/**
 * Legacy facade that keeps the existing static API surface while delegating all
 * traversal logic to {@link RegionLocator} and {@link NarrativeSpace}. Mods can
 * continue using {@code World.getRegion(...)} without worrying about the
 * underlying math organization.
 */
public class World {

    private static final RegionLocator LOCATOR = new RegionLocator(new NarrativeSpace());

    public static void setRoot(Region newRoot) {
        LOCATOR.setRoot(newRoot);
    }

    public static Region getRoot() {
        return LOCATOR.getRoot();
    }

    /**
     * Get the leaf Region at a given location by traversing the hierarchy.
     */
    public static Region getRegion(int x, int z, Strategy context) {
        return LOCATOR.getRegion(x, z, context);
    }

    /**
     * Get the Region at a specific depth in the hierarchy.
     */
    public static Region getRegionAtDepth(int x, int z, Strategy context, int targetDepth) {
        return LOCATOR.getRegionAtDepth(x, z, context, targetDepth);
    }

    public static long getRegionSeedAtDepth(int x, int z, Strategy context, int targetDepth) {
        return LOCATOR.getRegionSeedAtDepth(x, z, context, targetDepth);
    }
}
