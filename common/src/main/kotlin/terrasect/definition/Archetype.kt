package terrasect.definition

// An archetype is a pre-baked bundle of constraints — a meta-layer that expands into the same
// climate/noise levers a region could set by hand. It only fills constraints the region did not set
// explicitly (see RegionBuilder.applyArchetype), so an archetype is a starting character the region
// can still override.
//
// Terrain shape is driven by pinning the vanilla climate density functions — continentalness,
// erosion, ridges — to constants, exactly the inputs vanilla's terrain splines already key off. The
// pin composes all the way into finalDensity, so the offset/factor splines place the surface the
// same way they would for a real biome and the natural aquifer fills water on its own. The parallel
// climate ranges keep biome selection agreeing with the reshaped terrain; values are in the mod's
// ±10000 climate units.
sealed class Archetype {
  abstract fun apply(builder: RegionBuilder)

  // Open water everywhere. Pin continentalness into vanilla's deep-ocean band and let the offset
  // spline place the sea floor at natural ocean depth (~y45); the floor stays shallow, the natural
  // aquifer fills it to sea level, and generation costs the same as a real ocean — no aquifer
  // flooding or finalDensity carving. `depth` in [0,1] pushes continentalness deeper.
  data class Ocean(val depth: Float = 0.6f) : Archetype() {
    override fun apply(builder: RegionBuilder) {
      val d = depth.coerceIn(0f, 1f)
      builder.noise {
        densityFunction("continents") { it.multiply(0.0).add(-1.0 - 0.1 * d) }
        densityFunction("erosion") { it.multiply(0.0).add(0.2) }
        densityFunction("ridges") { it.multiply(0.0).add(0.0) }
      }
      builder.climate { continentalness(-10000, -4000) }
    }
  }

  // No open ocean forms. Solidly-inland continentalness keeps the surface above sea level so no
  // ocean
  // forms, while vanilla rivers and lakes still cut through. `shore` in [0,1] nudges
  // continentalness
  // toward the coast (lower, closer to sea) without reaching the ocean band.
  data class Landlocked(val shore: Float = 0.3f) : Archetype() {
    override fun apply(builder: RegionBuilder) {
      val s = shore.coerceIn(0f, 1f)
      builder.noise {
        densityFunction("continents") { it.multiply(0.0).add(0.5 - 0.2 * s) }
        densityFunction("erosion") { it.multiply(0.0).add(0.25) }
      }
      builder.climate { continentalness(1000, 10000) }
    }
  }

  // Gentle, low terrain: high erosion flattens it and the density is dropped so it settles below
  // vanilla but above sea level. `strength` in [0,1] (1 = flattest and lowest).
  data class Flatlands(val strength: Float = 0.7f) : Archetype() {
    override fun apply(builder: RegionBuilder) {
      val t = strength.coerceIn(0f, 1f)
      builder.noise {
        densityFunction("continents") { it.multiply(0.0).add(0.2) }
        densityFunction("erosion") { it.multiply(0.0).add(0.15 + 0.2 * t) }
        densityFunction("ridges") { it.multiply(0.0).add(0.2) }
        densityFunction("finalDensity") { it.add(-0.05 - 0.14 * t) }
      }
      builder.climate { erosion(2000, 10000) }
    }
  }

  // A raised plateau: inland continents, low erosion, and a lifted surface level. `strength` in
  // [0,1] (1 = highest and most dramatic).
  data class Highlands(val strength: Float = 0.7f) : Archetype() {
    override fun apply(builder: RegionBuilder) {
      val t = strength.coerceIn(0f, 1f)
      builder.noise {
        densityFunction("continents") { it.multiply(0.0).add(0.35) }
        densityFunction("erosion") { it.multiply(0.0).add(-0.3 - 0.5 * t) }
        densityFunction("ridges") { it.multiply(0.0).add(0.3 + 0.43 * t) }
        densityFunction("preliminarySurfaceLevel") { it.add(8.0 + 11.0 * t) }
        densityFunction("finalDensity") { it.add(0.02) }
      }
      builder.climate { erosion(-10000, -3000) }
    }
  }

  companion object {
    fun ocean(depth: Float = 0.6f) = Ocean(depth)

    fun landlocked(shore: Float = 0.3f) = Landlocked(shore)

    fun flatlands(strength: Float = 0.7f) = Flatlands(strength)

    fun highlands(strength: Float = 0.7f) = Highlands(strength)
  }
}
