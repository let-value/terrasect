# Mob Spawn Constraints Research

- **Date:** 2026-05-30
- **Original branch:** `feature/mob-spawn-constraints-planning`
- **Consolidated branch:** `feature/mob-spawn-constraints-implementation` / PR #56
- **Scope:** Research only. No production code modified in the original planning PR.

---

## 1. Overview of Minecraft Mob Spawning

Minecraft has two distinct mob-spawning paths that must be addressed separately:

| Path | When | Determinism | Frequency |
|------|------|-------------|-----------|
| **Worldgen spawning** | During chunk decoration / generation | Deterministic per seed | Once per chunk |
| **Runtime spawning** | Server tick, around loaded players | Non-deterministic | Every server tick |

Both paths read spawn candidate lists from `MobSpawnSettings`, validate placement rules via `SpawnPlacements`, and eventually call `Mob.finalizeSpawn`. The critical difference is *where* biome data, chunk data, structure context, and random state are sourced.

---

## 2. Worldgen Spawning

### 2.1 Purpose

Worldgen spawning places creatures into a chunk as it is first decorated. It uses the biome's spawn settings, the chunk generator's configuration, and a seeded `RandomSource`. It is called once per chunk and must be deterministic for a given seed.

### 2.2 Call Flow

```
NoiseBasedChunkGenerator.spawnOriginalMobs(WorldGenRegion)
  ├─ checks NoiseGeneratorSettings.disableMobGeneration()
  ├─ gets center BlockPos from WorldGenRegion
  ├─ gets Holder<Biome> from WorldGenRegion at center pos
  └─ NaturalSpawner.spawnMobsForChunkGeneration(
         ServerLevelAccessor,    // is a WorldGenRegion
         Holder<Biome>,
         ChunkPos,
         RandomSource)
       ├─ reads MobSpawnSettings from Holder<Biome>
       ├─ iterates MobCategory values
       └─ for each category:
           ├─ MobSpawnSettings.getMobs(MobCategory) → WeightedList<SpawnerData>
           ├─ picks random SpawnerData entries
           ├─ finds suitable ground position via NaturalSpawner.getTopNonCollidingPos
           └─ instantiates and places mob
```

### 2.3 Key Classes and Methods

| Class | Method | Role |
|-------|--------|------|
| `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator` | `spawnOriginalMobs(WorldGenRegion)` | Entry point for worldgen mob placement |
| `net.minecraft.world.level.NaturalSpawner` | `spawnMobsForChunkGeneration(ServerLevelAccessor, Holder<Biome>, ChunkPos, RandomSource)` | Iterates biome spawn list and places mobs |
| `net.minecraft.world.level.biome.MobSpawnSettings` | `getMobs(MobCategory)` | Returns `WeightedList<SpawnerData>` for a category |
| `net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData` | `type()`, `minCount()`, `maxCount()` | Spawn entry: entity type and group size range |
| `net.minecraft.world.level.NaturalSpawner` | `getTopNonCollidingPos(LevelReader, EntityType<?>, int, int)` | Finds spawn position |

### 2.4 Available Data at Worldgen Spawn Time

At the `spawnOriginalMobs` / `spawnMobsForChunkGeneration` boundary:

| Data | Source | Available? |
|------|--------|-----------|
| `ChunkPos` | Parameter to `spawnMobsForChunkGeneration` | Yes |
| `Holder<Biome>` | Derived from center block in `WorldGenRegion` | Yes |
| `ServerLevel` | `WorldGenRegion.getLevel()` | Yes |
| Dimension key | Via `ServerLevel.dimension()` | Yes |
| `RandomSource` | Parameter | Yes |
| `StructureManager` | *Not* passed to `spawnMobsForChunkGeneration` | No |
| Per-block position | Computed internally | Partially |
| `ChunkAccess` with `ChunkContext` | Not directly accessible here | No (see §6) |

**Uncertain:** Whether the `ChunkAccess` objects held by the `WorldGenRegion` at worldgen-spawn time have a fully-populated `ChunkContext`. `ChunkAccessMixin` injects into `ChunkAccess.<init>` and builds `ChunkContext` immediately, but the context requires `DimensionContext` to be registered — which should be true by the point chunk decoration runs. Needs verification.

### 2.5 Hot-Path Assessment

Worldgen spawn is **setup-time for a chunk** — called once per chunk, never again. Allocation here is acceptable. This is not a hot path.

---

## 3. Runtime (Natural) Spawning

### 3.1 Purpose

Runtime spawning is the continuous mob population around active players. The server ticks every loaded chunk within player range every game tick, attempting to fill mob-category slots up to per-category density caps. This is the hot path.

### 3.2 Call Flow

