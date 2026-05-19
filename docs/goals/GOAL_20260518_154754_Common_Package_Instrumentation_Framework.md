# Goal: Common Package Instrumentation Framework

Status: COMPLETE — scoped instrumentation API and curated semantic counter placement implemented

Branch: `feature/common-instrumentation`
Worktree: `/home/alex/terrasect/.worktrees/common-instrumentation`

## Objective

Add a lightweight, low-overhead instrumentation framework to `common/` for counters and timers. The public API should feel like scoped logging: callers use stable scope/event identities, can count or time semantic operations with zero to three low-cardinality tags, and do not know about storage/exporter internals.

## Current Implementation

Files in `common/src/main/kotlin/terrasect/instrumentation/`:

- `MetricId.kt`: `InstrScope`, `MetricEvent`, `RootInstrScope`, `MetricTag`, scope-aware `MetricId`, `CounterSnapshot`, `TimerSnapshot`, and low-cardinality tag guidance.
- `Metrics.kt`: `MetricsConfig` global/counter/timer flags plus scope-level overrides for all metrics, counters, and timers. Defaults remain disabled.
- `Instr.kt`: global `Instr` facade plus `ScopedInstr` handles. Includes `count`, `time`, `recordDurationNanos`, `counter`, `timer`, and `isTimingEnabled`/`isCounterEnabled` APIs. Direct hot-path count/time/manual-duration APIs have explicit zero/one/two/three-tag overloads with lazy tag suppliers and no varargs/maps.
- `Counter.kt`: optional bound `InstrCounter` handles backed by `NoOpCounter`, `InMemoryCounter` with `LongAdder`, and `ManagedCounter` runtime gates. Tagged bound handles delay tag-supplier evaluation until enabled use.
- `Timer.kt`: optional bound `InstrTimer` handles backed by `NoOpTimer`, `InMemoryTimer` with `LongAdder` total/count and atomic max, and `ManagedTimer` runtime gates.
- `MetricsBackend.kt`: replaceable `MetricsBackend`, `NoOpBackend`, and `InMemoryBackend` with stable sorted snapshots and snapshot-and-reset support.

## Design Notes

- Disabled facade hot paths check config before tag suppliers are evaluated, before tag lists are allocated, before backend lookup, and before `System.nanoTime()` is called.
- Scope identity is part of every backend key and every snapshot via `MetricId(scope, event, tags)`.
- `Instr.scoped(scope)` returns a `ScopedInstr`, so call sites can cache a logging-like scoped instrumentation handle.
- Bound `InstrCounter` / `InstrTimer` handles remain optional for reusable metric references and obey runtime global/type/scope gates.
- Tags are explicit overloads for zero to three low-cardinality pairs; arbitrary maps and varargs are not exposed on hot paths.
- Snapshots are stable sorted lists from the backend and are suitable for debug output and tests.

## Progress

- [x] Reverted unrelated `fabric/src/gametest/kotlin/terrasect/StructureConstraintStatisticsGameTest.kt` edit.
- [x] Replaced raw `Metrics` call style with `Instr` global facade and `ScopedInstr` handles.
- [x] Added `InstrScope` and `MetricEvent` as stable identity abstractions.
- [x] Added zero/one/two/three tag overloads for direct count/time/manual-duration and optional bound counter/timer handles.
- [x] Added cheap disabled-path gates before lazy tag evaluation and timer `nanoTime()` calls.
- [x] Added scope-aware config gates and scope-inclusive snapshots.
- [x] Added no-op/in-memory backend separation, thread-safe counters/timers, snapshots, and reset support.
- [x] Added/updated tests for disabled counters/timers, lazy tags, scoped identity, separate tagged counters, scope gates, bound handles, timer stats, return values, exception propagation, manual timing, stable snapshots, and no-op backend behavior.
- [x] Added the curated Terrasect scope/event vocabulary and cached scoped handles for business-code call sites.
- [x] Introduced the first business-logic counter: `structure.applied` increments when structure constraints are applied in generation filtering and locate filtering.
- [x] Integrated the rest of the curated counter set at semantic boundaries without adding broad/speculative metrics: traversal completion/step, chunk context creation/error/traversal/cache miss, climate applied/chunk-missing, noise router/function wrap/applied/chunk-missing, structure chunk-missing, and the explicit test/debug `structure.generated` recording helper.

## Curated Call-Site Vocabulary

Actual mod integration stayed narrow and used only the current curated counters:

- Traversal: `traversal.completed`, `traversal.step` tagged by `region`.
- Chunk context: `chunk.created`, `chunk.error`, `chunk.traverse`, `chunk.traverse.cache_miss` tagged by `dimension`.
- Climate handler: `climate.applied`, `climate.chunk_missing`.
- Noise handler: `noise.router.wrap`, `noise.function.wrap`, `noise.applied`, `noise.chunk_missing` tagged by `noise_key` where relevant.
- Structure handler: `structure.applied`, `structure.chunk_missing`.
- Structure generation/test signal: `structure.generated` tagged by `structure_id` and `location`; this is debug/test instrumentation, not an always-on production metric.

Do not add strategy traversal counters or preserve legacy/speculative counter names for compatibility.

## Blockers

No build blocker. Earlier Claude Code delegation hit provider/turn limits and left a partial scaffold; Hermes completed the framework API and the curated semantic counter placement directly in the existing dedicated worktree.

## Verification

- `./gradlew spotlessApply` — PASS.
- `./gradlew :common:test --rerun-tasks` — PASS after scoped API implementation.
- `./gradlew :common:test --rerun-tasks` — PASS after curated semantic counter placement.
- `./gradlew spotlessApply` — PASS after curated semantic counter placement.
- Reverted the unrelated Spotless change to `fabric/src/gametest/kotlin/terrasect/StructureConstraintStatisticsGameTest.kt`.
- Final `git status --short` contains only intended instrumentation/business-counter/test changes and this goal file.

## Current Status

Framework API, curated metric vocabulary, and curated semantic counter placement are implemented and validated. Placement is intentionally narrow: counters are present only at traversal, chunk context, climate, noise, and structure boundaries from the approved vocabulary, plus an explicit test/debug helper for `structure.generated`.
