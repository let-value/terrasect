package com.terrasect.common.generation;

import com.terrasect.common.compat.ResourceKeyCompat;
import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.definition.StructureRules;
import com.terrasect.common.helpers.StructureHandler;
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
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

        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        var registry = lookup.lookupOrThrow(Registries.STRUCTURE);
        var structures = collectStructures(registry);
        System.out.println("Structure count=" + structures.ids.length
                + " (registry=" + structures.hasRegistryStructures + ")");
        if (structures.ids.length == 0) {
            throw new IllegalStateException("No structures registered for visualization test");
        }

        StructureLookup structureLookup = structures.hasRegistryStructures ? StructureLookup.build(registry) : null;
        StructureSetSnapshot[] structureSets = collectStructureSets(lookup.lookupOrThrow(Registries.STRUCTURE_SET), seed);
        if (structureSets.length == 0) {
            throw new IllegalStateException("No structure sets registered for visualization test");
        }

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

                StructureSelection selection = pickStructureForChunk(seed, chunkX, chunkZ, structureSets);
                if (selection == null) {
                    vanilla.setRGB(px, py, 0x000000);
                    filtered.setRGB(px, py, 0x000000);
                    continue;
                }

                Structure structure = selection.structure;
                String structureId = selection.id;

                TraversalResult traversal = World.traverse(context, blockX, blockZ);
                Region region = traversal != null ? traversal.region : null;
                StructureRules rules = region != null ? region.definition().structures() : null;

                boolean allowed;
                if (structureLookup != null && structure != null) {
                    allowed = structureLookup.isAllowed(structure, rules);
                } else {
                    var ruleSelection = rules != null ? rules.selection() : null;
                    allowed = StructureHandler.checkStructure(ruleSelection, structureId, Set.of())
                            != StructureHandler.FilterResult.BLOCKED;
                }
                totalCount++;
                if (!allowed) {
                    blockedCount++;
                    filtered.setRGB(px, py, 0x000000);
                } else {
                    filtered.setRGB(px, py, structureToColor(structureId));
                }
                vanilla.setRGB(px, py, structureToColor(structureId));
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
                String.format(Locale.ROOT, "%.2f", totalCount > 0 ? 100.0 * blockedCount / totalCount : 0))
                .append('\n');
        appendImageHash(snapshotText, "vanilla_structures", vanilla);
        appendImageHash(snapshotText, "filtered_structures", filtered);
        snapshot.assertThat(snapshotText.toString()).asText().matchesSnapshotText();
    }

    private Region buildFilteredStructureRegions() {
        var registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategy.voronoi())
                .child("SAFE_HARBORS", region -> region.radius(200).structures(structures -> structures.allowNames(
                        "minecraft:shipwreck",
                        "minecraft:shipwreck_beached",
                        "minecraft:ruined_portal_standard",
                        "minecraft:ruined_portal_desert",
                        "minecraft:ruined_portal_jungle",
                        "minecraft:ruined_portal_swamp",
                        "minecraft:ruined_portal_mountain",
                        "minecraft:ruined_portal_ocean",
                        "minecraft:ruined_portal_nether")))
                .child("SETTLED_LANDS", region -> region.radius(260).structures(structures -> structures
                        .allowMods("minecraft")
                        .blockNames("minecraft:stronghold", "minecraft:monument")))
                .child("WILDERNESS", region -> region.radius(320).structures(structures -> structures
                        .blockNames(
                                "minecraft:village_plains",
                                "minecraft:village_desert",
                                "minecraft:village_savanna",
                                "minecraft:village_snowy",
                                "minecraft:village_taiga",
                                "minecraft:mineshaft",
                                "minecraft:mineshaft_mesa")));

        return registry.build("WORLD");
    }

    private static StructureSnapshotData collectStructures(HolderLookup.RegistryLookup<Structure> registry) {
        var holders = registry.listElements().toList();
        int size = holders.size();
        if (size == 0) {
            String[] builtinIds = collectBuiltinStructureIds();
            return new StructureSnapshotData(null, builtinIds, false);
        }

        var entries = new ArrayList<StructureEntry>(size);
        for (var holder : holders) {
            String id = holder.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
            entries.add(new StructureEntry(holder.value(), id));
        }
        entries.sort((a, b) -> a.id.compareTo(b.id));

        Structure[] structures = new Structure[size];
        String[] ids = new String[size];
        for (int i = 0; i < size; i++) {
            var entry = entries.get(i);
            structures[i] = entry.structure;
            ids[i] = entry.id;
        }
        return new StructureSnapshotData(structures, ids, true);
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

    private static StructureSetSnapshot[] collectStructureSets(
            HolderLookup.RegistryLookup<StructureSet> registry,
            long seed) {
        var holders = registry.listElements().toList();
        if (holders.isEmpty()) {
            return new StructureSetSnapshot[0];
        }

        var entries = new ArrayList<StructureSetSnapshot>(holders.size());
        for (var holder : holders) {
            var set = holder.value();
            var structures = set.structures();
            if (structures.isEmpty()) {
                continue;
            }
            String setId = holder.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown_set");
            var selectionEntries = new StructureSelection[structures.size()];
            int totalWeight = 0;
            for (int i = 0; i < structures.size(); i++) {
                var entry = structures.get(i);
                var structureHolder = entry.structure();
                String structureId = structureHolder.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
                selectionEntries[i] = new StructureSelection(structureHolder.value(), structureId, entry.weight());
                totalWeight += entry.weight();
            }

            long[] ringPositions = null;
            if (set.placement() instanceof ConcentricRingsStructurePlacement rings) {
                ringPositions = computeRingPositions(seed, rings);
            }

            entries.add(new StructureSetSnapshot(setId, set.placement(), selectionEntries, totalWeight, ringPositions));
        }

        entries.sort((a, b) -> a.id.compareTo(b.id));
        return entries.toArray(new StructureSetSnapshot[0]);
    }

    private static StructureSelection pickStructureForChunk(
            long seed,
            int chunkX,
            int chunkZ,
            StructureSetSnapshot[] sets) {
        for (StructureSetSnapshot set : sets) {
            if (!isStructureChunk(seed, chunkX, chunkZ, set)) {
                continue;
            }
            StructureSelection picked = pickStructureFromSet(seed, chunkX, chunkZ, set);
            if (picked != null) {
                return picked;
            }
        }
        return null;
    }

    private static boolean isStructureChunk(
            long seed,
            int chunkX,
            int chunkZ,
            StructureSetSnapshot set) {
        StructurePlacement placement = set.placement;
        if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
            if (!randomSpread.applyAdditionalChunkRestrictions(chunkX, chunkZ, seed)) {
                return false;
            }
            ChunkPos potential = randomSpread.getPotentialStructureChunk(seed, chunkX, chunkZ);
            return potential.getMinBlockX() == chunkX * CHUNK_SIZE
                    && potential.getMinBlockZ() == chunkZ * CHUNK_SIZE;
        }
        if (placement instanceof ConcentricRingsStructurePlacement rings) {
            if (!rings.applyAdditionalChunkRestrictions(chunkX, chunkZ, seed)) {
                return false;
            }
            return containsRingChunk(set.ringPositions, chunkX, chunkZ);
        }
        return false;
    }

    private static boolean containsRingChunk(long[] ringPositions, int chunkX, int chunkZ) {
        if (ringPositions == null || ringPositions.length == 0) {
            return false;
        }
        long target = packChunkPos(chunkX, chunkZ);
        for (long ringPos : ringPositions) {
            if (ringPos == target) {
                return true;
            }
        }
        return false;
    }

    private static StructureSelection pickStructureFromSet(
            long seed,
            int chunkX,
            int chunkZ,
            StructureSetSnapshot set) {
        if (set.totalWeight <= 0) {
            return null;
        }
        int roll = (int) Math.floorMod(
                MathUtils.hash64(seed, chunkX, chunkZ, set.selectionSalt()),
                (long) set.totalWeight);
        for (StructureSelection selection : set.selections) {
            roll -= selection.weight;
            if (roll < 0) {
                return selection;
            }
        }
        return set.selections[set.selections.length - 1];
    }

    private static long[] computeRingPositions(long seed, ConcentricRingsStructurePlacement placement) {
        int count = placement.count();
        if (count <= 0) {
            return new long[0];
        }

        int distance = placement.distance();
        int spread = placement.spread();
        long[] positions = new long[count];

        RandomSource random = RandomSource.create();
        random.setSeed(seed);

        double angle = random.nextDouble() * Math.PI * 2.0;
        int ringIndex = 0;
        int ring = 0;

        for (int i = 0; i < count; i++) {
            double radius = 4 * distance + distance * ring * 6 + (random.nextDouble() - 0.5) * (distance * 2.5);
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            positions[i] = packChunkPos(x, z);

            angle += (Math.PI * 2) / spread;
            ringIndex++;
            if (ringIndex == spread) {
                ring++;
                ringIndex = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - i);
                if (spread > 0) {
                    angle += random.nextDouble() * Math.PI * 2.0;
                }
            }
        }
        return positions;
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    private static void appendImageHash(StringBuilder sb, String name, BufferedImage image) {
        sb.append(name).append('=').append(SnapshotHashes.imageSha256(image)).append('\n');
    }

    private static String[] collectBuiltinStructureIds() {
        Field[] fields = BuiltinStructures.class.getDeclaredFields();
        List<String> ids = new ArrayList<>(fields.length);
        for (Field field : fields) {
            if (!net.minecraft.resources.ResourceKey.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value instanceof net.minecraft.resources.ResourceKey<?> key) {
                    ids.add(ResourceKeyCompat.getKeyId(key));
                }
            } catch (IllegalAccessException ignored) {
                // Ignore inaccessible fields.
            }
        }
        if (ids.isEmpty()) {
            return new String[]{
                    "minecraft:shipwreck",
                    "minecraft:shipwreck_beached",
                    "minecraft:ruined_portal_standard",
                    "minecraft:ruined_portal_desert",
                    "minecraft:ruined_portal_jungle",
                    "minecraft:ruined_portal_swamp",
                    "minecraft:ruined_portal_mountain",
                    "minecraft:ruined_portal_ocean",
                    "minecraft:ruined_portal_nether",
                    "minecraft:mineshaft",
                    "minecraft:mineshaft_mesa",
                    "minecraft:stronghold",
                    "minecraft:monument",
                    "minecraft:ancient_city",
                    "minecraft:trail_ruins",
                    "minecraft:trial_chambers",
                    "minecraft:village_plains",
                    "minecraft:village_desert",
                    "minecraft:village_savanna",
                    "minecraft:village_snowy",
                    "minecraft:village_taiga"
            };
        }

        String[] result = ids.toArray(new String[0]);
        Arrays.sort(result);
        return result;
    }

    private record StructureSnapshotData(Structure[] structures, String[] ids, boolean hasRegistryStructures) {
    }

    private record StructureEntry(Structure structure, String id) {
    }

    private record StructureSetSnapshot(
            String id,
            StructurePlacement placement,
            StructureSelection[] selections,
            int totalWeight,
            long[] ringPositions) {
        private int selectionSalt() {
            return id.hashCode();
        }
    }

    private record StructureSelection(Structure structure, String id, int weight) {
    }
}
