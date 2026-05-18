Status: DONE — Documentation written to `docs/MINECRAFT_STRUCTURES.md`.

## Progress log

### Source files inspected (all in `minecraft/net/minecraft/`)

- `world/level/levelgen/structure/placement/StructurePlacement.java` — abstract base; `isStructureChunk`, `FrequencyReductionMethod` enum (4 variants), `ExclusionZone` record, `placementCodec` shared fields.
- `world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java` — `getPotentialStructureChunk`, `spacing`/`separation`/`spread_type` fields, codec with validation (`spacing > separation` enforced).
- `world/level/levelgen/structure/placement/ConcentricRingsStructurePlacement.java` — `isPlacementChunk` delegates to pre-computed list; `distance`/`spread`/`count`/`preferred_biomes` fields.
- `world/level/levelgen/structure/placement/RandomSpreadType.java` — `LINEAR` (uniform) and `TRIANGULAR` (bell-shaped) offset distributions.
- `world/level/levelgen/structure/placement/StructurePlacementType.java` — registry interface; two singletons: `RANDOM_SPREAD`, `CONCENTRIC_RINGS`.
- `world/level/levelgen/structure/StructureSet.java` — `structures` list + `placement`; `StructureSelectionEntry(structure, weight)`.
- `world/level/levelgen/structure/BuiltinStructureSets.java` — 20 registered set keys.
- `world/level/levelgen/structure/Structure.java` — `generate()`, `findValidGenerationPoint()`, `GenerationContext.makeRandom(worldSeed, chunkPos)`, `StructureSettings`, `TerrainAdjustment`.
- `world/level/levelgen/structure/StructureStart.java` — validity check (`!pieceContainer.isEmpty()`), `INVALID_START` sentinel.
- `world/level/chunk/ChunkGeneratorStructureState.java` — `createForNormal`/`createForFlat`, lazy `generatePositions()`, `generateRingPositions()` async algorithm (full loop read), `hasStructureChunkInRange`.
- `world/level/chunk/ChunkGenerator.java` (lines 452–577) — `createStructures`, `tryGenerateStructure`, weighted selection loop.
- `world/level/levelgen/WorldgenRandom.java` — `setLargeFeatureWithSalt` and `setLargeFeatureSeed` exact formulas.

### Verification notes

- All code blocks in the doc were verified against the actual source (not paraphrased from subagent summary).
- `concentricRingsSeed = 0L` for flat worlds confirmed at `ChunkGeneratorStructureState.java:47`.
- Exclusion zone `@Deprecated` annotation confirmed at `StructurePlacement.java:128`.
- `HIGHLY_ARBITRARY_RANDOM_SALT = 10387320` confirmed at `StructurePlacement.java:27`, used at line 115.
- Weighted selection loop verified at `ChunkGenerator.java:494–533`: seed is `setLargeFeatureSeed` (no salt), not `setLargeFeatureWithSalt`.
- Ring radius formula `4*d + d*ring*6` confirmed at `ChunkGeneratorStructureState.java:116`.
- Output: `docs/MINECRAFT_STRUCTURES.md` (~370 lines, 13 sections).
