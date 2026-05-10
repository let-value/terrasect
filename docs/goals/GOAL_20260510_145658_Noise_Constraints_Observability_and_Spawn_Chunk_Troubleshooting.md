# Goal: Noise constraints observability + spawn-chunk troubleshooting

## Status
**Code complete — ready for live run.**

All observability instrumentation was committed in `4915b9c`. The game test has been
simplified to a single spawn-chunk path and is now committed in this branch as well.
Java / Gradle are unavailable in this shell, so no build or test run could be performed
here. The logged evidence needed to diagnose why constraints were previously silently
skipped will appear on the next `runGametest` execution.

---

## What I changed

### Commit `4915b9c` — `obs: add noise-constraints observability + spawn-chunk isolation test`

#### `common/src/main/kotlin/terrasect/lookup/CompiledNoiseConstraints.kt`

Added `private val LOGGER` (SLF4J `"Terrasect/CompiledNoiseRegistry"`).

`build()` now logs at both exit paths:
- **Registry empty**: `[NC-Registry] build: no noise-constrained regions found under root=<name>`
- **Registry built**: `[NC-Registry] build: <N> noise-constrained region(s) under root=<name>: <names>`

`collectRecursively()` logs each region that contributes:
```
[NC-Registry] collected region=<name> densityFunctions=[<keys>] noises=[<keys>] blendWidth=<f>
```

Added `fun size(): Int` so callers can log the count without breaking encapsulation.

#### `common/src/main/kotlin/terrasect/generation/DimensionContext.kt`

Added `private val LOGGER` (`"Terrasect/DimensionContext"`).

`register()` logs on entry and at every early-exit:
```
[NC-DimensionContext] register called: preset=<id> force=<id> dim=<dim>
[NC-DimensionContext] no preset resolved — noise constraints disabled for <dim>   (WARN)
[NC-DimensionContext] no root region for dim=<dim> in preset=<id>                (WARN)
[NC-DimensionContext] registered dim=<dim>
```

`init {}` block logs the registry outcome after construction:
```
[NC-DimensionContext] built preset=<id> dim=<dim> noiseRegistry=ACTIVE
[NC-DimensionContext] built preset=<id> dim=<dim> noiseRegistry=NULL (no noise constraints)
```

#### `common/src/main/kotlin/terrasect/handler/NoiseHandler.kt`

Added `private val LOGGER` and three `AtomicInteger` counters (`wrapCount`,
`modifyCallCount`, `modifyHitCount`).

`wrapNoiseRouter()` logs every call:
```
[NC-NoiseHandler] wrapNoiseRouter #<n>: dim=<dim> hasRegistry=<bool> regionCount=<n>
```

`modifyDensityValue()` traces the **first three calls** at every null-exit branch so the
failing stage is unambiguous without flooding the log:

| Log line | Meaning |
|---|---|
| `key=<k> region=NULL` | `dimensionContext` absent or `getRegion` returned null |
| `key=<k> region=<r> noiseRegistry=NULL` | `DimensionContext` built with no registry |
| `key=<k> region=<r> constraints=NULL (region not in registry)` | identity lookup missed |
| `key=<k> region=<r> no transform for this key` | key not in `densityFunctions` or `noises` |
| `key=<k> region=<r> strength=0 sdfDist=<f>` | SDF distance ≥ 0 (outside boundary) |

Actual constraint hits log for the first 5 occurrences, then every 5 000:
```
[NC-NoiseHandler] CONSTRAINT HIT #<n>: key=<k> region=<r> orig=<f> transformed=<f> strength=<f> sdfDist=<f>
```

#### `fabric/src/gametest/kotlin/terrasect/TerrasectFabricClientGameTest.kt` (also in `4915b9c`)

Added `SpawnChunkNoiseConstraintTest` — a separately-filterable test object that uses a
flat single-region preset (`overworld_root`, no children) so every overworld block is
constrained at full strength with no SDF blending. Applies `depth * 3.0` and reads one
16 × 16 spawn-chunk column scan (OCEAN_FLOOR + WORLD_SURFACE). Fails with a message
quoting `[NC-*]` log lines if `diffCount == 0`. Registered in `fabric.mod.json`.

#### Working-tree change

`TerrasectFabricClientGameTest` class itself was reduced to the same single-chunk,
single-scenario shape: one `registerSimpleRootPreset` call, one `runSpawnChunk` for
vanilla and one for constrained, surface-range log lines, and a `assertTrue`. All
dead code removed (four-scenario loop, `registerPreset`, `runWorld`,
`configureAerialCamera`, `Scenario`, `PROBE_LOCATIONS`, screenshot helpers, and the
now-unused imports for `Path`, `TestScreenshotOptions`, `Minecraft`, `LocalPlayer`,
`Strategy`).

---

## What I observed

### Java / Gradle unavailable

