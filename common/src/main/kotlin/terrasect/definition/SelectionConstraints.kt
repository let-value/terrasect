package terrasect.definition

class SelectionConstraints(
  val allowedMods: Set<String>?,
  val allowedTags: Set<String>?,
  val allowedNames: Set<String>?,
  val blockedMods: Set<String>?,
  val blockedTags: Set<String>?,
  val blockedNames: Set<String>?,
) {
  fun evaluate(resourceId: String?, tags: Set<String>?): Boolean {
    if (blockedNames?.contains(resourceId) == true) return false
    if (allowedNames?.contains(resourceId) == true) return true

    if (tags != null) {
      if (blockedTags?.containsAll(tags) == true) {
        return false
      }
      if (allowedTags?.containsAll(tags) == true) {
        return true
      }
    }

    val namespace = (resourceId)
    if (blockedMods?.contains(namespace) == true) return false
    if (allowedMods?.contains(namespace) == true) return true

    return true
  }

  companion object {
    fun extractNamespace(resourceId: String?): String {
      if (resourceId.isNullOrEmpty()) return "minecraft"
      val colonIndex = resourceId.indexOf(':')
      return if (colonIndex > 0) resourceId.take(colonIndex) else "minecraft"
    }

    fun builder(): Builder = Builder()
  }

  class Builder {
    private val allowedMods = HashSet<String>()
    private val allowedTags = HashSet<String>()
    private val allowedNames = HashSet<String>()
    private val blockedMods = HashSet<String>()
    private val blockedTags = HashSet<String>()
    private val blockedNames = HashSet<String>()

    fun allowMods(vararg mods: String) = apply { for (m in mods) allowedMods.add(m) }

    fun allowTags(vararg tags: String) = apply { for (t in tags) allowedTags.add(t) }

    fun allowNames(vararg names: String) = apply { for (n in names) allowedNames.add(n) }

    fun blockMods(vararg mods: String) = apply { for (m in mods) blockedMods.add(m) }

    fun blockTags(vararg tags: String) = apply { for (t in tags) blockedTags.add(t) }

    fun blockNames(vararg names: String) = apply { for (n in names) blockedNames.add(n) }

    fun inheritParent(parent: Builder) = apply {
      allowedMods.addAll(parent.allowedMods)
      allowedTags.addAll(parent.allowedTags)
      allowedNames.addAll(parent.allowedNames)
      blockedMods.addAll(parent.blockedMods)
      blockedTags.addAll(parent.blockedTags)
      blockedNames.addAll(parent.blockedNames)
    }

    fun build(): SelectionConstraints {
      return SelectionConstraints(
        allowedMods = HashSet(allowedMods),
        allowedTags = HashSet(allowedTags),
        allowedNames = HashSet(allowedNames),
        blockedMods = HashSet(blockedMods),
        blockedTags = HashSet(blockedTags),
        blockedNames = HashSet(blockedNames),
      )
    }
  }
}