```
ServerChunkCache.tick(BooleanSupplier, boolean)
  └─ ServerChunkCache.tickChunks()
       └─ ServerChunkCache.tickChunks(ProfilerFiller, long)
            ├─ NaturalSpawner.createState(
            │      int spawnableChunkCount,
            │      Iterable<Entity> entities,
            │      ChunkGetter,
            │      LocalMobCapCalculator)
            │     → NaturalSpawner$SpawnState
            │        (global mob counts per MobCategory,
            │         local mob cap data per chunk)
            ├─ NaturalSpawner.getFilteredSpawningCategories(
            │      SpawnState, boolean, boolean, boolean)
            │     → List<MobCategory>
            │        (only categories below global cap)
            └─ for each spawning chunk:
                 ServerChunkCache.tickSpawningChunk(
                     LevelChunk, long, List<MobCategory>, SpawnState)
                   └─ NaturalSpawner.spawnForChunk(
                          ServerLevel,
                          LevelChunk,
                          SpawnState,
                          List<MobCategory>)
                        └─ for each MobCategory in list:
                             NaturalSpawner.spawnCategoryForChunk(
                                 MobCategory,
                                 ServerLevel,
                                 LevelChunk,
                                 SpawnPredicate,          ← functional interface
                                 AfterSpawnCallback)      ← functional interface
                               ├─ NaturalSpawner.getRandomPosWithin(Level, LevelChunk)
                               │     → random BlockPos in chunk
                               └─ NaturalSpawner.spawnCategoryForPosition(
                                      MobCategory,
                                      ServerLevel,
                                      ChunkAccess,
                                      BlockPos,
                                      SpawnPredicate,
                                      AfterSpawnCallback)
                                    │
                                    ├─ SPAWN CANDIDATE SELECTION:
                                    │  NaturalSpawner.getRandomSpawnMobAt(
                                    │      ServerLevel, StructureManager,
                                    │      ChunkGenerator, MobCategory,
                                    │      RandomSource, BlockPos)
                                    │     → Optional<SpawnerData>
                                    │        └─ NaturalSpawner.mobsAt(
                                    │               ServerLevel, StructureManager,
                                    │               ChunkGenerator, MobCategory,
                                    │               BlockPos, Holder<Biome>)
                                    │              → WeightedList<SpawnerData>
                                    │                (from ChunkGenerator.getMobsAt,
                                    │                 which merges biome + structure entries)
                                    │
                                    ├─ SPAWN PLACEMENT VALIDATION:
                                    │  NaturalSpawner.isValidSpawnPostitionForType(
                                    │      ServerLevel, MobCategory,
                                    │      StructureManager, ChunkGenerator,
                                    │      SpawnerData, BlockPos$MutableBlockPos,
                                    │      double)
                                    │     └─ NaturalSpawner.canSpawnMobAt(
                                    │            ServerLevel, StructureManager,
                                    │            ChunkGenerator, MobCategory,
                                    │            SpawnerData, BlockPos)
                                    │           └─ SpawnPlacements.checkSpawnRules(
                                    │                  EntityType<?>,
                                    │                  ServerLevelAccessor,
                                    │                  EntitySpawnReason,
                                    │                  BlockPos,
                                    │                  RandomSource)
                                    │                 └─ Mob.checkSpawnRules(
                                    │                        LevelAccessor,
                                    │                        EntitySpawnReason)
                                    │
                                    ├─ SpawnPredicate.test(EntityType<?>, BlockPos, ChunkAccess)
                                    │     ← THIS IS WHERE PER-ENTITY TYPE FILTER RUNS
                                    │
                                    └─ Mob.finalizeSpawn(
                                           ServerLevelAccessor,
                                           DifficultyInstance,
                                           EntitySpawnReason,
                                           SpawnGroupData)
```

### 3.3 Key Classes and Methods

