package com.terrasect.common.integration;

import com.terrasect.common.Context;
import com.terrasect.common.compat.NoiseChunkNoiseAccess;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionDefinition;
import com.terrasect.common.generation.World;
import com.terrasect.common.handler.NoiseHandler;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates "no ocean" and "only ocean" outcomes by constraining root noise inputs.
 *
 * <p>This does not rely on loader mixins; it exercises {@link NoiseHandler} directly using a
 * precomputed {@link NoiseHandler.NoiseChunkLookup} per chunk.
 */
class MinecraftOceanNoiseConstraintsSnapshotTest {
    private static final int IMG_SIZE = 256;
    private static final int WORLD_SIZE = 1024;
    private static final int STEP = WORLD_SIZE / IMG_SIZE; // 4 blocks per pixel (quart resolution)
    private static final int CHUNK_SIZE = 16;
    private static final int QUART_SIZE = 4;

    private static final int SURFACE_IMG_SIZE = 128;
    private static final int SURFACE_STEP = WORLD_SIZE / SURFACE_IMG_SIZE; // 8 blocks per pixel
    private static final int SURFACE_PIXELS_PER_CHUNK = CHUNK_SIZE / SURFACE_STEP;

    @BeforeAll
    static void setupMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void writesNoOceanAndOnlyOceanSnapshots() throws Exception {
        assertEquals(4, STEP, "test assumes quart-aligned pixels");
        assertEquals(8, SURFACE_STEP, "test assumes 8-block pixels for surface views");
        assertTrue(SURFACE_STEP % 4 == 0, "surface pixels must align to quart grid");
        assertEquals(2, SURFACE_PIXELS_PER_CHUNK, "surface pixels must divide chunk size");

        long seed = 12345L;

        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = lookup.lookupOrThrow(Registries.NOISE);

        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }

        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        NoiseSettings noiseSettings = settings.noiseSettings();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height() - 1;
        int seaLevel = settings.seaLevel();

        Holder<NormalNoise.NoiseParameters> continentalnessHolder = noiseParams.getOrThrow(Noises.CONTINENTALNESS);
        NormalNoise continentalnessNoise = randomState.getOrCreateNoise(Noises.CONTINENTALNESS);
        DensityFunction.NoiseHolder continentalness = new DensityFunction.NoiseHolder(continentalnessHolder, continentalnessNoise);

        File outDir = new File("build/test-snapshots/noise-constraints/ocean-control");
        Files.createDirectories(outDir.toPath());

        StringBuilder index = new StringBuilder(8 * 1024);
        index.append("<!doctype html><html><head><meta charset=\"utf-8\">")
            .append("<title>Terrasect Ocean Control (NoiseConstraints)</title>")
            .append("<style>body{font-family:sans-serif}table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:6px}code{white-space:nowrap}</style>")
            .append("</head><body>")
            .append("<h1>Terrasect Ocean Control (NoiseConstraints)</h1>")
            .append("<p>Noise sampled: <code>minecraft:continentalness</code> (scaled coords x/z * 0.25). Ocean mask uses threshold &lt; 0.</p>")
            .append("<table><tr><th>Scenario</th><th>Values</th><th>Ocean Mask</th><th>Ocean Pixels</th></tr>");

        RenderResult vanilla = render(outDir, "vanilla", continentalness, seed, null);
        indexRow(index, "Vanilla (no constraints)", vanilla);
        assertTrue(vanilla.oceanPixels > 0 && vanilla.oceanPixels < (long) IMG_SIZE * IMG_SIZE,
            "expected vanilla continentalness to contain both signs");

