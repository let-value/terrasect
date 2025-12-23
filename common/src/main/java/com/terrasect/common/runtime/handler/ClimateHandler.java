package com.terrasect.common.runtime.handler;

import com.terrasect.common.Terrasect;
import com.terrasect.common.runtime.ClimateModifier;
import com.terrasect.common.api.Region;
import com.terrasect.common.runtime.TraversalResult;
import com.terrasect.common.api.Context;
import com.terrasect.common.runtime.World;
import com.terrasect.common.devtools.MixinSampler;
import com.terrasect.common.devtools.Profiler;
import com.terrasect.common.generation.definition.ClimateSettings;
import net.minecraft.world.level.biome.Climate;

/**
 * Shared climate modification logic for platform mixins.
 * 
 * This class contains all the common climate handling code that is shared
 * between Fabric and NeoForge ClimateMixin implementations.
 * 
 * <p>
 * Mixins should be thin - they only redirect calls to this handler.
 * All debug logging and statistics tracking is centralized here.
 */
public final class ClimateHandler {
    private ClimateHandler() {
    }

    public record ClimateResult(
            long temperature,
            long humidity,
            long continentalness,
            long erosion,
            long depth,
            long weirdness,
            boolean modified,
            String regionName) {
        public static ClimateResult unmodified(long temperature, long humidity, long continentalness,
                long erosion, long depth, long weirdness) {
            return new ClimateResult(temperature, humidity, continentalness, erosion, depth, weirdness, false, null);
        }

        public static ClimateResult unmodifiedWithRegion(long temperature, long humidity, long continentalness,
                long erosion, long depth, long weirdness, String regionName) {
            return new ClimateResult(temperature, humidity, continentalness, erosion, depth, weirdness, false,
                    regionName);
        }
    }

    public static ClimateResult modifyClimate(
            Context context,
            int x, int y, int z,
            long originalTemperature,
            long originalHumidity,
            long originalContinentalness,
            long originalErosion,
            long originalDepth,
            long originalWeirdness) {

        if (context == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity, originalContinentalness,
                    originalErosion, originalDepth, originalWeirdness);
        }

        int blockX = x << 2;
        int blockZ = z << 2;

        TraversalResult traversal = World.traverse(context, blockX, blockZ);

        if (traversal == null || traversal.region == null) {
            return ClimateResult.unmodified(originalTemperature, originalHumidity, originalContinentalness,
                    originalErosion, originalDepth, originalWeirdness);
        }

        Region region = traversal.region;

        ClimateSettings climate = region.definition().climate();
        if (climate == null) {

            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, originalContinentalness,
                    originalErosion, originalDepth, originalWeirdness, region.name());
        }

        if (climate.temperature() == null && climate.humidity() == null && climate.continentalness() == null &&
                climate.erosion() == null && climate.depth() == null && climate.weirdness() == null) {

            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, originalContinentalness,
                    originalErosion, originalDepth, originalWeirdness, region.name());
        }

        float edgeFactor = 1.0f - traversal.edgeDistance;

        ClimateModifier.ClimateOffset offset = ClimateModifier.calculateOffset(
                climate,
                originalTemperature,
                originalHumidity,
                originalContinentalness,
                originalErosion,
                originalDepth,
                originalWeirdness,
                edgeFactor);

        if (!offset.hasModifications()) {
            return ClimateResult.unmodifiedWithRegion(originalTemperature, originalHumidity, originalContinentalness,
                    originalErosion, originalDepth, originalWeirdness, region.name());
        }

        long modifiedTemp = ClimateModifier.applyTemperatureOffset(originalTemperature, offset);
        long modifiedHumid = ClimateModifier.applyHumidityOffset(originalHumidity, offset);
        long modifiedCont = ClimateModifier.applyContinentalnessOffset(originalContinentalness, offset);
        long modifiedErosion = ClimateModifier.applyErosionOffset(originalErosion, offset);
        long modifiedDepth = ClimateModifier.applyDepthOffset(originalDepth, offset);
        long modifiedWeirdness = ClimateModifier.applyWeirdnessOffset(originalWeirdness, offset);

        return new ClimateResult(modifiedTemp, modifiedHumid, modifiedCont,
                modifiedErosion, modifiedDepth, modifiedWeirdness, true, region.name());
    }

    public static Climate.TargetPoint modifyTargetPoint(
            Context context,
            int x, int y, int z,
            Climate.TargetPoint original) {

        ClimateResult result = modifyClimate(
                context, x, y, z,
                original.temperature(),
                original.humidity(),
                original.continentalness(),
                original.erosion(),
                original.depth(),
                original.weirdness());

        if (!result.modified()) {
            return original;
        }

        return new Climate.TargetPoint(
                result.temperature(),
                result.humidity(),
                result.continentalness(),
                result.erosion(),
                result.depth(),
                result.weirdness());
    }
}
