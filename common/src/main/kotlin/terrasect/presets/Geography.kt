package terrasect.presets

import terrasect.definition.Archetype
import terrasect.definition.RegionRegistry
import terrasect.definition.Strategy

val GEOGRAPHY_REGIONS =
  listOf(
    "mountain",
    "volcano",
    "glacier",
    "plateau",
    "hills",
    "cliffs",
    "mesa",
    "butte",
    "canyon",
    "plain",
    "prairie",
    "forest",
    "rainforest",
    "basin",
    "marsh",
    "swamp",
    "cave",
    "desert",
    "dune",
    "oasis",
    "tundra",
    "beach",
    "delta",
    "cape",
    "peninsula",
    "isthmus",
    "fjord",
    "bay",
    "river",
    "waterfall",
    "sea",
    "gulf",
    "strait",
    "channel",
    "lagoon",
    "island",
    "atoll",
    "deep_ocean",
    "ocean",
  )

var GEOGRAPHY =
  RegionRegistry().let {
    it.setRoot("minecraft:overworld", "overworld")

    it.region("overworld").radius(400).strategy(Strategy.hex("ocean").tiling(true))
    it.region("ocean").radius(100).archetype(Archetype.ocean(0.8f))

    it.region("continent").parent("overworld").strategy(Strategy.voronoi())

    // ── Highlands ──
    it.region("highlands_zone").parent("continent").strategy(Strategy.voronoi())

    it
      .region("mountain")
      .parent("highlands_zone")
      .radius(65)
      .archetype(Archetype.highlands(0.9f))
      .climate { temperature(-5000).humidity(0) }

    it
      .region("volcano")
      .parent("highlands_zone")
      .radius(55)
      .archetype(Archetype.highlands(0.8f))
      .climate { temperature(7000).humidity(-3000) }

    it
      .region("glacier")
      .parent("highlands_zone")
      .radius(70)
      .archetype(Archetype.highlands(0.6f))
      .climate { temperature(-9000).humidity(5000) }

    it
      .region("plateau")
      .parent("highlands_zone")
      .radius(80)
      .archetype(Archetype.highlands(0.5f))
      .climate { temperature(0).humidity(0) }

    it
      .region("hills")
      .parent("highlands_zone")
      .radius(70)
      .archetype(Archetype.highlands(0.3f))
      .climate { temperature(2000).humidity(2000) }

    it
      .region("cliffs")
      .parent("highlands_zone")
      .radius(50)
      .archetype(Archetype.highlands(0.7f))
      .climate { temperature(0).humidity(-1000) }

    it
      .region("mesa")
      .parent("highlands_zone")
      .radius(65)
      .archetype(Archetype.highlands(0.6f))
      .climate { temperature(5000).humidity(-7000) }

    it
      .region("butte")
      .parent("highlands_zone")
      .radius(40)
      .archetype(Archetype.highlands(0.4f))
      .climate { temperature(5000).humidity(-6000) }

    it
      .region("canyon")
      .parent("highlands_zone")
      .radius(55)
      .archetype(Archetype.highlands(0.5f))
      .climate { temperature(3000).humidity(-4000) }

    // ── Lowlands ──
    it.region("lowlands_zone").parent("continent").strategy(Strategy.voronoi())

    it
      .region("plain")
      .parent("lowlands_zone")
      .radius(80)
      .archetype(Archetype.landlocked(0.3f))
      .climate { temperature(0).humidity(0) }

    it
      .region("prairie")
      .parent("lowlands_zone")
      .radius(70)
      .archetype(Archetype.landlocked(0.4f))
      .climate { temperature(3000).humidity(-2000) }

    it
      .region("forest")
      .parent("lowlands_zone")
      .radius(80)
      .archetype(Archetype.landlocked(0.3f))
      .climate { temperature(0).humidity(5000) }

    it
      .region("rainforest")
      .parent("lowlands_zone")
      .radius(70)
      .archetype(Archetype.landlocked(0.2f))
      .climate { temperature(8000).humidity(9000) }

    it
      .region("basin")
      .parent("lowlands_zone")
      .radius(65)
      .archetype(Archetype.flatlands(0.9f))
      .climate { temperature(0).humidity(0) }

    it
      .region("marsh")
      .parent("lowlands_zone")
      .radius(55)
      .archetype(Archetype.flatlands(0.8f))
      .climate { temperature(0).humidity(7000) }

    it
      .region("swamp")
      .parent("lowlands_zone")
      .radius(55)
      .archetype(Archetype.flatlands(0.7f))
      .climate { temperature(4000).humidity(8000) }

    it
      .region("cave")
      .parent("lowlands_zone")
      .radius(50)
      .archetype(Archetype.landlocked(0.5f))
      .climate { temperature(0).humidity(0) }

    // ── Arid ──
    it.region("arid_zone").parent("continent").strategy(Strategy.voronoi())

    it
      .region("desert")
      .parent("arid_zone")
      .radius(80)
      .archetype(Archetype.landlocked(0.5f))
      .climate { temperature(9000).humidity(-9000) }

    it.region("dune").parent("arid_zone").radius(65).archetype(Archetype.landlocked(0.6f)).climate {
      temperature(8000).humidity(-8000)
    }

    it
      .region("oasis")
      .parent("arid_zone")
      .radius(40)
      .archetype(Archetype.landlocked(0.8f))
      .climate {
        temperature(6000).humidity(5000)
      }

    it
      .region("tundra")
      .parent("arid_zone")
      .radius(70)
      .archetype(Archetype.landlocked(0.3f))
      .climate { temperature(-8000).humidity(-3000) }

    // ── Coastal ──
    it.region("coastal_zone").parent("continent").strategy(Strategy.voronoi())

    it
      .region("beach")
      .parent("coastal_zone")
      .radius(50)
      .archetype(Archetype.flatlands(0.6f))
      .climate { temperature(3000).humidity(0) }

    it
      .region("delta")
      .parent("coastal_zone")
      .radius(55)
      .archetype(Archetype.flatlands(0.8f))
      .climate { temperature(2000).humidity(3000) }

    it
      .region("cape")
      .parent("coastal_zone")
      .radius(50)
      .archetype(Archetype.landlocked(0.2f))
      .climate { temperature(0).humidity(0) }

    it
      .region("peninsula")
      .parent("coastal_zone")
      .radius(65)
      .archetype(Archetype.landlocked(0.3f))
      .climate { temperature(0).humidity(0) }

    it
      .region("isthmus")
      .parent("coastal_zone")
      .radius(40)
      .archetype(Archetype.landlocked(0.2f))
      .climate { temperature(1000).humidity(0) }

    it
      .region("fjord")
      .parent("coastal_zone")
      .radius(55)
      .archetype(Archetype.highlands(0.3f))
      .climate { temperature(-5000).humidity(4000) }

    it.region("bay").parent("coastal_zone").radius(65).archetype(Archetype.ocean(0.3f)).climate {
      temperature(0).humidity(0)
    }

    // ── Inland Water ──
    it.region("water_zone").parent("continent").strategy(Strategy.voronoi())

    it
      .region("river")
      .parent("water_zone")
      .radius(50)
      .archetype(Archetype.flatlands(0.3f))
      .climate { temperature(0).humidity(3000) }

    it
      .region("waterfall")
      .parent("water_zone")
      .radius(40)
      .archetype(Archetype.highlands(0.4f))
      .climate { temperature(0).humidity(5000) }

    it.region("sea").parent("water_zone").radius(65).archetype(Archetype.ocean(0.5f)).climate {
      temperature(0).humidity(0)
    }

    it.region("gulf").parent("water_zone").radius(55).archetype(Archetype.ocean(0.4f)).climate {
      temperature(3000).humidity(0)
    }

    it.region("strait").parent("water_zone").radius(40).archetype(Archetype.ocean(0.3f)).climate {
      temperature(0).humidity(0)
    }

    it.region("channel").parent("water_zone").radius(40).archetype(Archetype.ocean(0.3f)).climate {
      temperature(0).humidity(0)
    }

    it.region("lagoon").parent("water_zone").radius(50).archetype(Archetype.ocean(0.2f)).climate {
      temperature(4000).humidity(3000)
    }

    // ── Island Zone ──
    it.region("island_zone").parent("continent").strategy(Strategy.archipelago("deep_ocean"))

    it
      .region("island")
      .parent("island_zone")
      .radius(55)
      .archetype(Archetype.landlocked(0.5f))
      .climate { temperature(4000).humidity(3000) }

    it.region("atoll").parent("island_zone").radius(40).archetype(Archetype.ocean(0.2f)).climate {
      temperature(5000).humidity(4000)
    }

    it.region("deep_ocean").parent("island_zone").radius(160).archetype(Archetype.ocean(0.9f))

    return@let it
  }
