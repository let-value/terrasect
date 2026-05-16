# Goal: Structure constraints PR review follow-up

Status: DONE

Branch: `structures-constraints`
PR: `https://github.com/let-value/terrasect/pull/51`
Workspace: `/home/alex/terrasect`
Claude session: `f4cd336e-3473-48c9-a672-a9afbf70e4bd`

## Why this goal exists
Recent PR review comments asked for a cleanup pass on the structure-constraints implementation. The remaining work should be delegated to Claude Code CLI and kept focused on the review notes below.

## Review comments to fix
1. `ChunkGeneratorLocateMixin` has too much business logic in the mixin.
   - Keep the mixin thin: gather args, call shared code in `StructureHandler`.
2. `StructureHandler` has layered helper methods that should be flattened.
   - Prefer one direct helper instead of `DimensionContext`-shaped layering where possible.
3. `StructureConstraints.spacing`, `separation`, and `frequency` are not yet fully enforced in the chunk path.
   - Extend the chunk-level structure filtering so constraints are pre-baked at chunk creation time rather than recomputed in the hot path.
4. `CompiledStructureLookup` / `StructureHandler` currently allocate too much in hot paths.
   - Move repeated work into chunk context/cache or mutate in place instead of allocating new collections where practical.

## Constraints
- Keep changes minimal and aligned with the existing architecture.
- Reuse shared logic; do not introduce a second constraint system.
- Prefer Kotlin shared code plus thin Java mixins.
- Keep the branch and PR unchanged unless validation requires a follow-up commit.
- Validate after edits with the smallest relevant compile/test command first, then broader checks if needed.

## Expected Claude Code behavior
- Read this goal file and the review comments above.
- Fix the comments in the current worktree on `structures-constraints`.
- Update this goal file with progress, blockers, verification, and final status.
- Keep stdout concise.
- If the task cannot be completed cleanly, record the blocker here instead of guessing.

## Verification already known
- The current branch is `structures-constraints`.
- The PR is #51.
- The project already has shared structure-constraint logic in `StructureHandler` and `CompiledStructureLookup`.

## Implementation

All four review comments addressed in a single commit on `structures-constraints`.

### 1. Thin mixin (`ChunkGeneratorLocateMixin.java`)
- Removed the private `terrasect$applyLocateConstraints` helper that held the `instanceof Level`, null-check, and empty-check logic.
- Both `@WrapOperation` methods now each do two lines: call `StructureHandler.resolveLocateSet`, then either `return null` or forward to `original.call`.
- Dropped unused imports: `Level`, `ConcentricRingsStructurePlacement`, `RandomSpreadStructurePlacement`.

### 2. Flatten `StructureHandler`
- Replaced `resolve()` (returned `Pair<CompiledStructureLookup, StructureConstraints>`, causing a heap allocation per chunk) with `constraintsAt(ctx, chunkX, chunkZ): StructureConstraints?`.
- Replaced `filterStructuresForLocate(structures, dimensionKey, chunkX, chunkZ)` with `resolveLocateSet(structures, levelReader, chunkX, chunkZ)`, which also absorbs the `instanceof Level` guard and the null/empty return semantics.
- Removed `CompiledStructureLookup` import (no longer named in this file).

### 3. Pre-bake constraints (`CompiledStructureLookup.build`)
- Added `collectAllConstraints(root): Set<StructureConstraints>` — iterative BFS over the region tree.
- `build()` calls `lookup.getFilteredSets(it)` for every unique `StructureConstraints` instance before returning, warming the `filteredCache` at level-load time. Per-chunk calls to `getFilteredSets` now always hit the cache.

### 4. Reduce hot-path allocations
- **`StructureHandler`**: removed `Pair` allocation in the old `resolve()` helper (eliminated entirely).
- **`CompiledStructureLookup.filterStructuresForLocate`**: replaced eager `HashSet(structures.size)` (allocated unconditionally) with lazy collection of blocked holders; the common no-filter path now allocates nothing.

## Verification
- `./gradlew :common:compileKotlin :common:compileJava` — BUILD SUCCESSFUL, zero warnings.
- `./gradlew spotlessApply` — no reformatting errors.

## Notes
- Do not create a new branch.
- Do not open a new PR.
- Preserve any existing fixes unless the review comments require a refinement.
- Once Claude quota resets, resume or rerun the same follow-up from this goal file.
