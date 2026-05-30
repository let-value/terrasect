# Mob Spawn Constraints Implementation

## Task
**Date:** 20260530
**Submitted By:** Hermes Agent
**Status:** COMPLETED

### Request
A new agent session has started.

The previous step was completed successfully. The research and planning artifact is available at:

`docs/goals/GOAL_20260530_163836_Mob_Spawn_Constraints_Planning.md`

Move from research/planning into the first implementation step for Terrasect mob spawn constraints.

Delegate the implementation work to Claude Code CLI and orchestrate it carefully.

Claude Code should start implementing the mob spawn constraint system according to the research artifact, existing Terrasect architecture, and project conventions.

Implementation scope for this step is the smallest safe slice only:

- Define the mob spawn constraint model.
- Integrate it with the existing region / coordinate constraint lookup pattern.
- Add Kotlin-side handler logic where most decisions will live.
- Add thin Java mixin hooks only where needed.
- Ensure no allocations occur in hot paths.
- Reuse dimension context or chunk context for any required allocation/caching.
- Preserve existing behavior when no mob spawn constraints apply.

Expected behavior eventually includes suppressing specific mobs by region, allowing replacements later, and supporting both worldgen spawning and runtime spawning. For this implementation step, only the parts clearly supported by the research artifact and current codebase should be implemented.

Hard constraints:

- Read the research artifact before implementation.
- Follow existing project architecture and naming conventions.
- Keep Java mixins extremely thin.
- Put the main logic in Kotlin.
- Do not allocate in hot paths.
- Only allocate in dimension context or chunk context.
- Do not introduce broad rewrites.
- Do not change unrelated systems.
- Do not guess class or method names; verify them from code/dependencies/mappings.
- Preserve vanilla behavior when Terrasect constraints are absent or disabled.
- Add tests only if the project has a clear existing pattern for this type of logic.
- Build or run the relevant verification command after implementation, if available.

Claude Code delegation instructions:

1. Read the planning artifact.
2. Inspect the existing codebase for analogous constraint systems.
3. Propose the smallest safe implementation slice before editing.
4. Implement that slice.
5. Keep mixins thin and delegate to Kotlin handlers.
6. Avoid hot-path allocations.
7. Run the most relevant build/test/check command.
8. Report:
   - files changed
   - what was implemented
   - what was intentionally left for later
   - unresolved risks or assumptions
   - verification results

Stop after the first implementation slice is complete and verified. Do not proceed to additional phases or large follow-up implementation work without a new instruction.

### Context
- Dedicated implementation worktree: `/home/alex/terrasect/.worktrees/mob-spawn-constraints-implementation`
- Branch: `feature/mob-spawn-constraints-implementation`
- Active base branch: `reborn`
- Research artifact to read first: `docs/MOB_SPAWN_CONSTRAINTS_RESEARCH.md` in the planning worktree and the planning goal file `docs/goals/GOAL_20260530_163836_Mob_Spawn_Constraints_Planning.md`
- Terrasect project gate: load `terrasect` skill before changing code.
- Existing Terrasect constraint architecture to inspect:
  - `common/src/main/kotlin/terrasect/generation/DimensionContext.kt`
  - `common/src/main/kotlin/terrasect/generation/ChunkContext.kt`
  - `common/src/main/kotlin/terrasect/handler/NoiseHandler.kt`
  - `common/src/main/kotlin/terrasect/handler/ClimateHandler.kt`
  - `common/src/main/kotlin/terrasect/handler/StructureHandler.kt`
  - `common/src/main/kotlin/terrasect/lookup/CompiledNoiseRegistry.kt`
  - `common/src/main/kotlin/terrasect/lookup/CompiledStructureLookup.kt`
  - `common/src/main/kotlin/terrasect/definition/SelectionConstraints.kt`
  - `common/src/main/kotlin/terrasect/definition/Region.kt`
  - `common/src/main/kotlin/terrasect/definition/RegionDefinition.kt`
- Verified mapped Minecraft sources from unpacked sources at `/home/alex/terrasect/minecraft`:
  - `net.minecraft.world.level.NaturalSpawner`
  - `net.minecraft.world.level.chunk.ChunkGenerator`
  - `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator`
  - `net.minecraft.server.level.ServerChunkCache`
- Verified runtime hot-path method of interest from mapped sources:
  - `NaturalSpawner.spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)`
  - candidate call site: `SpawnPredicate.test(EntityType<?>, BlockPos, ChunkAccess)`
