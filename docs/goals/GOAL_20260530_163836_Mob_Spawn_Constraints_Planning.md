# Mob Spawn Constraints Planning

Status: REVIEW_READY

Research step completed 2026-05-30 by claude-sonnet-4-6 (session e3fd5b47-83b3-44f2-9065-dbbabb0ba20c).
Artifact: `docs/MOB_SPAWN_CONSTRAINTS_RESEARCH.md`
Branch: `feature/mob-spawn-constraints-planning`
PR: pending creation at user request.
Summary: Verified Minecraft 1.21.11 runtime and worldgen spawn call chains via `javap` inspection of the Fabric-loom mapped jar. After review, the preferred runtime strategy is an allocation-free wrap/redirect around `NaturalSpawner.spawnCategoryForPosition`'s `SpawnPredicate.test(...)` call, delegating directly to a Kotlin handler; avoid per-tick predicate wrapper allocation. Worldgen entry point remains `NoiseBasedChunkGenerator.spawnOriginalMobs`. Key open items before implementation: hot-path entity id/tag precompilation (`CompiledMobConstraints`), proto-chunk `ChunkContext` availability during worldgen, `WeightedList` ownership in `getMobsAt`, and custom-spawner coverage gaps.
Verification: documentation-only change; no production code or tests modified. `git diff --check` passed before commit.

Worktree: `/home/alex/terrasect/.worktrees/mob-spawn-constraints-planning`

Branch: `feature/mob-spawn-constraints-planning`

Scope: research and design planning only. Do not implement mob spawn constraints in this goal. Do not modify production code. Do not add tests.

## Purpose

Terrasect currently uses region-derived constraints to guide terrain, climate, noise, and structures. The next major constraint category should guide mob population so each region can support a stronger narrative identity: safer routes, hostile wildlands, undead ruins, animal-rich plains, empty dead zones, or modpack-specific population control.

Mob spawn constraints should eventually let Terrasect influence which mobs can appear at a location without turning the system into a hardcoded mob list. The goal is to let world coordinates, current region constraints, dimension context, and chunk context shape the population rules that Minecraft and modded loaders already use.

This matters especially in modpacks. Many mods add mobs independently, and the combined spawn population can become inflated or narratively incoherent. Terrasect should provide a coordinated constraint layer that can suppress some candidates, allow others to take their place, and keep population aligned with the region story.

## Why research comes first

Minecraft has more than one spawning path. Before designing Terrasect hooks, we need to identify where Minecraft decides spawn candidates, where it validates spawn rules, and which paths are hot enough that allocation mistakes would be costly.

The research phase should answer:

- Which classes and methods drive mob spawning during world generation?
- Which classes and methods drive runtime spawning after chunks already exist?
- Where are biome spawn lists, spawn categories, density caps, light rules, structure rules, and entity-specific checks applied?
- Which hooks are shared between vanilla and modded spawn entries?
- Which calls have access to world coordinates, dimension/server-level context, chunk context, biome, structure context, and random state?
- Which paths are hot and must not allocate?
- Which path is the best thin Java mixin entry point, and which data can be handed to Kotlin handlers?

Research should inspect the current Minecraft 1.21.11 mapped sources and the existing Terrasect constraint patterns before proposing implementation.

## Worldgen spawning vs. runtime spawning

### World generation spawning

Worldgen spawning is population that happens while chunks are being generated or decorated. It may use biome spawn settings, generation step context, chunk position, structure/terrain context, and worldgen random state. It is part of chunk creation, so it must be deterministic for a given world seed and chunk state.

For Terrasect, worldgen spawn constraints should eventually be derived from the target chunk and the region constraints active at those coordinates. This path is likely the right place to shape baseline regional population: what creatures are native to a region, what hostile mobs belong in a ruin-dense area, or which mobs should never appear in a narrative zone.

Research must determine whether worldgen spawning has a candidate-list selection point, a per-candidate validation point, or both. The future design should prefer the lowest-risk hook that can filter or redirect candidates without copying large collections or allocating per spawn attempt.

### Runtime spawning

Runtime spawning happens after chunks exist while the server is ticking. It includes natural mob spawning around players and may involve category caps, despawn rules, light checks, biome entries, chunk/player distance, entity-specific placement rules, and dimension rules.

For Terrasect, runtime spawn constraints should eventually enforce ongoing regional identity. If a region suppresses a mob during generation, the runtime path should not immediately refill the area with that same mob. Conversely, if a region allows a replacement population, runtime spawning should keep that population stable over time.

This path is likely hotter and more frequent than worldgen spawning. Future work must be very careful about allocations, lookups, logging, and collection transformations here.

## Future constraint source model

The eventual mob spawn constraint system should pull context from:

- **World coordinates:** block/chunk position used to find the active Terrasect region.
- **Current region constraints:** narrative rules already derived by the region registry.
- **Dimension context:** dimension-level cached data, registry access, rule tables, and stable references that can be allocated or prepared outside hot spawn attempts.
- **Chunk context:** chunk-level cached region/constraint state prepared once and reused by hot spawning calls.

The implementation should avoid recomputing region data or allocating temporary objects for every spawn candidate. Dimension context and chunk context are the allowed places to prepare data structures. Hot-path calls should receive or resolve already-prepared context and perform direct checks.

## Performance constraints

Future implementation must follow these constraints:

- No allocations in hot paths.
- Allocations are allowed only while building dimension context or chunk context.
- Avoid per-spawn collection copies, streams, lambdas, boxing, temporary lists, temporary maps, string formatting, and uncapped diagnostics.
- Avoid broad region lookups during every candidate check if chunk context can cache the required constraint state.
- Keep disabled diagnostics near-zero cost.
- Treat runtime spawning as stricter than worldgen spawning because it can run continuously around players.

Research should explicitly mark each candidate hook as hot, warm, or setup-time, and should state what data is available at that hook without allocation.

## Intended design direction

The future implementation should be Kotlin-first:

- Put policy, filtering, replacement decisions, diagnostics, and context interpretation in Kotlin handlers.
- Keep Java mixins thin: capture Minecraft arguments, call the Kotlin handler, and return/apply the handler result.
- Keep loader-specific code limited to loader-specific entry points when possible.
- Preserve the existing Terrasect style: direct code, explicit constraints, minimal magic, and no compatibility wrappers unless needed.

The Java mixin layer should not own mob-spawn policy. It should only bridge Minecraft internals to stable Terrasect handlers.

## Research deliverable for the next step

A later research task should produce a concise document that includes:

1. The relevant Minecraft 1.21.11 classes and methods for worldgen mob spawning.
2. The relevant Minecraft 1.21.11 classes and methods for runtime mob spawning.
3. The available context at each candidate hook: coordinates, level/dimension, chunk, biome, structure, entity type, spawn category, random, and registry access.
4. A hot-path allocation risk assessment for each candidate hook.
5. Recommended hook points for the first implementation pass.
6. A proposed Kotlin handler API shape that can be implemented without hot-path allocations.
7. Open questions for modded spawn integration and replacement behavior.

## Non-goals for this planning goal

- Do not implement mob spawn constraints.
- Do not add or modify mixins.
- Do not modify Kotlin handlers or production code.
- Do not add tests.
- Do not run GameTests for this goal.
- Do not open a PR unless a later task explicitly asks for one.

## Completion criteria

This planning goal is complete when:

- A dedicated worktree exists for mob spawn constraints planning.
- This goal document exists under `docs/goals/`.
- The document captures purpose, research need, worldgen/runtime distinction, context sources, allocation constraints, Kotlin-first design, and non-implementation boundaries.
- `git status` shows only documentation changes for this planning scope in the new worktree.
