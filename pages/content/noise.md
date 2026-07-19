## Climate

A region can push a dimension's climate values in a direction, either as a single number or a
`min, max` range:

```toml
[regions.desert.climate]
temperature = [4000, 10000]
humidity = -8000
precipitation = "none"
```

Available climate values: `temperature`, `humidity`, `continentalness`, `erosion`, `depth`,
`weirdness`, `precipitation`, and `climate_preset`. Child regions inherit whatever their parent
doesn't override, so you only need to set what's actually different about a region.

## Height

Cap how high or low a region is allowed to build, either an exact value or a range:

```toml
[regions.canyon.height]
range = [40, 90]
```

## Terrain (noise)

For finer control than climate alone, a region can transform the underlying noise/density values
that shape terrain — for example, flattening a region or exaggerating its bumpiness:

```toml
[regions.flatlands.noise]
blend_width = 24.0 # how smoothly this region's noise blends into its neighbors

[regions.flatlands.noise.density_functions]
continents = [
  { op = "multiply", factor = 0.0 },
  { op = "add", value = 0.35 },
]
```

Each entry is a small chain of operations applied in order to one of the terrain generator's
internal values (named per Minecraft version — Terrasect handles that mapping for you). Available
operations: `clamp`, `add`, `multiply`, `remap`, `map`, `abs`, `square`, `cube`, `half_negative`,
`quarter_negative`, `invert`, and `squeeze`. Think of this as a small pipeline: each step reshapes
the value a little more before it reaches the terrain generator.

This is the most powerful (and most technical) constraint type — most presets get most of their
character from climate and structure/mob/loot rules alone, and only reach for noise transforms when
they want a genuinely different terrain shape in one region. If you just want a common shape like an
ocean, a raised plateau, or flat lowlands without hand-tuning values yourself, see
[Archetypes](#archetypes) — ready-made noise/climate bundles you can drop onto a region and
still override.

## Current limitations

A few options are accepted in preset files today but don't change generation yet: restricting a
region to specific biomes, enforcing a region's height range, and overriding `precipitation` or
`climate_preset`. They're validated and preserved through config round-trips, just not wired up to
world generation yet. Check the project's Known Issues page for the current status before relying
on them.
