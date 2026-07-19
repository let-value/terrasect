## Allow or block dropped items

A region's `loot` table filters the *items* that come out of loot generation inside it — chest
loot, mob drops, block drops, anything that rolls a loot table at a real position in the world —
by mod, item tag, or exact item id:

```toml
[regions.desert.loot]
block_tags = ["c:foods"]
```

- `allow_mods` / `block_mods` — filter by the mod (namespace) of the dropped item
- `allow_tags` / `block_tags` — filter by item tag (e.g. `c:foods`, `minecraft:swords`)
- `allow_names` / `block_names` — filter by exact item id

An `allow_*` list means only matching items are kept in a roll's final results; `block_*` removes
just the matching items and leaves the rest of the roll untouched. This applies after the vanilla
loot table has already rolled — Terrasect filters the resulting items rather than rewriting loot
tables, so it works alongside other mods' loot table changes without conflicting.
