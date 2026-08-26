package terrasect.lookup

import java.lang.reflect.Constructor
import java.util.IdentityHashMap
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeGenerationSettings
import net.minecraft.world.level.biome.BiomeSpecialEffects
import net.minecraft.world.level.biome.MobSpawnSettings

internal object BiomeFactory {
  init {
    // Loading BiomeGenerationSettings.EMPTY triggers BuiltInRegistries.<clinit>, which throws
    // unless vanilla registries are bootstrapped (checkBootstrapCalled). Both calls are idempotent:
    // tryDetectVersion sets CURRENT_VERSION only if null, and bootStrap is a no-op if already done,
    // so this is safe whether or not another test booted the registries first.
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
  }

  // The dummy biome is never rendered, so every field needs only to be a valid instance: a nominal
  // ClimateSettings, the NONE GrassColorModifier, and the EMPTY generation/mob settings.
  private val ids = IdentityHashMap<Biome, String>()
  private val biome: Biome = createBiome().also { ids[it] = "test:dummy" }

  private fun createBiome(): Biome {
    // On >=1.21.11 Biome's ctor carries EnvironmentAttributeMap (absent on 1.21.1, so a direct
    // reference would not compile there). Detect that class by name so the source stays
    // version-uniform, select the matching ctor, and supply an empty attribute map only when
    // present.
    val attrMapClass: Class<*>? =
      runCatching { Class.forName("net.minecraft.world.attribute.EnvironmentAttributeMap") }
        .getOrNull()
    val ctor: Constructor<*> =
      if (attrMapClass != null) {
        Biome::class
          .java
          .declaredConstructors
          .first { c -> c.parameterTypes.any { it == attrMapClass } }
          .apply { isAccessible = true }
      } else {
        Biome::class.java.declaredConstructors.single().apply { isAccessible = true }
      }
    val climate: Any = run {
      val c = Biome::class.java.declaredClasses.first { it.name.endsWith("ClimateSettings") }
      c.getDeclaredConstructor(
          Boolean::class.javaPrimitiveType,
          Float::class.javaPrimitiveType,
          Biome.TemperatureModifier::class.java,
          Float::class.javaPrimitiveType,
        )
        .apply { isAccessible = true }
        .newInstance(false, 0.5f, Biome.TemperatureModifier.NONE, 0.5f)
    }
    val args =
      ctor.parameterTypes
        .map { p ->
          when (p.simpleName) {
            "ClimateSettings" -> climate
            "BiomeSpecialEffects" -> emptySpecialEffects()
            "GrassColorModifier" -> BiomeSpecialEffects.GrassColorModifier.NONE
            "BiomeGenerationSettings" -> BiomeGenerationSettings.EMPTY
            "MobSpawnSettings" -> MobSpawnSettings.EMPTY
            else -> emptyInstanceOrNull(p)
          }
        }
        .toTypedArray()
    return ctor.newInstance(*args) as Biome
  }

  private fun emptySpecialEffects(): BiomeSpecialEffects {
    val builder = BiomeSpecialEffects.Builder().waterColor(0)
    // spotless:off
    //? if >=1.21.11 {
    builder
    //?} else {
    /*builder.waterFogColor(0)
    builder.fogColor(0)
    builder.skyColor(0)
    *///?}
    // spotless:on
    return builder.build()
  }

  // The only parameter not covered above is EnvironmentAttributeMap (>=1.21.11). Use its EMPTY
  // field
  // if present, else a no-arg instance. Best-effort: the value is never read by the dummy biome.
  private fun emptyInstanceOrNull(p: Class<*>): Any? {
    runCatching { p.getDeclaredField("EMPTY") }
      .getOrNull()
      ?.let { f ->
        f.isAccessible = true
        return f.get(null)
      }
    return runCatching { p.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }
      .getOrNull()
  }

  fun instance(): Biome = biome

  fun instance(id: String): Biome = createBiome().also { ids[it] = id }

  fun idOf(biome: Biome): String = ids[biome] ?: "test:dummy"
}
