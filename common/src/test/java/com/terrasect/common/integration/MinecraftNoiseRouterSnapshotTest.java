package com.terrasect.common.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.terrasect.common.testing.SnapshotHashes;
import com.terrasect.common.testing.SnapshotOutputPaths;
import com.terrasect.common.testing.SnapshotTests;
import com.terrasect.common.util.MutablePointContext;
import de.skuzzle.test.snapshots.Snapshot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SnapshotTests
class MinecraftNoiseRouterSnapshotTest {
    private static final int IMG_SIZE = 256;
    private static final int WORLD_SIZE = 4096;
    private static final int STEP = WORLD_SIZE / IMG_SIZE;
    private static final DefaultMustacheFactory MUSTACHE_FACTORY =
            new DefaultMustacheFactory(new ClasspathResolver("templates"));
    private static final String REPORT_TITLE = "Vanilla NoiseRouter Snapshots";

    @BeforeAll static void setupMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void writesOverworldNoiseRouterSnapshots(Snapshot snapshot) throws Exception {
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
        var router = randomState.router();

        var densityFunctions = lookup.lookupOrThrow(Registries.DENSITY_FUNCTION);
        var noiseSettings = settings.noiseSettings();
        var minY = noiseSettings.minY();
        var maxY = minY + noiseSettings.height() - 1;
        var seaLevel = settings.seaLevel();

        var outDir = SnapshotOutputPaths.forTestClass(MinecraftNoiseRouterSnapshotTest.class, "overworld");
        Files.createDirectories(outDir.toPath());

        var functions = collectDensityFunctions(router);
        assertTrue(!functions.isEmpty(), "expected NoiseRouter density functions");

        var sources = new LinkedHashMap<String, DensityFunction>();
        sources.put(
                "source_overworld_offset",
                wireForRandomState(
                        densityFunctions.getOrThrow(NoiseRouterData.OFFSET).value(), randomState, settings, seed));
        sources.put(
                "source_overworld_factor",
                wireForRandomState(
                        densityFunctions.getOrThrow(NoiseRouterData.FACTOR).value(), randomState, settings, seed));

        var point = new MutablePointContext();

        var writtenImages = 0;
        var varyingImages = 0;
        var noiseRows = new ArrayList<NoiseRow>(functions.size());
        for (var entry : functions.entrySet()) {
            var name = entry.getKey();
            var function = entry.getValue();

            var xzFile = name + "_xz_y" + seaLevel + ".png";
            var yzFile = name + "_yz_x0.png";

            RenderStats xz = renderXZ(outDir, xzFile, function, point, seaLevel);
            RenderStats yz = renderYZ(outDir, yzFile, function, point, minY, maxY);
            writtenImages += 2;
            if (xz.max > xz.min) varyingImages++;
            if (yz.max > yz.min) varyingImages++;

            noiseRows.add(new NoiseRow(name, xzFile, yzFile, statsFor(function, xz, yz)));
        }

        var sourceRows = new ArrayList<NoiseRow>(sources.size());
        for (var entry : sources.entrySet()) {
            var name = entry.getKey();
            var function = entry.getValue();

            var xzFile = name + "_xz_y" + seaLevel + ".png";
            var yzFile = name + "_yz_x0.png";

            RenderStats xz = renderXZ(outDir, xzFile, function, point, seaLevel);
            RenderStats yz = renderYZ(outDir, yzFile, function, point, minY, maxY);
            writtenImages += 2;
            if (xz.max > xz.min) varyingImages++;
            if (yz.max > yz.min) varyingImages++;

            sourceRows.add(new NoiseRow(name, xzFile, yzFile, statsFor(function, xz, yz)));
        }

        assertEquals((functions.size() + sources.size()) * 2, writtenImages, "expected 2 images per NoiseRouter entry");
        assertTrue(varyingImages > 0, "expected at least one varying noise slice");

        var densityOutDir = new File(outDir, "density-functions");
        var sections = buildNoiseRouterDataSections(densityOutDir, lookup, noiseParams, seed);
        assertTrue(sections.densityImages > 0, "expected at least one density function snapshot image");

        var page = new NoiseRouterPage(
                REPORT_TITLE,
                seed,
                STEP,
                IMG_SIZE,
                seaLevel,
                minY,
                maxY,
                noiseRows,
                sourceRows,
                sections.noiseParameterRows,
                sections.densityRows);
        var indexHtml = renderTemplate("noise-router/layout.mustache", page);
        String indexDigest = SnapshotHashes.sha256Hex(indexHtml);
        snapshot.assertThat(indexDigest).asText().matchesSnapshotText();
        Files.writeString(outDir.toPath().resolve("index.html"), indexHtml, StandardCharsets.UTF_8);
        System.out.println("Wrote combined noise snapshot report to: " + outDir.getAbsolutePath());
    }

