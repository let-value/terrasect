# Noise Narrative Constraint Expansion

Status: DONE

## User request

Expand the Terrasect noise test suite with more meaningful constraints that mirror narrative world shapes rather than generic noise transforms.

The target scenarios are:

- desert world: the entire world is turned into desert
- river world: a world with many rivers
- lake world: a world with many bodies of water
- ocean world: huge bodies of water and small islands

Important constraint: do **not** use climate constraints. Rely only on noise constraints in isolation.

## Context

The previous goal covered noise generation with tests. The next step is to add stronger, narrative-shaped tests that encode worldgen intent through noise-only constraints.

Claude Code should first explore the Minecraft internals by unpacking the game sources with the Gradle task before changing code.

Terrasect repo path: `/home/alex/terrasect`
Dedicated workspace: `/home/alex/terrasect/.worktrees/noise-narrative-constraints`
Active base branch: `reborn`

## Required workflow for Claude Code

1. Read this goal file in full.
2. Run `./gradlew unpackMinecraft` to inspect the unpacked Minecraft sources.
3. Trace the noise dependencies needed for the narrative scenarios.
4. Implement or extend tests to cover the four scenario shapes using noise constraints only.
5. Avoid climate constraints entirely.
6. Run focused verification and record the results here.
7. Write the complete outcome back into this goal file, including status, implementation summary, verification, and Claude session id if available.

## Acceptance criteria

- Test coverage includes at least the four narrative scenarios listed above.
- The scenarios are expressed through noise constraints, not climate constraints.
- The implementation is backed by Minecraft source inspection via `unpackMinecraft`.
- Verification results are recorded in this file.

## Implementation summary

### Files changed

**`common/src/test/kotlin/terrasect/lookup/CompiledNoiseConstraintsTest.kt`** — sole change. Added `NarrativeNoiseConstraintsTest` class (93 lines) with four tests, appended after the existing `CompiledNoiseRegistryTest` class. No other files were created or modified.

### Minecraft source insights (from unpackMinecraft)

Sources extracted to `minecraft/` via `./gradlew :fabric:genSources && ./gradlew unpackMinecraft`.

Key files inspected:
- `net/minecraft/world/level/biome/OverworldBiomeBuilder.java`
- `net/minecraft/world/level/levelgen/NoiseRouterData.java`
- `net/minecraft/world/level/levelgen/Noises.java`

Relevant constants confirmed in source:

| Constant | Value | Used for |
|---|---|---|
| `temperatures[4]` | `[0.55, 1.0]` | Hottest band; all humidities → Desert in `MIDDLE_BIOMES[4]` |
| `inlandContinentalness` | `[-0.11, 0.55]` | Land (not ocean or coast) |
| `deepOceanContinentalness` | `[-1.05, -0.455]` | Deep ocean |
| `oceanContinentalness` | `[-0.455, -0.19]` | Shallow ocean |
| `coastContinentalness` | `[-0.19, -0.11]` | Beach/coast |
| `VALLEY_SIZE` | `0.05` | Valley weirdness span `[-0.05, 0.05]` → `addValleys()` → rivers |
| `erosions[6]` | `[0.55, 1.0]` | Highest erosion index; selects river biomes in valley slice |
| Density function IDs | `minecraft:overworld/continents`, `minecraft:overworld/erosion`, `minecraft:overworld/ridges_folded`, `minecraft:overworld/depth` | Terrain shape |
| Noise IDs | `minecraft:temperature`, `minecraft:vegetation`, `minecraft:continentalness`, `minecraft:ridge` | Climate inputs |

The `NoiseConstraints` system intercepts both raw noise parameters (via `.noise()`) and computed density functions (via `.densityFunction()`), so either type can be targeted without touching `ClimateConstraints`.

### Scenario designs

**Desert world** — `minecraft:temperature` clamped to `[0.55, 1.0]` (temperatures[4], the all-desert row in `MIDDLE_BIOMES`) plus `minecraft:overworld/continents` clamped to `[-0.11, 1.0]` (inland minimum) so ocean biomes cannot override.

**River world** — `minecraft:overworld/ridges_folded` clamped to `[-0.05, 0.05]` (the `VALLEY_SIZE` span that triggers `addValleys()`) plus `minecraft:overworld/erosion` clamped to `[0.55, 1.0]` (erosions[6], which selects River over Swamp in the valley slice).

**Lake world** — `minecraft:overworld/continents` clamped to `[-0.455, -0.11]` (ocean + coast band, terrain near sea level) plus `minecraft:overworld/erosion` clamped to `[0.45, 1.0]` (erosions[4–6], flat terrain that retains surface water).

**Ocean world** — `minecraft:overworld/continents` clamped to `[-1.05, -0.455]` (`deepOceanContinentalness`) plus `minecraft:overworld/depth` multiplied by `2.0` to push the density field further below sea level across the whole slice, leaving only occasional high-noise protrusions as islands.

## Verification

Command: `./gradlew :common:test`

```
> Task :common:test
BUILD SUCCESSFUL in 8s
8 actionable tasks: 2 executed, 6 up-to-date
```

`NarrativeNoiseConstraintsTest` results (from `TEST-terrasect.lookup.NarrativeNoiseConstraintsTest.xml`):
- tests: 4, failures: 0, errors: 0, skipped: 0
- `desert world pins temperature to hottest band and continentalness to inland` — PASSED
- `river world clamps weirdness to valley band and erosion to highest index` — PASSED
- `lake world restricts continents to coast band with flat high-erosion terrain` — PASSED
- `ocean world forces deep ocean continentalness and amplifies depth below sea level` — PASSED

Full suite: BUILD SUCCESSFUL (all pre-existing tests continue to pass).

Spotless check: no violations in `common/src/` (violations reported only for `minecraft/` unpacked sources, which are not part of the project's formatting scope).

### Bug fixed during this session

The first attempt had a wrong assertion in the ocean-world test: `assertEquals(-1.05, continents.apply(0.5))` — `clamp(-1.05, -0.455)` applied to `0.5` clamps to the ceiling `-0.455`, not the floor. Corrected by using `apply(-1.2)` (below the floor) to exercise the lower bound and `apply(0.5)` to exercise the ceiling, reflecting actual clamp semantics.

## Execution log

- 2026-05-10 12:06:22 UTC: Goal file created by Hermes before delegating to Claude Code.
- 2026-05-10 (session 1): `genSources` + `unpackMinecraft` run; `NarrativeNoiseConstraintsTest` drafted but ocean-world assertion was wrong (expected floor, got ceiling).
- 2026-05-10 (session 2): Removed explanatory comments per project style; fixed ocean-world assertion; all 4 tests green.

## PR URL

https://github.com/let-value/terrasect/pull/50

## Claude Code invocation requirement

Hermes should invoke Claude Code explicitly and ask it to perform the work in the dedicated workspace, then write its complete result back into this file.
