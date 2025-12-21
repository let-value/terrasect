package com.terrasect.common.generation.definition;

/**
 * Narrative-friendly climate knobs. Values are optional so children can inherit or override selectively.
 * 
 * <p>All parameters use {@link ClimateRange} which defines an allowed min/max range.
 * Original world values are mapped into this range, preserving relative variation.
 * 
 * <p>Climate parameters and their effects:
 * <ul>
 *   <li><b>temperature</b>: -1.0 (freezing) to 1.0 (hot) - affects biome selection</li>
 *   <li><b>humidity</b>: -1.0 (dry) to 1.0 (wet) - affects biome selection</li>
 *   <li><b>continentalness</b>: -1.0 (deep ocean) to 1.0 (inland) - affects terrain height</li>
 *   <li><b>erosion</b>: -1.0 (not eroded/steep) to 1.0 (eroded/flat) - affects terrain smoothness</li>
 *   <li><b>depth</b>: surface depth parameter - affects underground features</li>
 *   <li><b>weirdness</b>: -1.0 to 1.0 - affects biome variant selection (e.g., shattered vs normal)</li>
 * </ul>
 * 
 * <p>Continentalness values (approximate):
 * <ul>
 *   <li>-1.0 to -0.5: Deep ocean</li>
 *   <li>-0.5 to -0.2: Ocean</li>
 *   <li>-0.2 to 0.0: Coast/beach</li>
 *   <li>0.0 to 0.3: Near-inland</li>
 *   <li>0.3 to 1.0: Inland/mountains</li>
 * </ul>
 * 
 * <p>Erosion values (approximate):
 * <ul>
 *   <li>-1.0: Very steep terrain, mountains</li>
 *   <li>0.0: Normal terrain</li>
 *   <li>1.0: Very flat, eroded plains</li>
 * </ul>
 */
