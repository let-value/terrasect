# GOAL: StructureConstraints composition peer implementation

- **Status:** COMPLETE
- **Branch:** `structures-constraints`
- **Repo:** `/home/alex/terrasect`
- **Scope:** implementation
- **User ask:** introduce `StructureConstraints` as a new peer type via composition, not inheritance

## Plan

Implement a new `StructureConstraints` peer that wraps selection plus placement-density concerns without becoming an inheritance base.

### Phase 1: ship the narrow, high-value slice

Start with only:
- the selection wrapper
- `spacing`
- `separation`
- `frequency`

### Phase 2: defer the harder placement inputs

Do **not** add the `exclusion_zone` and `grid_position` inputs yet. Defer those until the placement override mixin design is ready.

## Intent

This incremental path should:
- provide immediate testing value through density override behavior,
- keep each concern in its own class for narrative coherence,
- avoid taking on the threading / override complexity before the placement hook design is settled,
- minimize code churn by reusing the existing shared filtering / selection flow where possible.

## Constraints

- Keep the design composition-based, not inheritance-based.
- Prefer common/Kotlin shared logic with thin Java mixins.
- Make the smallest change that demonstrates the new density control slice clearly.
- Keep `StructureConstraints` narrowly scoped for this phase.
- Update this goal file with progress, blockers, and verification as work proceeds.
- If a file is touched for this goal, preserve the existing Terrasect process: concise code, concise status updates, and verification recorded in the goal file.

## Expected deliverable

By the end of this phase, the repo should have:
- a new `StructureConstraints` peer class or equivalent composition wrapper,
- selection + spacing/separation/frequency support wired in,
- the deferred fields still absent or explicitly stubbed as future work,
- verification notes recorded here,
- compile/test evidence where practical.

## Verification

- `./gradlew :common:compileJava :common:compileKotlin :common:test` ✅
- `StructureConstraints` is now a separate peer wrapper with its own builder.
- `Region` / `RegionDefinition` now reference `StructureConstraints` for structure-specific selection + density fields.
- Deferred placement-only inputs (`exclusion_zone`, `grid_position`) remain out of scope for this phase.

## Outcome

The narrow composition slice is in place: selection wrapper plus spacing/separation/frequency are wired through `StructureConstraints`, with the implementation kept separate from inheritance and validated by compile/test.

## Notes for the provider

- Read this file first.
- Keep stdout concise.
- Update this file with concrete progress and verification.
- If you discover the design needs a different split, record the reason here before broadening scope.
