package com.terrasect.common.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.terrasect.common.testing.SnapshotHashes;
import com.terrasect.common.util.MutablePointContext;
import de.skuzzle.test.snapshots.Snapshot;
import com.terrasect.common.testing.SnapshotTests;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import net.minecraft.world.level.levelgen.NoiseSettings;
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

    @BeforeAll
    static void setupMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void writesOverworldNoiseRouterSnapshots(Snapshot snapshot) throws Exception {
        long seed = 12345L;
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = lookup.lookupOrThrow(Registries.NOISE);

        NoiseGeneratorSettings settings;
        try {
            settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                    .value();
        } catch (Exception e) {
            settings = NoiseGeneratorSettings.dummy();
        }

        RandomState randomState = RandomState.create(settings, noiseParams, seed);
        NoiseRouter router = randomState.router();

        HolderGetter<DensityFunction> densityFunctions = lookup.lookupOrThrow(Registries.DENSITY_FUNCTION);
        NoiseSettings noiseSettings = settings.noiseSettings();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height() - 1;
        int seaLevel = settings.seaLevel();

        File outDir = new File("build/test-snapshots/noise-router/overworld");
        Files.createDirectories(outDir.toPath());

        Map<String, DensityFunction> functions = collectDensityFunctions(router);
        assertTrue(!functions.isEmpty(), "expected NoiseRouter density functions");

        Map<String, DensityFunction> sources = new LinkedHashMap<>();
        sources.put(
                "source_overworld_offset",
                wireForRandomState(
                        densityFunctions.getOrThrow(NoiseRouterData.OFFSET).value(), randomState, settings, seed));
        sources.put(
                "source_overworld_factor",
                wireForRandomState(
                        densityFunctions.getOrThrow(NoiseRouterData.FACTOR).value(), randomState, settings, seed));

        MutablePointContext point = new MutablePointContext();

        StringBuilder index = new StringBuilder(32 * 1024);
        index.append("<!doctype html>\n<html><head><meta charset=\"utf-8\">")
                .append("<title>Vanilla NoiseRouter Snapshots</title>")
                .append("<style>")
                .append(
                        "body{font-family:sans-serif}table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:6px;vertical-align:top}")
                .append("img{image-rendering:pixelated}")
                .append("a{color:inherit}a:hover{text-decoration:underline}")
                .append("ul.deps{margin:4px 0;padding-left:18px}")
                .append("div.preset{margin:6px 0}")
                .append("</style></head><body>\n");
        index.append("<h1>Vanilla NoiseRouter Snapshots</h1>\n");
        index.append("<p>seed=")
                .append(seed)
                .append(" step=")
                .append(STEP)
                .append(" size=")
                .append(IMG_SIZE)
                .append(" y=")
                .append(seaLevel)
                .append(" minY=")
                .append(minY)
                .append(" maxY=")
                .append(maxY)
                .append("</p>\n");
        index.append(
                "<p><b>NoiseRouter</b> = the final wired router used by worldgen (from <code>RandomState.router()</code>).</p>\n");
        index.append(
                "<p><b>NoiseRouterData density functions</b> = the registered building blocks (splines, caches, noise sources) used to construct generator settings.</p>\n");
        index.append(
                        "<p><b>Note:</b> Overworld <code>depth</code> is derived from <code>overworld/offset</code> (plus a Y-gradient). ")
                .append(
                        "<code>preliminary_surface_level</code> uses <code>overworld/offset</code> and <code>overworld/factor</code>, ")
                .append("so similarity between them is expected.</p>\n");

        index.append("<h2>NoiseRouter Outputs</h2>\n");
        index.append("<table><tr><th>Noise</th><th>XZ @ y=")
                .append(seaLevel)
                .append("</th><th>YZ @ x=0</th><th>Stats</th></tr>\n");

        int writtenImages = 0;
        int varyingImages = 0;
        for (var entry : functions.entrySet()) {
            String name = entry.getKey();
            DensityFunction function = entry.getValue();

            String xzFile = name + "_xz_y" + seaLevel + ".png";
            String yzFile = name + "_yz_x0.png";

            RenderStats xz = renderXZ(outDir, xzFile, function, point, seaLevel);
            RenderStats yz = renderYZ(outDir, yzFile, function, point, minY, maxY);
            writtenImages += 2;
            if (xz.max > xz.min) varyingImages++;
            if (yz.max > yz.min) varyingImages++;

            index.append("<tr><td><code>")
                    .append(name)
                    .append("</code></td>")
                    .append("<td><img width=\"")
                    .append(IMG_SIZE)
                    .append("\" height=\"")
                    .append(IMG_SIZE)
                    .append("\" src=\"")
                    .append(xzFile)
                    .append("\"></td>")
                    .append("<td><img width=\"")
                    .append(IMG_SIZE)
                    .append("\" height=\"")
                    .append(IMG_SIZE)
                    .append("\" src=\"")
                    .append(yzFile)
                    .append("\"></td>")
                    .append("<td>")
                    .append("xz[min=")
                    .append(format(xz.min))
                    .append(", max=")
                    .append(format(xz.max))
                    .append("]<br>")
                    .append("yz[min=")
                    .append(format(yz.min))
                    .append(", max=")
                    .append(format(yz.max))
                    .append("]<br>")
                    .append("theoretical[min=")
                    .append(format(function.minValue()))
                    .append(", max=")
                    .append(format(function.maxValue()))
                    .append("]<br>")
                    .append("xzDigest=")
                    .append(xz.digest)
                    .append("<br>")
                    .append("yzDigest=")
                    .append(yz.digest)
                    .append("</td></tr>\n");
        }

        index.append("<tr><th colspan=\"4\">Sources (density function registry)</th></tr>\n");
        for (var entry : sources.entrySet()) {
            String name = entry.getKey();
            DensityFunction function = entry.getValue();

            String xzFile = name + "_xz_y" + seaLevel + ".png";
            String yzFile = name + "_yz_x0.png";

            RenderStats xz = renderXZ(outDir, xzFile, function, point, seaLevel);
            RenderStats yz = renderYZ(outDir, yzFile, function, point, minY, maxY);
            writtenImages += 2;
            if (xz.max > xz.min) varyingImages++;
            if (yz.max > yz.min) varyingImages++;

            index.append("<tr><td><code>")
                    .append(name)
                    .append("</code></td>")
                    .append("<td><img width=\"")
                    .append(IMG_SIZE)
                    .append("\" height=\"")
                    .append(IMG_SIZE)
                    .append("\" src=\"")
                    .append(xzFile)
                    .append("\"></td>")
                    .append("<td><img width=\"")
                    .append(IMG_SIZE)
                    .append("\" height=\"")
                    .append(IMG_SIZE)
                    .append("\" src=\"")
                    .append(yzFile)
                    .append("\"></td>")
                    .append("<td>")
                    .append("xz[min=")
                    .append(format(xz.min))
                    .append(", max=")
                    .append(format(xz.max))
                    .append("]<br>")
                    .append("yz[min=")
                    .append(format(yz.min))
                    .append(", max=")
                    .append(format(yz.max))
                    .append("]<br>")
                    .append("theoretical[min=")
                    .append(format(function.minValue()))
                    .append(", max=")
                    .append(format(function.maxValue()))
                    .append("]<br>")
                    .append("xzDigest=")
                    .append(xz.digest)
                    .append("<br>")
                    .append("yzDigest=")
                    .append(yz.digest)
                    .append("</td></tr>\n");
        }

        index.append("</table>\n");

        assertEquals((functions.size() + sources.size()) * 2, writtenImages, "expected 2 images per NoiseRouter entry");
        assertTrue(varyingImages > 0, "expected at least one varying noise slice");

        File densityOutDir = new File(outDir, "density-functions");
        int densityImages =
                appendNoiseRouterDataDensityFunctionSnapshots(index, densityOutDir, lookup, noiseParams, seed);
        assertTrue(densityImages > 0, "expected at least one density function snapshot image");

        index.append("</body></html>\n");
        String indexHtml = index.toString();
        String indexDigest = SnapshotHashes.sha256Hex(indexHtml);
        snapshot.assertThat(indexDigest).asText().matchesSnapshotText();
        Files.writeString(outDir.toPath().resolve("index.html"), indexHtml, StandardCharsets.UTF_8);
        System.out.println("Wrote combined noise snapshot report to: " + outDir.getAbsolutePath());
    }

    private static Map<String, DensityFunction> collectDensityFunctions(NoiseRouter router) {
        Map<String, DensityFunction> out = new LinkedHashMap<>();
        for (RecordComponent component : NoiseRouter.class.getRecordComponents()) {
            if (component.getType() != DensityFunction.class) continue;
            try {
                DensityFunction function =
                        (DensityFunction) component.getAccessor().invoke(router);
                out.put(component.getName(), function);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read NoiseRouter component: " + component.getName(), e);
            }
        }
        return out;
    }

    private static int appendNoiseRouterDataDensityFunctionSnapshots(
            StringBuilder index,
            File outDir,
            HolderLookup.Provider lookup,
            HolderGetter<NormalNoise.NoiseParameters> noiseParams,
            long seed)
            throws IOException {

        Files.createDirectories(outDir.toPath());
        Files.deleteIfExists(outDir.toPath().resolve("index.html"));

        HolderGetter<DensityFunction> densityFunctions = lookup.lookupOrThrow(Registries.DENSITY_FUNCTION);

        NoiseGeneratorSettings overworldSettings = resolveSettings(lookup, NoiseGeneratorSettings.OVERWORLD);
        NoiseGeneratorSettings netherSettings = resolveSettings(lookup, NoiseGeneratorSettings.NETHER);
        NoiseGeneratorSettings endSettings = resolveSettings(lookup, NoiseGeneratorSettings.END);

        Preset overworld = Preset.from("overworld", overworldSettings, noiseParams, seed);
        Preset nether = Preset.from("nether", netherSettings, noiseParams, seed);
        Preset end = Preset.from("end", endSettings, noiseParams, seed);

        Map<String, Preset> presets = Map.of(
                overworld.name, overworld,
                nether.name, nether,
                end.name, end);

        List<DensityKeyEntry> keys = collectNoiseRouterDataKeys();
        assertTrue(!keys.isEmpty(), "expected NoiseRouterData density function keys");

        Map<String, DensityKeyMeta> metas = new LinkedHashMap<>();
        for (DensityKeyEntry entry : keys) {
            String id = entry.key.identifier().toString();
            DensityFunction raw = densityFunctions.getOrThrow(entry.key).value();
            DirectDeps deps = analyzeDirectDependencies(raw);
            String presetName = presetFor(entry.key.identifier());
            metas.put(id, new DensityKeyMeta(entry, presetName, deps));
        }

        Set<String> allNoiseKeys = new TreeSet<>();
        Map<String, Set<String>> noiseToPresets = new LinkedHashMap<>();
        for (DensityKeyMeta meta : metas.values()) {
            allNoiseKeys.addAll(meta.deps.noiseKeys);
            for (String noiseKey : meta.deps.noiseKeys) {
                noiseToPresets
                        .computeIfAbsent(noiseKey, ignored -> new TreeSet<>())
                        .add(meta.presetName);
            }
        }

        File rootDir = outDir.getParentFile() != null ? outDir.getParentFile() : outDir;
        File noiseOutDir = new File(rootDir, "noise");
        int noiseImages = appendNoiseParameterSnapshots(
                index, noiseOutDir, allNoiseKeys, noiseToPresets, presets, noiseParams, seed);
        if (!allNoiseKeys.isEmpty()) {
            int expectedNoiseImages = 0;
            for (String noiseKey : allNoiseKeys) {
                expectedNoiseImages += 2
                        * noiseToPresets
                                .getOrDefault(noiseKey, Set.of("overworld"))
                                .size();
            }
            assertEquals(expectedNoiseImages, noiseImages, "expected 2 images per (noise key, preset)");
        }

        List<String> orderedIds = topologicalSort(metas);
        Map<String, Integer> levels = computeLevels(orderedIds, metas);

        MutablePointContext point = new MutablePointContext();

        index.append("<h2>NoiseRouterData Density Functions</h2>\n");
        index.append("<p>Rows are ordered by direct dependency depth (best-effort topo sort) so roots appear first. ")
                .append("Images are written under <code>density-functions/</code>.</p>\n");

        index.append("<table><tr>")
                .append("<th>Level</th>")
                .append("<th>Key</th>")
                .append("<th>Preset</th>")
                .append("<th>XZ</th>")
                .append("<th>YZ</th>")
                .append("<th>Deps</th>")
                .append("</tr>\n");

        int writtenImages = 0;
        for (String id : orderedIds) {
            DensityKeyMeta meta = metas.get(id);
            if (meta == null) continue;

            Preset preset = presets.getOrDefault(meta.presetName, overworld);
            DensityFunction raw = densityFunctions.getOrThrow(meta.entry.key).value();
            DensityFunction wired = wireForRandomState(raw, preset.randomState, preset.settings, seed);

            String anchor = densityFunctionAnchorId(meta.entry.key.identifier());
            String base = meta.entry.key.identifier().toDebugFileName();
            String xzFile = base + "_xz_y" + preset.xzY + ".png";
            String yzFile = base + "_yz_x0.png";
            String relXzFile = "density-functions/" + xzFile;
            String relYzFile = "density-functions/" + yzFile;

            RenderStats xz = renderXZ(outDir, xzFile, wired, point, preset.xzY);
            RenderStats yz = renderYZ(outDir, yzFile, wired, point, preset.minY, preset.maxY);
            writtenImages += 2;

            int level = levels.getOrDefault(id, 0);
            index.append("<tr id=\"")
                    .append(escapeHtml(anchor))
                    .append("\">")
                    .append("<td>")
                    .append(level)
                    .append("</td>")
                    .append("<td><code>")
                    .append(escapeHtml(meta.entry.fieldName))
                    .append("</code><br><code>")
                    .append("<a href=\"#")
                    .append(escapeHtml(anchor))
                    .append("\">")
                    .append(escapeHtml(id))
                    .append("</a></code></td>")
                    .append("<td>")
                    .append(escapeHtml(preset.name))
                    .append("<br>")
                    .append("xzY=")
                    .append(preset.xzY)
                    .append("<br>")
                    .append("y=[")
                    .append(preset.minY)
                    .append("..")
                    .append(preset.maxY)
                    .append("]")
                    .append("</td>")
                    .append("<td><img width=\"")
                    .append(IMG_SIZE)
                    .append("\" height=\"")
                    .append(IMG_SIZE)
                    .append("\" src=\"")
                    .append(escapeHtml(relXzFile))
                    .append("\"></td>")
                    .append("<td><img width=\"")
                    .append(IMG_SIZE)
                    .append("\" height=\"")
                    .append(IMG_SIZE)
                    .append("\" src=\"")
                    .append(escapeHtml(relYzFile))
                    .append("\"></td>")
                    .append("<td>")
                    .append(depsList("density", meta.deps.densityFunctionKeys, metas.keySet(), "df_"))
                    .append(depsList("noise", meta.deps.noiseKeys, allNoiseKeys, "noise_"))
                    .append("<div>min/max xz=")
                    .append(escapeHtml(format(xz.min)))
                    .append("/")
                    .append(escapeHtml(format(xz.max)))
                    .append("</div>")
                    .append("<div>min/max yz=")
                    .append(escapeHtml(format(yz.min)))
                    .append("/")
                    .append(escapeHtml(format(yz.max)))
                    .append("</div>")
                    .append("</td>")
                    .append("</tr>\n");
        }

        index.append("</table>\n");
        assertEquals(keys.size() * 2, writtenImages, "expected 2 images per density function key");
        System.out.println(
                "Wrote NoiseRouterData density function snapshots (images only) to: " + outDir.getAbsolutePath());
        return writtenImages;
    }

    private static int appendNoiseParameterSnapshots(
            StringBuilder index,
            File outDir,
            Set<String> noiseKeys,
            Map<String, Set<String>> noiseToPresets,
            Map<String, Preset> presets,
            HolderGetter<NormalNoise.NoiseParameters> noiseParams,
            long seed)
            throws IOException {
        Files.createDirectories(outDir.toPath());

        index.append("<h2>Noise Parameters</h2>\n");
        index.append(
                        "<p>Rows are the underlying <code>minecraft:noise</code> entries referenced by density functions. ")
                .append("Images are written under <code>noise/</code>.</p>\n");

        index.append("<table><tr>")
                .append("<th>Key</th>")
                .append("<th>Used In</th>")
                .append("<th>XZ</th>")
                .append("<th>YZ</th>")
                .append("<th>Stats</th>")
                .append("</tr>\n");

        int writtenImages = 0;
        MutablePointContext point = new MutablePointContext();

        List<String> presetOrder = List.of("overworld", "nether", "end");
        for (String noiseKey : noiseKeys) {
            Identifier identifier = Identifier.parse(noiseKey);
            String anchor = noiseAnchorId(identifier);

            ResourceKey<NormalNoise.NoiseParameters> key = ResourceKey.create(Registries.NOISE, identifier);
            Holder<NormalNoise.NoiseParameters> holder = noiseParams.getOrThrow(key);
            DensityFunction base = DensityFunctions.noise(holder);

            Set<String> usedPresets = noiseToPresets.getOrDefault(noiseKey, Set.of("overworld"));

            StringBuilder usedIn = new StringBuilder(64);
            boolean first = true;
            for (String presetName : presetOrder) {
                if (!usedPresets.contains(presetName)) continue;
                if (!first) usedIn.append("<br>");
                usedIn.append("<code>").append(escapeHtml(presetName)).append("</code>");
                first = false;
            }

            StringBuilder xzCell = new StringBuilder(256);
            StringBuilder yzCell = new StringBuilder(256);
            StringBuilder statsCell = new StringBuilder(256);

            for (String presetName : presetOrder) {
                if (!usedPresets.contains(presetName)) continue;
                Preset preset = presets.getOrDefault(presetName, presets.get("overworld"));
                if (preset == null) continue;

                DensityFunction wired = wireForRandomState(base, preset.randomState, preset.settings, seed);

                String baseName = identifier.toDebugFileName();
                String xzFile = baseName + "_" + preset.name + "_xz_y" + preset.xzY + ".png";
                String yzFile = baseName + "_" + preset.name + "_yz_x0.png";
                String relXzFile = "noise/" + xzFile;
                String relYzFile = "noise/" + yzFile;

                RenderStats xz = renderXZ(outDir, xzFile, wired, point, preset.xzY);
                RenderStats yz = renderYZ(outDir, yzFile, wired, point, preset.minY, preset.maxY);
                writtenImages += 2;

                xzCell.append("<div class=\"preset\"><code>")
                        .append(escapeHtml(preset.name))
                        .append("</code><br>")
                        .append("<img width=\"")
                        .append(IMG_SIZE)
                        .append("\" height=\"")
                        .append(IMG_SIZE)
                        .append("\" src=\"")
                        .append(escapeHtml(relXzFile))
                        .append("\"></div>");

                yzCell.append("<div class=\"preset\"><code>")
                        .append(escapeHtml(preset.name))
                        .append("</code><br>")
                        .append("<img width=\"")
                        .append(IMG_SIZE)
                        .append("\" height=\"")
                        .append(IMG_SIZE)
                        .append("\" src=\"")
                        .append(escapeHtml(relYzFile))
                        .append("\"></div>");

                statsCell
                        .append("<div class=\"preset\"><code>")
                        .append(escapeHtml(preset.name))
                        .append("</code><br>")
                        .append("xz[min=")
                        .append(escapeHtml(format(xz.min)))
                        .append(", max=")
                        .append(escapeHtml(format(xz.max)))
                        .append("]<br>")
                        .append("yz[min=")
                        .append(escapeHtml(format(yz.min)))
                        .append(", max=")
                        .append(escapeHtml(format(yz.max)))
                        .append("]<br>")
                        .append("theoretical[min=")
                        .append(escapeHtml(format(base.minValue())))
                        .append(", max=")
                        .append(escapeHtml(format(base.maxValue())))
                        .append("]<br>")
                        .append("xzDigest=")
                        .append(escapeHtml(xz.digest))
                        .append("<br>")
                        .append("yzDigest=")
                        .append(escapeHtml(yz.digest))
                        .append("</div>");
            }

            index.append("<tr id=\"")
                    .append(escapeHtml(anchor))
                    .append("\">")
                    .append("<td><code><a href=\"#")
                    .append(escapeHtml(anchor))
                    .append("\">")
                    .append(escapeHtml(noiseKey))
                    .append("</a></code></td>")
                    .append("<td>")
                    .append(usedIn)
                    .append("</td>")
                    .append("<td>")
                    .append(xzCell)
                    .append("</td>")
                    .append("<td>")
                    .append(yzCell)
                    .append("</td>")
                    .append("<td>")
                    .append(statsCell)
                    .append("</td>")
                    .append("</tr>\n");
        }

        index.append("</table>\n");
        return writtenImages;
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
        String path = identifier.getPath();
        if (path.startsWith("nether/")) return "nether";
        if (path.startsWith("end/")) return "end";
        return "overworld";
    }

    private static List<DensityKeyEntry> collectNoiseRouterDataKeys() {
        Map<String, DensityKeyEntry> deduped = new LinkedHashMap<>();
        for (Field field : NoiseRouterData.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!ResourceKey.class.isAssignableFrom(field.getType())) continue;
            String genericType = field.getGenericType().getTypeName();
            if (!genericType.contains("DensityFunction")) continue;

            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (!(value instanceof ResourceKey<?> rawKey)) continue;
                if (!rawKey.isFor(Registries.DENSITY_FUNCTION)) continue;
                @SuppressWarnings("unchecked")
                ResourceKey<DensityFunction> key = (ResourceKey<DensityFunction>) rawKey;
                deduped.put(key.identifier().toString(), new DensityKeyEntry(field.getName(), key));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read NoiseRouterData field: " + field.getName(), e);
            }
        }
        return List.copyOf(deduped.values());
    }

    private static DirectDeps analyzeDirectDependencies(DensityFunction function) {
        Set<String> densityKeys = new TreeSet<>();
        Set<String> noiseKeys = new TreeSet<>();
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
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

        Class<?> cls = obj.getClass();
        if (cls.isArray()) {
            if (cls.getComponentType().isPrimitive()) return;
            Object[] values = (Object[]) obj;
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
                    Object value = accessor.invoke(obj);
                    walkDependencies(value, densityKeys, noiseKeys, visited);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to inspect record: " + cls.getName(), e);
                }
            }
        }
    }

    private static List<String> topologicalSort(Map<String, DensityKeyMeta> metas) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();

        Set<String> nodes = metas.keySet();
        for (String id : nodes) {
            DensityKeyMeta meta = metas.get(id);
            Set<String> deps = new LinkedHashSet<>();
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

        PriorityQueue<String> ready = new PriorityQueue<>();
        for (var entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(id);
            for (String child : dependents.getOrDefault(id, List.of())) {
                int next = indegree.computeIfPresent(child, (k, v) -> v - 1);
                if (next == 0) {
                    ready.add(child);
                }
            }
        }

        if (order.size() != nodes.size()) {
            PriorityQueue<String> remaining = new PriorityQueue<>();
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
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (String id : orderedIds) {
            DensityKeyMeta meta = metas.get(id);
            if (meta == null) continue;
            int level = 0;
            for (String dep : meta.deps.densityFunctionKeys) {
                Integer depLevel = levels.get(dep);
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

    private static String depsList(String label, Set<String> items, Set<String> linkable, String anchorPrefix) {
        if (items == null || items.isEmpty()) {
            return "<div><code>" + escapeHtml(label) + "(0)</code></div>";
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("<div><code>")
                .append(escapeHtml(label))
                .append("(")
                .append(items.size())
                .append(")</code></div>");
        sb.append("<ul class=\"deps\">");
        for (String item : items) {
            String id = Identifier.parse(item).toDebugFileName();
            String anchor = anchorPrefix + id;
            if (linkable != null && linkable.contains(item)) {
                sb.append("<li><a href=\"#")
                        .append(escapeHtml(anchor))
                        .append("\"><code>")
                        .append(escapeHtml(item))
                        .append("</code></a></li>");
            } else {
                sb.append("<li><code>").append(escapeHtml(item)).append("</code></li>");
            }
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static RenderStats renderXZ(
            File outDir, String fileName, DensityFunction function, MutablePointContext point, int y) {

        int[] xs = axisCoordinates(-WORLD_SIZE / 2, STEP, IMG_SIZE);
        int[] zs = axisCoordinates(-WORLD_SIZE / 2, STEP, IMG_SIZE);

        double[] values = new double[IMG_SIZE * IMG_SIZE];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int iz = 0; iz < IMG_SIZE; iz++) {
            int z = zs[iz];
            for (int ix = 0; ix < IMG_SIZE; ix++) {
                int x = xs[ix];
                point.set(x, y, z);
                double v = function.compute(point);
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

        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);
        MessageDigest digest = sha256();
        writeNormalizedGrayscale(img, values, min, max, digest);

        writeImage(outDir, fileName, img);
        return new RenderStats(min, max, HexFormat.of().formatHex(digest.digest()));
    }

    private static DensityFunction wireForRandomState(
            DensityFunction function, RandomState randomState, NoiseGeneratorSettings settings, long seed) {
        PositionalRandomFactory randomFactory = getRootRandomFactory(randomState);
        boolean legacy = settings.useLegacyRandomSource();

        return function.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
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

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
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
                NormalNoise noise = randomState.getOrCreateNoise(key);
                return new DensityFunction.NoiseHolder(holder, noise);
            }
        });
    }

    private static PositionalRandomFactory getRootRandomFactory(RandomState randomState) {
        try {
            Field field = RandomState.class.getDeclaredField("random");
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
            Class<?> cls =
                    Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction");
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
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        int x = 0;
        for (int iy = 0; iy < IMG_SIZE; iy++) {
            int y = ys[iy];
            for (int iz = 0; iz < IMG_SIZE; iz++) {
                int z = zs[iz];
                point.set(x, y, z);
                double v = function.compute(point);
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

        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);
        MessageDigest digest = sha256();
        writeNormalizedGrayscale(img, values, min, max, digest);

        writeImage(outDir, fileName, img);
        return new RenderStats(min, max, HexFormat.of().formatHex(digest.digest()));
    }

    private static void writeNormalizedGrayscale(
            BufferedImage img, double[] values, double min, double max, MessageDigest digest) {

        double range = max - min;
        if (range <= 0.0 || Double.isNaN(range) || Double.isInfinite(range)) {
            range = 1.0;
        }

        for (int y = 0; y < IMG_SIZE; y++) {
            for (int x = 0; x < IMG_SIZE; x++) {
                double v = values[x + y * IMG_SIZE];
                double t = (v - min) / range;
                if (t < 0.0) t = 0.0;
                if (t > 1.0) t = 1.0;
                int gray = (int) Math.round(t * 255.0);
                int rgb = (gray << 16) | (gray << 8) | gray;
                img.setRGB(x, y, rgb);
                digest.update((byte) gray);
            }
        }
    }

    private static int[] axisCoordinates(int start, int step, int size) {
        int[] out = new int[size];
        int v = start;
        for (int i = 0; i < size; i++) {
            out[i] = v;
            v += step;
        }
        return out;
    }

    private static int[] verticalCoordinates(int minY, int maxY, int size) {
        int[] out = new int[size];
        int range = maxY - minY;
        for (int i = 0; i < size; i++) {
            float t = i / (float) (size - 1);
            int y = maxY - Math.round(range * t);
            out[i] = y;
        }
        return out;
    }

    private static void writeImage(File outDir, String fileName, BufferedImage img) {
        File outFile = new File(outDir, fileName);
        try {
            boolean ok = ImageIO.write(img, "png", outFile);
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

    private record Preset(
            String name, NoiseGeneratorSettings settings, RandomState randomState, int minY, int maxY, int xzY) {
        static Preset from(
                String name,
                NoiseGeneratorSettings settings,
                HolderGetter<NormalNoise.NoiseParameters> noiseParams,
                long seed) {
            RandomState randomState = RandomState.create(settings, noiseParams, seed);
            NoiseSettings noiseSettings = settings.noiseSettings();
            int minY = noiseSettings.minY();
            int maxY = minY + noiseSettings.height() - 1;
            int xzY = settings.seaLevel();
            return new Preset(name, settings, randomState, minY, maxY, xzY);
        }
    }

    private record DensityKeyEntry(String fieldName, ResourceKey<DensityFunction> key) {}

    private record DirectDeps(Set<String> densityFunctionKeys, Set<String> noiseKeys) {}

    private record DensityKeyMeta(DensityKeyEntry entry, String presetName, DirectDeps deps) {}

    private record RenderStats(double min, double max, String digest) {}
}
