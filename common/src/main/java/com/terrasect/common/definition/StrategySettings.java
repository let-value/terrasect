package com.terrasect.common.definition;

public record StrategySettings(
        HexSettings hex, VoronoiSettings voronoi, SubdivisionSettings subdivision, TemplateSettings template) {

    public static StrategySettings defaults() {
        return new StrategySettings(null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public record HexSettings(String ringRegionName) {
        public static HexSettings withRing(String regionName) {
            return new HexSettings(regionName);
        }
    }

    public record VoronoiSettings(int relaxationIterations) {
        public VoronoiSettings {
            relaxationIterations = Math.max(0, Math.min(20, relaxationIterations));
        }

        public static VoronoiSettings defaults() {
            return new VoronoiSettings(15);
        }
    }

    public record SubdivisionSettings(float jitter) {
        public SubdivisionSettings {
            jitter = Math.max(0f, Math.min(0.5f, jitter));
        }

        public static SubdivisionSettings defaults() {
            return new SubdivisionSettings(0.15f);
        }
    }

    public record TemplateSettings(TemplateType type, CenterSurroundSettings centerSurround) {
        public static TemplateSettings auto() {
            return new TemplateSettings(null, null);
        }

        public static TemplateSettings of(TemplateType type) {
            return new TemplateSettings(type, null);
        }

        public static TemplateSettings centerSurround(String centerRegionName) {
            return new TemplateSettings(TemplateType.CENTER_SURROUND, new CenterSurroundSettings(centerRegionName));
        }
    }

    public record CenterSurroundSettings(String centerRegionName) {}

    public enum TemplateType {
        BINARY,

        TRIANGLE,

        CENTER_SURROUND,

        RADIAL
    }

    public static class Builder {
        private HexSettings hex = null;
        private VoronoiSettings voronoi = null;
        private SubdivisionSettings subdivision = null;
        private TemplateSettings template = null;

        public Builder hex(HexSettings settings) {
            this.hex = settings;
            return this;
        }

        public Builder hexRing(String regionName) {
            this.hex = HexSettings.withRing(regionName);
            return this;
        }

        public Builder voronoi(VoronoiSettings settings) {
            this.voronoi = settings;
            return this;
        }

        public Builder voronoiRelaxation(int iterations) {
            this.voronoi = new VoronoiSettings(iterations);
            return this;
        }

        public Builder subdivision(SubdivisionSettings settings) {
            this.subdivision = settings;
            return this;
        }

        public Builder subdivisionJitter(float jitter) {
            this.subdivision = new SubdivisionSettings(jitter);
            return this;
        }

        public Builder template(TemplateSettings settings) {
            this.template = settings;
            return this;
        }

        public Builder template(TemplateType type) {
            this.template = TemplateSettings.of(type);
            return this;
        }

        public Builder centerSurround(String centerRegionName) {
            this.template = TemplateSettings.centerSurround(centerRegionName);
            return this;
        }

        public StrategySettings build() {
            return new StrategySettings(hex, voronoi, subdivision, template);
        }
    }
}
