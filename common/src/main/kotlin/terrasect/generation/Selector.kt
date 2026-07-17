package terrasect.generation

data class SelectorPart(val id: String?, val name: String?, val immediate: Boolean)

object Selector {
  private val part = Regex("^(?:#([^.#>\\s]+))?(?:\\.([^.#>\\s]+))?$")

  fun parse(query: String): List<SelectorPart> {
    val tokens = query.replace(">", " > ").trim().split(Regex("\\s+"))
    val parts = mutableListOf<SelectorPart>()
    var immediate = false

    for (token in tokens) {
      if (token == ">") {
        if (parts.isEmpty() || immediate) {
          return emptyList()
        }
        immediate = true
        continue
      }

      val match = part.matchEntire(token) ?: return emptyList()
      val (id, name) = match.destructured
      if (id.isEmpty() && name.isEmpty()) {
        return emptyList()
      }

      parts += SelectorPart(id.ifEmpty { null }, name.ifEmpty { null }, immediate)
      immediate = false
    }

    if (immediate) {
      return emptyList()
    }

    return parts
  }
}
