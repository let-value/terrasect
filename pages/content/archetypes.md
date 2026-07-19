## What is an archetype?

Hand-tuning noise and climate values (see [Noise & Climate](#noise)) gives you the most control,
but it takes some trial and error to get a recognizable terrain shape. An **archetype** is a
ready-made bundle of noise and climate values for a common terrain shape — apply one to a region and
it fills in sensible defaults for you.

Archetypes only fill in values the region hasn't already set. Anything you set explicitly on a
region — its own `noise` or `climate` tables — always wins over the archetype, and the archetype
always wins over whatever the region would otherwise inherit from its parent. Think of an archetype
as a starting character for a region, not a lock on it.

```toml
[regions.bay.archetype]
type = "ocean"
depth = 0.7
```

## Available archetypes

### Ocean

Open water, generated the same way a real vanilla ocean is — natural depth, a natural aquifer
filling it to sea level, no extra cost. `depth` (0–1, default `0.6`) controls how deep.

```toml
[regions.sea.archetype]
type = "ocean"
depth = 0.6
```

### Landlocked

Keeps the surface above sea level so no ocean forms in the region, while vanilla rivers and lakes
still cut through normally. `shore` (0–1, default `0.3`) nudges the region closer to the coast
without ever reaching the ocean band.

```toml
[regions.heartland.archetype]
type = "landlocked"
shore = 0.3
```

### Flatlands

Gentle, low terrain — flattened and settled below the usual terrain height, but still above sea
level. `strength` (0–1, default `0.7`) controls how flat and how low; `1` is flattest and lowest.

```toml
[regions.plains.archetype]
type = "flatlands"
strength = 0.7
```

### Highlands

A raised plateau — inland, low erosion, and a lifted surface. `strength` (0–1, default `0.7`)
controls how dramatic the rise is; `1` is highest.

```toml
[regions.plateau.archetype]
type = "highlands"
strength = 0.7
```

## Combining with your own constraints

Archetypes are meant to be layered under your own tweaks, not instead of them. A common pattern is
an archetype for the overall terrain shape, plus a region's own `climate`, `structures`, `mobs`, or
`loot` tables for everything else:

```toml
[regions.frontier.archetype]
type = "highlands"
strength = 0.9

[regions.frontier.mobs]
block_names = ["minecraft:zombie"]
```
