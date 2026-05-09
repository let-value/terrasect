package terrasect

import kotlin.reflect.KClass

object GameTestFilter {
  val FOCUS: String? = null

  private val included: Set<String>? by lazy {
    (FOCUS ?: System.getProperty("test"))
        ?.takeIf { it.isNotBlank() }
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.toSet()
  }

  fun shouldRun(klass: KClass<*>): Boolean {
    val name = klass.simpleName ?: return true
    return included == null || name.lowercase() in included!!
  }
}
