package terrasect.compat

import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure
import terrasect.mixin.structure.JigsawStructureAccessor

object StructureMetadataCompat {
  fun jigsawRadius(structure: Structure): Int? {
    if (structure !is JigsawStructure) return null
    val accessor = structure as Any as JigsawStructureAccessor
    // spotless:off
    //? if >=1.21.11 {
    return accessor.`terrasect$maxDistanceFromCenter`().horizontal()
    //?} else {
    /*return accessor.`terrasect$maxDistanceFromCenter`()
     */
    //?}
    // spotless:on
  }
}
