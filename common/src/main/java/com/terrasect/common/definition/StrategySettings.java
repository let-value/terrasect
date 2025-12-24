package com.terrasect.common.definition;

/**
 * Strategy-specific settings for region generation.
 * 
 * Each strategy has its own settings record. Configure the ones you need,
 * ignore the rest - strategies use sensible defaults for unconfigured values.
 */
public record StrategySettings(
    HexSettings hex,
    VoronoiSettings voronoi,
    SubdivisionSettings subdivision,
    TemplateSettings template
) {
    /** Default settings - all null, strategies use their own defaults */
    public static StrategySettings defaults() {
        return new StrategySettings(null, null, null, null);
    }

    /** Builder for fluent configuration */
    public static Builder builder() {
        return new Builder();
    }

    // ========== HEX Settings ==========
    
    /**
     * @param ringRegionName Name of region for buffer zone between hex cells (null = no ring)
     */
    public record HexSettings(String ringRegionName) {
        public static HexSettings withRing(String regionName) {
            return new HexSettings(regionName);
        }
    }

    // ========== VORONOI Settings ==========
    
    /**
     * @param relaxationIterations Lloyd relaxation iterations (0-20, default 15)
     */
    public record VoronoiSettings(int relaxationIterations) {
        public VoronoiSettings {
            relaxationIterations = Math.max(0, Math.min(20, relaxationIterations));
        }
        
        public static VoronoiSettings defaults() {
            return new VoronoiSettings(15);
        }
    }

    // ========== SUBDIVISION Settings ==========
    
    /**
     * @param jitter Randomness in split positions (0.0-0.5, default 0.15)
     */
    public record SubdivisionSettings(float jitter) {
        public SubdivisionSettings {
            jitter = Math.max(0f, Math.min(0.5f, jitter));
        }
        
        public static SubdivisionSettings defaults() {
            return new SubdivisionSettings(0.15f);
        }
    }

    // ========== TEMPLATE Settings ==========
    
    /**
     * @param type Template layout type (null = auto-select based on children)
     * @param centerSurround Settings for CENTER_SURROUND template
     */
    public record TemplateSettings(
        TemplateType type,
        CenterSurroundSettings centerSurround
    ) {
        public static TemplateSettings auto() {
            return new TemplateSettings(null, null);
        }
        
        public static TemplateSettings of(TemplateType type) {
            return new TemplateSettings(type, null);
        }
        
        public static TemplateSettings centerSurround(String centerRegionName) {
            return new TemplateSettings(
                TemplateType.CENTER_SURROUND, 
                new CenterSurroundSettings(centerRegionName)
            );
        }
    }

    /**
     * Settings specific to CENTER_SURROUND template.
     * @param centerRegionName Name of region to place at center (null = use highest budget)
     */
    public record CenterSurroundSettings(String centerRegionName) {}

    /**
     * Available template layouts.
     */
    public enum TemplateType {
        /** Two regions split along a random axis */
        BINARY,
        
        /** Three regions in triangular formation */
        TRIANGLE,
        
        /** One region in center, others distributed around */
        CENTER_SURROUND,
        
        /** Regions arranged in concentric rings from center */
        RADIAL
    }

    // ========== Builder ==========

    public static class Builder {
        private HexSettings hex = null;
        private VoronoiSettings voronoi = null;
        private SubdivisionSettings subdivision = null;
        private TemplateSettings template = null;

        /** HEX: Set ring region name for buffer zones between hex cells */
        public Builder hex(HexSettings settings) {
            this.hex = settings;
            return this;
        }

        /** HEX: Shorthand for ring region */
        public Builder hexRing(String regionName) {
            this.hex = HexSettings.withRing(regionName);
            return this;
        }

        /** VORONOI: Set relaxation settings */
        public Builder voronoi(VoronoiSettings settings) {
            this.voronoi = settings;
            return this;
        }

        /** VORONOI: Shorthand for relaxation iterations */
        public Builder voronoiRelaxation(int iterations) {
            this.voronoi = new VoronoiSettings(iterations);
            return this;
        }

        /** SUBDIVISION: Set subdivision settings */
        public Builder subdivision(SubdivisionSettings settings) {
            this.subdivision = settings;
            return this;
        }

        /** SUBDIVISION: Shorthand for jitter amount */
        public Builder subdivisionJitter(float jitter) {
            this.subdivision = new SubdivisionSettings(jitter);
            return this;
        }

        /** TEMPLATE: Set template settings */
        public Builder template(TemplateSettings settings) {
            this.template = settings;
            return this;
        }

        /** TEMPLATE: Shorthand for template type */
        public Builder template(TemplateType type) {
            this.template = TemplateSettings.of(type);
            return this;
        }

        /** TEMPLATE: Shorthand for center-surround with specific center region */
        public Builder centerSurround(String centerRegionName) {
            this.template = TemplateSettings.centerSurround(centerRegionName);
            return this;
        }

        public StrategySettings build() {
            return new StrategySettings(hex, voronoi, subdivision, template);
        }
    }
}