| Class | Method | Role |
|-------|--------|------|
| `net.minecraft.server.level.ServerChunkCache` | `tickChunks(ProfilerFiller, long)` | Outer runtime spawn loop; creates SpawnState |
| `net.minecraft.server.level.ServerChunkCache` | `tickSpawningChunk(LevelChunk, long, List<MobCategory>, SpawnState)` | Per-chunk spawn dispatch |
| `net.minecraft.world.level.NaturalSpawner` | `createState(int, Iterable<Entity>, ChunkGetter, LocalMobCapCalculator)` | Computes global mob density state |
| `net.minecraft.world.level.NaturalSpawner` | `getFilteredSpawningCategories(SpawnState, boolean, boolean, boolean)` | Returns categories not yet at global cap |
| `net.minecraft.world.level.NaturalSpawner` | `spawnForChunk(ServerLevel, LevelChunk, SpawnState, List<MobCategory>)` | Per-chunk per-category dispatch |
| `net.minecraft.world.level.NaturalSpawner` | `spawnCategoryForChunk(MobCategory, ServerLevel, LevelChunk, SpawnPredicate, AfterSpawnCallback)` | Gets random position, calls per-position spawn |
| `net.minecraft.world.level.NaturalSpawner` | `spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)` | Core per-position spawn loop with candidate selection and placement checks |
| `net.minecraft.world.level.NaturalSpawner` | `getRandomSpawnMobAt(ServerLevel, StructureManager, ChunkGenerator, MobCategory, RandomSource, BlockPos)` | Selects a random spawn candidate from weighted list |
| `net.minecraft.world.level.NaturalSpawner` | `mobsAt(ServerLevel, StructureManager, ChunkGenerator, MobCategory, BlockPos, Holder<Biome>)` | Returns `WeightedList<SpawnerData>` from `ChunkGenerator.getMobsAt` |
| `net.minecraft.world.level.NaturalSpawner` | `isValidSpawnPostitionForType(ServerLevel, MobCategory, StructureManager, ChunkGenerator, SpawnerData, BlockPos$MutableBlockPos, double)` | Terrain and rule validation for a candidate |
| `net.minecraft.world.level.NaturalSpawner` | `canSpawnMobAt(ServerLevel, StructureManager, ChunkGenerator, MobCategory, SpawnerData, BlockPos)` | Delegates to `SpawnPlacements.checkSpawnRules` |
| `net.minecraft.world.level.NaturalSpawner.SpawnPredicate` | `test(EntityType<?>, BlockPos, ChunkAccess)` | Functional interface; callers pass this as a gate |
| `net.minecraft.world.level.NaturalSpawner.AfterSpawnCallback` | `run(Mob, ChunkAccess)` | Post-spawn accounting callback |
| `net.minecraft.world.level.NaturalSpawner.SpawnState` | `canSpawnForCategoryGlobal(MobCategory)` | Global mob cap check |
| `net.minecraft.world.level.NaturalSpawner.SpawnState` | `canSpawnForCategoryLocal(MobCategory, ChunkPos)` | Local mob cap check |
| `net.minecraft.world.level.chunk.ChunkGenerator` | `getMobsAt(Holder<Biome>, StructureManager, MobCategory, BlockPos)` | Returns `WeightedList<SpawnerData>` merging biome and structure entries |
| `net.minecraft.world.entity.SpawnPlacements` | `checkSpawnRules(EntityType<?>, ServerLevelAccessor, EntitySpawnReason, BlockPos, RandomSource)` | Final per-entity type spawn predicate |
| `net.minecraft.world.entity.Mob` | `checkSpawnRules(LevelAccessor, EntitySpawnReason)` | Entity-specific spawn rule override |
| `net.minecraft.world.entity.Mob` | `finalizeSpawn(ServerLevelAccessor, DifficultyInstance, EntitySpawnReason, SpawnGroupData)` | Post-construction entity initialization |
| `net.minecraft.world.entity.MobCategory` | Enum values: `MONSTER`, `CREATURE`, `AMBIENT`, `AXOLOTLS`, `UNDERGROUND_WATER_CREATURE`, `WATER_CREATURE`, `WATER_AMBIENT`, `MISC` | Spawn categories with per-chunk density caps |
| `net.minecraft.world.entity.EntitySpawnReason` | Enum values include `NATURAL`, `CHUNK_GENERATION`, `SPAWNER`, etc. | Reason for spawn — affects which rules apply |
| `net.minecraft.server.level.ServerLevel` | `tickCustomSpawners(boolean)` | Calls each `CustomSpawner.tick(ServerLevel, boolean)` |
| `net.minecraft.world.level.CustomSpawner` | `tick(ServerLevel, boolean)` | Interface for non-natural spawners (wandering trader, phantom, patrol, etc.) |

### 3.4 Spawn Entry Selection

Spawn candidates come from `NaturalSpawner.mobsAt(...)`, which calls `ChunkGenerator.getMobsAt(Holder<Biome>, StructureManager, MobCategory, BlockPos)`. This method merges two sources:

1. **Biome spawn entries**: `MobSpawnSettings.getMobs(MobCategory)` from the biome.
2. **Structure spawn entries**: Some structures (Nether Fortress, Swamp Hut, Ocean Monument) override or append spawn lists for positions within their bounds. `ChunkGenerator.getMobsAt` handles this check internally via `StructureManager`.

The result is a `WeightedList<SpawnerData>`. `getRandomSpawnMobAt` picks one entry at random from this list.

**Uncertain:** Whether `getMobsAt` returns a new `WeightedList` object per call or reuses/borrows one. If it allocates, this is already a hot-path allocation in vanilla code. Terrasect should not add *additional* allocations here.

### 3.5 Spawn Placement Rule Checks

After candidate selection, `isValidSpawnPostitionForType` verifies:
- Distance from player
- Block terrain suitability (`isValidEmptySpawnBlock`)
- Entity-specific spawn rules via `SpawnPlacements.checkSpawnRules` → `Mob.checkSpawnRules`

These checks include light level requirements, surface type, fluid state, and mob-specific overrides. They run per spawn attempt.

### 3.6 Where Biome, Structure, Dimension, Chunk, and Position Data Are Available

| Data | Available at `spawnCategoryForPosition` | Notes |
|------|-----------------------------------------|-------|
| `BlockPos` | Yes — parameter | Exact spawn position |
| `ChunkAccess` | Yes — parameter | Can be cross-cast to `ChunkAccessExtender` for `ChunkContext` |
| `ServerLevel` | Yes — parameter | Full level access including dimension |
| Dimension key | `serverLevel.dimension()` | Available |
| `MobCategory` | Yes — parameter | Current spawn category |
| `EntityType<?>` | Yes — inside `SpawnPredicate.test` | The candidate entity type |
| `Holder<Biome>` | Derived internally; not a direct parameter | Available via `ServerLevel.getBiome(BlockPos)` but costs a lookup |
| `StructureManager` | Used internally; not a parameter to `SpawnPredicate` | Not at predicate site |
| `ChunkContext` (Terrasect) | Via `((ChunkAccessExtender) chunkAccess).terrasect$getContext()` | Pre-built; no allocation |
| `Region` (Terrasect) | Via `ChunkContext.getRegion(blockX, blockZ)` | O(1) grid lookup; no allocation |

