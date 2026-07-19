## Allow or block mob spawns

A region's `mobs` table filters which mobs are allowed to spawn there, by mod, entity tag, or exact
entity id — both when the world first generates a chunk and during normal, ongoing spawning while
you play:

```toml
[regions.sanctuary.mobs]
block_names = ["minecraft:zombie", "minecraft:skeleton"]
```

- `allow_mods` / `block_mods` — filter by the mod (namespace) that adds the entity type
- `allow_tags` / `block_tags` — filter by entity type tag (e.g. `minecraft:undead`)
- `allow_names` / `block_names` — filter by exact entity id

An `allow_*` list means only those mobs are permitted to spawn in the region; `block_*` removes
specific mobs while leaving every other spawn untouched. Leave both empty and the region doesn't
restrict spawning at all.

This governs whether a spawn is allowed to happen in the first place — it doesn't affect mobs that
already exist (from a spawner, a mod, or wandering in from outside the region), and it doesn't touch
the mob's other spawn rules (light level, block type, and so on); it's an extra region-shaped filter
on top of everything vanilla already checks.