        RegionDefinition noOceanDef = RegionDefinition.builder()
            .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(0.05, 2.0)))
            .build();
        RenderResult noOcean = render(outDir, "no_ocean", continentalness, seed, noOceanDef);
        indexRow(index, "No ocean (clamp to +)", noOcean);
        assertEquals(0L, noOcean.oceanPixels, "expected no-ocean clamp to remove all ocean pixels");

        RegionDefinition onlyOceanDef = RegionDefinition.builder()
            .noise(n -> n.noise("minecraft:continentalness", t -> t.clamp(-2.0, -0.05)))
            .build();
        RenderResult onlyOcean = render(outDir, "only_ocean", continentalness, seed, onlyOceanDef);
        indexRow(index, "Only ocean (clamp to -)", onlyOcean);
        assertEquals((long) IMG_SIZE * IMG_SIZE, onlyOcean.oceanPixels, "expected only-ocean clamp to make all pixels ocean");

        index.append("</table>");

        // ===== Two-region demo (in one run) =====
        Region regionsRoot = buildTwoRegionRoot(noOceanDef, onlyOceanDef);
        RenderRegionsResult regions = renderRegions(outDir, "two_regions", continentalness, seed, regionsRoot);

        index.append("<h2>Two-region demo</h2>")
            .append("<p>Same clamp rules, but applied per-region with edge blending (8-block boundary zone).</p>")
            .append("<table><tr><th>Scenario</th><th>Values</th><th>Ocean Mask</th><th>Region Map</th><th>Interior Stats</th></tr>");
        index.append("<tr><td><code>NO_OCEAN</code> vs <code>ONLY_OCEAN</code></td>")
            .append("<td><img width=\"").append(IMG_SIZE).append("\" height=\"").append(IMG_SIZE).append("\" src=\"")
            .append(regions.valuesFile).append("\"></td>")
            .append("<td><img width=\"").append(IMG_SIZE).append("\" height=\"").append(IMG_SIZE).append("\" src=\"")
            .append(regions.maskFile).append("\"></td>")
            .append("<td><img width=\"").append(IMG_SIZE).append("\" height=\"").append(IMG_SIZE).append("\" src=\"")
            .append(regions.regionFile).append("\"></td>")
            .append("<td><code>")
            .append("NO_OCEAN=").append(regions.noOceanInteriorOceanPixels).append("/").append(regions.noOceanInteriorPixels)
            .append("<br>")
            .append("ONLY_OCEAN=").append(regions.onlyOceanInteriorOceanPixels).append("/").append(regions.onlyOceanInteriorPixels)
            .append("</code></td></tr>\n");
        index.append("</table>");

        assertTrue(regions.noOceanInteriorPixels > 0, "expected at least one NO_OCEAN interior sample");
        assertTrue(regions.onlyOceanInteriorPixels > 0, "expected at least one ONLY_OCEAN interior sample");
        assertEquals(0L, regions.noOceanInteriorOceanPixels, "NO_OCEAN interior should contain no ocean pixels");
        assertEquals(regions.onlyOceanInteriorPixels, regions.onlyOceanInteriorOceanPixels, "ONLY_OCEAN interior should be all ocean pixels");

        // ===== Terrain impact (preliminary surface level) =====
        index.append("<h2>Terrain impact</h2>")
            .append("<p>Computed <code>NoiseRouter.preliminary_surface_level</code> under the same constraints. ")
            .append("Below-sea mask uses <code>height &lt; seaLevel</code> (seaLevel=").append(seaLevel).append(").</p>")
            .append("<table><tr><th>Scenario</th><th>Height</th><th>Below Sea</th><th>Below Sea Pixels</th><th>Min/Max</th></tr>");

        SurfaceRenderResult vanillaSurface = renderPreliminarySurfaceLevel(outDir, "vanilla", randomState, seed, seaLevel, minY, maxY, null);
        indexSurfaceRow(index, "Vanilla (no constraints)", vanillaSurface);

        SurfaceRenderResult noOceanSurface = renderPreliminarySurfaceLevel(outDir, "no_ocean", randomState, seed, seaLevel, minY, maxY, noOceanDef);
        indexSurfaceRow(index, "No ocean (clamp to +)", noOceanSurface);

        SurfaceRenderResult onlyOceanSurface = renderPreliminarySurfaceLevel(outDir, "only_ocean", randomState, seed, seaLevel, minY, maxY, onlyOceanDef);
        indexSurfaceRow(index, "Only ocean (clamp to -)", onlyOceanSurface);

        index.append("</table>");

        SurfaceRegionsRenderResult regionsSurface = renderPreliminarySurfaceLevelRegions(outDir, "two_regions", randomState, seed, seaLevel, minY, maxY, regionsRoot);
        index.append("<h3>Two-region terrain</h3>")
            .append("<table><tr><th>Scenario</th><th>Height</th><th>Below Sea</th><th>Region Map</th><th>Interior Stats</th></tr>");
        index.append("<tr><td><code>NO_OCEAN</code> vs <code>ONLY_OCEAN</code></td>")
            .append("<td><img width=\"").append(SURFACE_IMG_SIZE).append("\" height=\"").append(SURFACE_IMG_SIZE).append("\" src=\"")
            .append(regionsSurface.heightFile).append("\"></td>")
            .append("<td><img width=\"").append(SURFACE_IMG_SIZE).append("\" height=\"").append(SURFACE_IMG_SIZE).append("\" src=\"")
            .append(regionsSurface.belowSeaFile).append("\"></td>")
            .append("<td><img width=\"").append(SURFACE_IMG_SIZE).append("\" height=\"").append(SURFACE_IMG_SIZE).append("\" src=\"")
            .append(regions.regionFile).append("\"></td>")
            .append("<td><code>")
            .append("NO_OCEAN=").append(regionsSurface.noOceanInteriorBelowSea).append("/").append(regionsSurface.noOceanInteriorPixels)
            .append("<br>")
            .append("ONLY_OCEAN=").append(regionsSurface.onlyOceanInteriorBelowSea).append("/").append(regionsSurface.onlyOceanInteriorPixels)
            .append("</code></td></tr>\n");
        index.append("</table>");

        index.append("</body></html>\n");
        Files.writeString(outDir.toPath().resolve("index.html"), index.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote ocean-control snapshot report to: " + outDir.getAbsolutePath());
    }

    private static void indexRow(StringBuilder index, String title, RenderResult result) {
        index.append("<tr><td>").append(escapeHtml(title)).append("</td>")
            .append("<td><img width=\"").append(IMG_SIZE).append("\" height=\"").append(IMG_SIZE).append("\" src=\"")
            .append(result.valuesFile).append("\"></td>")
            .append("<td><img width=\"").append(IMG_SIZE).append("\" height=\"").append(IMG_SIZE).append("\" src=\"")
            .append(result.maskFile).append("\"></td>")
            .append("<td><code>").append(result.oceanPixels).append(" / ").append((long) IMG_SIZE * IMG_SIZE).append("</code></td></tr>\n");
    }

    private static void indexSurfaceRow(StringBuilder index, String title, SurfaceRenderResult result) {
        index.append("<tr><td>").append(escapeHtml(title)).append("</td>")
            .append("<td><img width=\"").append(SURFACE_IMG_SIZE).append("\" height=\"").append(SURFACE_IMG_SIZE).append("\" src=\"")
            .append(result.heightFile).append("\"></td>")
            .append("<td><img width=\"").append(SURFACE_IMG_SIZE).append("\" height=\"").append(SURFACE_IMG_SIZE).append("\" src=\"")
            .append(result.belowSeaFile).append("\"></td>")
            .append("<td><code>").append(result.belowSeaPixels).append(" / ").append((long) SURFACE_IMG_SIZE * SURFACE_IMG_SIZE).append("</code></td>")
            .append("<td><code>").append(result.minHeight).append(" / ").append(result.maxHeight).append("</code></td>")
            .append("</tr>\n");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static DensityFunction constrainNoiseSampling(DensityFunction function) {
        return function.mapAll(new ConstrainedNoiseVisitor());
    }

    private static Object recordComponent(Object record, String name) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException(record.getClass().getName() + " is not a record");
        }

        for (RecordComponent component : components) {
            if (!name.equals(component.getName())) continue;
            Method accessor = component.getAccessor();
            try {
                accessor.setAccessible(true);
                return accessor.invoke(record);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read record component '" + name + "' from " + record.getClass().getName(), e);
            }
        }

        throw new IllegalArgumentException("Record component '" + name + "' not found on " + record.getClass().getName());
    }

    private static final class ConstrainedNoiseVisitor implements DensityFunction.Visitor {
        @Override
        public DensityFunction apply(DensityFunction function) {
            String className = function.getClass().getName();
            return switch (className) {
                case "net.minecraft.world.level.levelgen.DensityFunctions$Noise" -> ConstrainedNoise.wrap(function);
                case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise" -> ConstrainedShiftedNoise.wrap(function);
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
            DensityFunction.NoiseHolder noise = (DensityFunction.NoiseHolder) recordComponent(function, "noise");
            double xzScale = ((Number) recordComponent(function, "xzScale")).doubleValue();
            double yScale = ((Number) recordComponent(function, "yScale")).doubleValue();
            return new ConstrainedNoise(noise, xzScale, yScale);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            double x = functionContext.blockX() * xzScale;
            double y = functionContext.blockY() * yScale;
            double z = functionContext.blockZ() * xzScale;
            return NoiseHandler.sampleNoise(noise, x, y, z, functionContext);
        }

        @Override
        public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            DensityFunction.NoiseHolder visitedNoise = visitor.visitNoise(noise);
            return visitor.apply(new ConstrainedNoise(visitedNoise, xzScale, yScale));
        }

        @Override
        public double minValue() {
            return -maxValue();
        }

        @Override
        public double maxValue() {
            return noise.maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
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
            DensityFunction shiftX = (DensityFunction) recordComponent(function, "shiftX");
            DensityFunction shiftY = (DensityFunction) recordComponent(function, "shiftY");
            DensityFunction shiftZ = (DensityFunction) recordComponent(function, "shiftZ");
            double xzScale = ((Number) recordComponent(function, "xzScale")).doubleValue();
            double yScale = ((Number) recordComponent(function, "yScale")).doubleValue();
            DensityFunction.NoiseHolder noise = (DensityFunction.NoiseHolder) recordComponent(function, "noise");
            return new ConstrainedShiftedNoise(shiftX, shiftY, shiftZ, xzScale, yScale, noise);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            double x = functionContext.blockX() * xzScale + shiftX.compute(functionContext);
            double y = functionContext.blockY() * yScale + shiftY.compute(functionContext);
            double z = functionContext.blockZ() * xzScale + shiftZ.compute(functionContext);
            return NoiseHandler.sampleNoise(noise, x, y, z, functionContext);
        }

        @Override
        public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            DensityFunction mappedShiftX = shiftX.mapAll(visitor);
            DensityFunction mappedShiftY = shiftY.mapAll(visitor);
            DensityFunction mappedShiftZ = shiftZ.mapAll(visitor);
            DensityFunction.NoiseHolder mappedNoise = visitor.visitNoise(noise);
            return visitor.apply(new ConstrainedShiftedNoise(mappedShiftX, mappedShiftY, mappedShiftZ, xzScale, yScale, mappedNoise));
        }

        @Override
        public double minValue() {
            return -maxValue();
        }

        @Override
        public double maxValue() {
            return noise.maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("test-only DensityFunction wrapper");
        }
    }

    private abstract static class ConstrainedAbstractShift implements DensityFunction {
        final DensityFunction.NoiseHolder offsetNoise;

        ConstrainedAbstractShift(DensityFunction.NoiseHolder offsetNoise) {
            this.offsetNoise = offsetNoise;
        }

        @Override
        public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(copyWith(visitor.visitNoise(offsetNoise)));
        }

        abstract DensityFunction copyWith(DensityFunction.NoiseHolder noise);

        @Override
        public double minValue() {
            return -maxValue();
        }

        @Override
        public double maxValue() {
            return offsetNoise.maxValue() * 4.0;
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("test-only DensityFunction wrapper");
        }
    }

    private static final class ConstrainedShift extends ConstrainedAbstractShift {
        private ConstrainedShift(DensityFunction.NoiseHolder offsetNoise) {
            super(offsetNoise);
        }

        static DensityFunction wrap(DensityFunction function) {
            DensityFunction.NoiseHolder offsetNoise = (DensityFunction.NoiseHolder) recordComponent(function, "offsetNoise");
            return new ConstrainedShift(offsetNoise);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            double x = functionContext.blockX() * 0.25;
            double y = functionContext.blockY() * 0.25;
            double z = functionContext.blockZ() * 0.25;
            return NoiseHandler.sampleNoise(offsetNoise, x, y, z, functionContext) * 4.0;
        }

        @Override
        DensityFunction copyWith(DensityFunction.NoiseHolder noise) {
            return new ConstrainedShift(noise);
        }
    }

    private static final class ConstrainedShiftA extends ConstrainedAbstractShift {
        private ConstrainedShiftA(DensityFunction.NoiseHolder offsetNoise) {
            super(offsetNoise);
        }

        static DensityFunction wrap(DensityFunction function) {
            DensityFunction.NoiseHolder offsetNoise = (DensityFunction.NoiseHolder) recordComponent(function, "offsetNoise");
            return new ConstrainedShiftA(offsetNoise);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            double x = functionContext.blockX() * 0.25;
            double z = functionContext.blockZ() * 0.25;
            return NoiseHandler.sampleNoise(offsetNoise, x, 0.0, z, functionContext) * 4.0;
        }

        @Override
        DensityFunction copyWith(DensityFunction.NoiseHolder noise) {
            return new ConstrainedShiftA(noise);
        }
    }

    private static final class ConstrainedShiftB extends ConstrainedAbstractShift {
        private ConstrainedShiftB(DensityFunction.NoiseHolder offsetNoise) {
            super(offsetNoise);
        }

        static DensityFunction wrap(DensityFunction function) {
            DensityFunction.NoiseHolder offsetNoise = (DensityFunction.NoiseHolder) recordComponent(function, "offsetNoise");
            return new ConstrainedShiftB(offsetNoise);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            double x = functionContext.blockZ() * 0.25;
            double y = functionContext.blockX() * 0.25;
            return NoiseHandler.sampleNoise(offsetNoise, x, y, 0.0, functionContext) * 4.0;
        }

        @Override
        DensityFunction copyWith(DensityFunction.NoiseHolder noise) {
            return new ConstrainedShiftB(noise);
        }
    }

    private static RenderResult render(
            File outDir,
            String name,
            DensityFunction.NoiseHolder noise,
            long seed,
            @Nullable RegionDefinition rootDefinition) throws Exception {

        TestContext context = new TestContext(seed);

        World.clear();
        if (rootDefinition != null) {
            World.register(new Region("ROOT", 10000, rootDefinition, java.util.Collections.emptySet(), java.util.List.of(), java.util.List.of(), false), World.OVERWORLD);
        } else {
            World.register(World.emptyRoot("ROOT"), World.OVERWORLD);
        }

        NoiseTestContext functionContext = new NoiseTestContext();

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        long oceanPixels = 0;

        int start = -WORLD_SIZE / 2; // divisible by chunk size (1024/2=512)
        int chunks = WORLD_SIZE / CHUNK_SIZE; // 64

        for (int cz = 0; cz < chunks; cz++) {
            int chunkMinZ = start + cz * CHUNK_SIZE;
            int pixelBaseZ = cz * QUART_SIZE;
            for (int cx = 0; cx < chunks; cx++) {
                int chunkMinX = start + cx * CHUNK_SIZE;
                int pixelBaseX = cx * QUART_SIZE;

                NoiseHandler.NoiseChunkLookup lookup = rootDefinition != null ? NoiseHandler.buildLookup(context, chunkMinX, chunkMinZ) : null;

                for (int qz = 0; qz < QUART_SIZE; qz++) {
                    int blockZ = chunkMinZ + (qz << 2);
                    int iz = pixelBaseZ + qz;
                    for (int qx = 0; qx < QUART_SIZE; qx++) {
                        int blockX = chunkMinX + (qx << 2);
                        int ix = pixelBaseX + qx;

                        functionContext.set(blockX, 0, blockZ, lookup);

                        double x = blockX * 0.25;
                        double z = blockZ * 0.25;
                        double v = (lookup == null)
                            ? noise.getValue(x, 0.0, z)
                            : NoiseHandler.sampleNoise(noise, x, 0.0, z, functionContext);

                        if (!Double.isFinite(v)) v = 0.0;
                        values[ix + iz * IMG_SIZE] = v;
                        if (v < 0.0) oceanPixels++;
                    }
                }
            }
        }

        String valuesFile = name + "_values.png";
        String maskFile = name + "_ocean_mask.png";

        writeSignedGreenBlue(outDir, valuesFile, values);
        writeOceanMask(outDir, maskFile, values);

        return new RenderResult(valuesFile, maskFile, oceanPixels);
    }

    private static RenderRegionsResult renderRegions(
            File outDir,
            String name,
            DensityFunction.NoiseHolder noise,
            long seed,
            Region root) throws Exception {

        TestContext context = new TestContext(seed);

        World.clear();
        World.register(root, World.OVERWORLD);

        NoiseTestContext functionContext = new NoiseTestContext();

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        int[] regionIds = new int[IMG_SIZE * IMG_SIZE];

        long noOceanInteriorPixels = 0;
        long noOceanInteriorOceanPixels = 0;
        long onlyOceanInteriorPixels = 0;
        long onlyOceanInteriorOceanPixels = 0;

        int start = -WORLD_SIZE / 2;
        int chunks = WORLD_SIZE / CHUNK_SIZE;

        for (int cz = 0; cz < chunks; cz++) {
            int chunkMinZ = start + cz * CHUNK_SIZE;
            int pixelBaseZ = cz * QUART_SIZE;
            for (int cx = 0; cx < chunks; cx++) {
                int chunkMinX = start + cx * CHUNK_SIZE;
                int pixelBaseX = cx * QUART_SIZE;

                NoiseHandler.NoiseChunkLookup lookup = NoiseHandler.buildLookup(context, chunkMinX, chunkMinZ);

                for (int qz = 0; qz < QUART_SIZE; qz++) {
                    int blockZ = chunkMinZ + (qz << 2);
                    int iz = pixelBaseZ + qz;
                    for (int qx = 0; qx < QUART_SIZE; qx++) {
                        int blockX = chunkMinX + (qx << 2);
                        int ix = pixelBaseX + qx;

                        int pixelIndex = ix + iz * IMG_SIZE;

                        functionContext.set(blockX, 0, blockZ, lookup);

                        double x = blockX * 0.25;
                        double z = blockZ * 0.25;
                        double v = lookup == null
                            ? noise.getValue(x, 0.0, z)
                            : NoiseHandler.sampleNoise(noise, x, 0.0, z, functionContext);

                        if (!Double.isFinite(v)) v = 0.0;
                        values[pixelIndex] = v;

                        var traversal = World.traverse(context, blockX, blockZ);
                        String regionName = traversal != null && traversal.region != null ? traversal.region.name() : "";
                        int regionId = "NO_OCEAN".equals(regionName) ? 1 : ("ONLY_OCEAN".equals(regionName) ? 2 : 0);
                        regionIds[pixelIndex] = regionId;

                        boolean interior = traversal != null && traversal.edgeInfluence <= 0.0f;
                        boolean ocean = v < 0.0;
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

        String valuesFile = name + "_values.png";
        String maskFile = name + "_ocean_mask.png";
        String regionFile = name + "_regions.png";

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
            onlyOceanInteriorOceanPixels
        );
    }

    private static SurfaceRenderResult renderPreliminarySurfaceLevel(
            File outDir,
            String name,
            RandomState randomState,
            long seed,
            int seaLevel,
            int minY,
            int maxY,
            @Nullable RegionDefinition rootDefinition) throws Exception {

        TestContext context = new TestContext(seed);

        World.clear();
        if (rootDefinition != null) {
            World.register(new Region("ROOT", 10000, rootDefinition, java.util.Collections.emptySet(), java.util.List.of(), java.util.List.of(), false), World.OVERWORLD);
        } else {
            World.register(World.emptyRoot("ROOT"), World.OVERWORLD);
        }

        DensityFunction preliminarySurfaceLevel = constrainNoiseSampling(randomState.router().preliminarySurfaceLevel());

        NoiseTestContext functionContext = new NoiseTestContext();

        int[] heights = new int[SURFACE_IMG_SIZE * SURFACE_IMG_SIZE];
        long belowSeaPixels = 0L;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        int start = -WORLD_SIZE / 2;
        int chunks = WORLD_SIZE / CHUNK_SIZE;

        for (int cz = 0; cz < chunks; cz++) {
            int chunkMinZ = start + cz * CHUNK_SIZE;
            int pixelBaseZ = cz * SURFACE_PIXELS_PER_CHUNK;
            for (int cx = 0; cx < chunks; cx++) {
                int chunkMinX = start + cx * CHUNK_SIZE;
                int pixelBaseX = cx * SURFACE_PIXELS_PER_CHUNK;

                NoiseHandler.NoiseChunkLookup lookup = rootDefinition != null ? NoiseHandler.buildLookup(context, chunkMinX, chunkMinZ) : null;
                NoiseHandler.NoiseChunkLookup previous = NoiseHandler.setActiveLookup(lookup);
                try {
                    for (int pz = 0; pz < SURFACE_PIXELS_PER_CHUNK; pz++) {
                        int blockZ = chunkMinZ + pz * SURFACE_STEP;
                        int iz = pixelBaseZ + pz;
                        for (int px = 0; px < SURFACE_PIXELS_PER_CHUNK; px++) {
                            int blockX = chunkMinX + px * SURFACE_STEP;
                            int ix = pixelBaseX + px;
                            int pixelIndex = ix + iz * SURFACE_IMG_SIZE;

                            functionContext.set(blockX, 0, blockZ, lookup);

                            double surface = preliminarySurfaceLevel.compute(functionContext);
                            if (!Double.isFinite(surface)) surface = minY;
                            int height = (int) Math.floor(surface);

                            heights[pixelIndex] = height;
                            if (height < seaLevel) belowSeaPixels++;
                            if (height < minHeight) minHeight = height;
                            if (height > maxHeight) maxHeight = height;
                        }
                    }
                } finally {
                    NoiseHandler.restoreActiveLookup(previous);
                }
            }
        }

        String heightFile = name + "_preliminary_surface_height.png";
        String belowSeaFile = name + "_preliminary_surface_below_sea.png";

        writeHeightMap(outDir, heightFile, SURFACE_IMG_SIZE, heights, minY, maxY);
        writeBelowSeaMask(outDir, belowSeaFile, SURFACE_IMG_SIZE, heights, seaLevel);

        return new SurfaceRenderResult(heightFile, belowSeaFile, belowSeaPixels, minHeight, maxHeight);
    }

    private static SurfaceRegionsRenderResult renderPreliminarySurfaceLevelRegions(
            File outDir,
            String name,
            RandomState randomState,
            long seed,
            int seaLevel,
            int minY,
            int maxY,
            Region root) throws Exception {

        TestContext context = new TestContext(seed);

        World.clear();
        World.register(root, World.OVERWORLD);

        DensityFunction preliminarySurfaceLevel = constrainNoiseSampling(randomState.router().preliminarySurfaceLevel());

        NoiseTestContext functionContext = new NoiseTestContext();

        int[] heights = new int[SURFACE_IMG_SIZE * SURFACE_IMG_SIZE];

        long noOceanInteriorPixels = 0L;
        long noOceanInteriorBelowSea = 0L;
        long onlyOceanInteriorPixels = 0L;
        long onlyOceanInteriorBelowSea = 0L;

        int start = -WORLD_SIZE / 2;
        int chunks = WORLD_SIZE / CHUNK_SIZE;

        for (int cz = 0; cz < chunks; cz++) {
            int chunkMinZ = start + cz * CHUNK_SIZE;
            int pixelBaseZ = cz * SURFACE_PIXELS_PER_CHUNK;
            for (int cx = 0; cx < chunks; cx++) {
                int chunkMinX = start + cx * CHUNK_SIZE;
                int pixelBaseX = cx * SURFACE_PIXELS_PER_CHUNK;

                NoiseHandler.NoiseChunkLookup lookup = NoiseHandler.buildLookup(context, chunkMinX, chunkMinZ);
                NoiseHandler.NoiseChunkLookup previous = NoiseHandler.setActiveLookup(lookup);
                try {
                    for (int pz = 0; pz < SURFACE_PIXELS_PER_CHUNK; pz++) {
                        int blockZ = chunkMinZ + pz * SURFACE_STEP;
                        int iz = pixelBaseZ + pz;
                        for (int px = 0; px < SURFACE_PIXELS_PER_CHUNK; px++) {
                            int blockX = chunkMinX + px * SURFACE_STEP;
                            int ix = pixelBaseX + px;

                            int pixelIndex = ix + iz * SURFACE_IMG_SIZE;

                            functionContext.set(blockX, 0, blockZ, lookup);

                            double surface = preliminarySurfaceLevel.compute(functionContext);
                            if (!Double.isFinite(surface)) surface = minY;
                            int height = (int) Math.floor(surface);
                            heights[pixelIndex] = height;

                            var traversal = World.traverse(context, blockX, blockZ);
                            String regionName = traversal != null && traversal.region != null ? traversal.region.name() : "";
                            int regionId = "NO_OCEAN".equals(regionName) ? 1 : ("ONLY_OCEAN".equals(regionName) ? 2 : 0);

                            boolean interior = traversal != null && traversal.edgeInfluence <= 0.0f;
                            boolean belowSea = height < seaLevel;
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
                } finally {
                    NoiseHandler.restoreActiveLookup(previous);
                }
            }
        }

        String heightFile = name + "_preliminary_surface_height.png";
        String belowSeaFile = name + "_preliminary_surface_below_sea.png";

        writeHeightMap(outDir, heightFile, SURFACE_IMG_SIZE, heights, minY, maxY);
        writeBelowSeaMask(outDir, belowSeaFile, SURFACE_IMG_SIZE, heights, seaLevel);

        return new SurfaceRegionsRenderResult(
            heightFile,
            belowSeaFile,
            noOceanInteriorPixels,
            noOceanInteriorBelowSea,
            onlyOceanInteriorPixels,
            onlyOceanInteriorBelowSea
        );
    }

    private static Region buildTwoRegionRoot(RegionDefinition noOcean, RegionDefinition onlyOcean) {
        Region noOceanRegion = new Region("NO_OCEAN", 10_000, noOcean, java.util.Collections.emptySet(), java.util.List.of(), java.util.List.of(), false);
        Region onlyOceanRegion = new Region("ONLY_OCEAN", 10_000, onlyOcean, java.util.Collections.emptySet(), java.util.List.of(), java.util.List.of(), false);

        RegionDefinition rootDef = RegionDefinition.builder()
            .strategy(com.terrasect.common.definition.GenerationStrategyType.HEX)
            .build();

        int rootRadiusBlocks = 128;
        int rootBudget = rootRadiusBlocks * rootRadiusBlocks;
        return new Region("ROOT", rootBudget, rootDef, java.util.Collections.emptySet(), java.util.List.of(noOceanRegion, onlyOceanRegion), java.util.List.of(), false);
    }

    private static void writeSignedGreenBlue(File outDir, String fileName, double[] values) throws Exception {
        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        // Fixed range for comparability; saturate outside [-1, 1].
        for (int y = 0; y < IMG_SIZE; y++) {
            for (int x = 0; x < IMG_SIZE; x++) {
                double v = values[x + y * IMG_SIZE];
                double t = Math.max(-1.0, Math.min(1.0, v));
                int green = t > 0.0 ? (int) Math.round(t * 255.0) : 0;
                int blue = t < 0.0 ? (int) Math.round(-t * 255.0) : 0;
                int rgb = (green << 8) | blue;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeRegionMap(File outDir, String fileName, int[] regionIds) throws Exception {
        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        // 0 = none (black), 1 = NO_OCEAN (red), 2 = ONLY_OCEAN (cyan)
        for (int y = 0; y < IMG_SIZE; y++) {
            for (int x = 0; x < IMG_SIZE; x++) {
                int id = regionIds[x + y * IMG_SIZE];
                int rgb = switch (id) {
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
        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < IMG_SIZE; y++) {
            for (int x = 0; x < IMG_SIZE; x++) {
                double v = values[x + y * IMG_SIZE];
                int rgb = v < 0.0 ? 0x0000FF : 0x00AA00; // blue = ocean, green = land
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeHeightMap(File outDir, String fileName, int size, int[] heights, int minY, int maxY) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        int range = Math.max(1, maxY - minY);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int height = heights[x + y * size];
                int clamped = Math.max(minY, Math.min(maxY, height));
                int gray = (int) Math.round((clamped - minY) * 255.0 / range);
                int rgb = (gray << 16) | (gray << 8) | gray;
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "png", outDir.toPath().resolve(fileName).toFile());
    }

    private static void writeBelowSeaMask(File outDir, String fileName, int size, int[] heights, int seaLevel) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int height = heights[x + y * size];
                int rgb = height < seaLevel ? 0x0000FF : 0x00AA00; // blue = below sea, green = above/equal
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
            String heightFile,
            String belowSeaFile,
            long belowSeaPixels,
            int minHeight,
            int maxHeight) {
    }

    private record SurfaceRegionsRenderResult(
            String heightFile,
            String belowSeaFile,
            long noOceanInteriorPixels,
            long noOceanInteriorBelowSea,
            long onlyOceanInteriorPixels,
            long onlyOceanInteriorBelowSea) {
    }

    private static final class TestContext implements Context {
        private final long seed;

        private TestContext(long seed) {
            this.seed = seed;
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public long getInfluence(int x, int z) {
            return 0L;
        }
    }

    private static final class NoiseTestContext implements DensityFunction.FunctionContext, NoiseChunkNoiseAccess {
        private int blockX;
        private int blockY;
        private int blockZ;
        private @Nullable NoiseHandler.NoiseChunkLookup lookup;

        void set(int blockX, int blockY, int blockZ, @Nullable NoiseHandler.NoiseChunkLookup lookup) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.lookup = lookup;
        }

        @Override
        public int blockX() {
            return blockX;
        }

        @Override
        public int blockY() {
            return blockY;
        }

        @Override
        public int blockZ() {
            return blockZ;
        }

        @Override
        public @Nullable NoiseHandler.NoiseChunkLookup terrasect$getNoiseLookup() {
            return lookup;
        }

        @Override
        public void terrasect$setNoiseLookup(@Nullable NoiseHandler.NoiseChunkLookup lookup) {
            this.lookup = lookup;
        }
    }
}
