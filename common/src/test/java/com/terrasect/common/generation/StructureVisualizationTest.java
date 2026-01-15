package com.terrasect.common.generation;

import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.definition.StructureRules;
import com.terrasect.common.lookup.StructureLookup;
import com.terrasect.common.testing.SnapshotHashes;
import com.terrasect.common.testing.SnapshotHtmlReports;
import com.terrasect.common.testing.SnapshotOutputPaths;
import com.terrasect.common.testing.SnapshotTests;
import com.terrasect.common.util.MathUtils;
import de.skuzzle.test.snapshots.Snapshot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SnapshotTests
public class StructureVisualizationTest {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int CHUNK_SIZE = 16;

    @BeforeAll public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test public void visualizeStructureFiltering(Snapshot snapshot) throws IOException {
        var seed = 24681357L;

        var root = buildFilteredStructureRegions();
        World.register(root, World.OVERWORLD);

        var context = new SnapshotTest.MockStrategy(seed);
        World.initialize(context);

        var registry = BuiltInRegistries.STRUCTURE;
        var structures = collectStructures(registry);
        if (structures.structures.length == 0) {
            throw new IllegalStateException("No structures registered for visualization test");
        }

        StructureLookup lookup = StructureLookup.build(registry);

        var vanilla = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var filtered = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        var blockedCount = 0;
        var totalCount = 0;

        for (var py = 0; py < HEIGHT; py++) {
            for (var px = 0; px < WIDTH; px++) {
                var chunkX = px - WIDTH / 2;
                var chunkZ = py - HEIGHT / 2;
                var blockX = chunkX * CHUNK_SIZE;
                var blockZ = chunkZ * CHUNK_SIZE;

                var index = (int) Math.floorMod(MathUtils.hash64(seed, chunkX, chunkZ, 91), structures.count);
                Structure structure = structures.structures[index];
                String structureId = structures.ids[index];

                vanilla.setRGB(px, py, structureToColor(structureId));

                TraversalResult traversal = World.traverse(context, blockX, blockZ);
                Region region = traversal != null ? traversal.region : null;
                StructureRules rules = region != null ? region.definition().structures() : null;

                boolean allowed = lookup.isAllowed(structure, rules);
                totalCount++;
                if (!allowed) {
                    blockedCount++;
                    filtered.setRGB(px, py, 0x000000);
                } else {
                    filtered.setRGB(px, py, structureToColor(structureId));
                }
            }
        }

        var outDir = SnapshotOutputPaths.forTestClass(StructureVisualizationTest.class);
        outDir.mkdirs();

        ImageIO.write(vanilla, "png", new File(outDir, "vanilla_structures.png"));
        ImageIO.write(filtered, "png", new File(outDir, "filtered_structures.png"));
        SnapshotHtmlReports.writeIndex(
                outDir,
                "Structure Visualization",
                java.util.List.of(
                        SnapshotHtmlReports.ImageEntry.of(
                                "vanilla_structures", "vanilla_structures.png", WIDTH, HEIGHT),
                        SnapshotHtmlReports.ImageEntry.of(
                                "filtered_structures", "filtered_structures.png", WIDTH, HEIGHT)));

        var snapshotText = new StringBuilder(256);
        snapshotText.append("totalCount=").append(totalCount).append('\n');
        snapshotText.append("blockedCount=").append(blockedCount).append('\n');
        snapshotText.append("blockedRate=").append(
                String.format("%.2f", totalCount > 0 ? 100.0 * blockedCount / totalCount : 0)).append('\n');
        appendImageHash(snapshotText, "vanilla_structures", vanilla);
        appendImageHash(snapshotText, "filtered_structures", filtered);
        snapshot.assertThat(snapshotText.toString()).asText().matchesSnapshotText();
    }

    private Region buildFilteredStructureRegions() {
        var registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategy.voronoi())
                .child("SAFE_HARBORS", region -> region.radius(200).structures(structures -> structures
                        .allowNames("minecraft:shipwreck", "minecraft:ruined_portal")))
                .child("SETTLED_LANDS", region -> region.radius(260).structures(structures -> structures
                        .allowMods("minecraft")
                        .blockNames("minecraft:stronghold", "minecraft:monument")))
                .child("WILDERNESS", region -> region.radius(320).structures(structures -> structures
                        .blockNames("minecraft:village", "minecraft:mineshaft")));

        return registry.build("WORLD");
    }

    private static StructureSnapshotData collectStructures(net.minecraft.core.Registry<Structure> registry) {
        int size = registry.size();
        Structure[] structures = new Structure[size];
        String[] ids = new String[size];
        int index = 0;
        for (var key : registry.keySet()) {
            Structure structure = registry.get(key);
            if (structure == null) {
                continue;
            }
            structures[index] = structure;
            ids[index] = key.toString();
            index++;
        }
        if (index == size) {
            return new StructureSnapshotData(structures, ids, index);
        }
        Structure[] trimmedStructures = new Structure[index];
        String[] trimmedIds = new String[index];
        System.arraycopy(structures, 0, trimmedStructures, 0, index);
        System.arraycopy(ids, 0, trimmedIds, 0, index);
        return new StructureSnapshotData(trimmedStructures, trimmedIds, index);
    }

    private static int structureToColor(String structureId) {
        var hash = structureId.hashCode();
        var r = ((hash >> 16) & 0xFF);
        var g = ((hash >> 8) & 0xFF);
        var b = (hash & 0xFF);

        var brightness = (r + g + b) / 3;
        if (brightness < 80) {
            r = Math.min(255, r + 80);
            g = Math.min(255, g + 80);
            b = Math.min(255, b + 80);
        }

        return (r << 16) | (g << 8) | b;
    }

    private static void appendImageHash(StringBuilder sb, String name, BufferedImage image) {
        sb.append(name).append('=').append(SnapshotHashes.imageSha256(image)).append('\n');
    }

    private record StructureSnapshotData(Structure[] structures, String[] ids, int count) {
    }
}
