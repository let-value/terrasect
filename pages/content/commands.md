## `/ts` commands

Terrasect adds a `/ts` command with three subcommands. All of them require operator permissions on
a server (you already have this in singleplayer).

### `/ts query`

Tells you which region you're standing in right now, and lists its named children (if it has a
strategy):

```
/ts query
> #3f2a.cell > .forest :
- #3f2a1c.meadow
- #3f2a1d.rocks
```

Add a selector to look up a specific region relative to where you're standing instead:

```
/ts query .desert
```

### `/ts print`

Like `query`, but with the full picture: the region's center, distance from you, bounding box,
approximate area, which strategy it uses, its children, and — if it comes from a preset — the
resolved TOML for that region, exactly as Terrasect understands it:

```
/ts print
```

This is the fastest way to check "is my preset actually doing what I think it's doing?" without
digging through config files.

### `/ts locate <selector>`

Finds a named region anywhere in the current dimension and reports its center coordinates and your
distance from it — handy for tracking down a [forced structure](#forcing-a-structure)
or just finding a named region you haven't visited yet:

```
/ts locate .village_plains
```

### Selectors

A selector picks out a region by name (`.name`), by its exact instance address (`#address`), or
both together (`#address.name`). Chain selectors with `>` to require an immediate parent/child
relationship:

```
.forest              -- any region named "forest", anywhere
.cell > .meadow       -- a "meadow" that is a direct child of a "cell"
#3f2a.cell            -- the one specific "cell" instance at address 3f2a
```

`query` and `print` default to your current region when you don't give a selector.

## Debug overlay (F3)

On Minecraft 1.21.11 and newer, Terrasect adds a line to the vanilla F3 debug screen showing your
current region chain from root to the deepest match, each with its distance from the region
boundary — the same information `/ts query` gives you, always visible while you play. (The debug
screen's plugin API doesn't exist on older supported versions, so this is 1.21.11+ only; the `/ts`
commands work everywhere.)
