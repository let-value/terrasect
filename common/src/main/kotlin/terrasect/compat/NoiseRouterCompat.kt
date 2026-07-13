package terrasect.compat

import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.NoiseRouter

object NoiseRouterCompat {
  // spotless:off
  //? if >=1.21.11 {
  const val SURFACE_FUNCTION_KEY = "preliminarySurfaceLevel"

  //?} else {
  /*const val SURFACE_FUNCTION_KEY = "initialDensityWithoutJaggedness"
   */
  //?}
  // spotless:on

  fun surfaceFunction(router: NoiseRouter): DensityFunction {
    // spotless:off
    //? if >=1.21.11 {
    return router.preliminarySurfaceLevel
    //?} else {
    /*return router.initialDensityWithoutJaggedness
     */
    //?}
    // spotless:on
  }
}
