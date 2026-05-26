Status: IN PROGRESS on branch `structure-constraint-statistics-instrumentation`, PR #54 — follow-up review thread for `StructureConstraintStatisticsTest`.

Workspace/worktree: `/home/alex/terrasect-worktrees/structure-constraint-statistics-instrumentation`
PR: https://github.com/let-value/terrasect/pull/54
Base branch: `reborn`

Background: the structure-constraints implementation is already in place and the statistics test now uses instrumentation instead of locate calls. Prior work covered the shared structure-constraints layer, locate filtering, deterministic client GameTests, and the initial statistics snapshots.

Current review focus, narrowed to the latest active comments about structure origin coordinates and `TERRASECT_STRUCTURE_ARGS`:

1. Structure origin coordinates in snapshots
   - Latest review comment: "Double-check structure origin coordinates" on `fabric/src/gametest/resources/terrasect/StructureConstraintStatisticsTest_snapshots/vanilla.snapshot`.
   - Current snapshot rows look like `minecraft:mineshaft,origin=66,72`, which is suspicious/unclear for a `10x10` chunk checking area.
   - Actionable items:
     - Verify what `StructureStartMixin.placeInChunk` is printing: chunk coordinates, block coordinates, section coordinates, or another coordinate type.
     - Verify whether the origin values are relative to the test area, the region origin, or absolute world coordinates.
     - Check why values are all positive and whether that follows from the generated/checking area.
     - Check whether the printed origin values align with the `10x10` generated/checking chunk area and the filtering logic.
     - If the coordinates are correct, make the snapshot label explicit, e.g. `origin_chunk=x,z` or `origin_block=x,z`, and keep the format deterministic.
     - If the calculation is wrong, fix the origin calculation and update snapshot generation accordingly.

2. Remove `TERRASECT_STRUCTURE_ARGS` and restore direct `chunkAccess` context plumbing
   - Latest review comment: "Remove `TERRASECT_STRUCTURE_ARGS` and restore direct `chunkAccess` plumbing" on `common/src/main/java/terrasect/mixin/structure/ChunkGeneratorStructureMixin.java`.
   - Current code introduced a thread-local `TERRASECT_STRUCTURE_ARGS` workaround around `ChunkGenerator#createStructures(...)`.
   - Actionable items:
     - Remove `TERRASECT_STRUCTURE_ARGS`.
     - Restore direct use of `chunkAccess` to read Terrasect context, e.g. Java equivalent of `((ChunkAccessExtender) chunkAccess).terrasect$getContext()`.
     - Investigate why direct `chunkAccess` stopped working; likely some `ChunkAccess` instances in this generation path lack Terrasect context.
     - Fix the underlying context propagation gap rather than keeping extra argument/thread-local plumbing.
     - Preserve structure filtering/enforcement behavior.
     - Do not introduce another thread-local argument-passing workaround unless no viable alternative exists and the reason is documented in this goal file.

Still-required verification:
- Run `./gradlew :fabric:runClientGameTest -Ptest=StructureConstraintStatisticsTest -PupdateSnapshots=true --no-daemon`.
- Inspect the updated snapshot output directly, especially origin labels/values and banned-village behavior.
- Keep the worktree/PR state aligned with this goal file.

Prior Claude follow-up run:
- Session id: `2e6ae3b6-8e2c-4ee4-92a5-22ef3a10e6ec`
- Status before this follow-up: partial implementation landed and the snapshot format update is present, but review comments remain for origin-coordinate clarity/correctness and removal of `TERRASECT_STRUCTURE_ARGS`.
- Earlier verification had failed with `Expected the banned-village preset to suppress village generation, but got 3`; preserve filtering/enforcement while making the requested review fixes.

Claude follow-up run on 2026-05-26:
- Invoked Claude Code CLI in blocking print mode from `/home/alex/terrasect-worktrees/structure-constraint-statistics-instrumentation` using prior session id `2e6ae3b6-8e2c-4ee4-92a5-22ef3a10e6ec` and a narrowed prompt for the two latest active review comments.
- Hermes terminal timed out after 600s before Claude emitted its JSON result, but filesystem changes were present and inspected.
- Implementation result:
  - Removed the `TERRASECT_STRUCTURE_ARGS` thread-local/record from `ChunkGeneratorStructureMixin`.
  - Replaced the prior `@ModifyExpressionValue` + thread-local capture with a direct `@WrapOperation` around `ChunkGeneratorStructureState.possibleStructureSets()` and `@Local(argsOnly = true)` access to `ChunkAccess` and `ResourceKey<Level>`.
  - Restored direct context lookup via `((ChunkAccessExtender) chunkAccess).terrasect$getContext()` and preserved fallback behavior through `StructureHandler.getFilteredSets(...)`.
  - Adjusted `ChunkContext(chunk, position)` so chunks without an attached `Level` produce an empty/nullable context instead of throwing during construction; `StructureHandler` then falls back to dimension traversal by `dimensionKey` for structure filtering.
  - Confirmed `StructureStartMixin` uses `StructureStart.chunkPos`, which is a logical start/origin chunk coordinate; snapshot payload now prints `origin_chunk=x,z`.
- Snapshot interpretation:
  - The generated/checking area is chunk `64..73` on both axes (`GENERATED_CHUNK_BASE=64`, `GENERATED_CHUNK_SIZE=10`).
  - Snapshot rows are filtered by placed chunk (`location=chunk=x,z`) in that target area, then deduplicated by `structure_id|origin_chunk`.
  - Some `origin_chunk` values such as `74` or `75` can be outside the placed-chunk checking area because a logical structure start can originate outside the area while placing pieces into chunks inside the area; this is expected with origin-based deduplication.
- Verification:
  - `./gradlew :common:compileJava :common:compileKotlin --no-daemon` — passed.
  - `./gradlew :fabric:runClientGameTest -Ptest=StructureConstraintStatisticsTest -PupdateSnapshots=true --no-daemon` — passed in 1m27s.
  - `./gradlew spotlessCheck --no-daemon` — passed.
  - Inspected snapshots directly: `vanilla.snapshot`, `dense.snapshot`, and `banned_village.snapshot` now use explicit `origin_chunk=` rows.
  - Banned-village run reported `target_village_event_values=0`, `total=4`, `types=1`, and the snapshot contains only four `minecraft:mineshaft` rows.
- Status: IMPLEMENTATION VERIFIED LOCALLY; worktree has expected uncommitted changes pending review/commit/push.
