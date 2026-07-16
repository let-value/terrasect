# Configuration

Terrasect loads configuration once during mod initialization from `<game>/config/terrasect`.
Changes require a game or server restart. Missing files are created without overwriting existing
files:

- `config.toml` contains global settings and the selected preset.
- Every other `.toml` file in the directory is a preset. Its file name without `.toml` is its ID.
- `example.toml` and `climate_debug.toml` are created when the directory has no preset files.

The generated configuration leaves `preset` empty, so installing Terrasect does not select a preset
until the user opts in.

## Main Configuration

```toml
preset = "example"

[logging]
load_summary = true
validation_warnings = true
registry_debug = false

[instrumentation]
enabled = false
counters = false
timers = false

[instrumentation.scopes.structure]
enabled = true
counters = true
timers = false

[instrumentation.events]
"structure.generated" = false
```

Instrumentation scope IDs are `structure`, `climate`, `noise`, `chunk`, `traversal`, `loot`, and
`mob`. A scope may also be assigned a boolean directly, such as `traversal = false`. Event keys are
the IDs declared by `TerrasectMetricEvent`.

## Presets

Every preset uses `schema = 1`, has one or more dimension roots, and declares regions by name:

```toml
schema = 1

[roots]
"minecraft:overworld" = "overworld"

[regions.overworld]
radius = 150
origin_anchor = true

[regions.overworld.strategy]
type = "voronoi"

[regions.forest]
parent = "overworld"
budget = 40000
```

Region declarations are order-independent. `radius` maps to `RegionBuilder.radius`, while `budget`
maps to `RegionBuilder.budget`; they cannot be used together. Supported strategy types are `hex`,
`voronoi`, `subdivision`, and `surround`. Hex accepts `tiling` and `ring_region`; surround requires
`surround_region`.

Code-defined presets use the same model in the other direction. Pass a `RegionRegistry` populated
through its existing region builders to `TerrasectTomlWriter.write` to produce schema-compatible
TOML; parsing that TOML reconstructs a `RegionRegistry` through the corresponding builder calls.

Region sub-tables map directly to the corresponding builders:

- `climate`: `temperature`, `humidity`, `continentalness`, `erosion`, `depth`, `weirdness`,
  `precipitation`, and `climate_preset`. Numeric climate values accept either one integer or a
  two-integer range.
- `height`: exactly one of `exact` or the two-integer `range`.
- `biomes`, `mobs`, and `loot`: `allow_mods`, `allow_tags`, `allow_names`, `block_mods`,
  `block_tags`, and `block_names`.
- `structures`: the same selection properties plus `spacing`, `separation`, `frequency`, and
  `force`. Forced entries accept `name` and at most one of `budget` or `radius`.
- `noise`: `blend_width`, `noises`, and `density_functions`.

Noise transforms are ordered arrays. Supported operations are `clamp`, `add`, `multiply`, `remap`,
`map`, `abs`, `square`, `cube`, `half_negative`, `quarter_negative`, `invert`, and `squeeze`.

```toml
[regions.forest.noise]
blend_width = 24.0

[regions.forest.noise.density_functions]
continents = [
  { op = "multiply", factor = 0.0 },
  { op = "add", value = 0.35 },
]
```

Minecraft noise-router names can differ by version. Generated examples use the Stonecutter-selected
`NoiseRouterCompat.SURFACE_FUNCTION_KEY`, producing `initialDensityWithoutJaggedness` on older
targets and `preliminarySurfaceLevel` on newer targets.

Unknown properties, invalid types, missing references, cycles, unsupported schema versions, and a
selected preset that does not exist stop initialization with a path-qualified error. The properties
listed under "Region Properties Without Runtime Effects" in `KNOWN_ISSUES.md` are still parsed and
passed to their builders so enabling their runtime behavior will not change the schema.