- Verified worldgen entry point:
  - `NoiseBasedChunkGenerator.spawnOriginalMobs(WorldGenRegion)`
  - `NaturalSpawner.spawnMobsForChunkGeneration(ServerLevelAccessor, Holder<Biome>, ChunkPos, RandomSource)`
- Relevant existing Terrasect behavior:
  - `Region` already has `mobs: SelectionConstraints?`
  - `RegionDefinition` already exposes `mobs { ... }`
  - `SelectionConstraints.evaluate(resourceId, tags)` already exists for structure/biome filtering
  - existing hot-path handlers keep Java mixins thin and delegate to Kotlin

Smallest safe implementation slice selected for this run:
- runtime spawn gating only, using the `SpawnPredicate.test(...)` call inside `NaturalSpawner.spawnCategoryForPosition`
- a Kotlin handler that resolves the active region from chunk context and evaluates mob selection constraints without allocating in the hot path
- any needed compiled mob-constraint lookup in dimension context, if required for zero-allocation evaluation
- no worldgen hook yet unless it is required to make the runtime slice coherent
- no custom-spawner coverage yet

### Acceptance criteria
A correct result for this task will:

- implement the smallest safe runtime slice only, with no broad rewrites
- preserve vanilla behavior when mob constraints are absent
- keep the Java mixin thin and the main logic in Kotlin
- avoid hot-path allocations
- reuse existing dimension/chunk context patterns
- keep unrelated systems untouched
- run the most relevant verification command available and report its result
- update this goal file with progress, blockers, verification, and final outcome

### Run metadata
- Provider: Claude Code CLI
- Session ID: 8f39f3aa-ff31-4e35-b296-32b2ce9e4af5
- Retry count: 2
- Blocked reason: none
- Verification: `./gradlew :common:test` ✅

---

## Response

- Implemented the first runtime-only mob spawn constraint slice.
- Added `CompiledMobLookup`, which is built in `DimensionContext` and precomputes per-region, per-`EntityType` boolean decisions at dimension setup time.
- Added `MobHandler` to resolve the active `ChunkContext` / `DimensionContext`, read the precomputed lookup, and decide spawn permission without calling generic selection evaluation on the hot path.
- Added a thin Java mixin on `NaturalSpawner.spawnCategoryForPosition(...)` / `SpawnPredicate.test(...)` that delegates policy to Kotlin.
- Registered a dedicated mob instrumentation scope/event and wired the new spawn mixin into `common.mixins.json`.

- Files changed:
  - `common/src/main/kotlin/terrasect/generation/DimensionContext.kt`
  - `common/src/main/kotlin/terrasect/handler/MobHandler.kt`
  - `common/src/main/kotlin/terrasect/lookup/CompiledMobLookup.kt`
  - `common/src/main/kotlin/terrasect/instrumentation/TerrasectMetrics.kt`
  - `common/src/main/java/terrasect/mixin/spawn/NaturalSpawnerMixin.java`
  - `common/src/main/resources/common.mixins.json`
  - `docs/goals/GOAL_20260530_171456_Mob_Spawn_Constraints_Implementation.md`

- Verified hook points:
  - Runtime natural spawning: `NaturalSpawner.spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)`
  - Hot decision point: `SpawnPredicate.test(EntityType<?>, BlockPos, ChunkAccess)`
  - Context construction: `DimensionContext` now builds mob lookup alongside the existing compiled lookups

- Runtime allocation strategy:
  - All mob constraint decisions are precomputed during dimension-context construction.
  - Runtime spawn checks only do chunk-context lookup + region lookup + identity-map boolean lookup.
  - The hot path does not call `SelectionConstraints.evaluate(...)` and does not allocate additional runtime lookup objects.
  - The mixin remains thin and simply forwards to Kotlin policy code.

- Intentionally left for later:
  - Worldgen mob-spawn interception
  - Custom-spawner coverage
  - Spawn substitution / replacement behavior
  - Any broader mob-constraint config/data-model expansion beyond the current region constraint hookup

- Verification:
  - Ran `./gradlew :common:test`
  - Result: `BUILD SUCCESSFUL`

- Remaining risks / unknowns:
  - Runtime behavior depends on chunks carrying Terrasect chunk context, which is the existing project pattern.
  - This slice currently covers runtime natural spawning only; worldgen and custom-spawner paths remain intentionally out of scope.
  - No dedicated mob GameTests were added in this slice because the implementation matched the existing first-step scope and the targeted Gradle verification passed.

