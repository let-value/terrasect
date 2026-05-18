# Goal: Common Package Instrumentation Framework

Status: DONE — scoped instrumentation API complete

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

## Blockers

None remaining. Earlier Claude Code delegation hit provider/turn limits and left a partial scaffold; Hermes completed the implementation directly in the existing dedicated worktree.

## Verification

- `./gradlew spotlessApply` — PASS.
- `./gradlew :common:test --rerun-tasks` — PASS after formatting.
- Reverted the unrelated Spotless change to `fabric/src/gametest/kotlin/terrasect/StructureConstraintStatisticsGameTest.kt`.
- Final `git status --short` contains only intended instrumentation package/test changes and this goal file.

## Final Status

DONE. The instrumentation framework now has the requested scoped logging-like API shape (`Instr`, `ScopedInstr`, `InstrScope`, `MetricEvent`, `InstrCounter`, `InstrTimer`), scope-aware runtime gates, scope-inclusive snapshots, manual batch timing support, and low-overhead disabled behavior for strategic instrumentation in common mod code.