public record ClimateSettings(
    ClimateRange temperature,
    ClimateRange humidity,
    ClimateRange continentalness,
    ClimateRange erosion,
    ClimateRange depth,
    ClimateRange weirdness,
    Integer targetHeight,
    String precipitation,
    String climatePreset
) {
    /**
     * Represents an allowed range for a climate parameter.
     * Original world values are mapped from [-1, 1] into this range,
     * preserving relative variation but constraining to the allowed bounds.
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code range(-1.0f, -0.5f)} - force deep ocean terrain</li>
     *   <li>{@code range(0.3f, 1.0f)} - force inland/mountain terrain</li>
     *   <li>{@code exact(-0.8f)} - force exact value (no variation)</li>
     * </ul>
     */
    public record ClimateRange(float min, float max) {
        /**
         * Create a range that allows values between min and max.
         * Original world values are linearly mapped into this range.
         */
        public static ClimateRange range(float min, float max) {
            return new ClimateRange(Math.min(min, max), Math.max(min, max));
        }
        
        /**
         * Create a range that forces an exact value (no variation).
         */
        public static ClimateRange exact(float value) {
            return new ClimateRange(value, value);
        }
        
        /**
         * @return true if this range allows any variation
         */
        public boolean hasVariation() {
            return min != max;
        }
        
        /**
         * @return the center value of this range
         */
        public float center() {
            return (min + max) / 2.0f;
        }
        
        /**
         * @return the size of this range
         */
        public float size() {
            return max - min;
        }
    }
    
    public static ClimateSettings empty() {
        return builder().build();
    }

    public ClimateSettings resolveWithParent(ClimateSettings parent) {
        if (parent == null) return this;
        ClimateRange mergedTemperature = temperature != null ? temperature : parent.temperature;
        ClimateRange mergedHumidity = humidity != null ? humidity : parent.humidity;
        ClimateRange mergedContinentalness = continentalness != null ? continentalness : parent.continentalness;
        ClimateRange mergedErosion = erosion != null ? erosion : parent.erosion;
        ClimateRange mergedDepth = depth != null ? depth : parent.depth;
        ClimateRange mergedWeirdness = weirdness != null ? weirdness : parent.weirdness;
        Integer mergedTargetHeight = targetHeight != null ? targetHeight : parent.targetHeight;
        String mergedPrecipitation = precipitation != null ? precipitation : parent.precipitation;
        String mergedPreset = climatePreset != null ? climatePreset : parent.climatePreset;
        return new ClimateSettings(mergedTemperature, mergedHumidity, mergedContinentalness, 
            mergedErosion, mergedDepth, mergedWeirdness, mergedTargetHeight, mergedPrecipitation, mergedPreset);
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
        private Integer targetHeight;
        private String precipitation;
        private String climatePreset;

        /**
         * Set temperature range to control hot vs cold biomes.
         * @param min minimum temperature (-1.0 = freezing)
         * @param max maximum temperature (1.0 = hot)
         */
        public Builder temperature(float min, float max) {
            this.temperature = ClimateRange.range(min, max);
            return this;
        }
        
        /**
         * Set exact temperature value.
         * @param value -1.0 (freezing) to 1.0 (hot)
         */
        public Builder temperature(float value) {
            this.temperature = ClimateRange.exact(value);
            return this;
        }

        /**
         * Set humidity range to control dry vs wet biomes.
         * @param min minimum humidity (-1.0 = dry/desert)
         * @param max maximum humidity (1.0 = wet/jungle)
         */
        public Builder humidity(float min, float max) {
            this.humidity = ClimateRange.range(min, max);
            return this;
        }
        
        /**
         * Set exact humidity value.
         * @param value -1.0 (dry) to 1.0 (wet)
         */
        public Builder humidity(float value) {
            this.humidity = ClimateRange.exact(value);
            return this;
        }
        
        /**
         * Set continentalness range to control ocean vs land terrain.
         * @param min minimum continentalness (-1.0 = deep ocean)
         * @param max maximum continentalness (1.0 = inland/mountains)
         */
        public Builder continentalness(float min, float max) {
            this.continentalness = ClimateRange.range(min, max);
            return this;
        }
        
        /**
         * Set exact continentalness value.
         * @param value -1.0 (deep ocean) to 1.0 (inland)
         */
        public Builder continentalness(float value) {
            this.continentalness = ClimateRange.exact(value);
            return this;
        }
        
        /**
         * Set erosion range to control terrain steepness.
         * @param min minimum erosion (-1.0 = steep/mountains)
         * @param max maximum erosion (1.0 = flat/eroded)
         */
        public Builder erosion(float min, float max) {
            this.erosion = ClimateRange.range(min, max);
            return this;
        }
        
        /**
         * Set exact erosion value.
         * @param value -1.0 (steep) to 1.0 (flat)
         */
        public Builder erosion(float value) {
            this.erosion = ClimateRange.exact(value);
            return this;
        }
        
        /**
         * Set depth range for underground features.
         * @param min minimum depth
         * @param max maximum depth
         */
        public Builder depth(float min, float max) {
            this.depth = ClimateRange.range(min, max);
            return this;
        }
        
        /**
         * Set exact depth value.
         * @param value depth parameter value
         */
        public Builder depth(float value) {
            this.depth = ClimateRange.exact(value);
            return this;
        }
        
        /**
         * Set weirdness range to control biome variants.
         * @param min minimum weirdness
         * @param max maximum weirdness
         */
        public Builder weirdness(float min, float max) {
            this.weirdness = ClimateRange.range(min, max);
            return this;
        }
        
        /**
         * Set exact weirdness value.
         * @param value -1.0 to 1.0, affects variant selection
         */
        public Builder weirdness(float value) {
            this.weirdness = ClimateRange.exact(value);
            return this;
        }

        /**
         * Set target terrain height for this region.
         * Terrain will be pushed toward this Y level.
         * @param height target Y level (e.g., 50 for ocean floor, 80 for highlands)
         */
        public Builder targetHeight(int height) {
            this.targetHeight = height;
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
            return new ClimateSettings(temperature, humidity, continentalness, 
                erosion, depth, weirdness, targetHeight, precipitation, climatePreset);
        }

        public Builder copyFrom(ClimateSettings settings) {
            if (settings == null) return this;
            this.temperature = settings.temperature();
            this.humidity = settings.humidity();
            this.continentalness = settings.continentalness();
            this.erosion = settings.erosion();
            this.depth = settings.depth();
            this.weirdness = settings.weirdness();
            this.targetHeight = settings.targetHeight();
            this.precipitation = settings.precipitation();
            this.climatePreset = settings.climatePreset();
            return this;
        }
    }
}
