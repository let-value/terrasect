# Known Issues

## Forced Structures on 1.20.1

The server smoke test compiles on 1.20.1, but forced placement is not confirmed there yet.

The test does not hardcode the planned chunk. It asks the runtime forced-structure planner for the
site:

```kotlin
val start = forced.sitesAt(context.traverser, context.cache, 0, 0).single()
```

Then it loads `start.site.chunkX/start.site.chunkZ` at `STRUCTURE_STARTS` and checks that the
planned structure has a valid `StructureStart`.

On 1.21.1 this now passes after adding the `ChunkStatusTasks.generateStructureStarts` hook, because
that version can bypass the `ChunkGenerator.createStructures` tail hook in the old server gametest
path. On 1.20.1 the `net.minecraft.world.level.chunk.status.ChunkStatusTasks` class does not exist,
so that fallback hook cannot apply. The previous `ChunkStatus` compile problem is fixed, but the
runtime forced-start assertion still fails.

This should be revisited after the newer-version forced-structure behavior is settled. Likely next
step: inspect the 1.20.1 structure-start generation call path and add the equivalent version-specific
hook, or decide that the 1.20.1 smoke test should only assert compiled pipeline presence rather than
forced placement.

## Region Properties Without Runtime Effects

The preset configuration schema supports every property exposed by `RegionBuilder` and its
sub-builders. The configuration loader parses these properties and makes the corresponding builder
calls even though the following properties do not currently affect generation:

- `originAnchor` is retained by `RegionDefinition`, but `RegionRegistry.buildTree` does not copy it
  into `Region` and no runtime code reads it.
- `HeightConstraints` are copied into `Region`, but no generation handler reads `Region.height`.
- Biome `SelectionConstraints` are copied into `Region`, but no generation handler evaluates
  `Region.biomes`.
- `ClimateConstraints.precipitation` is stored and inherited, but `ClimateHandler` does not apply it.
- `ClimateConstraints.climatePreset` is stored and inherited, but no runtime code reads it.

Supporting these fields in TOML preserves the one-to-one mapping between preset files and the
registry builder API. Their runtime behavior should be implemented and tested separately without a
later configuration schema change.