### 3.7 Hot-Path Assessment

| Method | Frequency | Allocation Risk |
|--------|-----------|----------------|
| `ServerChunkCache.tickChunks` | Once per server tick | Warm — creates `SpawnState` |
| `NaturalSpawner.spawnForChunk` | Once per active chunk per tick | Warm |
| `NaturalSpawner.spawnCategoryForChunk` | Once per category per active chunk per tick | HOT |
| `NaturalSpawner.spawnCategoryForPosition` | Multiple times per category per chunk per tick | HOT |
| `SpawnPredicate.test` | Once per spawn candidate attempt | **HOTTEST — must not allocate** |
| `ChunkContext.getRegion(x, z)` | O(1) grid read | Safe — no allocation |
| `SpawnPlacements.checkSpawnRules` | Once per validated candidate | HOT |
| `Mob.finalizeSpawn` | Once per actually-spawned mob | Warm — less frequent |

---

## 4. Loader-Specific Spawn Hooks

### 4.1 NeoForge Events

NeoForge provides dedicated spawn events fired from within the vanilla spawn flow:

| Event | Fired In | Data Available | Cancellable |
|-------|----------|----------------|-------------|
| `net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck` | `SpawnPlacements.checkSpawnRules` | `EntityType<?>`, `ServerLevelAccessor`, `EntitySpawnReason`, `BlockPos`, `RandomSource`, default result | Yes (via `Result`) |
| `net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck` | After mob is instantiated, before placement confirmed | `Mob`, `ServerLevelAccessor`, `EntitySpawnReason`, `BaseSpawner`, x/y/z | Yes (via `Result`) |
| `net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent` | `Mob.finalizeSpawn` | `Mob`, `ServerLevelAccessor`, `EntitySpawnReason`, `DifficultyInstance`, `SpawnGroupData` | Yes (`setSpawnCancelled`) |

`SpawnPlacementCheck` is the most efficient NeoForge hook: it fires for both `NATURAL` and `CHUNK_GENERATION` reasons, and the mob has not been fully instantiated yet (entity type only). However, it fires *after* the entity type has been selected, not at the candidate-list level.

### 4.2 Fabric API

Fabric API does **not** provide a runtime spawn filter event equivalent to NeoForge's `SpawnPlacementCheck`. The only spawn-related Fabric API surface is:

- `net.fabricmc.fabric.api.biome.v1.BiomeModificationContext.SpawnSettingsContext`: used at biome load time to add/remove spawn entries from `MobSpawnSettings`. This is a **setup-time** hook, not a per-spawn hot-path hook.

For Fabric, filtering runtime spawns requires a mixin directly into `NaturalSpawner`.

### 4.3 Architecture Implication

Because Fabric requires a mixin and NeoForge offers an event, a uniform approach using a common-module mixin is preferable to maintain the Terrasect invariant that all mixin code lives in `common/` and is loader-agnostic. The NeoForge event can still be subscribed to *in addition* as an optional compatibility layer, but the primary control path should be via the mixin.

---

## 5. Candidate Injection Points

### 5.1 Point A — `NaturalSpawner.spawnCategoryForPosition` (Recommended Primary)

| Field | Value |
|-------|-------|
| **Class** | `net.minecraft.world.level.NaturalSpawner` |
| **Method** | `spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)` |
| **Why relevant** | This is the innermost loop that selects and validates candidates. It has the exact `EntityType`, `BlockPos`, `ChunkAccess`, `ServerLevel`, and `MobCategory` needed for a Terrasect regional gate. |
| **Data available** | `MobCategory`, `ServerLevel` (dimension, registry), `ChunkAccess` (cross-cast to `ChunkAccessExtender` for `ChunkContext`), `BlockPos`, original `SpawnPredicate`, candidate `EntityType<?>` at the predicate call site |
| **Applies to** | Runtime spawning primarily. Also called for some natural-style spawns from custom spawners. |
| **Hot path** | Yes — called multiple times per category per chunk per tick. |
| **Allocation safety** | Must not allocate. Do **not** compose a new predicate wrapper per chunk/category because Terrasect's policy only allows allocations in dimension context or chunk context, not in runtime spawning loops. |
| **Mixin thinness** | Thin: use `@WrapOperation`/`@Redirect` around the `SpawnPredicate.test(EntityType<?>, BlockPos, ChunkAccess)` invocation and call a static Kotlin handler before delegating to the original predicate. |
| **Kotlin delegation** | `SpawnHandler.allowRuntimeSpawn(serverLevel, mobCategory, chunkAccess, blockPos, entityType)` performs a no-allocation check using prebuilt dimension/chunk context; if it returns true, the mixin calls the original predicate. |