    private static Map<String, DensityFunction> collectDensityFunctions(NoiseRouter router) {
        var out = new LinkedHashMap<String, DensityFunction>();
        for (RecordComponent component : NoiseRouter.class.getRecordComponents()) {
            if (component.getType() != DensityFunction.class) continue;
            try {
                var function =
                        (DensityFunction) component.getAccessor().invoke(router);
                out.put(component.getName(), function);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read NoiseRouter component: " + component.getName(), e);
            }
        }
        return out;
    }

    private static NoiseRouterDataSections buildNoiseRouterDataSections(
            File outDir,
            HolderLookup.Provider lookup,
            HolderGetter<NormalNoise.NoiseParameters> noiseParams,
            long seed)
            throws IOException {

        Files.createDirectories(outDir.toPath());
        Files.deleteIfExists(outDir.toPath().resolve("index.html"));

        var densityFunctions = lookup.lookupOrThrow(Registries.DENSITY_FUNCTION);

        NoiseGeneratorSettings overworldSettings = resolveSettings(lookup, NoiseGeneratorSettings.OVERWORLD);
        NoiseGeneratorSettings netherSettings = resolveSettings(lookup, NoiseGeneratorSettings.NETHER);
        NoiseGeneratorSettings endSettings = resolveSettings(lookup, NoiseGeneratorSettings.END);

        Preset overworld = Preset.from("overworld", overworldSettings, noiseParams, seed);
        Preset nether = Preset.from("nether", netherSettings, noiseParams, seed);
        Preset end = Preset.from("end", endSettings, noiseParams, seed);

        var presets = Map.of(
                overworld.name, overworld,
                nether.name, nether,
                end.name, end);

        List<DensityKeyEntry> keys = collectNoiseRouterDataKeys();
        assertTrue(!keys.isEmpty(), "expected NoiseRouterData density function keys");

        var metas = new LinkedHashMap<String, DensityKeyMeta>();
        for (DensityKeyEntry entry : keys) {
            var id = entry.key.identifier().toString();
            var raw = densityFunctions.getOrThrow(entry.key).value();
            DirectDeps deps = analyzeDirectDependencies(raw);
            String presetName = presetFor(entry.key.identifier());
            metas.put(id, new DensityKeyMeta(entry, presetName, deps));
        }

        var allNoiseKeys = new TreeSet<String>();
        var noiseToPresets = new LinkedHashMap<String, Set<String>>();
        for (DensityKeyMeta meta : metas.values()) {
            allNoiseKeys.addAll(meta.deps.noiseKeys);
            for (String noiseKey : meta.deps.noiseKeys) {
                noiseToPresets
                        .computeIfAbsent(noiseKey, ignored -> new TreeSet<>())
                        .add(meta.presetName);
            }
        }

        File rootDir = outDir.getParentFile() != null ? outDir.getParentFile() : outDir;
        var noiseOutDir = new File(rootDir, "noise");
        var noiseSection = buildNoiseParameterSnapshots(
                noiseOutDir, allNoiseKeys, noiseToPresets, presets, noiseParams, seed);
        if (!allNoiseKeys.isEmpty()) {
            var expectedNoiseImages = 0;
            for (String noiseKey : allNoiseKeys) {
                expectedNoiseImages += 2
                        * noiseToPresets
                        .getOrDefault(noiseKey, Set.of("overworld"))
                        .size();
            }
            assertEquals(expectedNoiseImages, noiseSection.writtenImages, "expected 2 images per (noise key, preset)");
        }

        var orderedIds = topologicalSort(metas);
        var levels = computeLevels(orderedIds, metas);

        var point = new MutablePointContext();

        var writtenImages = 0;
        var densityRows = new ArrayList<DensityRow>(orderedIds.size());
        for (String id : orderedIds) {
            var meta = metas.get(id);
            if (meta == null) continue;

            var preset = presets.getOrDefault(meta.presetName, overworld);
            var raw = densityFunctions.getOrThrow(meta.entry.key).value();
            DensityFunction wired = wireForRandomState(raw, preset.randomState, preset.settings, seed);

            String anchor = densityFunctionAnchorId(meta.entry.key.identifier());
            var base = meta.entry.key.identifier().toDebugFileName();
            var xzFile = base + "_xz_y" + preset.xzY + ".png";
            var yzFile = base + "_yz_x0.png";
            var relXzFile = "density-functions/" + xzFile;
            var relYzFile = "density-functions/" + yzFile;

            RenderStats xz = renderXZ(outDir, xzFile, wired, point, preset.xzY);
            RenderStats yz = renderYZ(outDir, yzFile, wired, point, preset.minY, preset.maxY);
            writtenImages += 2;

            var level = levels.getOrDefault(id, 0);
            var densityItems = buildDepItems(meta.deps.densityFunctionKeys, metas.keySet(), "df_");
            var noiseItems = buildDepItems(meta.deps.noiseKeys, allNoiseKeys, "noise_");
            var deps = List.of(
                    new DepsBlock("density", densityItems.size(), !densityItems.isEmpty(), densityItems),
                    new DepsBlock("noise", noiseItems.size(), !noiseItems.isEmpty(), noiseItems));

            densityRows.add(new DensityRow(
                    anchor,
                    level,
                    meta.entry.fieldName,
                    id,
                    preset.name,
                    preset.xzY,
                    preset.minY,
                    preset.maxY,
                    relXzFile,
                    relYzFile,
                    deps,
                    format(xz.min),
                    format(xz.max),
                    format(yz.min),
                    format(yz.max)));
        }

        assertEquals(keys.size() * 2, writtenImages, "expected 2 images per density function key");
        System.out.println(
                "Wrote NoiseRouterData density function snapshots (images only) to: " + outDir.getAbsolutePath());
        return new NoiseRouterDataSections(
                noiseSection.rows,
                noiseSection.writtenImages,
                densityRows,
                writtenImages);
    }

