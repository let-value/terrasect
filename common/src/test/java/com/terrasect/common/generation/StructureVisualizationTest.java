package com.terrasect.common.generation;

import com.terrasect.common.compat.ResourceKeyCompat;
import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.definition.StructureRules;
import com.terrasect.common.lookup.StructureLookup;
import com.terrasect.common.testing.SnapshotHtmlReports;
import com.terrasect.common.testing.SnapshotOutputPaths;
import com.terrasect.common.util.MathUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StructureVisualizationTest {

  private static final int WIDTH = 256;
  private static final int HEIGHT = 256;
  private static final int CHUNK_SIZE = 16;

  @BeforeAll
  public static void setup() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @Test
  public void visualizeStructureFiltering() throws IOException {
    var seed = 24681357L;

    // Clear any existing state for deterministic behavior
    World.clear();

    var root = buildFilteredStructureRegions();
    World.register(root, World.OVERWORLD);

    var context = new SnapshotTest.MockStrategy(seed);
    World.initialize(context);

    HolderLookup.Provider provider = VanillaRegistries.createLookup();
    var structureSetRegistry = provider.lookupOrThrow(Registries.STRUCTURE_SET);

    // Build a StructureLookup for checking if structures are allowed
    var structureLookup = StructureLookup.build(provider.lookupOrThrow(Registries.STRUCTURE));

    // Build placement snapshots for chunk position checks
    List<Holder<StructureSet>> possibleSets =
        structureSetRegistry.listElements().map(h -> (Holder<StructureSet>) h).toList();
    var placementSnapshots = buildPlacementSnapshots(possibleSets, seed);

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

        // Find which structure would spawn at this chunk (vanilla)
        var vanillaSelection = pickStructureForChunk(seed, chunkX, chunkZ, placementSnapshots);
        if (vanillaSelection == null) {
          vanilla.setRGB(px, py, 0x000000);
          filtered.setRGB(px, py, 0x000000);
          continue;
        }

        vanilla.setRGB(px, py, structureToColor(vanillaSelection.structureId));

        // Get the region's structure rules at this location
        TraversalResult traversal = World.traverse(context, blockX, blockZ);
        Region region = traversal != null ? traversal.region : null;
        StructureRules rules = region != null ? region.definition().structures() : null;

        // Use StructureLookup.isAllowed() to check if structure is allowed (same as production)
        boolean allowed = structureLookup.isAllowed(vanillaSelection.structure, rules);

        totalCount++;
        if (!allowed) {
          blockedCount++;
          filtered.setRGB(px, py, 0x000000);
        } else {
          filtered.setRGB(px, py, structureToColor(vanillaSelection.structureId));
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

    // Assert reasonable filtering is happening
    // Due to non-deterministic registry iteration order, exact counts vary slightly
    var blockedRate = 100.0 * blockedCount / totalCount;

    // We expect significant filtering (>50%) with our restrictive rules
    if (blockedRate < 50.0 || blockedRate > 99.0) {
      throw new AssertionError(
          String.format(
              "Expected blocked rate between 50%% and 99%%, got %.2f%% (blocked=%d, total=%d)",
              blockedRate, blockedCount, totalCount));
    }

    System.out.printf(
        "Structure filtering: %d/%d blocked (%.2f%%)%n", blockedCount, totalCount, blockedRate);
  }

  private Region buildFilteredStructureRegions() {
    // Radii are in blocks. At 256x256 chunks (each pixel = 1 chunk = 16 blocks),
    // total area is ~4096 blocks from center. Scale radii to cover meaningful area.
    // Use hex strategy for deterministic behavior in tests.
    var registry = new RegionRegistry();
    registry
        .region("WORLD")
        .strategy(GenerationStrategy.hex())
        .child(
            "SAFE_HARBORS",
            region ->
                region
                    .radius(500)
                    .structures(
                        structures ->
                            structures.allowNames(
                                "minecraft:shipwreck",
                                "minecraft:shipwreck_beached",
                                "minecraft:ruined_portal_standard",
                                "minecraft:ruined_portal_desert",
                                "minecraft:ruined_portal_jungle",
                                "minecraft:ruined_portal_swamp",
                                "minecraft:ruined_portal_mountain",
                                "minecraft:ruined_portal_ocean",
                                "minecraft:ruined_portal_nether")))
        .child(
            "SETTLED_LANDS",
            region ->
                region
                    .radius(1200)
                    .structures(
                        structures ->
                            structures
                                .allowMods("minecraft")
                                .blockNames("minecraft:stronghold", "minecraft:monument")))
        .child(
            "WILDERNESS",
            region ->
                region
                    .radius(2000)
                    .structures(
                        structures ->
                            structures.blockNames(
                                "minecraft:village_plains",
                                "minecraft:village_desert",
                                "minecraft:village_savanna",
                                "minecraft:village_snowy",
                                "minecraft:village_taiga",
                                "minecraft:mineshaft",
                                "minecraft:mineshaft_mesa")));

    return registry.build("WORLD");
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

  // --- Placement snapshot helpers (for determining which chunk has structures) ---

  private static List<PlacementSnapshot> buildPlacementSnapshots(
      List<Holder<StructureSet>> sets, long seed) {
    var snapshots = new ArrayList<PlacementSnapshot>(sets.size());
    for (var holder : sets) {
      var set = holder.value();
      if (set.structures().isEmpty()) {
        continue;
      }

      var setId = holder.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown_set");
      long[] ringPositions = null;
      if (set.placement() instanceof ConcentricRingsStructurePlacement rings) {
        ringPositions = computeRingPositions(seed, rings);
      }

      snapshots.add(new PlacementSnapshot(set, setId, ringPositions));
    }
    // Sort by set ID for deterministic ordering
    snapshots.sort((a, b) -> a.setId.compareTo(b.setId));
    return snapshots;
  }

  private static StructureSelection pickStructureForChunk(
      long seed, int chunkX, int chunkZ, List<PlacementSnapshot> snapshots) {
    for (var snapshot : snapshots) {
      if (!isStructureChunk(seed, chunkX, chunkZ, snapshot)) {
        continue;
      }
      var picked = pickStructureFromSet(seed, chunkX, chunkZ, snapshot);
      if (picked != null) {
        return picked;
      }
    }
    return null;
  }

  private static boolean isStructureChunk(
      long seed, int chunkX, int chunkZ, PlacementSnapshot snapshot) {
    StructurePlacement placement = snapshot.set.placement();
    if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
      if (!randomSpread.applyAdditionalChunkRestrictions(chunkX, chunkZ, seed)) {
        return false;
      }
      var potential = randomSpread.getPotentialStructureChunk(seed, chunkX, chunkZ);
      return potential.getMinBlockX() == chunkX * CHUNK_SIZE
          && potential.getMinBlockZ() == chunkZ * CHUNK_SIZE;
    }
    if (placement instanceof ConcentricRingsStructurePlacement rings) {
      if (!rings.applyAdditionalChunkRestrictions(chunkX, chunkZ, seed)) {
        return false;
      }
      return containsRingChunk(snapshot.ringPositions, chunkX, chunkZ);
    }
    return false;
  }

  private static boolean containsRingChunk(long[] ringPositions, int chunkX, int chunkZ) {
    if (ringPositions == null || ringPositions.length == 0) {
      return false;
    }
    var target = packChunkPos(chunkX, chunkZ);
    for (long ringPos : ringPositions) {
      if (ringPos == target) {
        return true;
      }
    }
    return false;
  }

  private static StructureSelection pickStructureFromSet(
      long seed, int chunkX, int chunkZ, PlacementSnapshot snapshot) {
    var set = snapshot.set;
    var entries = set.structures();
    if (entries.isEmpty()) {
      return null;
    }

    var totalWeight = 0;
    for (var entry : entries) {
      totalWeight += entry.weight();
    }
    if (totalWeight <= 0) {
      return null;
    }

    var salt = snapshot.selectionSalt();
    var roll = (int) Math.floorMod(MathUtils.hash64(seed, chunkX, chunkZ, salt), totalWeight);
    for (var entry : entries) {
      roll -= entry.weight();
      if (roll < 0) {
        var structure = entry.structure().value();
        var structureId =
            entry.structure().unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
        return new StructureSelection(set, structure, structureId);
      }
    }
    var lastEntry = entries.get(entries.size() - 1);
    var structure = lastEntry.structure().value();
    var structureId =
        lastEntry.structure().unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
    return new StructureSelection(set, structure, structureId);
  }

  private static long[] computeRingPositions(
      long seed, ConcentricRingsStructurePlacement placement) {
    var count = placement.count();
    if (count <= 0) {
      return new long[0];
    }

    var distance = placement.distance();
    var spread = placement.spread();
    long[] positions = new long[count];

    RandomSource random = RandomSource.create();
    random.setSeed(seed);

    var angle = random.nextDouble() * Math.PI * 2.0;
    var ringIndex = 0;
    var ring = 0;

    for (var i = 0; i < count; i++) {
      var radius =
          4 * distance + distance * ring * 6 + (random.nextDouble() - 0.5) * (distance * 2.5);
      var x = (int) Math.round(Math.cos(angle) * radius);
      var z = (int) Math.round(Math.sin(angle) * radius);
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

  private record PlacementSnapshot(StructureSet set, String setId, long[] ringPositions) {
    int selectionSalt() {
      return setId.hashCode();
    }
  }

  private record StructureSelection(StructureSet set, Structure structure, String structureId) {}
}
