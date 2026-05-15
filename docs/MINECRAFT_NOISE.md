# Minecraft 1.21.x Noise Generation Pipeline

Deep-dive reference for everything involved in turning a world seed into block states.
Written from source in `minecraft/` to inform the design of Terrasect's noise-constraint mechanism.

---

## Table of Contents

1. [High-Level Concept](#1-high-level-concept)
2. [Key Classes Glossary](#2-key-classes-glossary)
3. [Lifecycle: From World Load to Block Fill](#3-lifecycle-from-world-load-to-block-fill)
4. [RandomState – Noise Wiring at World Init](#4-randomstate--noise-wiring-at-world-init)
5. [NoiseRouter – The 15 Named Density Functions](#5-noiserouter--the-15-named-density-functions)
6. [DensityFunction Interface & Implementations](#6-densityfunction-interface--implementations)
7. [Caching Markers](#7-caching-markers)
8. [NoiseChunk – Per-Chunk Interpolation Engine](#8-noisechunk--per-chunk-interpolation-engine)
9. [Cell Grid & Trilinear Interpolation](#9-cell-grid--trilinear-interpolation)
10. [Chunk Generation Stages](#10-chunk-generation-stages)
11. [Aquifer – Fluid Placement](#11-aquifer--fluid-placement)
12. [Overworld Noise Composition (NoiseRouterData)](#12-overworld-noise-composition-noisrouterdata)
13. [Synth Layer – Low-Level Noise Algorithms](#13-synth-layer--low-level-noise-algorithms)
14. [Injection Analysis for Terrasect](#14-injection-analysis-for-terrasect)

---

## 1. High-Level Concept

Minecraft's modern (1.18+) world generation is built on a **functional density-function graph** rather than a monolithic noise method.
Each position `(blockX, blockY, blockZ)` is the input to a pure function tree.
The final output is a `double` called **finalDensity**:

- `finalDensity > 0` → solid block  
- `finalDensity ≤ 0` → air (or fluid, via the Aquifer)

Every terrain shape, cave, biome climate parameter, ore vein, and aquifer barrier is expressed as a node in this DAG.
Nodes can be cached, interpolated, or evaluated lazily depending on their `Marker` wrapper.

---

## 2. Key Classes Glossary

| Class | Role |
|---|---|
| `NoiseBasedChunkGenerator` | Top-level generator; schedules all chunk gen tasks |
| `NoiseGeneratorSettings` | Per-dimension config (seaLevel, noiseRouter, noiseSettings, aquifersEnabled…) |
| `NoiseSettings` | Cell grid dimensions: `minY`, `height`, `noiseSizeHorizontal`, `noiseSizeVertical` |
| `RandomState` | Created once per dimension; wires `NormalNoise` instances into the density function tree |
| `NoiseRouter` | Java record of 15 named `DensityFunction` slots |
| `NoiseRouterData` | Data-gen class that builds all the overworld density function definitions |
| `DensityFunction` | Interface: `compute(ctx)`, `fillArray(arr, ctx)`, `mapAll(visitor)` |
| `DensityFunctions` | Static factory + all concrete DF implementations |
| `NoiseChunk` | Per-chunk wrapper; replaces Marker nodes with interpolators and caches |
| `NormalNoise` | Two-PerlinNoise octave stack; main stochastic source for surface noise |
| `BlendedNoise` | Three-PerlinNoise (min/max/main) blend; used for base 3-D terrain |
| `Aquifer` | Decides block vs fluid at each position based on barrier/flood noises |
| `SurfaceSystem` | Applies surface rules after the noise fill (dirt, grass, sand…) |
| `Beardifier` | Adds structure-start density offsets to the final density |
| `Blender` | Blends old/new chunks at world upgrade boundaries |

---

## 3. Lifecycle: From World Load to Block Fill

```
Server startup / dimension load
  └── RandomState.create(NoiseGeneratorSettings, noiseRegistry, seed)
        ├── Creates root PositionalRandomFactory from seed
        ├── NoiseWiringHelper (DensityFunction.Visitor) iterates the NoiseRouter tree
        │     ├── visitNoise() → instantiates NormalNoise for every NoiseHolder leaf
        │     └── apply()     → wires BlendedNoise random, EndIsland seed, etc.
        ├── router = noiseGeneratorSettings.noiseRouter().mapAll(NoiseWiringHelper)
        │     Now every leaf in the tree has a live NormalNoise attached.
        ├── sampler = Climate.Sampler from router.{temperature,vegetation,continents,
        │                                         erosion,depth,ridges} (HolderHolder unwrapped)
        └── surfaceSystem, aquiferRandom, oreRandom factories created

Per-chunk generation (one NoiseChunk allocated per chunk per stage):
  └── NoiseChunk.forChunk(chunkAccess, randomState, beardifier, settings, fluidPicker, blender)
        ├── noiseRouter2 = randomState.router().mapAll(noiseChunk::wrap)
        │     Replaces Marker nodes with live interpolators/caches bound to this chunk.
        ├── preliminarySurfaceLevel ← noiseRouter2.preliminarySurfaceLevel()
        ├── aquifer ← Aquifer.create(noiseRouter2.{barrier,floodedness,spread,lava,erosion,depth})
        ├── finalDensityDF ← cacheAllInCell( add(noiseRouter2.finalDensity, BeardifierMarker) )
        ├── blockStateRule ← aquifer.computeSubstance + OreVeinifier (if enabled)
        └── NoiseInterpolator slices pre-allocated for every Interpolated-marked DF

Block fill loop (doFill in NoiseBasedChunkGenerator):
  loop cellX(0..nCellsX):
    noiseChunk.advanceCellX(q)        → fills slice1 for next X column
    loop cellZ(0..nCellsZ):
      loop cellY(nCellsY-1..0):
        noiseChunk.selectCellYZ(t,r)  → loads 8 corner values, fills CacheAllInCell arrays
        loop inCellY:
          noiseChunk.updateForY(v, d) → lerp Y step
          loop inCellX:
            noiseChunk.updateForX(z, e) → lerp X step
            loop inCellZ:
              noiseChunk.updateForZ(ac, f) → lerp Z step (writes final .value to all interpolators)
              blockState = noiseChunk.getInterpolatedState()
                → blockStateRule.calculate(noiseChunk)
                  → aquifer.computeSubstance(ctx, finalDensity.compute(ctx))
    noiseChunk.swapSlices()
  noiseChunk.stopInterpolation()
```

Key invariant: `noiseChunk` is the `FunctionContext` passed everywhere during fill.
Its `blockX/blockY/blockZ` fields reflect which actual block is being computed.

---

## 4. RandomState – Noise Wiring at World Init

**File:** `RandomState.java`

`RandomState` is created **once per dimension per server session** and is **immutable** after construction.

### What it builds

```
RandomState
  .random           PositionalRandomFactory  (root, seeded from world seed)
  .aquiferRandom    fork of random for "aquifer" namespace
  .oreRandom        fork of random for "ore" namespace
  .noiseIntances    ConcurrentHashMap<ResourceKey<NoiseParameters>, NormalNoise>
  .positionalRandoms ConcurrentHashMap<Identifier, PositionalRandomFactory>
  .router           NoiseRouter (all leaves wired to live NormalNoise objects)
  .sampler          Climate.Sampler (uses router's 6 climate DFs, HolderHolder-unwrapped)
  .surfaceSystem    SurfaceSystem
```

### NoiseWiringHelper (inner Visitor)

During construction a local `NoiseWiringHelper` walks the entire density function tree via `mapAll`:

1. **For every `NoiseHolder` leaf** (`visitNoise`): calls `getOrCreateNoise(key)` which calls
   `Noises.instantiate(noises, random, key)` → returns a seeded `NormalNoise`.
   Result cached in `noiseIntances` (ConcurrentHashMap, thread-safe).

2. **For `BlendedNoise`** (`apply`): creates a new `BlendedNoise` with a fresh seeded `RandomSource`
   (`random.fromHashOf("terrain")`).

3. **For `EndIslandDensityFunction`**: re-instantiates with the world seed `l`.

4. **Other nodes**: cached by identity in a local `HashMap<DensityFunction, DensityFunction>`
   so shared sub-trees are wired once.

After wiring, a second visitor unwraps `HolderHolder` and removes `Marker` wrappers to produce
the `Climate.Sampler`'s functions (used outside the chunk-fill loop for biome placement).

### NormalNoise creation

```
RandomState.getOrCreateNoise(key)
  → Noises.instantiate(noises, random, key)
  → NormalNoise.create(random.fromHashOf(key.identifier()), parameters)
  → NormalNoise(randomSource, params, /*normal=*/true)
       .first  = PerlinNoise.create(random, firstOctave, amplitudes)
       .second = PerlinNoise.create(random, firstOctave, amplitudes)
```

Each `NormalNoise` is constructed once and reused for the entire world session.

---

## 5. NoiseRouter – The 15 Named Density Functions

**File:** `NoiseRouter.java` (Java record)

The `NoiseRouter` is a plain record with 15 `DensityFunction` slots. It is `mapAll`-able: every
call to `mapAll(visitor)` produces a new `NoiseRouter` with each slot transformed by the visitor.

| Slot | JSON key | Primary role |
|---|---|---|
| `barrierNoise` | `barrier` | Separates adjacent aquifer cells |
| `fluidLevelFloodednessNoise` | `fluid_level_floodedness` | Flood probability per aquifer cell |
| `fluidLevelSpreadNoise` | `fluid_level_spread` | Vertical spread of flood level |
| `lavaNoise` | `lava` | Lava vs water for flooded cells below Y=-54 |
| `temperature` | `temperature` | Biome climate axis T |
| `vegetation` | `vegetation` | Biome climate axis V |
| `continents` | `continents` | continental → ocean gradient (biome + terrain) |
| `erosion` | `erosion` | Erosion factor (biome + terrain) |
| `depth` | `depth` | Depth/offset: maps Y into terrain probability |
| `ridges` | `ridges` | Weirdness/river modifier (biome + terrain) |
| `preliminarySurfaceLevel` | `preliminary_surface_level` | Approx surface Y for aquifer skip optimization |
| `finalDensity` | `final_density` | **The master output**: positive = solid |
| `veinToggle` | `vein_toggle` | Ore vein activation |
| `veinRidged` | `vein_ridged` | Ore vein shape |
| `veinGap` | `vein_gap` | Vein gap noise |

**Important**: The same `DensityFunction` objects that live in `RandomState.router` are referenced
in multiple places (sampler, NoiseChunk, aquifer). Each `mapAll` produces **new** wrapper objects
but wraps the **same** underlying noise instances from `noiseIntances`.

---

## 6. DensityFunction Interface & Implementations

**File:** `DensityFunction.java`, `DensityFunctions.java`

### Core interface

```java
interface DensityFunction {
    double compute(FunctionContext ctx);      // evaluate at a single point
    void fillArray(double[] out, ContextProvider cp); // bulk-fill (optimization path)
    DensityFunction mapAll(Visitor v);       // tree transform
    double minValue();
    double maxValue();
}
```

`FunctionContext` provides `blockX/Y/Z()` and `getBlender()`.
`ContextProvider` provides `forIndex(i)` and `fillAllDirectly(arr, df)`.

### Concrete implementations

| Class | Behaviour |
|---|---|
| `Constant` | Always returns a fixed double. `fillArray` → `Arrays.fill`. |
| `Noise` | Calls `NormalNoise.getValue(x*xzScale, y*yScale, z*xzScale)`. |
| `ShiftedNoise` | Adds domain-warp offsets to coordinates first. |
| `ShiftA/B/Shift` | Domain-warp helpers: samples noise at ¼ scale along one axis. |
| `TwoArgumentSimpleFunction (Ap2 / MulOrAdd)` | ADD / MUL / MIN / MAX of two DFs. MIN/MAX short-circuit. |
| `Mapped` | Single-arg transforms: ABS, SQUARE, CUBE, HALF_NEGATIVE, QUARTER_NEGATIVE, INVERT, SQUEEZE. |
| `Clamp` | Clamps output to [min, max]. |
| `Spline` | `CubicSpline` over up to 4 DF coordinates (continents, erosion, ridges, ridges_folded). |
| `RangeChoice` | If input in [min, max) → fn1 else fn2. |
| `BlendDensity` | Delegates to `Blender.blendDensity` for old-chunk blending. |
| `BlendAlpha/BlendOffset` | Identity when no blending; replaced per-chunk by `NoiseChunk.BlendAlpha/BlendOffset`. |
| `YClampedGradient` | Linear gradient clamped to a Y range; used for depth/ceiling/floor biases. |
| `WeirdScaledSampler` | Scales noise sampling frequency by a rarity function of its input DF. |
| `EndIslandDensityFunction` | Simplex-based End island shape. |
| `FindTopSurface` | Iterates down from upperBound to find first positive density step. |
| `HolderHolder` | Indirection to a registry `Holder<DensityFunction>`; unwrapped during wiring. |
| `Marker` | Annotation wrapper for caching strategy (see §7). |
| `BeardifierMarker/Beardifier` | Adds structure density at runtime. |
| `BlendedNoise` | Three `PerlinNoise` (minLimit/maxLimit/main) blend; base 3-D terrain. |
| `MulOrAdd` | Optimised form of Ap2 when one argument is Constant. |

### mapAll traversal

Every composite DF calls `visitor.apply(rebuild(arg1.mapAll(v), arg2.mapAll(v)))`.
The visitor can intercept and replace nodes; this is used for:
- `NoiseWiringHelper` – attach live NormalNoise objects.
- `NoiseChunk::wrap` – replace Marker nodes with chunk-bound interpolators.
- Climate.Sampler wiring – unwrap HolderHolder and Marker nodes.

---

## 7. Caching Markers

**File:** `DensityFunctions.Marker`, `NoiseChunk` inner classes

Markers are annotations placed by `NoiseRouterData` during data-gen to tell `NoiseChunk` how to
cache a sub-tree. In the raw tree they are transparent pass-throughs. `NoiseChunk::wrap` replaces
them with live cache implementations.

| Marker type | NoiseChunk replacement | When cached |
|---|---|---|
| `Interpolated` | `NoiseInterpolator` | Noise sampled at cell corners only, trilinearly interpolated per block |
| `FlatCache` | `FlatCache` | 2D: sampled once per noise-grid column at Y=0 (flat arrays indexed by quart pos) |
| `Cache2D` | `Cache2D` | 2D: sampled once per unique (blockX, blockZ) pair; per-column within slice |
| `CacheOnce` | `CacheOnce` | Sampled once per interpolation counter tick; reused within same Y/X slice step |
| `CacheAllInCell` | `CacheAllInCell` | Sampled once per cell corner, result stored for all blocks in that cell |

**Which functions use which marker:** (set in `NoiseRouterData`)

- `continents`, `erosion`, `ridges` → `FlatCache` (2D, no Y variation needed for biome)
- `offset`, `factor`, `jaggedness`, `depth` → wrapped with `splineWithBlending` which applies `FlatCache`
- `sloped_cheese` (finalDensity) → `Interpolated` (full 3D interpolation)
- Raw noise sources inside `sloped_cheese` are either `CacheOnce` or `CacheAllInCell`

**FlatCache vs Cache2D:**
- `FlatCache` pre-fills the entire chunk grid at construction time (constructor loop).
- `Cache2D` lazily caches per (X, Z) as positions are visited.
Both are indexed by quart-space coordinates (`QuartPos.fromBlock`).

---

## 8. NoiseChunk – Per-Chunk Interpolation Engine

**File:** `NoiseChunk.java`

One `NoiseChunk` instance is created per chunk per stage (cached on `ChunkAccess` via
`getOrCreateNoiseChunk`). It is **NOT thread-safe in itself** but Minecraft runs each chunk fill
on a dedicated thread.

### Construction

```
NoiseChunk(cellCountXZ, randomState, startX, startZ, noiseSettings, beardifier, settings, fluidPicker, blender)
  cellWidth  = noiseSettings.getCellWidth()   // QuartPos.toBlock(noiseSizeHorizontal) = 4*nH blocks
  cellHeight = noiseSettings.getCellHeight()  // QuartPos.toBlock(noiseSizeVertical)   = 4*nV blocks
  // For overworld: cellWidth=4, cellHeight=8 (nH=1, nV=2)

  noiseRouter2 = randomState.router().mapAll(this::wrap)
    // Every Marker in the tree is replaced with a live interpolator/cache.
    // HolderHolder and BeardifierMarker are also unwrapped here.

  preliminarySurfaceLevel = noiseRouter2.preliminarySurfaceLevel()
  aquifer = Aquifer.create(noiseRouter2.{barrier,floodedness,spread,lava,erosion,depth}, ...)

  finalDensityDF = cacheAllInCell(add(noiseRouter2.finalDensity, beardifier)).mapAll(this::wrap)
  blockStateRule = aquifer + OreVeinifier
```

### State during fill

```
interpolating     bool  – true while fill loop is active
fillingCell       bool  – true while filling cell-corner arrays (CacheAllInCell)
cellStartBlockX/Y/Z    – absolute block coordinate of current cell origin
inCellX/Y/Z       int   – offset within current cell (0..cellWidth-1 or cellHeight-1)
interpolationCounter   long  – incremented on every updateForZ(); used by CacheOnce
arrayInterpolationCounter long – incremented on every slice-fill pass; used by CacheOnce
arrayIndex         int   – index into current cell's flat array
```

### wrap() logic

```java
private DensityFunction wrapNew(DensityFunction df) {
    if (df instanceof Marker m) return switch (m.type()) {
        case Interpolated   -> new NoiseInterpolator(m.wrapped());
        case FlatCache      -> new FlatCache(m.wrapped(), /*doFill=*/true);
        case Cache2D        -> new Cache2D(m.wrapped());
        case CacheOnce      -> new CacheOnce(m.wrapped());
        case CacheAllInCell -> new CacheAllInCell(m.wrapped());
    };
    if (df == BlendAlpha.INSTANCE)    return this.blendAlpha;      // if blender active
    if (df == BlendOffset.INSTANCE)   return this.blendOffset;     // if blender active
    if (df == BeardifierMarker.INSTANCE) return this.beardifier;
    if (df instanceof HolderHolder hh) return hh.function().value();
    return df;  // pass-through for plain DFs
}
```

The `wrapped` map ensures the same `DensityFunction` identity maps to the same cache instance,
so shared sub-trees share caches.

---

## 9. Cell Grid & Trilinear Interpolation

The overworld uses a cell grid of **4×8×4 blocks** (cellWidth×cellHeight×cellWidth).
`Interpolated` nodes are sampled only at cell corners → (cellCountX+1)×(cellCountY+1)×(cellCountZ+1)
points per chunk.

Every `NoiseInterpolator` maintains **two slices** (`slice0`, `slice1`), each a 2D array
`[cellCountZ+1][cellCountY+1]` of doubles.

Fill order:
1. `initializeForFirstCellX()` → fills `slice0` for the starting X cell column.
2. `advanceCellX(q)` → fills `slice1` for the next X column.
3. `selectCellYZ(cellY, cellZ)` → loads the 8 corners `noise000..noise111` from slices.
4. `updateForY(d)` → lerp Y, produces 4 XZ-planes (`valueXZ00..11`).
5. `updateForX(d)` → lerp X, produces 2 Z-lines (`valueZ0/1`).
6. `updateForZ(d)` → lerp Z, produces final `value`.
7. `swapSlices()` at end of cellX loop.

The trilinear interpolation is done by `Mth.lerp` chained in 3 axes.
When `fillingCell` is true (corner-filling pass), `compute()` does a full 3D lerp instead of
returning the cached `value`.

### Coordinate spaces

| Space | Unit | Conversion |
|---|---|---|
| Block | 1 block | — |
| Cell-relative | 1 block | `inCellX = blockX - cellStartBlockX` |
| Noise/quart | 4 blocks | `QuartPos.fromBlock(x)` |
| Cell | `cellWidth` blocks | `Math.floorDiv(x, cellWidth)` |

---

## 10. Chunk Generation Stages

`NoiseBasedChunkGenerator` runs the following stages. Each stage triggers `getOrCreateNoiseChunk`
which caches the `NoiseChunk` on `ChunkAccess` so it is built at most once per chunk.

### Stage 1: `createBiomes`
Thread: `init_biomes` executor
- Creates `NoiseChunk`.
- Calls `noiseChunk.cachedClimateSampler(router, spawnTarget)` which wraps climate DFs for the chunk.
- `ChunkAccess.fillBiomesFromNoise(biomeResolver, climateSampler)` – assigns a biome to every
  4×4×4 quart-space cell by sampling temperature/vegetation/continents/erosion/depth/ridges.

### Stage 2: `fillFromNoise`
Thread: `wgen_fill_noise` executor
- Creates `NoiseChunk` (may reuse if already built).
- Runs `doFill`: the triple-nested cell loop described in §9.
- For each block: `getInterpolatedState()` → `blockStateRule.calculate(noiseChunk)`.
- Sets `BlockState` in `LevelChunkSection`, updates `Heightmap`.

### Stage 3: `buildSurface`
Thread: main generation
- Uses `NoiseChunk` only for `noiseChunk.get/createPreliminarySurfaceLevel` lookups.
- `SurfaceSystem.buildSurface` applies surface rules column-by-column using a `SurfaceContext`
  that re-evaluates climate noise directly.

### Stage 4: `applyCarvers`
Thread: main generation
- Passes `noiseChunk.aquifer()` to each `ConfiguredWorldCarver`.
- Carvers use the aquifer to decide whether carved air fills with water.

### `iterateNoiseColumn` (used by `getBaseHeight`, `getBaseColumn`)
- Builds a **single-cell** `NoiseChunk` (cellCountXZ=1) for a 1-column scan.
- Used for feature placement height queries, not the actual fill.

---

## 11. Aquifer – Fluid Placement

**File:** `Aquifer.java`

The `NoiseBasedAquifer` divides space into a sparse grid (16×12×16 block spacing) of aquifer
cells. Each cell independently decides its fluid type and level.

Key density functions consumed (from `noiseRouter2`, which means **wrapped**, interpolated versions):
- `barrierNoise` – value at each block; if high between two cells → forms a barrier (no fluid here)
- `fluidLevelFloodednessNoise` – per-cell flood probability
- `fluidLevelSpreadNoise` – per-cell flood Y level spread
- `lavaNoise` – determines lava vs water
- `erosion` – used to skip fluid checks above a surface threshold
- `depth` – used to skip fluid checks in surface-air regions

`computeSubstance(ctx, finalDensity)`:
1. If `finalDensity > 0` → return `null` (solid, no fluid processing needed).
2. Otherwise check if near surface (skip for performance using `preliminarySurfaceLevel`).
3. Find the 2-3 nearest aquifer cells.
4. Sample `barrierNoise` between cells to see if a barrier exists.
5. Compare barrier value to a similarity threshold; if too different → solid barrier.
6. Otherwise → return `fluidStatus.at(blockY)` (either fluid or air).

The `disabled` aquifer simply returns `fluidPicker.computeFluid(x,y,z).at(y)` when density ≤ 0
with no per-position fluid variation.

---

## 12. Overworld Noise Composition (NoiseRouterData)

**File:** `NoiseRouterData.java`

The overworld router is assembled during data-gen bootstrap. Key building blocks:

### Domain warp (shift_x, shift_z)
```
shift_x = flatCache(cache2d(shiftA(Noises.SHIFT)))
shift_z = flatCache(cache2d(shiftB(Noises.SHIFT)))
```
Both sample `Noises.SHIFT` at ¼ scale to produce 2D X/Z offsetting used by all shifted 2D noises.

### 2D climate/terrain inputs
```
continents = flatCache(shiftedNoise2d(shift_x, shift_z, 0.25, Noises.CONTINENTALNESS))
erosion    = flatCache(shiftedNoise2d(shift_x, shift_z, 0.25, Noises.EROSION))
ridges     = flatCache(shiftedNoise2d(shift_x, shift_z, 0.25, Noises.RIDGE))
ridges_folded = peaksAndValleys(ridges)  // |x|-based transform into valleys/peaks
```
All are `FlatCache`-wrapped, so they evaluate once per noise-column and are constant across all Y.

### Terrain shape splines
```
offset     = spline(TerrainProvider.overworldOffset(continents, erosion, ridges_folded))
factor     = spline(TerrainProvider.overworldFactor(continents, erosion, ridges,  ridges_folded))
jaggedness = spline(TerrainProvider.overworldJaggedness(...))
```
These are CubicSpline lookups that map climate coordinates into terrain shape parameters.
All three are cached via `FlatCache` since they only depend on 2D climate inputs.

### Depth
```
depth = offsetToDepth(offset)
      = yClampedGradient(-64, 320, 1.5, -1.5) + offset
```
This maps Y coordinate into `[1.5, -1.5]` and adds the terrain offset.
Positive depth → more likely solid (underground); negative depth → more likely air (above terrain).

### Base 3D noise (BlendedNoise)
```
base_3d_noise_overworld = BlendedNoise(xzScale=0.25, yScale=0.125, xzFactor=80, yFactor=160, smear=8)
```
Three `PerlinNoise` instances combine to add small-scale 3D variation on top of the smooth terrain shape.

### Sloped cheese (finalDensity for solid/air)
```
noiseGradientDensity = factor * (depth + jaggedness * half_negative(jagged_noise))
sloped_cheese        = noiseGradientDensity + base_3d_noise
```
`sloped_cheese` is the primary terrain density function. It is wrapped in `Interpolated` so it
is trilinearly interpolated across each 4×8×4 cell.

Final `NoiseRouter.finalDensity` for overworld = `sloped_cheese` (via `HolderHolder` indirection).

### Cave noises
Several cave-specific density functions are registered separately:
- `spaghetti_2d` – thin cave passages (WeirdScaledSampler for variable width)
- `spaghetti_roughness_function` – adds roughness to spaghetti caves
- `entrances` – cave entrance blobs
- `noodle` – narrow passageways
- `pillars` – vertical pillar features

These are composed into the final density via `min/max/add` in the NoiseRouter's `finalDensity`
chain, though the exact composition is done in `NoiseRouterData.overworld(...)`.

---

## 13. Synth Layer – Low-Level Noise Algorithms

**Package:** `net.minecraft.world.level.levelgen.synth`

### PerlinNoise
Multi-octave Perlin noise. Takes a `firstOctave` (negative integer, e.g. -7) and an amplitude
list. Each octave is an `ImprovedNoise` instance. Frequency doubles per octave; amplitude is
per-config. Outputs summed across active octaves.

### ImprovedNoise
Classic Improved Perlin noise (Ken Perlin, 2002). Gradient-based, smooth, periodic at 256.

### NormalNoise
Wraps two `PerlinNoise` instances. Evaluates both at coordinates scaled by `INPUT_FACTOR ≈ 1.018`
apart, sums them, normalises to target deviation `≈ 0.333`.
Each `NormalNoise` is created **once** at `RandomState` construction and reused for the whole session.

```
NormalNoise.getValue(x, y, z):
  = (first.getValue(x, y, z) + second.getValue(x*1.018, y*1.018, z*1.018)) * valueFactor
```

### BlendedNoise
Three `PerlinNoise` (minLimit, maxLimit, main). `main` noise selects between `minLimit` and
`maxLimit` via linear interpolation. Produces the "old-school" 3D terrain noise with smooth Y
stratification. Frequency controlled by `xzScale/yScale`; smear via `smearScaleMultiplier`.

### SimplexNoise
Standard Simplex noise; used only for End island generation.

### PerlinSimplexNoise
Legacy Nether biome noise; not used for overworld.

---

## 14. Injection Analysis for Terrasect

Terrasect's goal is to modify noise values per region without breaking the interpolation engine.

### Why the current approach is unreliable

The current `NoiseHandler` injects via a mixin on `DensityFunctions.HolderHolder` (or similar),
intercepting `compute()` calls on named density functions. There are several pitfalls:

#### Problem 1: Interpolator already holds pre-computed corner values
`Interpolated`/`NoiseInterpolator` fills corner arrays via `fillArray` and then returns
`this.value` (the lerped result) during `compute()`. If a mod intercepts `compute()` on the
**inner** function during the **fill pass**, the modified value is stored. But if it intercepts
on the **outer** interpolator's `compute()` it gets the lerped result which no longer maps
back to a single canonical noise key.

#### Problem 2: FlatCache is pre-filled at NoiseChunk construction
`FlatCache` fills its entire grid in the constructor. Any injection that only runs during
`compute()` calls on the wrapped function will fire during chunk construction, not during fill.
The values are then frozen. This is correct timing, but only if the chunk context is available
at the time the FlatCache is being filled (which it is—NoiseChunk is `this`).

#### Problem 3: The density function tree is visited by mapAll multiple times
The same `DensityFunction` object flows through three `mapAll` passes:
1. `NoiseWiringHelper` in `RandomState` (attaches NormalNoise to NoiseHolder leaves).
2. `NoiseChunk::wrap` (replaces Markers with live caches).
3. Climate.Sampler wiring (different visitor, HolderHolder/Marker stripping).

A mixin placed on `HolderHolder.compute()` (which is the entry point for named registry-backed
DFs) fires during **all three** contexts. Without a bound `NoiseChunk`, the chunk context is null.

#### Problem 4: Thread safety
`fillFromNoise` runs on `wgen_fill_noise` background threads. `createBiomes` runs on `init_biomes`
threads. Multiple chunks can be in flight simultaneously. Any chunk-aware injection must
associate state with the active `NoiseChunk` (not a static/thread-local) to be correct when
multiple chunks are generated in parallel on the same thread is not guaranteed.

### Recommended injection point

**Target:** Override inside `NoiseChunk.wrapNew()` for the `Interpolated` case.

This is the most reliable injection point because:
1. It fires exactly once per named density function per chunk.
2. `NoiseChunk` is available as `this` — full context (start pos, dimension, etc.).
3. It wraps the **innermost** version of the function before the interpolator is built.
4. The modified function supplies values at cell corners → the modification is trilinearly
   interpolated, giving the same smooth result as vanilla.

**Mechanism:**

```
wrapNew(Marker(Interpolated, originalDF))
  →  new NoiseInterpolator( wrappedModifiedDF(originalDF, noiseChunkCtx) )
```

Where `wrappedModifiedDF` is a `DensityFunction` that:
- Has the same codec/min/max declared.
- In `compute(ctx)`: evaluates `originalDF.compute(ctx)`, then applies the regional transform
  if `ctx.blockX/Z` falls inside the region (use SDF distance).
- In `fillArray(arr, cp)`: delegates to `originalDF.fillArray(arr, cp)` then transforms each
  value in the array using the same logic.

This ensures:
- Modifications happen at cell-corner sampling time → automatically trilinear-interpolated.
- Thread safety: the wrapper captures `NoiseChunk` by reference (one per chunk per thread).
- No interference with `Cache2D/FlatCache/CacheOnce` wiring (those are still replaced normally).
- Correct handling of the `fillArray` bulk path (many DFs use this and it completely bypasses
  `compute`).

### For 2D functions (FlatCache-wrapped continents, erosion, ridges, offset, factor)

These are filled during `FlatCache` construction inside `NoiseChunk(...)`.
The `NoiseChunk` is available as `this` during the constructor.
Inject by replacing the `FlatCache`'s inner function before the fill loop runs.

In `wrapNew`, for `Marker(FlatCache, innerDF)`:

```kotlin
val modifiedInner = RegionAwareDensityFunction(innerDF, noiseChunk = this)
new FlatCache(modifiedInner, /*doFill=*/true)
```

`FlatCache` with `doFill=true` calls `densityFunction.compute(SinglePointContext(k, 0, n))` for
every quart position during construction. Since `NoiseChunk` is ready by then, region lookup works.

### What NOT to intercept

- `NoiseHolder.getValue()` / `NormalNoise.getValue()` — too low-level; called from many DFs; no
  block position context available.
- `NoiseRouter.mapAll` outside of `NoiseChunk::wrap` — happens at `RandomState` init with no
  per-chunk context; modifications would apply globally.
- Anything in `Climate.Sampler` — that is used for biome assignment, which samples at quart
  positions without full noise interpolation context.

### Summary table

| Target DF category | Cache type | Correct intercept timing | Where to inject |
|---|---|---|---|
| `finalDensity` / `sloped_cheese` | `Interpolated` | Cell-corner fill (slice fill) | `wrapNew(Interpolated)` → wrap inner DF |
| `continents`, `erosion`, `ridges` | `FlatCache` | Chunk ctor, before fill | `wrapNew(FlatCache)` → wrap inner DF |
| `offset`, `factor`, `depth` | `FlatCache` (via splineWithBlending) | Chunk ctor, before fill | `wrapNew(FlatCache)` → wrap inner DF |
| Aquifer sub-functions | (raw, no interpolation) | Each block query | `wrapNew` for the non-Marker versions |
| Vein noises | `CacheAllInCell` | Per cell fill | `wrapNew(CacheAllInCell)` → wrap inner |

The most impactful and simplest target is `Interpolated`/`finalDensity` for terrain shape,
and `FlatCache`/continents+erosion for climate-driven biome and terrain offset overrides.

