## Install

1. Install **Fabric Loader** or **NeoForge** for a supported Minecraft version (see the versions
   table on the home page).
2. Install the matching Kotlin dependency:
   - **Fabric:** Fabric API + Fabric Language Kotlin
   - **NeoForge:** Kotlin for Forge
3. Drop the Terrasect jar for your version and loader into your `mods` folder, like any other mod.

## First launch

Launch the game or server once. Terrasect creates a `config/terrasect/` folder containing:

- `config.toml` — global settings and which preset is active
- `example.toml` — a working example preset, showing off every constraint type
- `climate_debug.toml` — a small preset useful for inspecting climate values

No preset is active yet at this point, so your world generates exactly like vanilla. Terrasect
only changes generation once you deliberately turn it on.

## Activate a preset

Open `config/terrasect/config.toml` and set:

```toml
preset = "example"
```

`"example"` refers to `example.toml` in the same folder — the preset name is just the file name
without `.toml`. Restart the game or server (config is only read on startup) and your overworld will
generate using that preset.

## Write your own preset

Create a new `.toml` file in `config/terrasect/` (or copy and edit `example.toml`) and point
`preset` at it. Every preset needs a `schema` version, at least one dimension root, and one or more
named regions:

```toml
schema = 1

[roots]
"minecraft:overworld" = "world"

[regions.world]
radius = 200

[regions.world.strategy]
type = "hex"

[regions.forest]
parent = "world"
budget = 40000

[regions.forest.mobs]
block_names = ["minecraft:zombie"]
```

This carves the overworld into a hex grid, and every hex cell named `forest` blocks zombies from
spawning. See [Regions](#regions) for every strategy and how they nest, and
[Decorations](#decorations), [Noise & Climate](#noise), [Archetypes](#archetypes),
[Structures](#structures), [Mobs](#mobs), and [Loot](#loot) for the rest of what a
region can control.

## Check your work in-game

Once a preset is active, use the [`/ts` commands](#commands) to see exactly which region you're
standing in, list its neighbors, and inspect its full resolved configuration — no need to guess
whether your preset is doing what you expect.
