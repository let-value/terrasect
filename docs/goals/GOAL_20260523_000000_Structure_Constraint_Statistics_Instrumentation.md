Status: DONE on branch `structure-constraint-statistics-instrumentation` in worktree `/home/alex/terrasect-worktrees/structure-constraint-statistics-instrumentation`.

Goal: address the latest active PR #54 review comments for `StructureConstraintStatisticsTest` and finish the instrumentation/statistics follow-up without hardcoding observed counts.

## Active review comments to address

### 1. Narrow instrumentation from coarse scope to the single needed event
The test should rely on `TerrasectMetricEvent.STRUCTURE_GENERATED` only, not the broader `TerrasectInstrScope.STRUCTURE` bucket if that scope still contains unrelated structure metrics.

### 2. Add per-event filtering if coarse scopes are still the only switch
If the instrumentation system cannot yet enable a single event directly, extend it so the test can selectively enable `STRUCTURE_GENERATED` without turning on unrelated structure instrumentation.

### 3. Investigate `StructureStartMixin.placeInChunk` for structure origin/start information
Determine whether the mixin has access to the logical structure origin/start location, not just the currently placed chunk coordinates.

### 4. Include origin/start location in the `STRUCTURE_GENERATED` payload if available
If `StructureStartMixin.placeInChunk` can expose the origin/start location, record it *in addition to* the placed chunk so the test can still filter the target chunk area while also deduplicating the logical structure consistently.

### 5. Deduplicate structure counts by `(structure_id, origin/start location)`
Update `StructureConstraintStatisticsGameTest` so it deduplicates logical structure generation events by structure id plus origin/start location, while filtering to the target chunk area using the placed-chunk tag. Apply the same logic to both vanilla and dense/custom generation cases.

## Implementation plan

1. Inspect the current instrumentation and mixin code paths to confirm what data `STRUCTURE_GENERATED` currently records.
2. Add per-event filtering support if the instrumentation layer only supports coarse scope toggles today.
3. Extend the structure-generated payload with origin/start location if the mixin can provide it.
4. Update `StructureConstraintStatisticsGameTest` to deduplicate on the logical structure identity and location.
5. Verify the location filter is reading the placed-chunk tag (`chunk=`) rather than the origin tag, then run the relevant GameTest and inspect the generated statistics output before refreshing any snapshots.

## Constraints

- Do not hardcode the currently observed counts.
- Prefer deterministic, instrumentation-based counting.
- Keep the change limited to the current PR scope and the structure statistics test path.
- Preserve the branch/worktree/goal-file workflow.

## Verification target

- `./gradlew :fabric:runClientGameTest -Ptest=StructureConstraintStatisticsTest`
- Inspect the generated statistics output after the code changes.
- Update snapshots only if the corrected methodology changes the expected output.

## Verification

- Ran `./gradlew :fabric:runClientGameTest -Ptest=StructureConstraintStatisticsTest -PupdateSnapshots=true`.
- Verified the regenerated snapshots:
  - `vanilla` → `total_generated=4`, `minecraft:mineshaft=4`
  - `dense` → `total_generated=18`
  - `banned_village` → `total_generated=18`
- Confirmed the test passed with `BUILD SUCCESSFUL`.
