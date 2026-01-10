package com.terrasect.common.definition;

public record EdgeStatistics(
        float fineTransitionDensity,
        float fineHorizontalJitter,
        float fineVerticalJitter,
        float fineTemperatureDelta,
        float fineWeirdnessDelta,
        float coarseTransitionDensity,
        float coarseAverageRunBlocks) {
    private static final EdgeStatistics VANILLA_OVERWORLD =
            new EdgeStatistics(0.0257f, 1.90f, 1.78f, 47.3405f, 401.0312f, 0.2366f, 130.94f);

    public static EdgeStatistics vanillaOverworld() {
        return VANILLA_OVERWORLD;
    }
}
