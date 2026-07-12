# Terrasect Roadmap

Dispositions of the post-merge audits (`docs/PROJECT_AUDIT_GPT55.md`,
`docs/PROJECT_AUDIT_CLAUDE_OPUS47.md`). The two audits converge on five points;
the decisions below are authoritative and override the audit "recommendations".

## Decisions

### 1. Region definition statefulness — WONTFIX (by design)
The mutable builder state in `RegionDefinition` / `RegionBuilder` is the builder
pattern working as intended; state mutation during build is the point. No
immutability rework.

Carve-out: the `adjacentTo` machinery (no-op builder method, lazy set, build
wiring, constructor field) is removed entirely — it was never wired up and the
feature will not be implemented. Done.

### 2. Loot/mob/structure duplication — REFRAME (no new abstraction)
Do not extract a shared compiled-selection kernel. A common abstraction over
three young domains would force cohesion that isn't really there and couple the
domains' rules together. Keep the three pipelines parallel.

Instead: improve code quality in place and simplify constraints compilation —
tighten each `Compiled*Lookup` / `*Handler` on its own terms, remove incidental
noise, and make the constraint-compilation step more direct without unifying the
domains.

### 3. Common as "god module" — WONTFIX (by design)
We are writing a mod, not loader harnesses. Loader modules are deliberately
minimal; the shared logic, handlers, mixins, and builders belong in `common`.
This is the intended architecture, not boundary blur.

### 4. Instrumentation oversized — ACCEPT (slim it down)
`instrumentation/Instr.kt` (was 433 lines) is too large for its role. Done so
far: removed the dead `Instr`-object facade that re-exported every recording
overload (`Instr.count/time/recordDurationNanos/counter/timer`) — nothing called
them; production uses `ScopedInstr` via `TerrasectInstr.*`, tests use `Instr`
only for backend/snapshot/reset. Also dropped the now-unused `RootInstrScope`.
Instr.kt: 433 → 295 lines, no production or test breakage.

Decided: stop here. The remaining `ScopedInstr` surface — tagged
`time`/`recordDurationNanos`, `counter()`/`timer()` handles, and the
`Managed*`/`MetricIdFactory` machinery — stays. It is unused in production but
fully covered by `MetricsTest` and is the substrate for future perf measurement;
keeping it costs no test coverage and leaves the perf hook ready.

### 5. Tests — ACCEPT (on the roadmap)
The newest surfaces (loot filtering, mob gating, structure selection/caching)
lean on Fabric gametests with little fast local coverage. Add focused unit tests
for these paths; keep gametests for end-to-end validation.

## Action items

1. ~~Remove the dead `adjacentTo` machinery from `RegionBuilder`.~~ Done.
2. Simplify constraints compilation and clean up `Compiled*Lookup` / `*Handler`
   in place — no shared abstraction. Done: deduped the repeated selection-eval
   block in `CompiledStructureLookup` (`selectionAllows`). The rest of the
   lookup/constraints layer (`SelectionConstraints`, `CompiledNoise`, loot/mob
   lookups) was reviewed and left as-is — already direct; the loot/mob
   parallelism is intentional (see decision #2).
3. Done: removed dead `Instr` facade (433 → 295 lines). Kept the `ScopedInstr`
   handle/timer surface as the perf-measurement substrate (decided).
4. ~~Add fast unit tests for loot, mob, and structure selection paths.~~ Done:
   `SelectionConstraintsTest` (the shared decision kernel for all three paths)
   and `StructureConstraintsTest` (placement + inheritance). Registry-indexing
   glue stays covered by gametests.