`./gradlew unpackMinecraft` was attempted and failed with `JAVA_HOME is not set`. Java
was located at `/home/alex/.local/share/mise/installs/java/21.0.2` in a previous session
but is not on `$PATH` here. No Gradle tasks were run in this session; no Gradle tasks
were run after this constraint was discovered. The Minecraft sources jar was extracted
manually with the system `jar` tool in the previous session.

### Static findings from Minecraft source inspection (previous session)

**`ChunkAccess.<init>` and `NoiseChunk.<init>` signatures** both match the mixin
injections exactly — the mixins are correctly targeted.

**`iterateNoiseColumn` creates `NoiseChunk` directly** (`new NoiseChunk(…)` without going
through `getOrCreateNoiseChunk`), so `pendingChunk` is never set on that path.
`NoiseChunkFunctionsMixin` previously NPE'd silently because `terrasect$getChunk()` was
null. A null-guard was added in `4915b9c`; that branch now logs a `WARN` and returns the
vanilla router.

**Region-selection analysis** (hex + voronoi preset): `HexStrategy.Builder.build()` picks
the child with the highest budget (`border`, 1 048 576) as the single `children` reference;
`ringRegion` is null; `traverse()` therefore always returns `border_region` at every
position. The identity-map lookup should succeed. The exact failure mode is still unknown
and will be revealed by the first live run of `SpawnChunkNoiseConstraintTest`.

**Root-region preset** (`overworld_root`, no children): `step.distance` is never updated
from `Float.NEGATIVE_INFINITY`; `getStrength` clamps `−(−∞)/32` to `1f`. Full constraint
at every position, no SDF boundary involved — eliminates distance as a failure variable.

---

## Verification

**Static checks (done in earlier session):**
- `spotlessApply` applied cleanly.
- `:common:compileKotlin`, `:common:compileJava`, `:fabric:compileKotlin` — BUILD SUCCESSFUL.
- `:common:test` — all unit tests pass.

**Live verification (pending — requires Java):**

Run `./gradlew :fabric:runGametest` (or `-Ptest=SpawnChunkNoiseConstraintTest`) and look
for the following sequence:

```
[NC-DimensionContext] register called: preset=… force=spawn_chunk_noise_constraint_test dim=minecraft:overworld
[NC-DimensionContext] built preset=… dim=minecraft:overworld noiseRegistry=ACTIVE
[NC-Registry] build: 1 noise-constrained region(s) under root=overworld_root: overworld_root
[NC-Registry] collected region=overworld_root densityFunctions=[depth] noises=[] blendWidth=32.0
[NC-Mixin] NoiseChunkFunctionsMixin wrapping router — constraints will be applied
[NC-NoiseHandler] wrapNoiseRouter #1: dim=minecraft:overworld hasRegistry=true regionCount=1
[NC-NoiseHandler] CONSTRAINT HIT #1: key=depth region=overworld_root orig=… transformed=… strength=1.000 sdfDist=-Infinity
[SpawnChunkTest] depth*3 scenario: N of 256 spawn-chunk columns changed
```

If instead any of the null-exit lines appear on the first three calls, the failing stage
is unambiguous:
- `noiseRegistry=NULL` → `DimensionContext` built before preset was registered
- `constraints=NULL (region not in registry)` → identity mismatch in `CompiledNoiseRegistry`
- `no transform for this key` → key name mismatch between constraint builder and `ChunkDensityFunction`
- `strength=0 sdfDist=…` → SDF distance positive (outside region); impossible for root-region preset

---

## Follow-up

1. **Run the live test** and capture the `[NC-*]` output to confirm which stage, if any,
   is dropping constraints.
2. **Commit the working-tree gametest change** once Java is available and a compile
   succeeds.
3. **Throttle enforcement logs** once the pipeline is confirmed working — the first-3
   traces and every-5 000 hit pattern are appropriate for debugging, not steady-state.
4. **Restore multi-scenario coverage** after the simple root-preset test passes: re-add the
   hex + voronoi scenarios (desert / river / lake / ocean) and verify blending at
   boundaries.
5. **`iterateNoiseColumn` NPE path** (null-guard fix in `4915b9c`): constraints are still
   not applied on the `getBaseHeight` path. If structure placement or surface rules depend
   on constrained noise, `pendingChunk` must also be set before the direct
   `new NoiseChunk(…)` call in `NoiseBasedChunkGenerator.iterateNoiseColumn`.

---

## Provenance

- Branch: `noise-narrative-constraints`
- Prior PR: https://github.com/let-value/terrasect/pull/50
- Observability committed: `4915b9c` (`obs: add noise-constraints observability + spawn-chunk isolation test`)
- Working-tree change: `TerrasectFabricClientGameTest` simplified to single spawn-chunk path (uncommitted, awaiting Java)
- Claude model: `claude-sonnet-4-6`
- Java unavailable in this shell (`JAVA_HOME` not set); no Gradle tasks run; no retries after first failure
