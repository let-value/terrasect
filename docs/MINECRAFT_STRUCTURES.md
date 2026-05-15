# Minecraft 1.21.x Structure Placement Pipeline

Deep-dive reference for everything involved in deciding where structures are placed in the world.
Written from source in `minecraft/` to inform Terrasect's understanding of structure generation.

---

## Table of Contents

1. [High-Level Concept](#1-high-level-concept)
2. [Key Classes Glossary](#2-key-classes-glossary)
3. [Lifecycle: From World Load to Structure Start](#3-lifecycle-from-world-load-to-structure-start)
4. [StructureSet – The Container](#4-structureset--the-container)
5. [StructurePlacement – Abstract Base](#5-structureplacement--abstract-base)
6. [RandomSpreadStructurePlacement](#6-randomspreadstructureplacement)
7. [ConcentricRingsStructurePlacement](#7-concentricringsstructureplacement)
8. [WorldgenRandom – Seed Mixing](#8-worldgenrandom--seed-mixing)
9. [ChunkGeneratorStructureState](#9-chunkgeneratorstructurestate)
10. [ChunkGenerator.createStructures – Call Chain](#10-chunkgeneratorcreatestructures--call-chain)
11. [Structure.generate – Start Creation](#11-structuregenerate--start-creation)
12. [Legacy and Special Cases](#12-legacy-and-special-cases)
13. [Built-in Structure Sets Reference](#13-built-in-structure-sets-reference)

---

## 1. High-Level Concept

Structure placement is a **two-phase decision** per chunk:

1. **Placement check** — for every registered `StructureSet`, ask whether this chunk is the designated structure chunk according to the set's `StructurePlacement`. This is a purely deterministic (seed-based) spatial filter.
2. **Generation attempt** — if the placement check passes, attempt to actually generate a `StructureStart` by verifying biome constraints and building the structure's pieces.

The key insight separating structures from noise-based terrain: structures are **chunk-anchored**. Each potential structure is associated with exactly one "home" chunk computed from the world seed and per-structure salt. Every other chunk that overlaps the structure's bounding box simply references that start.

Two concrete placement strategies exist:
- **`random_spread`** — grid-based random placement with configurable spacing and separation.
- **`concentric_rings`** — used exclusively for Strongholds; pre-computes a spiral of positions centered on the world origin.

---

## 2. Key Classes Glossary

| Class | Package | Role |
|---|---|---|
| `StructureSet` | `levelgen.structure` | Associates a list of weighted structures with one `StructurePlacement`; the datapack-loadable unit |
| `StructurePlacement` | `levelgen.structure.placement` | Abstract base: `salt`, `frequency`, `exclusion_zone`, `locate_offset` |
| `RandomSpreadStructurePlacement` | `levelgen.structure.placement` | Grid-based random placement; `spacing`, `separation`, `spread_type` |
| `ConcentricRingsStructurePlacement` | `levelgen.structure.placement` | Spiral/ring placement; `distance`, `spread`, `count`, `preferred_biomes` |
| `RandomSpreadType` | `levelgen.structure.placement` | Enum: `LINEAR` or `TRIANGULAR` offset distribution within a spacing cell |
| `StructurePlacementType` | `levelgen.structure.placement` | Registry interface; holds `RANDOM_SPREAD` and `CONCENTRIC_RINGS` singletons |
| `ChunkGeneratorStructureState` | `world.level.chunk` | Per-dimension state: seed, biome filter, placement cache, ring futures |
| `ChunkGenerator` | `world.level.chunk` | Calls `createStructures` per chunk; implements weighted structure selection |
| `Structure` | `levelgen.structure` | Abstract structure type; holds `StructureSettings` (biomes, step, terrainAdaptation) |
| `StructureStart` | `levelgen.structure` | Result of a successful generation attempt: pieces + home chunk pos + reference count |
| `WorldgenRandom` | `levelgen` | Wraps `LegacyRandomSource`; provides `setLargeFeatureWithSalt` and `setLargeFeatureSeed` for seed mixing |
| `BuiltinStructureSets` | `levelgen.structure` | Registry key constants for all vanilla structure sets |

---

## 3. Lifecycle: From World Load to Structure Start

```
Server startup / dimension load
  └── ChunkGeneratorStructureState.createForNormal(randomState, worldSeed, biomeSource, structureSetLookup)
        ├── Filters structure sets to those that have at least one biome present in this dimension
        ├── Stores possibleStructureSets list
        └── Sets concentricRingsSeed = worldSeed   (flat worlds use 0L)

First time any structure-related query happens (lazy init):
  └── ChunkGeneratorStructureState.ensureStructuresGenerated()
        └── generatePositions()
              ├── For each StructureSet in possibleStructureSets:
              │     ├── Builds placementsForStructure map: Structure → List<StructurePlacement>
              │     └── If placement is ConcentricRings: kicks off generateRingPositions() as CompletableFuture
              └── hasGeneratedPositions = true

Per-chunk structure phase (called from ChunkStatus.STRUCTURE_STARTS):
  └── ChunkGenerator.createStructures(registryAccess, state, structureManager, chunkAccess, ...)
        └── For each StructureSet in state.possibleStructureSets():
              ├── Skip if any member structure already has a valid start in this chunk
              ├── placement.isStructureChunk(state, chunkX, chunkZ)
              │     ├── isPlacementChunk()       ← subclass-specific
              │     ├── applyAdditionalChunkRestrictions()  ← frequency check
              │     └── applyInteractionsWithOtherStructures()  ← exclusion zone
              └── If true:
                    ├── Single structure → tryGenerateStructure()
                    └── Multiple structures → weighted selection then tryGenerateStructure()

tryGenerateStructure():
  └── structure.generate(...)
        └── findValidGenerationPoint(generationContext)
              ├── findGenerationPoint(ctx)      ← abstract; each structure type implements this
              └── isValidBiome(stub, ctx)       ← checks noise biome at stub position
        └── If valid: new StructureStart(structure, chunkPos, references, pieces)
        └── structureManager.setStartForStructure(sectionPos, structure, start, chunkAccess)
```

---

## 4. StructureSet – The Container

**File:** `net.minecraft.world.level.levelgen.structure.StructureSet`

```java
public record StructureSet(
    List<StructureSet.StructureSelectionEntry> structures,
    StructurePlacement placement
)

public record StructureSelectionEntry(
    Holder<Structure> structure,
    int weight        // POSITIVE_INT; ≥1
)
```

**Codec field names (JSON):**
- `structures` — list of `{ "structure": "<id>", "weight": <int> }`
- `placement` — dispatched `StructurePlacement` object (see §5)

A `StructureSet` with a single structure uses a convenience constructor that wraps it with `weight=1`.
Multiple structures in one set means the game picks one per eligible chunk via weighted random selection (§10).

**Registry:** `Registries.STRUCTURE_SET`; loaded from datapacks under `data/<ns>/worldgen/structure_set/`.

---

## 5. StructurePlacement – Abstract Base

**File:** `net.minecraft.world.level.levelgen.structure.placement.StructurePlacement`

All placement types extend this class. The shared codec fields (added via `placementCodec(instance)`):

| JSON field | Type | Default | Role |
|---|---|---|---|
| `salt` | `NON_NEGATIVE_INT` | required | Per-structure-set component of the seed mix |
| `locate_offset` | `Vec3i` (max ±16 per axis) | `[0,0,0]` | Offset applied by `/locate` to the returned block position |
| `frequency` | `float` [0.0, 1.0] | `1.0` | If < 1.0, an extra per-chunk probability check is applied |
| `frequency_reduction_method` | `FrequencyReductionMethod` | `default` | Which legacy algorithm to use for the frequency roll |
| `exclusion_zone` | `ExclusionZone` | absent | Prevents placement if another structure set is nearby |

### isStructureChunk – the gate

```java
// StructurePlacement.java:78
public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
    return isPlacementChunk(state, chunkX, chunkZ)          // 1. subclass spatial check
        && applyAdditionalChunkRestrictions(chunkX, chunkZ, state.getLevelSeed())  // 2. frequency
        && applyInteractionsWithOtherStructures(state, chunkX, chunkZ);            // 3. exclusion
}
```

All three conditions must pass. They are evaluated left-to-right (short-circuit).

### Frequency reduction methods

`FrequencyReductionMethod` is an enum selecting a `FrequencyReducer` function:

| Enum value | JSON name | Algorithm |
|---|---|---|
| `DEFAULT` | `"default"` | `setLargeFeatureWithSalt(worldSeed, chunkX, chunkZ, salt)` → `nextFloat() < frequency` |
| `LEGACY_TYPE_1` | `"legacy_type_1"` | `setSeed(gridX>>4 ^ gridZ<<4 ^ worldSeed)` → `nextInt(round(1/f)) == 0`; used by Pillager Outposts |
| `LEGACY_TYPE_2` | `"legacy_type_2"` | `setLargeFeatureWithSalt(worldSeed, chunkX, chunkZ, 10387320)` → `nextFloat() < frequency`; ignores `salt` |
| `LEGACY_TYPE_3` | `"legacy_type_3"` | `setLargeFeatureSeed(worldSeed, chunkX, chunkZ)` → `nextDouble() < frequency`; no salt |

The `HIGHLY_ARBITRARY_RANDOM_SALT = 10387320` constant is baked into `LEGACY_TYPE_2`.

### ExclusionZone

```java
@Deprecated
public record ExclusionZone(Holder<StructureSet> otherSet, int chunkCount)
```

| JSON field | Range | Role |
|---|---|---|
| `other_set` | StructureSet reference | The set to avoid |
| `chunk_count` | 1–16 | Square radius (in chunks) within which the other set must not have a structure chunk |

`isPlacementForbidden` calls `state.hasStructureChunkInRange(otherSet, chunkX, chunkZ, chunkCount)`,
which iterates a `(2k+1)²` square and calls `isStructureChunk` on the other set's placement for each cell.
The `@Deprecated` annotation signals Mojang intends to replace this with a better mechanism.

---

## 6. RandomSpreadStructurePlacement

**File:** `net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement`

The workhorse for almost every overworld structure. Divides the world into a regular grid of
`spacing × spacing` chunk cells and picks one random chunk within each cell as the structure's
home chunk.

### Fields

| JSON field | Type | Constraint | Role |
|---|---|---|---|
| `spacing` | `int` | 0–4096 | Grid cell side length in chunks |
| `separation` | `int` | 0–4096 | Minimum margin from the cell edge; must be < `spacing` |
| `spread_type` | `RandomSpreadType` | `linear`/`triangular` | Distribution of the offset within the cell |

Codec validation enforces `spacing > separation` — loading a JSON with `spacing ≤ separation` produces an error.

### getPotentialStructureChunk — core algorithm

```java
// RandomSpreadStructurePlacement.java:69
public ChunkPos getPotentialStructureChunk(long worldSeed, int chunkX, int chunkZ) {
    int gridX = Math.floorDiv(chunkX, spacing);
    int gridZ = Math.floorDiv(chunkZ, spacing);

    WorldgenRandom rng = new WorldgenRandom(new LegacyRandomSource(0L));
    rng.setLargeFeatureWithSalt(worldSeed, gridX, gridZ, this.salt());

    int varianceRange = spacing - separation;        // max random offset
    int offsetX = spreadType.evaluate(rng, varianceRange);
    int offsetZ = spreadType.evaluate(rng, varianceRange);

    return new ChunkPos(gridX * spacing + offsetX, gridZ * spacing + offsetZ);
}
```

Step by step:

1. **Grid cell** — `gridX = floor(chunkX / spacing)`, `gridZ = floor(chunkZ / spacing)`.
   Every chunk within a `spacing × spacing` cell maps to the same grid cell.

2. **Seed** — `setLargeFeatureWithSalt(worldSeed, gridX, gridZ, salt)` produces a deterministic
   seed: `gridX * 341873128712L + gridZ * 132897987541L + worldSeed + salt`.
   Different salts isolate different structure types from each other even within the same grid cell.

3. **Offset** — two independent draws from `spreadType.evaluate(rng, varianceRange)`:
   - `LINEAR`: `rng.nextInt(varianceRange)` — uniform over `[0, varianceRange)`.
   - `TRIANGULAR`: `(rng.nextInt(varianceRange) + rng.nextInt(varianceRange)) / 2` — bell-shaped,
     biased toward the center of the variance range.

4. **Home chunk** — `(gridX * spacing + offsetX, gridZ * spacing + offsetZ)`.
   The result is always inside the current grid cell (offset < varianceRange ≤ spacing − separation,
   so it is at least `separation` chunks away from the cell boundary in the positive direction).

`isPlacementChunk` simply calls `getPotentialStructureChunk` for the current chunk's grid cell and
returns `chunkPos == (chunkX, chunkZ)`.

### Locate offset

`getLocatePos(chunkPos)` returns `chunkPos.getMinBlockX() + locateOffset.x` (etc. for Z), always at Y=0.
`/locate` uses this to report where to go. Buried Treasure uses a non-zero `locate_offset` to
shift the reported position to the center of the chunk rather than the corner.

---

## 7. ConcentricRingsStructurePlacement

**File:** `net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement`

Used exclusively for Strongholds. Rather than a per-chunk spatial hash, it pre-computes a fixed
list of `count` chunk positions arranged in expanding concentric rings around the world origin,
then returns true only for chunks in that list.

### Fields

| JSON field | Type | Constraint | Role |
|---|---|---|---|
| `distance` | `int` | 0–1023 | Nominal radius of the innermost ring (in chunks, before jitter) |
| `spread` | `int` | 0–1023 | Number of structures on the first ring; grows for outer rings |
| `count` | `int` | 1–4095 | Total number of structures to place across all rings |
| `preferred_biomes` | `HolderSet<Biome>` | required | Biome filter; structure snaps to nearest matching biome within 112 blocks |

### isPlacementChunk

```java
// ConcentricRingsStructurePlacement.java:84
@Override
protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
    List<ChunkPos> positions = state.getRingPositionsFor(this);
    return positions == null ? false : positions.contains(new ChunkPos(chunkX, chunkZ));
}
```

This is a list membership test against a pre-computed cache — the opposite of the random-spread hash.

### Ring generation algorithm

Computed lazily in `ChunkGeneratorStructureState.generateRingPositions()`, run as parallel
`CompletableFuture` tasks on the `"structureRings"` background executor:

```java
// ChunkGeneratorStructureState.java:97
RandomSource rng = RandomSource.create();
rng.setSeed(concentricRingsSeed);           // worldSeed in normal worlds, 0 in flat worlds
double angle = rng.nextDouble() * PI * 2;   // random initial angle
int ringIndex = 0;       // current ring (0-based)
int posInRing = 0;       // position within current ring
int currentSpread = spread;  // structures on current ring (grows outward)

for (int n = 0; n < count; n++) {
    // Jittered radius: 4*distance + distance*ringIndex*6 ± noise
    double radius = 4 * distance + distance * ringIndex * 6
                  + (rng.nextDouble() - 0.5) * (distance * 2.5);

    // Polar → chunk coords
    int chunkX = (int) Math.round(Math.cos(angle) * radius);
    int chunkZ = (int) Math.round(Math.sin(angle) * radius);

    // Snap to nearest preferred biome (async, 112-block search radius)
    ChunkPos finalPos = findBiomeOrFallback(chunkX, chunkZ, preferredBiomes);
    list.add(finalPos);

    angle += (PI * 2) / currentSpread;     // evenly space within ring

    if (++posInRing == currentSpread) {    // ring full → advance
        ringIndex++;
        posInRing = 0;
        currentSpread += 2 * currentSpread / (ringIndex + 1);  // grow spread
        currentSpread = Math.min(currentSpread, count - n);
        angle += rng.nextDouble() * PI * 2;  // random rotation for next ring
    }
}
```

Key properties:
- **Radius formula** `4*d + d*ring*6` produces rings at roughly `4d, 10d, 16d, 22d, …` chunk radii.
  The `±(d*2.5 * nextDouble())` jitter prevents perfect circles.
- **Spread growth** — outer rings hold more structures: `currentSpread += 2*k/(ring+1)`.
- **Biome snap** — each position asynchronously searches up to 112 blocks outward for the nearest
  `preferred_biomes` match (`BiomeSource.findBiomeHorizontal`). If no matching biome is found within
  the search radius the original polar-coordinate chunk is used as fallback.
- **Flat-world seed** — `concentricRingsSeed = 0L` in flat worlds (`createForFlat`), so all flat
  worlds share the same Stronghold ring positions regardless of world seed.
- The `count` positions are cached as `List<ChunkPos>` in `ringPositions` on the
  `ChunkGeneratorStructureState` and never recomputed.

---

## 8. WorldgenRandom – Seed Mixing

**File:** `net.minecraft.world.level.levelgen.WorldgenRandom`

Two seed-mixing methods are used by the structure pipeline:

### setLargeFeatureWithSalt (random-spread placement)

```java
// WorldgenRandom.java:64
public void setLargeFeatureWithSalt(long worldSeed, int chunkX, int chunkZ, int salt) {
    long seed = chunkX * 341873128712L + chunkZ * 132897987541L + worldSeed + salt;
    this.setSeed(seed);
}
```

Used for:
- `RandomSpreadStructurePlacement.getPotentialStructureChunk` (grid-cell random).
- `StructurePlacement.probabilityReducer` (default frequency reduction).

The multipliers `341873128712L` and `132897987541L` are arbitrary large primes that prevent
coordinate aliasing. Adding `salt` before `setSeed` is what separates different structure types
that share the same grid geometry.

### setLargeFeatureSeed (start generation / multi-structure selection)

```java
// WorldgenRandom.java:56
public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
    this.setSeed(worldSeed);
    long m = this.nextLong();
    long n = this.nextLong();
    long seed = chunkX * m ^ chunkZ * n ^ worldSeed;
    this.setSeed(seed);
}
```

Used for:
- `Structure.GenerationContext.makeRandom` (the RNG passed into `findGenerationPoint`).
- `ChunkGenerator.createStructures` weighted selection loop.

This method draws two longs from the seeded RNG first, then XORs them with chunk coordinates.
It does **not** accept a salt, so two different structure types hitting the same chunk get the
same weighted-selection seed — but they operate on different candidate lists so the outputs differ.

---

## 9. ChunkGeneratorStructureState

**File:** `net.minecraft.world.level.chunk.ChunkGeneratorStructureState`

Created once per dimension; holds all placement state for the chunk generator.

### Key fields

```java
private final long levelSeed;                    // world seed
private final long concentricRingsSeed;          // levelSeed in normal, 0 in flat
private final List<Holder<StructureSet>> possibleStructureSets;  // biome-filtered sets
private final Map<Structure, List<StructurePlacement>> placementsForStructure;
private final Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> ringPositions;
private boolean hasGeneratedPositions;           // lazy-init flag
```

### createForNormal vs createForFlat

```java
// Normal world: concentricRingsSeed = worldSeed
ChunkGeneratorStructureState.createForNormal(randomState, l, biomeSource, lookup)

// Flat world: concentricRingsSeed = 0L → all flat worlds share Stronghold positions
ChunkGeneratorStructureState.createForFlat(randomState, l, biomeSource, stream)
```

Both constructors filter `possibleStructureSets` to only include sets where at least one member
structure has a biome present in this dimension's `biomeSource.possibleBiomes()`.

### Lazy initialization

`generatePositions()` runs the first time any of `getRingPositionsFor`, `getPlacementsForStructure`,
or `hasStructureChunkInRange` is called (via `ensureStructuresGenerated()`). It populates
`placementsForStructure` and fires off all `ConcentricRings` futures.

### hasStructureChunkInRange (exclusion zone support)

```java
// ChunkGeneratorStructureState.java:178
public boolean hasStructureChunkInRange(Holder<StructureSet> holder, int chunkX, int chunkZ, int k) {
    StructurePlacement placement = holder.value().placement();
    for (int dx = chunkX - k; dx <= chunkX + k; dx++) {
        for (int dz = chunkZ - k; dz <= chunkZ + k; dz++) {
            if (placement.isStructureChunk(this, dx, dz)) return true;
        }
    }
    return false;
}
```

Scans a `(2k+1) × (2k+1)` square — `(2*chunkCount+1)²` placement checks per exclusion zone query.

---

## 10. ChunkGenerator.createStructures – Call Chain

**File:** `net.minecraft.world.level.chunk.ChunkGenerator` (lines 452–577)

```
ChunkGenerator.createStructures(registryAccess, state, structureManager, chunkAccess, templateManager, resourceKey)
│
├── Guard: SharedConstants.DEBUG_DISABLE_STRUCTURES → return immediately if true
│
├── For each Holder<StructureSet> in state.possibleStructureSets():
│     StructurePlacement placement = set.placement()
│     List<StructureSelectionEntry> candidates = set.structures()
│
│     ── Early exit: if any candidate already has a valid StructureStart in this chunk → skip set
│
│     ── placement.isStructureChunk(state, chunkPos.x, chunkPos.z)
│           → isPlacementChunk()                     [1]
│           → applyAdditionalChunkRestrictions()      [2]  if frequency < 1.0F
│           → applyInteractionsWithOtherStructures()  [3]  if exclusion zone present
│
│     ── If false → continue to next set
│
│     ── If true AND candidates.size() == 1:
│           tryGenerateStructure(candidates[0], ...)
│
│     ── If true AND candidates.size() > 1:
│           WorldgenRandom rng = new WorldgenRandom(LegacyRandomSource(0))
│           rng.setLargeFeatureSeed(worldSeed, chunkX, chunkZ)
│           totalWeight = sum of all weights
│
│           while candidates not empty:
│               j = rng.nextInt(totalWeight)
│               walk list: j -= weight; stop when j < 0  → selected entry
│               if tryGenerateStructure(selected, ...) → return
│               candidates.remove(selected); totalWeight -= selected.weight
│
└── (returns void; structure starts stored in StructureManager)

tryGenerateStructure(entry, structureManager, ..., worldSeed, chunkAccess, chunkPos, sectionPos, resourceKey)
  └── structure.generate(entry.structure(), resourceKey, registryAccess, this, biomeSource,
                         randomState, templateManager, worldSeed, chunkPos, references, chunkAccess, biomePredicate)
        ├── GenerationContext.makeRandom(worldSeed, chunkPos)   → setLargeFeatureSeed
        ├── structure.findValidGenerationPoint(ctx)
        │     ├── structure.findGenerationPoint(ctx)   [abstract; per-structure logic]
        │     └── isValidBiome(stub, ctx)              [noise biome check at stub.position()]
        └── If valid: new StructureStart(structure, chunkPos, references, pieces)
              structureManager.setStartForStructure(sectionPos, structure, start, chunkAccess)
              return true
        └── If invalid: return false  (next weighted candidate tried)
```

The weighted selection does **not** re-roll the seed between retries — it continues consuming from
the same `WorldgenRandom` sequence. This means if the first choice fails, the second choice is
determined by the weights of the remaining candidates only, not by another seed draw.

---

## 11. Structure.generate – Start Creation

**File:** `net.minecraft.world.level.levelgen.structure.Structure`

### StructureSettings

Every concrete `Structure` stores a `StructureSettings` record:

```java
public record StructureSettings(
    HolderSet<Biome> biomes,
    Map<MobCategory, StructureSpawnOverride> spawnOverrides,
    GenerationStep.Decoration step,
    TerrainAdjustment terrainAdaptation
)
```

| Field | JSON key | Role |
|---|---|---|
| `biomes` | `biomes` | Biomes where `findValidGenerationPoint` is allowed to succeed |
| `spawnOverrides` | `spawn_overrides` | Per-mob-category spawn list overrides inside the structure |
| `step` | `step` | `GenerationStep.Decoration` value; controls when structure pieces are placed |
| `terrainAdaptation` | `terrain_adaptation` | Beardifier mode for terrain fitting |

`TerrainAdjustment` values: `NONE`, `BURY`, `BEARD_THIN`, `BEARD_BOX`, `ENCAPSULATE`.
Non-`NONE` values cause `adjustBoundingBox` to inflate the bounding box by 12 blocks for the
Beardifier density offset calculation.

### GenerationContext

```java
public record GenerationContext(
    RegistryAccess registryAccess,
    ChunkGenerator chunkGenerator,
    BiomeSource biomeSource,
    RandomState randomState,
    StructureTemplateManager structureTemplateManager,
    WorldgenRandom random,      // seeded via setLargeFeatureSeed(worldSeed, chunkPos.x, chunkPos.z)
    long seed,
    ChunkPos chunkPos,
    LevelHeightAccessor heightAccessor,
    Predicate<Holder<Biome>> validBiome
)
```

The `random` field is seeded fresh for each `generate()` call, so each structure type can use it
freely without worrying about contaminating other structures.

### findValidGenerationPoint

```java
// Structure.java:204
public Optional<GenerationStub> findValidGenerationPoint(GenerationContext ctx) {
    return findGenerationPoint(ctx)           // abstract: subclass picks a position + pieces
        .filter(stub -> isValidBiome(stub, ctx));  // noise biome check at stub position
}
```

`isValidBiome` samples the noise biome at `QuartPos.fromBlock(stub.position())` and checks it
against `ctx.validBiome` (which is `structure.biomes()::contains`).

### StructureStart validity

A `StructureStart` is valid if `!pieceContainer.isEmpty()`. Structures that cannot fit (e.g.
no suitable terrain) return `StructureStart.INVALID_START` and `tryGenerateStructure` returns
`false`, causing the weighted selection loop to try the next candidate.

---

## 12. Legacy and Special Cases

### Pillager Outpost (LEGACY_TYPE_1)

`FrequencyReductionMethod.LEGACY_TYPE_1` uses a distinctly different seed:

```java
// StructurePlacement.java:119
int m = chunkX >> 4;   // equivalent to gridX (spacing is 32, so >>4 = /16... actually shift of raw chunkX)
int n = chunkZ >> 4;
rng.setSeed(m ^ n << 4 ^ worldSeed);
rng.nextInt();
return rng.nextInt((int)(1.0F / frequency)) == 0;
```

This is a legacy formula that ignores `salt` entirely and uses an older seed construction.

### Buried Treasure (non-zero locate_offset)

Buried Treasure uses `locate_offset: [9, 0, 9]` (chunk-relative block 9,0,9) so that `/locate`
reports the center of the 16×16 chunk area rather than the corner at `(0,0)`.
The placement is still per-chunk; the offset only affects the reported position.

### Flat worlds and Strongholds

`createForFlat` passes `concentricRingsSeed = 0L`. All flat worlds therefore share identical
Stronghold positions (same ring geometry, same biome-snap results for the fixed flat biome).

### Nether / End structures

Nether and End structures use `random_spread` like most overworld structures; they simply have
different spacing/separation/salt values and their biome sets restrict them to the correct dimension.
There is no dimension-specific placement type.

### `DEBUG_DISABLE_STRUCTURES`

`SharedConstants.DEBUG_DISABLE_STRUCTURES` is a compile-time flag that skips all of
`createStructures`. It is `false` in release builds and is only relevant for internal Mojang
development builds.

### ExclusionZone is deprecated

The `@Deprecated` on `ExclusionZone` signals that Mojang considers it a stopgap. Its current use
in vanilla data is to prevent Pillager Outposts from spawning within 10 chunks of a Village and
vice versa.

---

## 13. Built-in Structure Sets Reference

All keys defined in `BuiltinStructureSets` (package `net.minecraft.world.level.levelgen.structure`):

| Registry key | Placement type | Notes |
|---|---|---|
| `minecraft:villages` | `random_spread` | Multi-structure set (plains/desert/savanna/taiga/snowy/tropical villages) |
| `minecraft:desert_pyramids` | `random_spread` | |
| `minecraft:igloos` | `random_spread` | |
| `minecraft:jungle_temples` | `random_spread` | |
| `minecraft:swamp_huts` | `random_spread` | |
| `minecraft:pillager_outposts` | `random_spread` | `LEGACY_TYPE_1` frequency reduction; exclusion zone vs villages |
| `minecraft:ocean_monuments` | `random_spread` | |
| `minecraft:woodland_mansions` | `random_spread` | Large spacing |
| `minecraft:buried_treasures` | `random_spread` | `locate_offset: [9,0,9]`; 1-per-chunk frequency check |
| `minecraft:mineshafts` | `random_spread` | High frequency (`< 1.0F`), `LEGACY_TYPE_3` |
| `minecraft:ruined_portals` | `random_spread` | Multi-structure (overworld + nether variants) |
| `minecraft:shipwrecks` | `random_spread` | Multi-structure (regular + beached) |
| `minecraft:ocean_ruins` | `random_spread` | Multi-structure (warm + cold) |
| `minecraft:nether_complexes` | `random_spread` | Fortress + Bastion as weighted entries in one set |
| `minecraft:nether_fossils` | `random_spread` | |
| `minecraft:end_cities` | `random_spread` | |
| `minecraft:ancient_cities` | `random_spread` | Large separation |
| `minecraft:strongholds` | `concentric_rings` | Only vanilla use of `concentric_rings` |
| `minecraft:trail_ruins` | `random_spread` | |
| `minecraft:trial_chambers` | `random_spread` | |

---

### Source files inspected

- `minecraft/net/minecraft/world/level/levelgen/structure/placement/StructurePlacement.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/placement/ConcentricRingsStructurePlacement.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadType.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/placement/StructurePlacementType.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/StructureSet.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/BuiltinStructureSets.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/Structure.java`
- `minecraft/net/minecraft/world/level/levelgen/structure/StructureStart.java`
- `minecraft/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java`
- `minecraft/net/minecraft/world/level/chunk/ChunkGenerator.java` (lines 452–577)
- `minecraft/net/minecraft/world/level/levelgen/WorldgenRandom.java`
