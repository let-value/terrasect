## What is a region?

A **region** is a named area of a dimension. Every preset starts with one or more **root** regions
— each mapped to a dimension (`minecraft:overworld`, `minecraft:the_nether`, or a modded dimension)
— and every other region is a **child** of some parent region, sized either by a fixed `radius` or
a target `budget` (an approximate area in blocks; Terrasect works out a matching size for you).

A region on its own doesn't have to do anything special — it only becomes interesting once you give
it a **strategy** (how its own children divide up its space) and/or **constraints** (noise, climate,
structures, mobs, loot — see the other doc pages). Regions without children are leaves: this is
where constraints actually apply to the world.

```toml
[regions.world]
radius = 200

[regions.forest]
parent = "world"
budget = 40000
```

Here `forest` is a plain child of `world` with no strategy of its own — it's a leaf, ready to carry
constraints. To split `world` into many named cells instead of one, give `world` a strategy.

## Strategies

A strategy answers one question: **when this region's space is divided into named children, what
shape do the pieces take?** Every image below is a real image generated straight from Terrasect's
own strategy code, not a mockup.

### Hex

Splits an area into a honeycomb of hexagonal cells. Good for worlds that want an obviously
structured, grid-like feel.

![Hex grid strategy](images/regions/hex.png)

```toml
[regions.world.strategy]
type = "hex"
tiling = true # repeat the grid across the whole parent (default)
```

### Voronoi

Scatters seed points and gives each one the area closest to it — organic, cell-like shapes with no
two alike. This is the classic "biome blob" look.

![Voronoi strategy](images/regions/voronoi.png)

```toml
[regions.world.strategy]
type = "voronoi"
tiling = false # a single cluster of cells inside the parent (default)
```

### Subdivision

Recursively splits a shape into smaller pieces — bands, wedges, or nested slices, depending on
settings. Good for gradients and layered terrain.

![Subdivision strategy](images/regions/subdivision.png)

```toml
[regions.world.strategy]
type = "subdivision"
```

### Surround

Wraps one region entirely around another, like a moat or a border ring. Point it at an existing
sibling/child region and it forms a ring around it.

![Surround strategy](images/regions/surround.png)

```toml
[regions.world.strategy]
type = "surround"
surround_region = "core" # the region to wrap around
```

### Archipelago

Scatters discrete, separated blobs — islands — rather than tiling the whole area edge-to-edge. Each
island gets its own child region, and the rest stays as a "sea" region.

![Archipelago strategy](images/regions/archipelago.png)

```toml
[regions.world.strategy]
type = "archipelago"
sea_region = "ocean" # the leftover space between islands
```

## Nesting and composition

Strategies compose: any child produced by a strategy can have its own strategy for *its* children.
A big hex grid can have each individual hex cell further split by Voronoi, subdivision, or another
hex grid — as deep as you want.

![Hex grid with Voronoi-tiled cells inside each hex](images/regions/nested-composition.png)

This is what makes a handful of strategies produce worlds that don't feel repetitive: a coarse hex
or Voronoi layer sets the broad shape of your world, then each cell gets its own independent
sub-layout underneath.

## Softening the edges

Region boundaries don't have to be perfectly geometric — see [Decorations](#decorations) for
warping, rippling, terracing, and otherwise distorting a region's boundaries so they read as
natural rather than mathematical.

## Checking your layout in-game

Once your preset is active, walk around and use `/ts query` or open the F3 debug screen to see
exactly which region you're standing in at any moment — see [Commands & Debug UI](#commands).
