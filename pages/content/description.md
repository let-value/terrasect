## What is Terrasect?

Terrasect lets you divide your Minecraft world into named **regions** — laid out as a hex grid,
Voronoi cells, recursive subdivisions, surround shapes, or scattered islands — and give each region
its own rules for how the world generates there. One region can be a lush, gentle plain; its
neighbor a jagged, monster-infested wasteland with its own loot table — all in the same world, all
from the same seed, every time.

Nothing here is random per-visit: the same seed always produces the same layout, so a region you
found once will still be there next time.

## Issues and suggestions

Found a bug, or have an idea for something Terrasect should do? Open an issue on the
[GitHub issue tracker](https://github.com/let-value/terrasect/issues) — bug reports and
suggestions are both welcome.

## Features

- **Terrain & climate shaping.** Push a region's terrain flatter or rougher, and override its
  temperature, humidity, and other climate values independently of the surrounding world.
- **Height limits.** Cap how high or low a region is allowed to build.
- **Structure control.** Allow, block, or force specific structures (or whole mod/structure-tag
  groups) inside a region — want every plains village replaced with a pillager outpost? Done.
- **Mob spawn control.** Allow or block specific mobs (or entire mob tags) from spawning in a
  region, both during world generation and during normal play.
- **Loot control.** Allow or block loot table entries (or loot tags) inside a region.
- **Five ways to lay out regions.** Hex grid, Voronoi cells, recursive subdivision, surround shapes,
  and scattered archipelagos — pick whichever shape language fits the world you're building.
- **Simple config, or code if you want it.** Define regions in plain text preset files, or build
  them programmatically if you're writing your own add-on.
- **Built to stay fast.** Region lookups are cached per chunk, so adding a lot of regions doesn't
  mean a laggy world.

## Supported versions

| Minecraft | Fabric | NeoForge |
|---|---|---|
| 1.20.1 | ✅ | — |
| 1.21.1 | ✅ | ✅ |
| 1.21.11 | ✅ | ✅ |
| 26.1 | ✅ | ✅ |
| 26.2 | ✅ | ✅ |

Fabric builds additionally require **Fabric API** and **Fabric Language Kotlin**. NeoForge builds
additionally require **Kotlin for Forge**. Grab the matching dependency versions from the same
release page as the Terrasect jar you download.

## Quick start

1. Install Terrasect and its loader-specific Kotlin dependency (see above) like any other mod.
2. Launch the world once — Terrasect creates a `config/terrasect/` folder with an example preset and
   a `config.toml` file. No preset is active yet at this point, so world generation is untouched.
3. Open `config/terrasect/config.toml` and set `preset` to the name of a preset file in that folder
   (without the `.toml` extension) to activate it.
4. Restart the game or server. Config changes are only read on startup.

See [Getting Started](#getting-started) below for a step-by-step walkthrough, the complete
region/strategy reference (with pictures), and dedicated pages for noise, structures, and loot
constraints.

## Known limitations

Terrasect is under active development. A few preset options are accepted today but don't yet change
generation: restricting a region to specific biomes, enforcing a region's height limits, and
overriding precipitation or an inherited climate preset. These are recognized and validated, just
not wired up to world generation yet — check the project's Known Issues page for the current state
before relying on them.

## License & AI disclosure

Terrasect is MIT licensed — use it, fork it, learn from it.

LLMs were used extensively in building this mod, and some of the codebase is admittedly rough
around the edges as a result. The point of this project was the idea — region-based world
generation control — not a polished implementation. If you want to take the idea further in your
own mod, you're encouraged to; we'd love to hear about it.