    private static NoiseParameterSection buildNoiseParameterSnapshots(
            File outDir,
            Set<String> noiseKeys,
            Map<String, Set<String>> noiseToPresets,
            Map<String, Preset> presets,
            HolderGetter<NormalNoise.NoiseParameters> noiseParams,
            long seed)
            throws IOException {
        Files.createDirectories(outDir.toPath());

        var writtenImages = 0;
        var point = new MutablePointContext();
        var rows = new ArrayList<NoiseParamRow>(noiseKeys.size());

        var presetOrder = List.of("overworld", "nether", "end");
        for (String noiseKey : noiseKeys) {
            Identifier identifier = Identifier.parse(noiseKey);
            String anchor = noiseAnchorId(identifier);

            var key = ResourceKey.create(Registries.NOISE, identifier);
            var holder = noiseParams.getOrThrow(key);
            DensityFunction base = DensityFunctions.noise(holder);

            var usedPresets = noiseToPresets.getOrDefault(noiseKey, Set.of("overworld"));

            var usedIn = new ArrayList<NamedItem>(usedPresets.size());
            var presetSnapshots = new ArrayList<PresetSnapshot>(usedPresets.size());

            for (String presetName : presetOrder) {
                if (!usedPresets.contains(presetName)) continue;
                var preset = presets.getOrDefault(presetName, presets.get("overworld"));
                if (preset == null) continue;

                DensityFunction wired = wireForRandomState(base, preset.randomState, preset.settings, seed);

                var baseName = identifier.toDebugFileName();
                var xzFile = baseName + "_" + preset.name + "_xz_y" + preset.xzY + ".png";
                var yzFile = baseName + "_" + preset.name + "_yz_x0.png";
                var relXzFile = "noise/" + xzFile;
                var relYzFile = "noise/" + yzFile;

                RenderStats xz = renderXZ(outDir, xzFile, wired, point, preset.xzY);
                RenderStats yz = renderYZ(outDir, yzFile, wired, point, preset.minY, preset.maxY);
                writtenImages += 2;

                usedIn.add(new NamedItem(preset.name));
                presetSnapshots.add(new PresetSnapshot(preset.name, relXzFile, relYzFile, statsFor(base, xz, yz)));
            }

            rows.add(new NoiseParamRow(
                    anchor,
                    noiseKey,
                    usedIn,
                    presetSnapshots));
        }

        return new NoiseParameterSection(rows, writtenImages);
    }

