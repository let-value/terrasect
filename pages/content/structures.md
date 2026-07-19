## Allow or block structures

A region's `structures` table filters which structures are allowed to generate there, by mod,
structure tag, or exact name:

```toml
[regions.wasteland.structures]
allow_mods = ["minecraft"]
block_names = ["minecraft:village_plains", "minecraft:village_desert"]
```

- `allow_mods` / `block_mods` — filter by the mod (namespace) that adds the structure
- `allow_tags` / `block_tags` — filter by structure tag (e.g. `minecraft:village`)
- `allow_names` / `block_names` — filter by exact structure id

An `allow_*` list means *only* those structures are permitted in the region; `block_*` removes
specific structures while leaving everything else untouched. Leave both empty and the region doesn't
restrict structures at all.

## Spacing, separation, and frequency

A region can also override how densely its structures are spaced, using the same knobs Minecraft's
own structure placement uses:

```toml
[regions.dense_ruins.structures]
spacing = 16
separation = 6
frequency = 1.0
```

## Forcing a structure

Instead of just allowing a structure to *maybe* generate, you can force one to always exist
somewhere inside a region — deterministically, from the world seed, exactly once:

```toml
[regions.spawn.structures]
force = [
  { name = "minecraft:village_plains", radius = 96 },
]
```

A forced entry takes a structure name and, optionally, `radius` or `budget` (not both) to control
roughly how much space around it is reserved. Once you've found the region containing a forced
structure, [`/ts locate`](#commands) will tell you exactly where it landed.