**Preferred injection technique:** wrap the `SpawnPredicate.test(...)` call inside `spawnCategoryForPosition`. The injected Java code should only forward the existing arguments to Kotlin and call the original predicate. This keeps Java thin and avoids allocating closures/wrappers in the hot runtime spawn path.

### 5.2 Point B — `NaturalSpawner.spawnForChunk` (Chunk-Level Context Boundary)

| Field | Value |
|-------|-------|
| **Class** | `net.minecraft.world.level.NaturalSpawner` |
| **Method** | `spawnForChunk(ServerLevel, LevelChunk, SpawnState, List<MobCategory>)` |
| **Why relevant** | Called once per chunk per tick with the `LevelChunk`. It is useful as a possible future boundary for observing category iteration or confirming chunk context availability, but it should not allocate runtime wrapper objects. |
| **Data available** | `ServerLevel`, `LevelChunk` (implements `ChunkAccess`, so `ChunkAccessExtender` cast works), `SpawnState`, `List<MobCategory>` |
| **Applies to** | Runtime spawning |
| **Hot path** | Warm/hot — once per active chunk per tick |
| **Allocation safety** | No new allocations here unless they are moved into a true chunk-context construction path. Per-tick predicate wrappers are not allowed by the current design constraint. |
| **Mixin thinness** | Thin if used only for observation or to pass existing values into a static Kotlin handler. Avoid wrapper composition. |
| **Kotlin delegation** | Prefer direct static handler checks at Point A; use this point only if implementation needs chunk-level category gating that can be done without allocation. |

### 5.3 Point C — `ChunkGenerator.getMobsAt` (List-Level Filtering)

| Field | Value |
|-------|-------|
| **Class** | `net.minecraft.world.level.chunk.ChunkGenerator` |
| **Method** | `getMobsAt(Holder<Biome>, StructureManager, MobCategory, BlockPos)` |
| **Why relevant** | Returns the `WeightedList<SpawnerData>` before random selection. Filtering here removes candidates before the weighted pick, which is correct semantically (blocked mobs should not be a fallback for the random draw). |
| **Data available** | `Holder<Biome>`, `StructureManager`, `MobCategory`, `BlockPos`; `ServerLevel` is not directly available here (only accessible via `this` if the concrete subclass holds a reference) |
| **Applies to** | Runtime spawning and worldgen (`getMobsAt` is called from `mobsAt` which is called from both paths) |
| **Hot path** | Hot — called per spawn attempt position |
| **Allocation safety** | **Problematic.** Filtering a `WeightedList` without allocating requires either mutating it (unsafe) or returning a filtered copy (allocation). A bitmask or predicate-based lazy evaluation could avoid this but requires more design work. |
| **Mixin thinness** | Medium — would need to intercept the return value and wrap or filter the list |
| **Kotlin delegation** | `SpawnHandler.filterMobsAt(chunkContext, category, blockPos, original)` |
| **Verdict** | Viable for correctness, but the allocation problem makes it secondary to Point A/B. Revisit if region-based spawn *replacement* (adding new mobs) is needed. |

### 5.4 Point D — `NoiseBasedChunkGenerator.spawnOriginalMobs` (Worldgen Entry Point)

| Field | Value |
|-------|-------|
| **Class** | `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator` |
| **Method** | `spawnOriginalMobs(WorldGenRegion)` |
| **Why relevant** | Entry point for worldgen spawning. Runs once per chunk at generation time. This is the natural place to install worldgen-specific constraints before spawning begins. |
| **Data available** | `WorldGenRegion` → `ServerLevel` (via `.getLevel()`), `ChunkPos` (center of region), `Holder<Biome>`, `RandomSource` |
| **Applies to** | Worldgen spawning only |
| **Hot path** | No — once per chunk, during chunk generation |
| **Allocation safety** | Setup-time for a chunk; allocation acceptable |
| **Mixin thinness** | Thin: `@WrapOperation` wrapping the `NaturalSpawner.spawnMobsForChunkGeneration` call to intercept or skip it based on region constraints |
| **Kotlin delegation** | `SpawnHandler.shouldWorldgenSpawn(dimensionContext, chunkPos, mobCategory, spawnerData)` — returns a filter set or a skip flag |

### 5.5 Point E — `NaturalSpawner.spawnMobsForChunkGeneration` (Worldgen Candidate Loop)

| Field | Value |
|-------|-------|
| **Class** | `net.minecraft.world.level.NaturalSpawner` |
| **Method** | `spawnMobsForChunkGeneration(ServerLevelAccessor, Holder<Biome>, ChunkPos, RandomSource)` |
| **Why relevant** | Directly inside the worldgen spawn loop. Can intercept each SpawnerData candidate. No ChunkAccess with ChunkContext is directly available, but `ChunkPos` is present and `DimensionContext` can be looked up from the `ServerLevelAccessor`. |
| **Data available** | `ServerLevelAccessor` (from which dimension can be derived), `Holder<Biome>`, `ChunkPos`, `RandomSource` |
| **Applies to** | Worldgen spawning only |
| **Hot path** | Warm — once per chunk generation, not per tick |
| **Allocation safety** | Acceptable — this is setup-time per chunk |
| **Mixin thinness** | Medium: would need `@Inject` at the per-entry iteration point, or `@WrapOperation` around the weighted-list random selection |
| **Kotlin delegation** | `SpawnHandler.allowWorldgenSpawn(serverLevelAccessor, chunkPos, mobCategory, entityType)` |
| **Verdict** | Valid worldgen injection point. Slightly less ergonomic than Point D because `ChunkAccess` is not available, but `ChunkPos` + `DimensionContext` is sufficient. |

