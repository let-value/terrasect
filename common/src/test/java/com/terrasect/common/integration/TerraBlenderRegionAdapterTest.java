package com.terrasect.common.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerraBlenderRegionAdapterTest {
    @Test
    void buildsSingleParameterPointFromConstraintRanges() {
        RegionConstraints constraints = RegionConstraints.builder()
            .temperatureRange(-0.5, 0.5)
            .humidityRange(-0.2, 0.7)
            .continentalnessRange(-1.0, 0.1)
            .erosionRange(-0.6, 0.2)
            .weirdnessRange(-0.3, 0.4)
            .depthRange(-0.4, 0.0)
            .build();

        TerraBlenderRegionAdapter adapter = new TerraBlenderRegionAdapter("terrasect:test", RegionWeight.of(7));
        List<ClimateParameterPoint> points = adapter.toParameterPoints(constraints);

        assertEquals(1, points.size(), "Adapter should produce a single fused parameter point for the constraint envelope");
        ClimateParameterPoint point = points.getFirst();
        assertEquals(new ClimateRange(-0.5, 0.5), point.temperature());
        assertEquals(new ClimateRange(-0.2, 0.7), point.humidity());
        assertEquals(new ClimateRange(-1.0, 0.1), point.continentalness());
        assertEquals(new ClimateRange(-0.6, 0.2), point.erosion());
        assertEquals(new ClimateRange(-0.3, 0.4), point.weirdness());
        assertEquals(new ClimateRange(-0.4, 0.0), point.depth());
    }
}