    private static NoiseGeneratorSettings resolveSettings(
            HolderLookup.Provider lookup, ResourceKey<NoiseGeneratorSettings> key) {
        try {
            return lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(key)
                    .value();
        } catch (Exception e) {
            return NoiseGeneratorSettings.dummy();
        }
    }

    private static String presetFor(Identifier identifier) {
        var path = identifier.getPath();
        if (path.startsWith("nether/")) return "nether";
        if (path.startsWith("end/")) return "end";
        return "overworld";
    }

    private static List<DensityKeyEntry> collectNoiseRouterDataKeys() {
        var deduped = new LinkedHashMap<String, DensityKeyEntry>();
        for (Field field : NoiseRouterData.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!ResourceKey.class.isAssignableFrom(field.getType())) continue;
            var genericType = field.getGenericType().getTypeName();
            if (!genericType.contains("DensityFunction")) continue;

            try {
                field.setAccessible(true);
                var value = field.get(null);
                if (!(value instanceof ResourceKey<?> rawKey)) continue;
                if (!rawKey.isFor(Registries.DENSITY_FUNCTION)) continue;
                @SuppressWarnings("unchecked") ResourceKey<DensityFunction> key = (ResourceKey<DensityFunction>) rawKey;
                deduped.put(key.identifier().toString(), new DensityKeyEntry(field.getName(), key));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read NoiseRouterData field: " + field.getName(), e);
            }
        }
        return List.copyOf(deduped.values());
    }

    private static DirectDeps analyzeDirectDependencies(DensityFunction function) {
        var densityKeys = new TreeSet<String>();
        var noiseKeys = new TreeSet<String>();
        var visited = new IdentityHashMap<Object, Boolean>();
        walkDependencies(function, densityKeys, noiseKeys, visited);
        return new DirectDeps(densityKeys, noiseKeys);
    }

    private static void walkDependencies(
            Object obj, Set<String> densityKeys, Set<String> noiseKeys, IdentityHashMap<Object, Boolean> visited) {
        if (obj == null) return;
        if (visited.put(obj, Boolean.TRUE) != null) return;

        if (obj instanceof DensityFunction.NoiseHolder noiseHolder) {
            Holder<NormalNoise.NoiseParameters> holder = noiseHolder.noiseData();
            holder.unwrapKey().ifPresent(key -> noiseKeys.add(key.identifier().toString()));
            return;
        }

        if (obj instanceof Holder<?> holder) {
            holder.unwrapKey().ifPresent(key -> {
                if (key.isFor(Registries.DENSITY_FUNCTION)) {
                    densityKeys.add(key.identifier().toString());
                } else if (key.isFor(Registries.NOISE)) {
                    noiseKeys.add(key.identifier().toString());
                }
            });
            return;
        }

        if (obj instanceof Optional<?> optional) {
            optional.ifPresent(value -> walkDependencies(value, densityKeys, noiseKeys, visited));
            return;
        }

        var cls = obj.getClass();
        if (cls.isArray()) {
            if (cls.getComponentType().isPrimitive()) return;
            var values = (Object[]) obj;
            for (Object value : values) {
                walkDependencies(value, densityKeys, noiseKeys, visited);
            }
            return;
        }

        if (obj instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                walkDependencies(value, densityKeys, noiseKeys, visited);
            }
            return;
        }

        if (cls.isRecord()) {
            for (RecordComponent component : cls.getRecordComponents()) {
                try {
                    var accessor = component.getAccessor();
                    if (!accessor.canAccess(obj)) {
                        accessor.trySetAccessible();
                    }
                    var value = accessor.invoke(obj);
                    walkDependencies(value, densityKeys, noiseKeys, visited);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to inspect record: " + cls.getName(), e);
                }
            }
        }
    }

    private static List<String> topologicalSort(Map<String, DensityKeyMeta> metas) {
        var indegree = new LinkedHashMap<String, Integer>();
        var dependents = new LinkedHashMap<String, List<String>>();

        Set<String> nodes = metas.keySet();
        for (String id : nodes) {
            var meta = metas.get(id);
            var deps = new LinkedHashSet<String>();
            for (String dep : meta.deps.densityFunctionKeys) {
                if (!Objects.equals(dep, id) && nodes.contains(dep)) {
                    deps.add(dep);
                }
            }
            indegree.put(id, deps.size());
            for (String dep : deps) {
                dependents.computeIfAbsent(dep, ignored -> new ArrayList<>()).add(id);
            }
        }

        var ready = new PriorityQueue<String>();
        for (var entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        var order = new ArrayList<String>(nodes.size());
        while (!ready.isEmpty()) {
            var id = ready.poll();
            order.add(id);
            for (String child : dependents.getOrDefault(id, List.of())) {
                var next = indegree.computeIfPresent(child, (k, v) -> v - 1);
                if (next == 0) {
                    ready.add(child);
                }
            }
        }

        if (order.size() != nodes.size()) {
            var remaining = new PriorityQueue<String>();
            for (String id : nodes) {
                if (!order.contains(id)) remaining.add(id);
            }
            while (!remaining.isEmpty()) {
                order.add(remaining.poll());
            }
        }
        return order;
    }

    private static Map<String, Integer> computeLevels(List<String> orderedIds, Map<String, DensityKeyMeta> metas) {
        var levels = new LinkedHashMap<String, Integer>();
        for (String id : orderedIds) {
            var meta = metas.get(id);
            if (meta == null) continue;
            var level = 0;
            for (String dep : meta.deps.densityFunctionKeys) {
                var depLevel = levels.get(dep);
                if (depLevel != null) {
                    level = Math.max(level, depLevel + 1);
                }
            }
            levels.put(id, level);
        }
        return levels;
    }

    private static String noiseAnchorId(Identifier identifier) {
        return "noise_" + identifier.toDebugFileName();
    }

    private static String densityFunctionAnchorId(Identifier identifier) {
        return "df_" + identifier.toDebugFileName();
    }

    private static List<DepItem> buildDepItems(Set<String> items, Set<String> linkable, String anchorPrefix) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        var out = new ArrayList<DepItem>(items.size());
        for (String item : items) {
            var id = Identifier.parse(item).toDebugFileName();
            var anchor = anchorPrefix + id;
            var link = linkable != null && linkable.contains(item);
            out.add(new DepItem(item, anchor, link));
        }
        return out;
    }

    private static RenderStats renderXZ(
            File outDir, String fileName, DensityFunction function, MutablePointContext point, int y) {

        int[] xs = axisCoordinates(-WORLD_SIZE / 2, STEP, IMG_SIZE);
        int[] zs = axisCoordinates(-WORLD_SIZE / 2, STEP, IMG_SIZE);

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        var min = Double.POSITIVE_INFINITY;
        var max = Double.NEGATIVE_INFINITY;

        for (var iz = 0; iz < IMG_SIZE; iz++) {
            var z = zs[iz];
            for (var ix = 0; ix < IMG_SIZE; ix++) {
                var x = xs[ix];
                point.set(x, y, z);
                var v = function.compute(point);
                if (!Double.isFinite(v)) v = 0.0;
                values[ix + iz * IMG_SIZE] = v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = 0.0;
            max = 0.0;
        }

        var img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);
        MessageDigest digest = sha256();
        writeNormalizedGrayscale(img, values, min, max, digest);

        writeImage(outDir, fileName, img);
        return new RenderStats(min, max, HexFormat.of().formatHex(digest.digest()));
    }

    private static DensityFunction wireForRandomState(
            DensityFunction function, RandomState randomState, NoiseGeneratorSettings settings, long seed) {
        PositionalRandomFactory randomFactory = getRootRandomFactory(randomState);
        var legacy = settings.useLegacyRandomSource();

        return function.mapAll(new DensityFunction.Visitor() {
            @Override public DensityFunction apply(DensityFunction densityFunction) {
                if (densityFunction instanceof BlendedNoise blendedNoise) {
                    RandomSource randomSource;
                    if (legacy) {
                        randomSource = new LegacyRandomSource(seed);
                    } else if (randomFactory != null) {
                        randomSource = randomFactory.fromHashOf(Identifier.withDefaultNamespace("terrain"));
                    } else {
                        randomSource = new LegacyRandomSource(seed);
                    }
                    return blendedNoise.withNewRandom(randomSource);
                }
                if (isEndIslandDensityFunction(densityFunction)) {
                    return newEndIslandDensityFunction(seed);
                }
                return densityFunction;
            }

            @Override public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
                if (noiseHolder.noise() != null) {
                    return noiseHolder;
                }

                Holder<NormalNoise.NoiseParameters> holder = noiseHolder.noiseData();
                if (legacy) {
                    if (holder.is(Noises.TEMPERATURE)) {
                        NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(
                                new LegacyRandomSource(seed), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }

                    if (holder.is(Noises.VEGETATION)) {
                        NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(
                                new LegacyRandomSource(seed + 1L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }

                    if (holder.is(Noises.SHIFT) && randomFactory != null) {
                        NormalNoise normalNoise = NormalNoise.create(
                                randomFactory.fromHashOf(Noises.SHIFT.identifier()),
                                new NormalNoise.NoiseParameters(0, 0.0));
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }
                }

                var key = holder.unwrapKey().orElse(null);
                if (key == null) return noiseHolder;
                var noise = randomState.getOrCreateNoise(key);
                return new DensityFunction.NoiseHolder(holder, noise);
            }
        });
    }

    private static PositionalRandomFactory getRootRandomFactory(RandomState randomState) {
        try {
            var field = RandomState.class.getDeclaredField("random");
            field.setAccessible(true);
            return (PositionalRandomFactory) field.get(randomState);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean isEndIslandDensityFunction(DensityFunction function) {
        return function != null
                && function.getClass()
                .getName()
                .equals("net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction");
    }

    private static DensityFunction newEndIslandDensityFunction(long seed) {
        try {
            var cls = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction");
            var ctor = cls.getDeclaredConstructor(long.class);
            ctor.setAccessible(true);
            return (DensityFunction) ctor.newInstance(seed);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to construct EndIslandDensityFunction", e);
        }
    }

    private static RenderStats renderYZ(
            File outDir, String fileName, DensityFunction function, MutablePointContext point, int minY, int maxY) {

        int[] ys = verticalCoordinates(minY, maxY, IMG_SIZE);
        int[] zs = axisCoordinates(-WORLD_SIZE / 2, STEP, IMG_SIZE);

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        var min = Double.POSITIVE_INFINITY;
        var max = Double.NEGATIVE_INFINITY;

        var x = 0;
        for (var iy = 0; iy < IMG_SIZE; iy++) {
            var y = ys[iy];
            for (var iz = 0; iz < IMG_SIZE; iz++) {
                var z = zs[iz];
                point.set(x, y, z);
                var v = function.compute(point);
                if (!Double.isFinite(v)) v = 0.0;
                values[iz + iy * IMG_SIZE] = v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = 0.0;
            max = 0.0;
        }

        var img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);
        MessageDigest digest = sha256();
        writeNormalizedGrayscale(img, values, min, max, digest);

        writeImage(outDir, fileName, img);
        return new RenderStats(min, max, HexFormat.of().formatHex(digest.digest()));
    }

    private static void writeNormalizedGrayscale(
            BufferedImage img, double[] values, double min, double max, MessageDigest digest) {

        var range = max - min;
        if (range <= 0.0 || Double.isNaN(range) || Double.isInfinite(range)) {
            range = 1.0;
        }

        for (var y = 0; y < IMG_SIZE; y++) {
            for (var x = 0; x < IMG_SIZE; x++) {
                var v = values[x + y * IMG_SIZE];
                var t = (v - min) / range;
                if (t < 0.0) t = 0.0;
                if (t > 1.0) t = 1.0;
                var gray = (int) Math.round(t * 255.0);
                var rgb = (gray << 16) | (gray << 8) | gray;
                img.setRGB(x, y, rgb);
                digest.update((byte) gray);
            }
        }
    }

    private static int[] axisCoordinates(int start, int step, int size) {
        int[] out = new int[size];
        var v = start;
        for (var i = 0; i < size; i++) {
            out[i] = v;
            v += step;
        }
        return out;
    }

    private static int[] verticalCoordinates(int minY, int maxY, int size) {
        int[] out = new int[size];
        var range = maxY - minY;
        for (var i = 0; i < size; i++) {
            var t = i / (float) (size - 1);
            var y = maxY - Math.round(range * t);
            out[i] = y;
        }
        return out;
    }

    private static void writeImage(File outDir, String fileName, BufferedImage img) {
        var outFile = new File(outDir, fileName);
        try {
            var ok = ImageIO.write(img, "png", outFile);
            if (!ok) {
                throw new IOException("No ImageIO writer found for png");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write snapshot: " + outFile.getAbsolutePath(), e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256", e);
        }
    }

    private static String format(double v) {
        if (Double.isInfinite(v)) return v > 0 ? "Infinity" : "-Infinity";
        if (Double.isNaN(v)) return "NaN";
        return String.format("%.4f", v);
    }

    private static StatsRow statsFor(DensityFunction function, RenderStats xz, RenderStats yz) {
        return new StatsRow(
                format(xz.min),
                format(xz.max),
                format(yz.min),
                format(yz.max),
                format(function.minValue()),
                format(function.maxValue()),
                xz.digest,
                yz.digest);
    }

    private static String renderTemplate(String name, Object context) throws IOException {
        Mustache mustache = MUSTACHE_FACTORY.compile(name);
        var out = new StringWriter(32 * 1024);
        mustache.execute(out, context);
        return out.toString();
    }

    private record Preset(
            String name, NoiseGeneratorSettings settings, RandomState randomState, int minY, int maxY, int xzY) {
        static Preset from(
                String name,
                NoiseGeneratorSettings settings,
                HolderGetter<NormalNoise.NoiseParameters> noiseParams,
                long seed) {
            RandomState randomState = RandomState.create(settings, noiseParams, seed);
            var noiseSettings = settings.noiseSettings();
            var minY = noiseSettings.minY();
            var maxY = minY + noiseSettings.height() - 1;
            var xzY = settings.seaLevel();
            return new Preset(name, settings, randomState, minY, maxY, xzY);
        }
    }

    private record DensityKeyEntry(String fieldName, ResourceKey<DensityFunction> key) {
    }

    private record DirectDeps(Set<String> densityFunctionKeys, Set<String> noiseKeys) {
    }

    private record DensityKeyMeta(DensityKeyEntry entry, String presetName, DirectDeps deps) {
    }

    private record RenderStats(double min, double max, String digest) {
    }

    private record NoiseRouterPage(
            String title,
            long seed,
            int step,
            int imgSize,
            int seaLevel,
            int minY,
            int maxY,
            List<NoiseRow> noiseRows,
            List<NoiseRow> sourceRows,
            List<NoiseParamRow> noiseParameterRows,
            List<DensityRow> densityRows) {
    }

    private record NoiseRow(String name, String xzFile, String yzFile, StatsRow stats) {
    }

    private record NoiseParameterSection(List<NoiseParamRow> rows, int writtenImages) {
    }

    private record NoiseRouterDataSections(
            List<NoiseParamRow> noiseParameterRows,
            int noiseImages,
            List<DensityRow> densityRows,
            int densityImages) {
    }

    private record NoiseParamRow(String anchor, String key, List<NamedItem> usedIn, List<PresetSnapshot> presets) {
    }

    private record DensityRow(
            String anchor,
            int level,
            String fieldName,
            String id,
            String presetName,
            int xzY,
            int minY,
            int maxY,
            String xzFile,
            String yzFile,
            List<DepsBlock> deps,
            String xzMin,
            String xzMax,
            String yzMin,
            String yzMax) {
    }

    private record StatsRow(
            String xzMin,
            String xzMax,
            String yzMin,
            String yzMax,
            String theoreticalMin,
            String theoreticalMax,
            String xzDigest,
            String yzDigest) {
    }

    private record NamedItem(String name) {
    }

    private record PresetSnapshot(String name, String xzFile, String yzFile, StatsRow stats) {
    }

    private record DepsBlock(String label, int count, boolean hasItems, List<DepItem> items) {
    }

    private record DepItem(String name, String anchor, boolean link) {
    }
}
