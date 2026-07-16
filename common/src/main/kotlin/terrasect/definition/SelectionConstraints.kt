package terrasect.definition

class SelectionConstraints(
  val allowedMods: Set<String>,
  val allowedTags: Set<String>,
  val allowedNames: Set<String>,
  val blockedMods: Set<String>,
  val blockedTags: Set<String>,
  val blockedNames: Set<String>,
) {
  private val hasAllowRules =
    allowedMods.isNotEmpty() || allowedTags.isNotEmpty() || allowedNames.isNotEmpty()

  /**
   * Rules are checked by specificity: name, then tag, then mod. Within a tier block beats allow,
   * but a match at a more specific tier wins over any broader rule — allowNames(x) admits x even
   * when blockMods covers its namespace. Any allow rule at any tier makes the allow-list exclusive:
   * entries matching no rule are rejected. With no rules at all everything passes.
   */
  fun evaluate(resourceId: String?, tags: Set<String>?): Boolean {
    if (blockedNames.contains(resourceId)) return false
    if (allowedNames.contains(resourceId)) return true

    if (tags != null) {
      if (blockedTags.any(tags::contains)) return false
      if (allowedTags.any(tags::contains)) return true
    }

    val namespace = extractNamespace(resourceId)
    if (blockedMods.contains(namespace)) return false
    if (allowedMods.contains(namespace)) return true

    return !hasAllowRules
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