---

## 6. Terrasect Integration with ChunkContext

### 6.1 ChunkContext Availability

`ChunkAccessMixin` injects into `ChunkAccess.<init>` and builds a `ChunkContext` immediately. Every `ChunkAccess` instance (including `LevelChunk`) in a dimension where `DimensionContext` is registered will have a populated `ChunkContext` with a `PalettedGrid<Region>` covering the chunk area.

At `spawnCategoryForPosition`, the `ChunkAccess` parameter can be cast to `ChunkAccessExtender`:
```java
ChunkContext ctx = ((ChunkAccessExtender) chunkAccess).terrasect$getContext();
```
`ctx.getRegion(blockX, blockZ)` is an O(1) grid read — no allocation, no traversal.

The `Region` object carries `mobs: SelectionConstraints?` (already present in `Region.kt` as a field). If `mobs` is null, the region has no mob constraint and the predicate should pass through.

### 6.2 SelectionConstraints Reuse

`SelectionConstraints.evaluate(resourceId, tags)` is already the shared evaluation function used by structure constraints. For mob spawning, `resourceId` would be the `EntityType`'s registry name (`EntityType.builtInRegistryHolder().key().location().toString()` or equivalent), and `tags` would be entity type tags if available.

The evaluate method is allocation-free for the check itself (no string formatting, no collection creation, direct set lookups using the pre-built sets in the `SelectionConstraints` object).

---

## 7. Recommended Injection Strategy

### 7.1 Phase 1 — Runtime Spawning

**Target:** `NaturalSpawner.spawnCategoryForPosition`  
**Technique:** `@WrapOperation` or `@Redirect` around the `SpawnPredicate.test(EntityType<?>, BlockPos, ChunkAccess)` invocation.

The mixin should not allocate a new predicate wrapper. It should call a static Kotlin handler before delegating to vanilla's original predicate:

1. Java mixin receives the existing `EntityType<?>`, `BlockPos`, `ChunkAccess`, `MobCategory`, and `ServerLevel` values from the method frame/invocation.
2. Mixin calls `SpawnHandler.allowRuntimeSpawn(serverLevel, mobCategory, chunkAccess, blockPos, entityType)`.
3. Kotlin handler gets `ChunkContext` from `ChunkAccessExtender` — no allocation.
4. Kotlin handler reads `Region region = ctx.getRegion(blockX, blockZ)` — no allocation.
5. Kotlin handler evaluates a precompiled mob constraint using `EntityType<?>` identity or a cached id/tag table built in dimension/chunk context — no hot-path string allocation.
6. If Terrasect allows the candidate, the mixin calls the original `SpawnPredicate.test(...)`; otherwise it returns false.

To preserve the no-allocation rule, the future implementation must avoid both per-spawn string conversion and per-tick predicate wrapper allocation. Use a prebuilt `CompiledMobConstraints` / entity-type lookup prepared in dimension context, or another allocation-free representation prepared in chunk context.

### 7.2 Phase 2 — Worldgen Spawning

**Target:** `NoiseBasedChunkGenerator.spawnOriginalMobs` via `@WrapOperation` around the `NaturalSpawner.spawnMobsForChunkGeneration` call.

The mixin intercepts the call, derives the `DimensionContext` from the `WorldGenRegion`, resolves the active region for the chunk center, and either skips the call entirely (if the region blocks all spawning) or calls through. Per-entity filtering at the `getMobsAt` level is deferred to Phase 3.

### 7.3 Loader Targets

- **Fabric:** Common mixin in `common/src/main/java/terrasect/mixin/spawn/` (new package).
- **NeoForge:** Same common mixin applies. Optionally, a NeoForge event subscriber in `neoforge/` can additionally hook `MobSpawnEvent$SpawnPlacementCheck` for compatibility with mods that bypass `NaturalSpawner`.
- **Both loaders share the same `SpawnHandler` Kotlin object.**

---

## 8. Risks, Unknowns, and Open Questions

### 8.1 Hot-Path String Allocation

`EntityType.builtInRegistryHolder().key().location().toString()` allocates a `String` on every spawn predicate call. At Terrasect's allocation policy (no allocations in hot paths), this is not acceptable.

**Mitigation options:**
- Pre-build a `WeakHashMap<EntityType<?>, String>` or `IdentityHashMap<EntityType<?>, String>` at `DimensionContext` setup time. Since `EntityType` objects are singletons, identity hash lookup is O(1) and allocation-free per call.
- Alternatively, compare `EntityType` identity directly against pre-compiled allow/deny sets of `EntityType<?>` objects rather than `String` resource IDs. `SelectionConstraints` would need a companion pre-compiled form for mob constraints. This is consistent with `CompiledNoiseRegistry` precedent.

