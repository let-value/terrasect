# GOAL: Noise, structure redistribution, and narrative control exploration

- **Status:** DONE
- **Branch:** `structures-constraints`
- **Repo:** `/home/alex/terrasect`
- **Scope:** exploratory investigation only; **no code changes** requested
- **User ask:** investigate whether noise affects structure redistribution, what determines the density and placement of structures, and how to give mod users narrative control over structure generation

## Investigation prompts

1. Does world noise influence how structures are redistributed, or is structure placement primarily governed by structure sets, separation/spacing settings, and region/selection logic?
2. What Minecraft systems determine structure density and placement in the current pipeline?
3. What options exist for exposing *narrative control* over structure generation to mod users without adding unnecessary abstraction?
4. What parts of the current Terrasect architecture already provide the right control points, and what gaps remain?

## Constraints

- Do not change code.
- Prefer source-backed conclusions from local docs/code over assumptions.
- Keep the findings concise and actionable.
- If helpful, reference any relevant files or docs that clarify the vanilla or Terrasect pipeline.

## Deliverable

Update this goal file with:
- a short summary of findings,
- the key vanilla pipeline facts,
- Terrasect-specific implications,
- any recommended follow-up investigation (still no code changes).

---

## Findings

### Summary

Noise does **not** affect structure redistribution. Structure placement in vanilla is entirely seed-and-coordinate-determined. The only noise-adjacent interaction is the biome filter that can prevent a structure from generating at a grid-selected chunk — but this fails it in place rather than moving or redistributing it. Terrasect's current architecture correctly intercepts the set list at chunk-generation time and at `/locate` time, giving per-region narrative control over *which* structures are eligible, but it has no mechanism to control *density* or *positioning* within a region beyond what vanilla's placement parameters already encode.

---

### Key vanilla pipeline facts

1. **Two placement types, both purely deterministic:**
   - `random_spread` divides the world into `spacing × spacing` chunk cells; one home chunk per cell is picked by a seed mix of `(worldSeed, gridX, gridZ, salt)`. No terrain or noise input.
   - `concentric_rings` (Strongholds only) pre-computes a fixed list of positions from `worldSeed`; membership is a list lookup.

2. **Density levers are `spacing`, `separation`, `frequency`, and `exclusion_zone`** — all baked into the `StructureSet`'s `StructurePlacement` at datapack-load time. There is no runtime hook to adjust them per-chunk.

3. **Biome is the only noise-derived gate.** `Structure.findValidGenerationPoint` calls `isValidBiome`, which samples the noise biome at the candidate position. A biome mismatch causes placement failure (the chunk is vacated, not reassigned). `ConcentricRings` does a biome-snap search (up to 112 blocks) when building the ring list, which can shift Stronghold positions slightly toward preferred biomes.

4. **`frequency` is a secondary probability filter** applied after the grid check. It uses `setLargeFeatureWithSalt` (default) or one of three legacy algorithms. Mineshafts use `LEGACY_TYPE_3` (no salt); Pillager Outposts use `LEGACY_TYPE_1` (ignores salt, different seed construction).

5. **Weighted selection within a set** (`setLargeFeatureSeed`) determines which structure variant generates if a set contains multiple candidates (e.g., `minecraft:villages` picks among plains/desert/savanna/etc. variants). Failing candidates fall through to the next, consuming from the same RNG without re-seeding.

6. **`ChunkGeneratorStructureState.possibleStructureSets()`** is the single call site that gates which sets are iterated during `createStructures`. Wrapping it is sufficient to suppress a set for a chunk.

---

### Terrasect-specific implications

**What already works:**
- `ChunkGeneratorStructureMixin` wraps `possibleStructureSets()` in `createStructures` to return only region-allowed sets. When Terrasect has no constraint for a region it returns `null` and vanilla behavior is preserved exactly.
- `ChunkGeneratorLocateMixin` wraps `getStructureGeneratingAt` in both locate overloads (concentric and random-spread) so `/locate` also respects region constraints at the candidate chunk.
- `CompiledStructureLookup` filters at both the set level (drops entire sets) and the entry level (synthesizes a new `StructureSet` with the same `placement()` but fewer entries), so structure-tag–based constraints work correctly.
- `DimensionContext` builds `CompiledStructureLookup` only when at least one region has structure constraints; if none do, it is `null` and both mixins short-circuit to vanilla.

**Gaps identified:**

1. **No density control.** Terrasect can include or exclude structures per region but cannot change `spacing`, `separation`, or `frequency` within a region. To make a region "structure-dense" or "structure-sparse" relative to vanilla, something would need to synthesize modified `StructurePlacement` or intercept `isStructureChunk`.

2. **No noise-based redistribution.** Nothing in the current or vanilla pipeline uses terrain noise to bias structure positions. If narrative redistribution (e.g., clustering structures near dramatic terrain) is desired, it would require a wholly new mechanism — e.g., an additional probability multiplier derived from a noise sample at the candidate chunk, applied after the grid check.

3. **`/locate` region-boundary fidelity.** `ChunkGeneratorLocateMixin` evaluates constraints at `chunkPos` (the candidate start chunk), which is correct for point containment. However, because locate iterates outward from the search origin without knowing region boundaries, it can find a structure just inside a region boundary that a region constraint would deny on the opposite side of the boundary. This is an edge case but worth verifying under real multi-region worlds.

4. **`frequency` override gap.** `SelectionConstraints` has no way to express "allow this structure but at half vanilla density." Any narrative density difference must currently be achieved by excluding and providing a replacement datapack set with different spacing — not expressible in Terrasect config alone.

---

### Recommended follow-up investigation

1. **Placement interception feasibility** — Investigate whether `StructurePlacement.isStructureChunk` can be wrapped per-region (e.g., with an additional frequency multiplier sampled at the candidate chunk). This would be the minimal path to per-region density control without replacing vanilla sets.

2. **Noise-driven frequency** — If narrative redistribution is a real goal, investigate what Terrasect noise values are available at chunk resolution (`CompiledNoiseRegistry` / `DimensionContext.noiseRegistry`) and whether they could be plumbed into an additional `isStructureChunk` gate to bias structure density.

3. **`/locate` radius correctness** — Trace the full locate search loop in `ChunkGenerator.getNearestGeneratedStructure` to confirm whether multi-region boundary behavior is acceptable or produces inconsistencies that need addressing.

4. **Density as a `SelectionConstraints` field** — Evaluate whether adding an optional `density: Float` (or equivalent) to `SelectionConstraints` is the right API surface for narrative density control, or whether a separate placement-override type is cleaner.
