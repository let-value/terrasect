# Known Issues

## Region Properties Without Runtime Effects

The preset configuration schema supports every property exposed by `RegionBuilder` and its
sub-builders. The configuration loader parses these properties and makes the corresponding builder
calls even though the following properties do not currently affect generation:

- `HeightConstraints` are copied into `Region`, but no generation handler reads `Region.height`.
- Biome `SelectionConstraints` are copied into `Region`, but no generation handler evaluates
  `Region.biomes`.
- `ClimateConstraints.precipitation` is stored and inherited, but `ClimateHandler` does not apply it.
- `ClimateConstraints.climatePreset` is stored and inherited, but no runtime code reads it.

Supporting these fields in TOML preserves the one-to-one mapping between preset files and the
registry builder API. Their runtime behavior should be implemented and tested separately without a
later configuration schema change.
