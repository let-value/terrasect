package com.terrasect.neoforge.generation;

/**
 * Utility class for controlling vanilla sampling mode.
 * 
 * <p>When vanilla mode is enabled, the ClimateSamplerMixin will exit early
 * and return unmodified climate values. This prevents infinite recursion
 * when getRiverInfluence/getRidgeInfluence need vanilla climate data.
 */
public final class SamplerBypass {
    
    private static final ThreadLocal<Boolean> WANT_VANILLA = ThreadLocal.withInitial(() -> false);
    
    private SamplerBypass() {}
    
    /**
     * Set whether we want vanilla (unmodified) climate values.
     * @param want true to bypass mixin modifications
     */
    public static void setWantVanilla(boolean want) {
        WANT_VANILLA.set(want);
    }
    
    /**
     * Check if vanilla values are currently requested.
     * @return true if mixin should exit early
     */
    public static boolean isWantVanilla() {
        return WANT_VANILLA.get();
    }
}
