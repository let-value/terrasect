package com.terrasect.common.definition;

public record ClimateSettings(
        ClimateRange temperature,
        ClimateRange humidity,
        ClimateRange continentalness,
        ClimateRange erosion,
        ClimateRange depth,
        ClimateRange weirdness,
        String precipitation,
        String climatePreset,
        boolean hasRanges) {

    public ClimateSettings {
        hasRanges = temperature != null
                || humidity != null
                || continentalness != null
                || erosion != null
                || depth != null
                || weirdness != null;
    }

    public boolean hasRanges() {
        return hasRanges;
    }

    public record ClimateRange(float min, float max) {

        public static ClimateRange range(float min, float max) {
            return new ClimateRange(Math.min(min, max), Math.max(min, max));
        }

        public static ClimateRange exact(float value) {
            return new ClimateRange(value, value);
        }

        public boolean hasVariation() {
            return min != max;
        }

        public float center() {
            return (min + max) / 2.0f;
        }

        public float size() {
            return max - min;
        }
    }

    public static ClimateSettings empty() {
        return builder().build();
    }

    public ClimateSettings resolveWithParent(ClimateSettings parent) {
        if (parent == null) return this;
        var mergedTemperature = temperature != null ? temperature : parent.temperature;
        var mergedHumidity = humidity != null ? humidity : parent.humidity;
        var mergedContinentalness = continentalness != null ? continentalness : parent.continentalness;
        var mergedErosion = erosion != null ? erosion : parent.erosion;
        var mergedDepth = depth != null ? depth : parent.depth;
        var mergedWeirdness = weirdness != null ? weirdness : parent.weirdness;
        var mergedPrecipitation = precipitation != null ? precipitation : parent.precipitation;
        var mergedPreset = climatePreset != null ? climatePreset : parent.climatePreset;
        return new ClimateSettings(
                mergedTemperature,
                mergedHumidity,
                mergedContinentalness,
                mergedErosion,
                mergedDepth,
                mergedWeirdness,
                mergedPrecipitation,
                mergedPreset,
                false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ClimateRange temperature;
        private ClimateRange humidity;
        private ClimateRange continentalness;
        private ClimateRange erosion;
        private ClimateRange depth;
        private ClimateRange weirdness;
        private String precipitation;
        private String climatePreset;

        public Builder temperature(float min, float max) {
            this.temperature = ClimateRange.range(min, max);
            return this;
        }

        public Builder temperature(float value) {
            this.temperature = ClimateRange.exact(value);
            return this;
        }

        public Builder humidity(float min, float max) {
            this.humidity = ClimateRange.range(min, max);
            return this;
        }

        public Builder humidity(float value) {
            this.humidity = ClimateRange.exact(value);
            return this;
        }

        public Builder continentalness(float min, float max) {
            this.continentalness = ClimateRange.range(min, max);
            return this;
        }

        public Builder continentalness(float value) {
            this.continentalness = ClimateRange.exact(value);
            return this;
        }

        public Builder erosion(float min, float max) {
            this.erosion = ClimateRange.range(min, max);
            return this;
        }

        public Builder erosion(float value) {
            this.erosion = ClimateRange.exact(value);
            return this;
        }

        public Builder depth(float min, float max) {
            this.depth = ClimateRange.range(min, max);
            return this;
        }

        public Builder depth(float value) {
            this.depth = ClimateRange.exact(value);
            return this;
        }

        public Builder weirdness(float min, float max) {
            this.weirdness = ClimateRange.range(min, max);
            return this;
        }

        public Builder weirdness(float value) {
            this.weirdness = ClimateRange.exact(value);
            return this;
        }

        public Builder precipitation(String precipitation) {
            this.precipitation = precipitation;
            return this;
        }

        public Builder climatePreset(String climatePreset) {
            this.climatePreset = climatePreset;
            return this;
        }

        public ClimateSettings build() {
            return new ClimateSettings(
                    temperature,
                    humidity,
                    continentalness,
                    erosion,
                    depth,
                    weirdness,
                    precipitation,
                    climatePreset,
                    false);
        }

        public Builder copyFrom(ClimateSettings settings) {
            if (settings == null) return this;
            this.temperature = settings.temperature();
            this.humidity = settings.humidity();
            this.continentalness = settings.continentalness();
            this.erosion = settings.erosion();
            this.depth = settings.depth();
            this.weirdness = settings.weirdness();
            this.precipitation = settings.precipitation();
            this.climatePreset = settings.climatePreset();
            return this;
        }
    }
}
