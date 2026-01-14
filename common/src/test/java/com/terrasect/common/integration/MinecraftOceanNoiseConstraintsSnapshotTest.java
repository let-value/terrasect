package com.terrasect.common.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.terrasect.common.Context;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionDefinition;
import com.terrasect.common.generation.World;
import com.terrasect.common.handler.NoiseHandler;
import com.terrasect.common.lookup.NoiseChunkLookup;
import com.terrasect.common.mixin.NoiseChunkAccessor;
import com.terrasect.common.testing.SnapshotHashes;
import com.terrasect.common.testing.SnapshotOutputPaths;
import de.skuzzle.test.snapshots.Snapshot;
import com.terrasect.common.testing.SnapshotTests;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SnapshotTests
class MinecraftOceanNoiseConstraintsSnapshotTest {
    private static final int IMG_SIZE = 256;
    private static final int WORLD_SIZE = 1024;
    private static final int STEP = WORLD_SIZE / IMG_SIZE;
    private static final int CHUNK_SIZE = 16;
    private static final int QUART_SIZE = 4;

    private static final int SURFACE_IMG_SIZE = 128;
    private static final int SURFACE_STEP = WORLD_SIZE / SURFACE_IMG_SIZE;
    private static final int SURFACE_PIXELS_PER_CHUNK = CHUNK_SIZE / SURFACE_STEP;
    private static final DefaultMustacheFactory MUSTACHE_FACTORY =
            new DefaultMustacheFactory(new ClasspathResolver("templates"));
    private static final String REPORT_TITLE = "Terrasect Ocean Control (NoiseConstraints)";

    @BeforeAll static void setupMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void writesNoOceanAndOnlyOceanSnapshots(Snapshot snapshot) throws Exception {
        assertEquals(4, STEP, "test assumes quart-aligned pixels");
        assertEquals(8, SURFACE_STEP, "test assumes 8-block pixels for surface views");
        assertEquals(2, SURFACE_PIXELS_PER_CHUNK, "surface pixels must divide chunk size");

        var seed = 12345L;

        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        var noiseParams = lookup.lookupOrThrow(Registries.NOISE);

        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                    .value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }

        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        var noiseSettings = settings.noiseSettings();
        var minY = noiseSettings.minY();
        var maxY = minY + noiseSettings.height() - 1;
        var seaLevel = settings.seaLevel();

        var continentalnessHolder = noiseParams.getOrThrow(Noises.CONTINENTALNESS);
        var continentalnessNoise = randomState.getOrCreateNoise(Noises.CONTINENTALNESS);
        var continentalness =
                new DensityFunction.NoiseHolder(continentalnessHolder, continentalnessNoise);

        var outDir = SnapshotOutputPaths.forTestClass(
                MinecraftOceanNoiseConstraintsSnapshotTest.class, "ocean-control");
        Files.createDirectories(outDir.toPath());

        RenderResult vanilla = render(outDir, "vanilla", continentalness, seed, null);
        assertTrue(
                vanilla.oceanPixels > 0 && vanilla.oceanPixels < (long) IMG_SIZE * IMG_SIZE,
                "expected vanilla continentalness to contain both signs");

