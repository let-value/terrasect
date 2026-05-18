# Goal: Common Package Instrumentation Framework

Status: DONE — pass 2 scoped API refinement complete

Branch: `feature/common-instrumentation`
PR: https://github.com/let-value/terrasect/pull/52
Worktree: `/home/alex/terrasect/.worktrees/common-instrumentation`

## Objective

Add a lightweight, low-overhead instrumentation framework to `common/` for counters and timers. The public API should feel like scoped logging: callers use stable scope/event identities, can count or time semantic operations with zero to three low-cardinality tags, and do not know about storage/exporter internals.

## Current Implementation

Files in `common/src/main/kotlin/terrasect/instrumentation/`:

- `MetricId.kt`: `InstrScope`, `MetricEvent`, `RootInstrScope`, `MetricTag`, scope-aware `MetricId`, `CounterSnapshot`, `TimerSnapshot`.
- `Metrics.kt`: `MetricsConfig` global/counter/timer flags plus scope-level overrides for all metrics, counters, and timers.
- `Instr.kt`: global `Instr` facade plus `ScopedInstr` handles. Includes `count`, `time`, `counter`, and `timer` zero/one/two/three-tag overloads with lazy tag suppliers and no varargs.
- `Counter.kt`: optional bound `InstrCounter` handles backed by `NoOpCounter`, `InMemoryCounter` with `LongAdder`, and `ManagedCounter` runtime gates.
- `Timer.kt`: optional bound `InstrTimer` handles backed by `NoOpTimer`, `InMemoryTimer` with `LongAdder` total/count and atomic max, and `ManagedTimer` runtime gates.
- `MetricsBackend.kt`: replaceable `MetricsBackend`, `NoOpBackend`, and `InMemoryBackend` with stable sorted snapshots and snapshot-and-reset support.

## Design Notes

- Disabled facade hot paths check config before tag suppliers are evaluated, before tag lists are allocated, and before `System.nanoTime()` is called.
- Scope identity is part of every backend key and every snapshot via `MetricId(scope, event, tags)`.
- `Instr.scoped(scope)` returns a `ScopedInstr`, so call sites can cache a logging-like scoped instrumentation handle.
- Bound `InstrCounter` / `InstrTimer` handles remain optional for reusable metric references and obey runtime global/type/scope gates.
- Tags are explicit overloads for zero to three low-cardinality pairs; arbitrary maps and varargs are not exposed on hot paths.
- Snapshots are stable sorted lists from the backend and are suitable for debug output and tests.

## Progress

- [x] Reverted unrelated `fabric/src/gametest/kotlin/terrasect/StructureConstraintStatisticsGameTest.kt` edit.
- [x] Replaced raw `Metrics` call style with `Instr` global facade and `ScopedInstr` handles.
- [x] Added `InstrScope` and retained `MetricEvent` as stable identity abstractions.
- [x] Added zero/one/two/three tag overloads for direct count/time and optional bound counter/timer handles.
- [x] Added scope-aware config gates and scope-inclusive snapshots.
- [x] Added no-op/in-memory backend separation, thread-safe counters/timers, snapshots, and reset support.
- [x] Updated tests for disabled counters/timers, lazy tags, scoped identity, separate tagged counters, scope gates, bound handles, timer stats, return values, exception propagation, manual timing, stable snapshots, and no-op backend behavior.

## Blockers

None. Claude Code hit a plan limit during the requested delegation pass (`session_id: 34d455eb-9f18-432d-9ab0-401f8f2cf078`, reset message for 8:40pm Asia/Almaty), so Hermes completed the implementation pass directly in the existing worktree.

## Verification

- `./gradlew spotlessApply :common:test` — PASS.
- Reverted the unrelated Spotless change to `fabric/src/gametest/kotlin/terrasect/StructureConstraintStatisticsGameTest.kt`.
- `./gradlew :common:test` after that revert — PASS.

Compiler emits non-fatal Kotlin warnings for inline overloads that do not themselves take function-typed parameters. Those overloads are intentionally kept inline for API consistency with the hot-path overload set.

## Final Status

DONE. The instrumentation framework now has the requested scoped logging-like API shape (`Instr`, `ScopedInstr`, `InstrScope`, `MetricEvent`, `InstrCounter`, `InstrTimer`), scope-aware runtime gates, scope-inclusive snapshots, and the low-overhead disabled behavior required for strategic instrumentation in common mod code.
