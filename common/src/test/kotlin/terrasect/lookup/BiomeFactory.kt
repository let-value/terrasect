package terrasect.lookup

import java.lang.reflect.Constructor
import net.minecraft.world.attribute.EnvironmentAttributeMap
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeGenerationSettings
import net.minecraft.world.level.biome.BiomeSpecialEffects
import net.minecraft.world.level.biome.MobSpawnSettings

internal object BiomeFactory {
  private val biomeCtor: java.lang.reflect.Constructor<*> =
    Biome::class.java.getDeclaredConstructors().single().apply { isAccessible = true }

  private val effectsCtor: java.lang.reflect.Constructor<*> =
    BiomeSpecialEffects::class.java.getDeclaredConstructors().single().apply { isAccessible = true }

  private val effects: BiomeSpecialEffects =
    effectsCtor.newInstance(0, null, null, null, BiomeSpecialEffects.GrassColorModifier.NONE)
      as BiomeSpecialEffects

  private val biome: Biome =
    biomeCtor.newInstance(
      null,
      EnvironmentAttributeMap.EMPTY,
      effects,
      BiomeGenerationSettings.EMPTY,
      MobSpawnSettings.EMPTY,
    ) as Biome

  fun instance(): Biome = biome

  fun idOf(biome: Biome): String = "test:dummy"
}