        var noOceanDef = RegionDefinition.builder()
                .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(0.05, 2.0)))
                .build();
        RenderResult noOcean = render(outDir, "no_ocean", continentalness, seed, noOceanDef);
        assertEquals(0L, noOcean.oceanPixels, "expected no-ocean clamp to remove all ocean pixels");

        var onlyOceanDef = RegionDefinition.builder()
                .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(-2.0, -0.05)))
                .build();
        RenderResult onlyOcean = render(outDir, "only_ocean", continentalness, seed, onlyOceanDef);
        assertEquals(
                (long) IMG_SIZE * IMG_SIZE,
                onlyOcean.oceanPixels,
                "expected only-ocean clamp to make all pixels ocean");

        Region regionsRoot = buildTwoRegionRoot(noOceanDef, onlyOceanDef);
        RenderRegionsResult regions = renderRegions(outDir, "two_regions", continentalness, seed, regionsRoot);

        assertTrue(regions.noOceanInteriorPixels > 0, "expected at least one NO_OCEAN interior sample");
        assertTrue(regions.onlyOceanInteriorPixels > 0, "expected at least one ONLY_OCEAN interior sample");
        assertEquals(0L, regions.noOceanInteriorOceanPixels, "NO_OCEAN interior should contain no ocean pixels");
        assertEquals(
                regions.onlyOceanInteriorPixels,
                regions.onlyOceanInteriorOceanPixels,
                "ONLY_OCEAN interior should be all ocean pixels");

        SurfaceRenderResult vanillaSurface =
                renderPreliminarySurfaceLevel(outDir, "vanilla", randomState, seed, seaLevel, minY, maxY, null);

        SurfaceRenderResult noOceanSurface =
                renderPreliminarySurfaceLevel(outDir, "no_ocean", randomState, seed, seaLevel, minY, maxY, noOceanDef);

        SurfaceRenderResult onlyOceanSurface = renderPreliminarySurfaceLevel(
                outDir, "only_ocean", randomState, seed, seaLevel, minY, maxY, onlyOceanDef);

        SurfaceRegionsRenderResult regionsSurface = renderPreliminarySurfaceLevelRegions(
                outDir, "two_regions", randomState, seed, seaLevel, minY, maxY, regionsRoot);

        var totalPixels = (long) IMG_SIZE * IMG_SIZE;
        var totalSurfacePixels = (long) SURFACE_IMG_SIZE * SURFACE_IMG_SIZE;
        var scenarioRows = List.of(
                new ScenarioRow("Vanilla (no constraints)", vanilla.valuesFile, vanilla.maskFile, vanilla.oceanPixels),
                new ScenarioRow("No ocean (clamp to +)", noOcean.valuesFile, noOcean.maskFile, noOcean.oceanPixels),
                new ScenarioRow("Only ocean (clamp to -)", onlyOcean.valuesFile, onlyOcean.maskFile, onlyOcean.oceanPixels));
        var regionsRow = new RegionsRow(
                regions.valuesFile,
                regions.maskFile,
                regions.regionFile,
                regions.noOceanInteriorOceanPixels,
                regions.noOceanInteriorPixels,
                regions.onlyOceanInteriorOceanPixels,
                regions.onlyOceanInteriorPixels);
        var terrainRows = List.of(
                new TerrainRow(
                        "Vanilla (no constraints)",
                        vanillaSurface.heightFile,
                        vanillaSurface.belowSeaFile,
                        vanillaSurface.belowSeaPixels,
                        vanillaSurface.minHeight,
                        vanillaSurface.maxHeight),
                new TerrainRow(
                        "No ocean (clamp to +)",
                        noOceanSurface.heightFile,
                        noOceanSurface.belowSeaFile,
                        noOceanSurface.belowSeaPixels,
                        noOceanSurface.minHeight,
                        noOceanSurface.maxHeight),
                new TerrainRow(
                        "Only ocean (clamp to -)",
                        onlyOceanSurface.heightFile,
                        onlyOceanSurface.belowSeaFile,
                        onlyOceanSurface.belowSeaPixels,
                        onlyOceanSurface.minHeight,
                        onlyOceanSurface.maxHeight));
        var terrainRegions = new TerrainRegionsRow(
                regionsSurface.heightFile,
                regionsSurface.belowSeaFile,
                regions.regionFile,
                regionsSurface.noOceanInteriorBelowSea,
                regionsSurface.noOceanInteriorPixels,
                regionsSurface.onlyOceanInteriorBelowSea,
                regionsSurface.onlyOceanInteriorPixels);
        var page = new OceanConstraintsPage(
                REPORT_TITLE,
                seed,
                STEP,
                IMG_SIZE,
                SURFACE_IMG_SIZE,
                seaLevel,
                minY,
                maxY,
                totalPixels,
                totalSurfacePixels,
                scenarioRows,
                regionsRow,
                terrainRows,
                terrainRegions);
        var indexHtml = renderTemplate("ocean-constraints/layout.mustache", page);
        String indexDigest = SnapshotHashes.sha256Hex(indexHtml);
        snapshot.assertThat(indexDigest).asText().matchesSnapshotText();
        Files.writeString(outDir.toPath().resolve("index.html"), indexHtml, StandardCharsets.UTF_8);
        System.out.println("Wrote ocean-control snapshot report to: " + outDir.getAbsolutePath());
    }

    private static String renderTemplate(String name, Object context) throws IOException {
        Mustache mustache = MUSTACHE_FACTORY.compile(name);
        var out = new StringWriter(8 * 1024);
        mustache.execute(out, context);
        return out.toString();
    }

    private static DensityFunction constrainNoiseSampling(DensityFunction function) {
        return function.mapAll(new ConstrainedNoiseVisitor());
    }

    private static Object recordComponent(Object record, String name) {
        var components = record.getClass().getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException(record.getClass().getName() + " is not a record");
        }

        for (RecordComponent component : components) {
            if (!name.equals(component.getName())) continue;
            var accessor = component.getAccessor();
            try {
                accessor.setAccessible(true);
                return accessor.invoke(record);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to read record component '" + name + "' from "
                                + record.getClass().getName(),
                        e);
            }
        }

        throw new IllegalArgumentException("Record component '" + name + "' not found on "
                + record.getClass().getName());
    }

    private static final class ConstrainedNoiseVisitor implements DensityFunction.Visitor {
        @Override public DensityFunction apply(DensityFunction function) {
            var className = function.getClass().getName();
            return switch (className) {
                case "net.minecraft.world.level.levelgen.DensityFunctions$Noise" -> ConstrainedNoise.wrap(function);
                case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise" ->
                    ConstrainedShiftedNoise.wrap(function);
                case "net.minecraft.world.level.levelgen.DensityFunctions$Shift" -> ConstrainedShift.wrap(function);
                case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA" -> ConstrainedShiftA.wrap(function);
                case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB" -> ConstrainedShiftB.wrap(function);
                default -> function;
            };
        }
    }

    private static final class ConstrainedNoise implements DensityFunction {
        private final DensityFunction.NoiseHolder noise;
        private final double xzScale;
        private final double yScale;

        private ConstrainedNoise(DensityFunction.NoiseHolder noise, double xzScale, double yScale) {
            this.noise = noise;
            this.xzScale = xzScale;
            this.yScale = yScale;
        }

        static DensityFunction wrap(DensityFunction function) {
            var noise = (DensityFunction.NoiseHolder) recordComponent(function, "noise");
            var xzScale = ((Number) recordComponent(function, "xzScale")).doubleValue();
            var yScale = ((Number) recordComponent(function, "yScale")).doubleValue();
            return new ConstrainedNoise(noise, xzScale, yScale);
        }

        @Override public double compute(DensityFunction.FunctionContext functionContext) {
            var x = functionContext.blockX() * xzScale;
            var y = functionContext.blockY() * yScale;
            var z = functionContext.blockZ() * xzScale;
            return NoiseHandler.sampleNoise(noise, x, y, z, functionContext);
        }

        @Override public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            var visitedNoise = visitor.visitNoise(noise);
            return visitor.apply(new ConstrainedNoise(visitedNoise, xzScale, yScale));
        }

        @Override public double minValue() {
            return -maxValue();
        }

        @Override public double maxValue() {
            return noise.maxValue();
        }

        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("test-only DensityFunction wrapper");
        }
    }

    private static final class ConstrainedShiftedNoise implements DensityFunction {
        private final DensityFunction shiftX;
        private final DensityFunction shiftY;
        private final DensityFunction shiftZ;
        private final double xzScale;
        private final double yScale;
        private final DensityFunction.NoiseHolder noise;

        private ConstrainedShiftedNoise(
                DensityFunction shiftX,
                DensityFunction shiftY,
                DensityFunction shiftZ,
                double xzScale,
                double yScale,
                DensityFunction.NoiseHolder noise) {
            this.shiftX = shiftX;
            this.shiftY = shiftY;
            this.shiftZ = shiftZ;
            this.xzScale = xzScale;
            this.yScale = yScale;
            this.noise = noise;
        }

        static DensityFunction wrap(DensityFunction function) {
            var shiftX = (DensityFunction) recordComponent(function, "shiftX");
            var shiftY = (DensityFunction) recordComponent(function, "shiftY");
            var shiftZ = (DensityFunction) recordComponent(function, "shiftZ");
            var xzScale = ((Number) recordComponent(function, "xzScale")).doubleValue();
            var yScale = ((Number) recordComponent(function, "yScale")).doubleValue();
            var noise = (DensityFunction.NoiseHolder) recordComponent(function, "noise");
            return new ConstrainedShiftedNoise(shiftX, shiftY, shiftZ, xzScale, yScale, noise);
        }

        @Override public double compute(DensityFunction.FunctionContext functionContext) {
            var x = functionContext.blockX() * xzScale + shiftX.compute(functionContext);
            var y = functionContext.blockY() * yScale + shiftY.compute(functionContext);
            var z = functionContext.blockZ() * xzScale + shiftZ.compute(functionContext);
            return NoiseHandler.sampleNoise(noise, x, y, z, functionContext);
        }

        @Override public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            var mappedShiftX = shiftX.mapAll(visitor);
            var mappedShiftY = shiftY.mapAll(visitor);
            var mappedShiftZ = shiftZ.mapAll(visitor);
            var mappedNoise = visitor.visitNoise(noise);
            return visitor.apply(new ConstrainedShiftedNoise(
                    mappedShiftX, mappedShiftY, mappedShiftZ, xzScale, yScale, mappedNoise));
        }

        @Override public double minValue() {
            return -maxValue();
        }

        @Override public double maxValue() {
            return noise.maxValue();
        }

        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("test-only DensityFunction wrapper");
        }
    }

    private abstract static class ConstrainedAbstractShift implements DensityFunction {
        final DensityFunction.NoiseHolder offsetNoise;

        ConstrainedAbstractShift(DensityFunction.NoiseHolder offsetNoise) {
            this.offsetNoise = offsetNoise;
        }

        @Override public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(copyWith(visitor.visitNoise(offsetNoise)));
        }

        abstract DensityFunction copyWith(DensityFunction.NoiseHolder noise);

        @Override public double minValue() {
            return -maxValue();
        }

        @Override public double maxValue() {
            return offsetNoise.maxValue() * 4.0;
        }

        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("test-only DensityFunction wrapper");
        }
    }

    private static final class ConstrainedShift extends ConstrainedAbstractShift {
        private ConstrainedShift(DensityFunction.NoiseHolder offsetNoise) {
            super(offsetNoise);
        }

        static DensityFunction wrap(DensityFunction function) {
            var offsetNoise =
                    (DensityFunction.NoiseHolder) recordComponent(function, "offsetNoise");
            return new ConstrainedShift(offsetNoise);
        }

        @Override public double compute(DensityFunction.FunctionContext functionContext) {
            var x = functionContext.blockX() * 0.25;
            var y = functionContext.blockY() * 0.25;
            var z = functionContext.blockZ() * 0.25;
            return NoiseHandler.sampleNoise(offsetNoise, x, y, z, functionContext) * 4.0;
        }

        @Override DensityFunction copyWith(DensityFunction.NoiseHolder noise) {
            return new ConstrainedShift(noise);
        }
    }

    private static final class ConstrainedShiftA extends ConstrainedAbstractShift {
        private ConstrainedShiftA(DensityFunction.NoiseHolder offsetNoise) {
            super(offsetNoise);
        }

        static DensityFunction wrap(DensityFunction function) {
            var offsetNoise =
                    (DensityFunction.NoiseHolder) recordComponent(function, "offsetNoise");
            return new ConstrainedShiftA(offsetNoise);
        }

        @Override public double compute(DensityFunction.FunctionContext functionContext) {
            var x = functionContext.blockX() * 0.25;
            var z = functionContext.blockZ() * 0.25;
            return NoiseHandler.sampleNoise(offsetNoise, x, 0.0, z, functionContext) * 4.0;
        }

        @Override DensityFunction copyWith(DensityFunction.NoiseHolder noise) {
            return new ConstrainedShiftA(noise);
        }
    }

    private static final class ConstrainedShiftB extends ConstrainedAbstractShift {
        private ConstrainedShiftB(DensityFunction.NoiseHolder offsetNoise) {
            super(offsetNoise);
        }

        static DensityFunction wrap(DensityFunction function) {
            var offsetNoise =
                    (DensityFunction.NoiseHolder) recordComponent(function, "offsetNoise");
            return new ConstrainedShiftB(offsetNoise);
        }

        @Override public double compute(DensityFunction.FunctionContext functionContext) {
            var x = functionContext.blockZ() * 0.25;
            var y = functionContext.blockX() * 0.25;
            return NoiseHandler.sampleNoise(offsetNoise, x, y, 0.0, functionContext) * 4.0;
        }

        @Override DensityFunction copyWith(DensityFunction.NoiseHolder noise) {
            return new ConstrainedShiftB(noise);
        }
    }

    private static RenderResult render(
            File outDir,
            String name,
            DensityFunction.NoiseHolder noise,
            long seed,
            @Nullable RegionDefinition rootDefinition)
            throws Exception {

        var context = new TestContext(seed);

        World.clear();
        if (rootDefinition != null) {
            World.register(
                    new Region(
                            "ROOT",
                            10000,
                            rootDefinition,
                            java.util.Collections.emptySet(),
                            java.util.List.of(),
                            java.util.List.of(),
                            false),
                    World.OVERWORLD);
        } else {
            World.register(World.emptyRoot("ROOT"), World.OVERWORLD);
        }

        var functionContext = new NoiseTestContext();

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        var oceanPixels = 0L;

        var start = -WORLD_SIZE / 2;
        var chunks = WORLD_SIZE / CHUNK_SIZE;

        for (var cz = 0; cz < chunks; cz++) {
            var chunkMinZ = start + cz * CHUNK_SIZE;
            var pixelBaseZ = cz * QUART_SIZE;
            for (var cx = 0; cx < chunks; cx++) {
                var chunkMinX = start + cx * CHUNK_SIZE;
                var pixelBaseX = cx * QUART_SIZE;

                NoiseChunkLookup lookup =
                        rootDefinition != null ? NoiseChunkLookup.build(context, chunkMinX, chunkMinZ) : null;

                for (var qz = 0; qz < QUART_SIZE; qz++) {
                    var blockZ = chunkMinZ + (qz << 2);
                    var iz = pixelBaseZ + qz;
                    for (var qx = 0; qx < QUART_SIZE; qx++) {
                        var blockX = chunkMinX + (qx << 2);
                        var ix = pixelBaseX + qx;

                        functionContext.set(blockX, 0, blockZ, lookup);

                        var x = blockX * 0.25;
                        var z = blockZ * 0.25;
                        var v = NoiseHandler.sampleNoise(noise, x, 0.0, z, functionContext);

                        if (!Double.isFinite(v)) v = 0.0;
                        values[ix + iz * IMG_SIZE] = v;
                        if (v < 0.0) oceanPixels++;
                    }
                }
            }
        }

        var valuesFile = name + "_values.png";
        var maskFile = name + "_ocean_mask.png";

        writeSignedGreenBlue(outDir, valuesFile, values);
        writeOceanMask(outDir, maskFile, values);

        return new RenderResult(valuesFile, maskFile, oceanPixels);
    }

    private static RenderRegionsResult renderRegions(
            File outDir, String name, DensityFunction.NoiseHolder noise, long seed, Region root) throws Exception {

        var context = new TestContext(seed);

        World.clear();
        World.register(root, World.OVERWORLD);

        var functionContext = new NoiseTestContext();

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        int[] regionIds = new int[IMG_SIZE * IMG_SIZE];

        var noOceanInteriorPixels = 0L;
        var noOceanInteriorOceanPixels = 0L;
        var onlyOceanInteriorPixels = 0L;
        var onlyOceanInteriorOceanPixels = 0L;

        var start = -WORLD_SIZE / 2;
        var chunks = WORLD_SIZE / CHUNK_SIZE;

        for (var cz = 0; cz < chunks; cz++) {
            var chunkMinZ = start + cz * CHUNK_SIZE;
            var pixelBaseZ = cz * QUART_SIZE;
            for (var cx = 0; cx < chunks; cx++) {
                var chunkMinX = start + cx * CHUNK_SIZE;
                var pixelBaseX = cx * QUART_SIZE;

                NoiseChunkLookup lookup = NoiseChunkLookup.build(context, chunkMinX, chunkMinZ);

                for (var qz = 0; qz < QUART_SIZE; qz++) {
                    var blockZ = chunkMinZ + (qz << 2);
                    var iz = pixelBaseZ + qz;
                    for (var qx = 0; qx < QUART_SIZE; qx++) {
                        var blockX = chunkMinX + (qx << 2);
                        var ix = pixelBaseX + qx;

                        var pixelIndex = ix + iz * IMG_SIZE;

                        functionContext.set(blockX, 0, blockZ, lookup);

                        var x = blockX * 0.25;
                        var z = blockZ * 0.25;
                        var v = NoiseHandler.sampleNoise(noise, x, 0.0, z, functionContext);

                        if (!Double.isFinite(v)) v = 0.0;
                        values[pixelIndex] = v;

                        var traversal = World.traverse(context, blockX, blockZ);
                        String regionName =
                                traversal != null && traversal.region != null ? traversal.region.name() : "";
                        int regionId = "NO_OCEAN".equals(regionName) ? 1 : ("ONLY_OCEAN".equals(regionName) ? 2 : 0);
                        regionIds[pixelIndex] = regionId;

                        var interior = traversal != null && traversal.edgeInfluence <= 0.0f;
                        var ocean = v < 0.0;
                        if (interior) {
                            if (regionId == 1) {
                                noOceanInteriorPixels++;
                                if (ocean) noOceanInteriorOceanPixels++;
                            } else if (regionId == 2) {
                                onlyOceanInteriorPixels++;
                                if (ocean) onlyOceanInteriorOceanPixels++;
                            }
                        }
                    }
                }
            }
        }

        var valuesFile = name + "_values.png";
        var maskFile = name + "_ocean_mask.png";
        var regionFile = name + "_regions.png";

        writeSignedGreenBlue(outDir, valuesFile, values);
        writeOceanMask(outDir, maskFile, values);
        writeRegionMap(outDir, regionFile, regionIds);

        return new RenderRegionsResult(
                valuesFile,
                maskFile,
                regionFile,
                noOceanInteriorPixels,
                noOceanInteriorOceanPixels,
                onlyOceanInteriorPixels,
                onlyOceanInteriorOceanPixels);
    }

    private static SurfaceRenderResult renderPreliminarySurfaceLevel(
            File outDir,
            String name,
            RandomState randomState,
            long seed,
            int seaLevel,
            int minY,
            int maxY,
            @Nullable RegionDefinition rootDefinition)
            throws Exception {

        var context = new TestContext(seed);

        World.clear();
        if (rootDefinition != null) {
            World.register(
                    new Region(
                            "ROOT",
                            10000,
                            rootDefinition,
                            java.util.Collections.emptySet(),
                            java.util.List.of(),
                            java.util.List.of(),
                            false),
                    World.OVERWORLD);
        } else {
            World.register(World.emptyRoot("ROOT"), World.OVERWORLD);
        }

        DensityFunction preliminarySurfaceLevel =
                constrainNoiseSampling(randomState.router().preliminarySurfaceLevel());

        var functionContext = new NoiseTestContext();

        int[] heights = new int[SURFACE_IMG_SIZE * SURFACE_IMG_SIZE];
        var belowSeaPixels = 0L;
        var minHeight = Integer.MAX_VALUE;
        var maxHeight = Integer.MIN_VALUE;

        var start = -WORLD_SIZE / 2;
        var chunks = WORLD_SIZE / CHUNK_SIZE;

        for (var cz = 0; cz < chunks; cz++) {
            var chunkMinZ = start + cz * CHUNK_SIZE;
            var pixelBaseZ = cz * SURFACE_PIXELS_PER_CHUNK;
            for (var cx = 0; cx < chunks; cx++) {
                var chunkMinX = start + cx * CHUNK_SIZE;
                var pixelBaseX = cx * SURFACE_PIXELS_PER_CHUNK;

                NoiseChunkLookup lookup =
                        rootDefinition != null ? NoiseChunkLookup.build(context, chunkMinX, chunkMinZ) : null;

                for (var pz = 0; pz < SURFACE_PIXELS_PER_CHUNK; pz++) {
                    var blockZ = chunkMinZ + pz * SURFACE_STEP;
                    var iz = pixelBaseZ + pz;
                    for (var px = 0; px < SURFACE_PIXELS_PER_CHUNK; px++) {
                        var blockX = chunkMinX + px * SURFACE_STEP;
                        var ix = pixelBaseX + px;
                        var pixelIndex = ix + iz * SURFACE_IMG_SIZE;

                        functionContext.set(blockX, 0, blockZ, lookup);

                        var surface = preliminarySurfaceLevel.compute(functionContext);
                        if (!Double.isFinite(surface)) surface = minY;
                        var height = (int) Math.floor(surface);

                        heights[pixelIndex] = height;
                        if (height < seaLevel) belowSeaPixels++;
                        if (height < minHeight) minHeight = height;
                        if (height > maxHeight) maxHeight = height;
                    }
                }
            }
        }

        var heightFile = name + "_preliminary_surface_height.png";
        var belowSeaFile = name + "_preliminary_surface_below_sea.png";

        writeHeightMap(outDir, heightFile, SURFACE_IMG_SIZE, heights, minY, maxY);
        writeBelowSeaMask(outDir, belowSeaFile, SURFACE_IMG_SIZE, heights, seaLevel);

        return new SurfaceRenderResult(heightFile, belowSeaFile, belowSeaPixels, minHeight, maxHeight);
    }

    private static SurfaceRegionsRenderResult renderPreliminarySurfaceLevelRegions(
            File outDir, String name, RandomState randomState, long seed, int seaLevel, int minY, int maxY, Region root)
            throws Exception {

        var context = new TestContext(seed);

        World.clear();
        World.register(root, World.OVERWORLD);

        DensityFunction preliminarySurfaceLevel =
                constrainNoiseSampling(randomState.router().preliminarySurfaceLevel());

        var functionContext = new NoiseTestContext();

        int[] heights = new int[SURFACE_IMG_SIZE * SURFACE_IMG_SIZE];

        var noOceanInteriorPixels = 0L;
        var noOceanInteriorBelowSea = 0L;
        var onlyOceanInteriorPixels = 0L;
        var onlyOceanInteriorBelowSea = 0L;

        var start = -WORLD_SIZE / 2;
        var chunks = WORLD_SIZE / CHUNK_SIZE;

        for (var cz = 0; cz < chunks; cz++) {
            var chunkMinZ = start + cz * CHUNK_SIZE;
            var pixelBaseZ = cz * SURFACE_PIXELS_PER_CHUNK;
            for (var cx = 0; cx < chunks; cx++) {
                var chunkMinX = start + cx * CHUNK_SIZE;
                var pixelBaseX = cx * SURFACE_PIXELS_PER_CHUNK;

                NoiseChunkLookup lookup = NoiseChunkLookup.build(context, chunkMinX, chunkMinZ);

                for (var pz = 0; pz < SURFACE_PIXELS_PER_CHUNK; pz++) {
                    var blockZ = chunkMinZ + pz * SURFACE_STEP;
                    var iz = pixelBaseZ + pz;
                    for (var px = 0; px < SURFACE_PIXELS_PER_CHUNK; px++) {
                        var blockX = chunkMinX + px * SURFACE_STEP;
                        var ix = pixelBaseX + px;

                        var pixelIndex = ix + iz * SURFACE_IMG_SIZE;

                        functionContext.set(blockX, 0, blockZ, lookup);

                        var surface = preliminarySurfaceLevel.compute(functionContext);
                        if (!Double.isFinite(surface)) surface = minY;
                        var height = (int) Math.floor(surface);
                        heights[pixelIndex] = height;

                        var traversal = World.traverse(context, blockX, blockZ);
                        String regionName =
                                traversal != null && traversal.region != null ? traversal.region.name() : "";
                        int regionId = "NO_OCEAN".equals(regionName) ? 1 : ("ONLY_OCEAN".equals(regionName) ? 2 : 0);

                        var interior = traversal != null && traversal.edgeInfluence <= 0.0f;
                        var belowSea = height < seaLevel;
                        if (interior) {
                            if (regionId == 1) {
                                noOceanInteriorPixels++;
                                if (belowSea) noOceanInteriorBelowSea++;
                            } else if (regionId == 2) {
                                onlyOceanInteriorPixels++;
                                if (belowSea) onlyOceanInteriorBelowSea++;
                            }
                        }
                    }
                }
            }
        }

        var heightFile = name + "_preliminary_surface_height.png";
        var belowSeaFile = name + "_preliminary_surface_below_sea.png";

        writeHeightMap(outDir, heightFile, SURFACE_IMG_SIZE, heights, minY, maxY);
        writeBelowSeaMask(outDir, belowSeaFile, SURFACE_IMG_SIZE, heights, seaLevel);

        return new SurfaceRegionsRenderResult(
                heightFile,
                belowSeaFile,
                noOceanInteriorPixels,
                noOceanInteriorBelowSea,
                onlyOceanInteriorPixels,
                onlyOceanInteriorBelowSea);
    }

    private static Region buildTwoRegionRoot(RegionDefinition noOcean, RegionDefinition onlyOcean) {
        var noOceanRegion = new Region(
                "NO_OCEAN",
                10_000,
                noOcean,
                java.util.Collections.emptySet(),
                java.util.List.of(),
                java.util.List.of(),
                false);
        var onlyOceanRegion = new Region(
                "ONLY_OCEAN",
                10_000,
                onlyOcean,
                java.util.Collections.emptySet(),
                java.util.List.of(),
                java.util.List.of(),
                false);

        var rootDef = RegionDefinition.builder()
                .strategy(com.terrasect.common.definition.GenerationStrategy.hex())
                .build();

        var rootRadiusBlocks = 128;
        var rootBudget = rootRadiusBlocks * rootRadiusBlocks;
        return new Region(
                "ROOT",
                rootBudget,
                rootDef,
                java.util.Collections.emptySet(),
                java.util.List.of(noOceanRegion, onlyOceanRegion),
                java.util.List.of(),
                false);
    }

    private static void writeSignedGreenBlue(File outDir, String fileName, double[] values) throws Exception {
        var img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        for (var y = 0; y < IMG_SIZE; y++) {
            for (var x = 0; x < IMG_SIZE; x++) {
                var v = values[x + y * IMG_SIZE];
                var t = Math.max(-1.0, Math.min(1.0, v));
                int green = t > 0.0 ? (int) Math.round(t * 255.0) : 0;
                int blue = t < 0.0 ? (int) Math.round(-t * 255.0) : 0;
                var rgb = (green << 8) | blue;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeRegionMap(File outDir, String fileName, int[] regionIds) throws Exception {
        var img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        for (var y = 0; y < IMG_SIZE; y++) {
            for (var x = 0; x < IMG_SIZE; x++) {
                var id = regionIds[x + y * IMG_SIZE];
                var rgb =
                        switch (id) {
                            case 1 -> 0xCC0000;
                            case 2 -> 0x00CCCC;
                            default -> 0x000000;
                        };
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeOceanMask(File outDir, String fileName, double[] values) throws Exception {
        var img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        for (var y = 0; y < IMG_SIZE; y++) {
            for (var x = 0; x < IMG_SIZE; x++) {
                var v = values[x + y * IMG_SIZE];
                int rgb = v < 0.0 ? 0x0000FF : 0x00AA00;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeHeightMap(File outDir, String fileName, int size, int[] heights, int minY, int maxY)
            throws Exception {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        var range = Math.max(1, maxY - minY);
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var height = heights[x + y * size];
                var clamped = Math.max(minY, Math.min(maxY, height));
                var gray = (int) Math.round((clamped - minY) * 255.0 / range);
                var rgb = (gray << 16) | (gray << 8) | gray;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeBelowSeaMask(File outDir, String fileName, int size, int[] heights, int seaLevel)
            throws Exception {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var height = heights[x + y * size];
                int rgb = height < seaLevel ? 0x0000FF : 0x00AA00;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private record RenderResult(String valuesFile, String maskFile, long oceanPixels) {
    }

    private record RenderRegionsResult(
            String valuesFile,
            String maskFile,
            String regionFile,
            long noOceanInteriorPixels,
            long noOceanInteriorOceanPixels,
            long onlyOceanInteriorPixels,
            long onlyOceanInteriorOceanPixels) {
    }

    private record SurfaceRenderResult(
            String heightFile, String belowSeaFile, long belowSeaPixels, int minHeight, int maxHeight) {
    }

    private record SurfaceRegionsRenderResult(
            String heightFile,
            String belowSeaFile,
            long noOceanInteriorPixels,
            long noOceanInteriorBelowSea,
            long onlyOceanInteriorPixels,
            long onlyOceanInteriorBelowSea) {
    }

    private record OceanConstraintsPage(
            String title,
            long seed,
            int step,
            int imgSize,
            int surfaceImgSize,
            int seaLevel,
            int minY,
            int maxY,
            long totalPixels,
            long totalSurfacePixels,
            List<ScenarioRow> scenarios,
            RegionsRow regions,
            List<TerrainRow> terrainRows,
            TerrainRegionsRow terrainRegions) {
    }

    private record ScenarioRow(String title, String valuesFile, String maskFile, long oceanPixels) {
    }

    private record RegionsRow(
            String valuesFile,
            String maskFile,
            String regionFile,
            long noOceanInteriorOceanPixels,
            long noOceanInteriorPixels,
            long onlyOceanInteriorOceanPixels,
            long onlyOceanInteriorPixels) {
    }

    private record TerrainRow(
            String title,
            String heightFile,
            String belowSeaFile,
            long belowSeaPixels,
            int minHeight,
            int maxHeight) {
    }

    private record TerrainRegionsRow(
            String heightFile,
            String belowSeaFile,
            String regionFile,
            long noOceanInteriorBelowSea,
            long noOceanInteriorPixels,
            long onlyOceanInteriorBelowSea,
            long onlyOceanInteriorPixels) {
    }

    private static final class TestContext implements Context {
        private final long seed;

        private TestContext(long seed) {
            this.seed = seed;
        }

        @Override public long getSeed() {
            return seed;
        }

        @Override public long getInfluence(int x, int z) {
            return 0L;
        }
    }

    private static final class NoiseTestContext implements DensityFunction.FunctionContext, NoiseChunkAccessor {
        private int blockX;
        private int blockY;
        private int blockZ;
        private @Nullable NoiseChunkLookup lookup;

        void set(int blockX, int blockY, int blockZ, @Nullable NoiseChunkLookup lookup) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.lookup = lookup;
        }

        @Override public int blockX() {
            return blockX;
        }

        @Override public int blockY() {
            return blockY;
        }

        @Override public int blockZ() {
            return blockZ;
        }

        @Override public @Nullable NoiseChunkLookup terrasect$getNoiseLookup() {
            return lookup;
        }
    }
}
