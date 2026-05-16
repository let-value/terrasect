# Goal: Structure Constraints Implementation Retry

Status: DONE
Created: 2026-05-16 07:04:34
Branch: structures-constraints
Worktree: /home/alex/terrasect

## Objective
Finish the Terrasect structure-constraints implementation on the `structures-constraints` branch.

## Context
We already completed the structure-placement research doc:
- `docs/MINECRAFT_STRUCTURES.md`

There is also an earlier implementation attempt with partial Kotlin progress in the worktree. Treat the current repo state as the starting point and continue from there rather than restarting from scratch.

## Required Work
1. Read `docs/MINECRAFT_STRUCTURES.md` for the vanilla placement context.
2. Inspect the existing Kotlin structure-constraint code and the master-branch Java reference implementation.
3. Implement structure constraints in Kotlin in the same style as the existing climate/noise systems.
4. Add thin Fabric mixins in Java only where needed.
5. Leave NeoForge alone for now.
6. Do not write tests yet.
7. Keep the implementation native to the project style and performance constraints.
8. When finished, push the branch and create a PR.

## Quality Bar
- Prefer direct, readable code over abstractions.
- Avoid unnecessary allocations or hot-path overhead.
- Match the project's existing climate/noise architecture and naming patterns.
- Keep Fabric mixins thin and delegate logic to Kotlin/common code.

## Progress Log

### Implementation audit (2026-05-16)
Reviewed all existing branch work. Found the following files already in place from the earlier attempt:

**New files (untracked at audit time):**
- `common/src/main/java/terrasect/mixin/structure/ChunkGeneratorStructureMixin.java` — thin Java mixin wrapping `possibleStructureSets()` in `ChunkGenerator.createStructures`; delegates filtering to `StructureHandler`
- `common/src/main/kotlin/terrasect/handler/StructureHandler.kt` — Kotlin entry point; resolves `DimensionContext`, traverses to the current region, applies `SelectionConstraints` via the compiled lookup
- `common/src/main/kotlin/terrasect/lookup/CompiledStructureLookup.kt` — indexes all structure sets by identity at init time; filters them per `SelectionConstraints` with a `ConcurrentHashMap` cache

**Modified files:**
- `common/src/main/kotlin/terrasect/definition/SelectionConstraints.kt` — bug fix: namespace extraction was returning raw `resourceId` instead of calling `extractNamespace(resourceId)`
- `common/src/main/kotlin/terrasect/generation/DimensionContext.kt` — added `allSets: List<Holder<StructureSet>>` constructor parameter; builds `CompiledStructureLookup` at init; `register()` already received `structureSets` from `LevelMixin` and now passes it through
- `common/src/main/resources/common.mixins.json` — registered `structure.ChunkGeneratorStructureMixin`

**Pre-existing wiring (unchanged):**
- `common/src/main/java/terrasect/mixin/scaffold/LevelMixin.java` — already captured `possibleStructureSets()` and passed it to `DimensionContext.register()` at HEAD
- `common/src/main/kotlin/terrasect/definition/Region.kt` — already had `structures: SelectionConstraints?` field
- `common/src/main/kotlin/terrasect/definition/RegionDefinition.kt` — already had full DSL builder for `structures`

### Verification (2026-05-16)
- `./gradlew :common:compileKotlin :common:compileJava` — **PASS** (UP-TO-DATE)
- `./gradlew spotlessKotlinApply spotlessJavaApply` — applied (import ordering in ClimateHandler/NoiseHandler; indentation in CompiledNoiseConstraints/DimensionContext/StructureHandler/CompiledStructureLookup)
- `./gradlew spotlessKotlinCheck spotlessJavaCheck` — **PASS**
- `./gradlew build` — **PASS** (BUILD SUCCESSFUL in 2m 44s)
- PR created: https://github.com/let-value/terrasect/pull/51

### PR review follow-up (2026-05-16)
- Resolved the remaining review comment in `CompiledStructureLookup.kt` by replacing the constructor-based `RandomSpreadStructurePlacement` rebuild with a blank-instance + accessor mutation path in `RandomSpreadStructurePlacementMixin`.
- Re-ran `./gradlew :common:compileJava :common:compileKotlin` after the follow-up — **PASS**.
- Resolved the GitHub review thread after push (`PRRT_kwDOQj8JVM6CjOw5`).

### Final result
All structure-constraint machinery is complete and building clean:

1. **`CompiledStructureLookup`** — built once per dimension at `DimensionContext` init; indexes every `StructureSet` entry by object identity (avoids string lookups on the hot path); caches filtered results per `SelectionConstraints` instance.
2. **`StructureHandler`** — per-chunk entry point; looks up the region via the traverser (chunk-center block coords), reads `region.structures`, and returns the pre-filtered set list or `null` (meaning: use vanilla default).
3. **`ChunkGeneratorStructureMixin`** — single `@WrapOperation` on `possibleStructureSets()` inside `ChunkGenerator.createStructures`; returns filtered list when a region has structure constraints, vanilla list otherwise.
4. **`SelectionConstraints.evaluate`** — bug fixed; `extractNamespace` was not being called.
5. **NeoForge** — untouched.
6. **Tests** — deferred per spec.

## Completion Criteria
- [x] Structure constraints implemented in Kotlin (`CompiledStructureLookup`, `StructureHandler`)
- [x] Fabric hook in place (`ChunkGeneratorStructureMixin` registered in `common.mixins.json`)
- [x] Branch pushed
- [x] PR created
- [x] Goal file records verification and final result