**Recommendation:** Add a `CompiledMobConstraints` class (parallel to `CompiledNoiseRegistry`) that pre-resolves `SelectionConstraints` into `Set<EntityType<?>>` allow/deny sets at dimension load time. Hot-path check becomes identity set membership — no string allocation, no map lookup overhead.

### 8.2 WorldGen ChunkContext Availability

`ChunkAccessMixin` builds `ChunkContext` in `ChunkAccess.<init>`, but worldgen chunk objects may be in proto-chunk form (`ProtoChunk`) rather than `LevelChunk`. Whether the proto-chunk's `ChunkContext` is populated depends on whether `DimensionContext` is registered before `ChunkAccess.<init>` fires for worldgen chunks.

**Needs verification:** Does `DimensionContext.register()` happen before the worldgen chunk pipeline runs? From the current scaffold mixin inspection (preset/scaffold mixins inject at world-load time), it likely does. But this should be explicitly confirmed by tracing the registration order.

### 8.3 Structure Spawn Overrides

`ChunkGenerator.getMobsAt` merges structure-specific spawn lists (Nether Fortress zombified piglins, Swamp Hut witches, etc.) with biome spawn lists. Filtering at the `SpawnPredicate` level will affect all merged entries equally, which is correct for a regional constraint. However, if Terrasect ever needs to *preserve* structure-specific spawns while suppressing biome spawns (or vice versa), the injection point must move to `getMobsAt` where the sources are still separate.

**Currently:** This is out of scope, but the design should not preclude it.

### 8.4 Custom Spawners

`ServerLevel.tickCustomSpawners` calls additional `CustomSpawner.tick(ServerLevel, boolean)` implementations for wandering trader, phantom, patrol, cat, and similar spawns. These bypass `NaturalSpawner.spawnCategoryForPosition` entirely. They will not be affected by a `SpawnPredicate` mixin.

**Impact:** If a region should suppress wandering traders or phantoms, a separate injection point is needed per `CustomSpawner` implementation. This is a larger scope item; it is noted here as a known gap.

### 8.5 Modded Spawn Systems

Some mods implement entirely custom spawning (Lycanite Mobs, Ice & Fire, etc.) that does not go through `NaturalSpawner`. Terrasect's `SpawnPredicate` mixin will have no effect on these.

**Mitigation:** For modpacks, the most effective coverage is through `SpawnPlacements.checkSpawnRules` — which all well-behaved mods use for placement validation — or through the NeoForge `SpawnPlacementCheck` event. A NeoForge-specific subscriber can serve as a second layer.

**Unknown:** Which popular modded spawning systems do and do not call through `SpawnPlacements.checkSpawnRules`. This should be surveyed before declaring coverage complete.

### 8.6 Spawn Replacement

The goal document mentions allowing *replacement* populations when a region suppresses a mob. `SelectionConstraints` currently only allows/blocks by resource ID, tag, or namespace. Replacement would require a new `MobSpawnConstraints` type (parallel to `StructureConstraints`) that can name substitute entity types. This is out of scope for the first implementation but the data model extension is straightforward.

### 8.7 `WeightedList` Internals

`NaturalSpawner.mobsAt` returns a `WeightedList<SpawnerData>`. It is uncertain whether this is the exact same list object held in `MobSpawnSettings` (shared, must not be mutated) or a freshly allocated copy. Filtering at this level requires knowing the ownership model.

**Needs verification:** Decompile `ChunkGenerator.getMobsAt` fully to determine if it returns the biome's list directly or a merged copy.

### 8.8 `spawnCategoryForPosition` Overloads

`NaturalSpawner` has two overloads of `spawnCategoryForPosition`:
- `spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)` — main overload
- `spawnCategoryForPosition(MobCategory, ServerLevel, BlockPos)` — simpler overload without predicate

The simpler overload should also be covered if it is called in practice. The call graph should be verified.

---

## 9. Proposed Implementation Plan (Next Step)

This section proposes the implementation sequence. **No code is written here.**

### Step 1: Add `MobSpawnConstraints` to Region

Extend `Region` to hold a new `MobSpawnConstraints` type (distinct from the existing `mobs: SelectionConstraints?` field, or by converting it if `SelectionConstraints` is sufficient). For the first pass, `SelectionConstraints` is likely sufficient since allow/block by mod, tag, and name covers the main use cases.

The `Region.mobs: SelectionConstraints?` field already exists. No data model change may be needed for Phase 1.

### Step 2: Add `CompiledMobConstraints` for Hot-Path Resolution

Create `terrasect.lookup.CompiledMobConstraints` (or extend `DimensionContext`) to pre-compile region `mobs` constraints into identity-keyed `Set<EntityType<?>>` allow and deny sets. Build this at `DimensionContext` registration time.

This eliminates the String allocation concern on the hot path.

### Step 3: Create `SpawnHandler` Kotlin Object

Create `terrasect.handler.SpawnHandler` in `common/src/main/kotlin/terrasect/handler/SpawnHandler.kt`.

