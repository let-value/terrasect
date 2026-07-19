## What are decorations?

A strategy (see [Regions](#regions)) decides the basic shape of a region's children — hexagons,
Voronoi cells, and so on. **Decorations** distort that shape afterwards, so borders read as natural
or stylized instead of perfectly geometric. Every image below (including the "before" one) is
generated straight from Terrasect's own decoration code, applied to the same plain tiled-Voronoi
layout, so you can compare them directly.

![Plain tiled Voronoi layout with no decorations](images/decorations/base-voronoi.png)

Decorations go on the *parent* region — the one with the strategy — and affect how all of its
children's boundaries look:

```toml
[regions.world.strategy]
type = "voronoi"
tiling = true

[[regions.world.decorations]]
type = "warp"
amplitude = 12.0
scale = 48.0
```

You can stack more than one decoration on the same region — each `[[regions.world.decorations]]`
table adds another one, applied in order.

There are two kinds, though you don't need to track which is which to use them: **domain**
decorations bend the space itself before the strategy decides ownership (so every boundary in the
region warps together, and locating a point still lines up with what you see); **layer**
decorations reshape one child's own edge on top of that (gaps, rings, stripes). The reference below
groups them that way just for context.

## Domain decorations

### Warp

Smooth, organic distortion — the classic "hand-drawn" look.

![Warp decoration](images/decorations/warp.png)

```toml
[[regions.world.decorations]]
type = "warp"
amplitude = 12.0 # how far edges bend
scale = 48.0      # how large the bends are (bigger = smoother, broader waves)
octaves = 2       # optional, layers of detail (default 2)
```

### Dither

A subtler, higher-frequency version of warp — light roughness rather than big bends.

![Dither decoration](images/decorations/dither.png)

```toml
[[regions.world.decorations]]
type = "dither"
width = 4.0
scale = 6.0 # optional, default 8.0
```

### Swirl

Rotates space around the region's center, twisting nearby boundaries into a spiral.

![Swirl decoration](images/decorations/swirl.png)

```toml
[[regions.world.decorations]]
type = "swirl"
strength = 1.4 # how much rotation at the center
radius = 130.0  # how far the effect reaches
```

### Ripple

Wavy, rippling edges — good for water-adjacent or dreamlike regions.

![Ripple decoration](images/decorations/ripple.png)

```toml
[[regions.world.decorations]]
type = "ripple"
amplitude = 7.0
wavelength = 52.0
```

### Shear

Slants space along one axis, tilting every boundary in the same direction.

![Shear decoration](images/decorations/shear.png)

```toml
[[regions.world.decorations]]
type = "shear"
x = 0.6 # optional, default 0
z = 0.0 # optional, default 0
```

### Terrace

Snaps space onto a coarse grid, turning smooth curves into blocky, stepped edges.

![Terrace decoration](images/decorations/terrace.png)

```toml
[[regions.world.decorations]]
type = "terrace"
step = 9.0
```

## Layer decorations

### Gap

Pulls every child's boundary inward slightly, leaving a visible strip of the parent's own space
between neighboring children.

![Gap decoration](images/decorations/gap.png)

```toml
[[regions.world.decorations]]
type = "gap"
width = 4.0
```

### Onion

Turns each filled child into a thin shell — a ring outline instead of a solid area.

![Onion decoration](images/decorations/onion.png)

```toml
[[regions.world.decorations]]
type = "onion"
thickness = 5.0
```

### Stripes

Cuts repeating parallel stripes through every child, at any angle.

![Stripes decoration](images/decorations/stripes.png)

```toml
[[regions.world.decorations]]
type = "stripes"
width = 16.0
gap = 6.0
angle = 30.0 # optional, degrees, default 0
```

### Rings

Cuts repeating concentric rings around the region's center through every child.

![Rings decoration](images/decorations/rings.png)

```toml
[[regions.world.decorations]]
type = "rings"
width = 16.0
gap = 8.0
```
