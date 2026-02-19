package terrasect.generation

import terrasect.definition.Region

class Locator(val seed: Long, val root: Region) {
  val lookup = run {
    val map1 = HashMap<String, Region>()
    //    fun build(region: Region, id: ByteArray) {
    //      val key = String(id)
    //      map1[key] = region
    //
    //      for (child in region.children) {
    //        id[id.size - 1] = child.index.toByte()
    //        build(child, id)
    //      }
    //    }
    //    build(this@Locate.root, ByteArray(256))
    map1
  }
}