Responsibilities:
- `allowRuntimeSpawn(serverLevel: ServerLevel, category: MobCategory, chunkAccess: ChunkAccess, pos: BlockPos, type: EntityType<*>): Boolean` — allocation-free runtime gate called from the Java mixin before vanilla `SpawnPredicate.test(...)`.
- `allowWorldgenSpawn(dimensionContext: DimensionContext, chunkPos: ChunkPos, category: MobCategory, type: EntityType<*>): Boolean` — worldgen check.
- Internal helpers for region lookup and constraint evaluation.

### Step 4: Create Runtime Spawn Mixin

Create `terrasect.mixin.spawn.NaturalSpawnerMixin` in `common/src/main/java/terrasect/mixin/spawn/`.

Target: `NaturalSpawner.spawnCategoryForPosition` using `@WrapOperation` or `@Redirect` around the `SpawnPredicate.test(EntityType<?>, BlockPos, ChunkAccess)` invocation.
Call `SpawnHandler.allowRuntimeSpawn(serverLevel, mobCategory, chunkAccess, blockPos, entityType)` first; only call vanilla's original predicate if Terrasect allows the candidate.

Register in the appropriate mixin JSON configs for Fabric and NeoForge.

### Step 5: Create Worldgen Spawn Mixin

Create `terrasect.mixin.spawn.NoiseBasedChunkGeneratorSpawnMixin` targeting `NoiseBasedChunkGenerator.spawnOriginalMobs`.

Use `@WrapOperation` around the `NaturalSpawner.spawnMobsForChunkGeneration` call to intercept worldgen spawning.

Calls `SpawnHandler.allowWorldgenSpawnForChunk(worldGenRegion, chunkPos)` as a chunk-level gate.

### Step 6: Wire into RegionDefinition DSL

Ensure the `RegionDefinition` builder exposes `mobs { ... }` using `SelectionConstraints.Builder`, consistent with the existing structure constraint pattern.

### Step 7: Write Tests

Snapshot or unit tests for `SpawnHandler.allowRuntimeSpawn` behavior under various constraint configurations. Allocation-sensitive verification should prove the runtime gate does not allocate; JMH or profiler-based checks can be added later if needed.

---

## 10. Appendix: Verified Exact Class Names (from Minecraft 1.21.11 Fabric-Loom Mapped Jar)

All class names below were verified by `javap` inspection of  
`minecraft-common-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar`:

- `net.minecraft.world.level.NaturalSpawner`
- `net.minecraft.world.level.NaturalSpawner$SpawnState`
- `net.minecraft.world.level.NaturalSpawner$SpawnPredicate`
- `net.minecraft.world.level.NaturalSpawner$AfterSpawnCallback`
- `net.minecraft.world.level.NaturalSpawner$ChunkGetter`
- `net.minecraft.world.level.biome.MobSpawnSettings`
- `net.minecraft.world.level.biome.MobSpawnSettings$SpawnerData`
- `net.minecraft.world.level.biome.MobSpawnSettings$MobSpawnCost`
- `net.minecraft.world.entity.MobCategory`
- `net.minecraft.world.entity.SpawnPlacements`
- `net.minecraft.world.entity.EntitySpawnReason`
- `net.minecraft.world.level.chunk.ChunkGenerator` (method `getMobsAt`, `spawnOriginalMobs`)
- `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator` (method `spawnOriginalMobs`)
- `net.minecraft.server.level.ServerChunkCache` (field `lastSpawnState`, method `tickSpawningChunk`)
- `net.minecraft.server.level.WorldGenRegion` (methods `getLevel`, `getChunk`, `getBiomeManager`, `getRandom`, `dimensionType`)
- `net.minecraft.world.level.CustomSpawner`
- `net.minecraft.world.entity.Mob` (methods `checkSpawnRules`, `finalizeSpawn`)

NeoForge event classes verified from  
`compiledWithNeoForge_26d24ad01ca9e73254d766805ca4c99222e4a5c6_output.jar`:

- `net.neoforged.neoforge.event.entity.living.MobSpawnEvent`
- `net.neoforged.neoforge.event.entity.living.MobSpawnEvent$PositionCheck`
- `net.neoforged.neoforge.event.entity.living.MobSpawnEvent$SpawnPlacementCheck`
- `net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent`
- `net.neoforged.neoforge.event.entity.living.MobDespawnEvent`

Fabric API class verified from `fabric-biome-api-v1-17.1.1+4fc5413f3e.jar`:

- `net.fabricmc.fabric.api.biome.v1.BiomeModificationContext$SpawnSettingsContext`

**Uncertain class names (intermediary/obfuscated, not verified with mapped names):**
- The method-level names `method_35239`, `method_35238`, `method_27819` in `NaturalSpawner` are intermediary names for private methods — their purpose is unclear without decompilation. They are likely helper utilities inside the spawn loop. These should not be targeted directly.
- `LocalMobCapCalculator` — referenced from `NaturalSpawner$SpawnState` but not inspected in detail.
- `PotentialCalculator` — referenced in `SpawnState`, related to spawn potential/mob density scoring.
