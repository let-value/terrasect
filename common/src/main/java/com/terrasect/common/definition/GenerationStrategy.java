package com.terrasect.common.definition;

public sealed interface GenerationStrategy
permits GenerationStrategy.Hex, GenerationStrategy.Voronoi, GenerationStrategy.Subdivision, GenerationStrategy.Template {

    static GenerationStrategy hex() {
        return Hex.DEFAULT;
    }

    static GenerationStrategy hex(String ringRegionName) {
        return (ringRegionName == null || ringRegionName.isBlank()) ? Hex.DEFAULT : new Hex(ringRegionName);
    }

    static GenerationStrategy voronoi() {
        return Voronoi.DEFAULT;
    }

    static GenerationStrategy voronoi(int relaxationIterations) {
        return relaxationIterations == Voronoi.DEFAULT_RELAXATIONS
                ? Voronoi.DEFAULT
                : new Voronoi(relaxationIterations);
    }

    static GenerationStrategy subdivision() {
        return Subdivision.DEFAULT;
    }

    static GenerationStrategy subdivision(float jitter) {
        return jitter == Subdivision.DEFAULT_JITTER ? Subdivision.DEFAULT : new Subdivision(jitter);
    }

    static GenerationStrategy template() {
        return Template.AUTO;
    }

    static GenerationStrategy template(TemplateType type) {
        return type == null ? Template.AUTO : new Template(type, null);
    }

    static GenerationStrategy centerSurround(String centerRegionName) {
        return new Template(TemplateType.CENTER_SURROUND, centerRegionName);
    }

    record Hex(String ringRegionName) implements GenerationStrategy {
        static final Hex DEFAULT = new Hex(null);
    }

    record Voronoi(int relaxationIterations) implements GenerationStrategy {
        static final int DEFAULT_RELAXATIONS = 5;
        static final Voronoi DEFAULT = new Voronoi(DEFAULT_RELAXATIONS);

        public Voronoi {
            relaxationIterations = Math.max(0, Math.min(20, relaxationIterations));
        }
    }

    record Subdivision(float jitter) implements GenerationStrategy {
        static final float DEFAULT_JITTER = 0.05f;
        static final Subdivision DEFAULT = new Subdivision(DEFAULT_JITTER);

        public Subdivision {
            jitter = Math.max(0f, Math.min(0.5f, jitter));
        }
    }

    record Template(TemplateType type, String centerRegionName) implements GenerationStrategy {
        static final Template AUTO = new Template(null, null);
    }

    enum TemplateType {
        BINARY,
        TRIANGLE,
        CENTER_SURROUND,
        RADIAL
    }
}
