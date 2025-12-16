package com.terrasect.common.generation.definition;

/**
 * Narrative-friendly climate knobs. Values are optional so children can inherit or override selectively.
 */
public record ClimateSettings(
    Float temperature,
    Float humidity,
    String precipitation,
    String climatePreset
) {
    public static ClimateSettings empty() {
        return builder().build();
    }

    public ClimateSettings resolveWithParent(ClimateSettings parent) {
        if (parent == null) return this;
        Float mergedTemperature = temperature != null ? temperature : parent.temperature;
        Float mergedHumidity = humidity != null ? humidity : parent.humidity;
        String mergedPrecipitation = precipitation != null ? precipitation : parent.precipitation;
        String mergedPreset = climatePreset != null ? climatePreset : parent.climatePreset;
        return new ClimateSettings(mergedTemperature, mergedHumidity, mergedPrecipitation, mergedPreset);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Float temperature;
        private Float humidity;
        private String precipitation;
        private String climatePreset;

        public Builder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder humidity(Float humidity) {
            this.humidity = humidity;
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
            return new ClimateSettings(temperature, humidity, precipitation, climatePreset);
        }

        public Builder copyFrom(ClimateSettings settings) {
            if (settings == null) return this;
            this.temperature = settings.temperature();
            this.humidity = settings.humidity();
            this.precipitation = settings.precipitation();
            this.climatePreset = settings.climatePreset();
            return this;
        }
    }
}
