package terrasect.gametest

import kotlin.reflect.KClass

object GameTestFilter {
  private val included by lazy {
    System.getProperty("test")
      .orEmpty()
      .split(',')
      .map { it.trim().lowercase() }
      .filter(String::isNotEmpty)
      .toSet()
  }

  fun shouldRun(klass: KClass<*>): Boolean =
    included.isEmpty() || klass.simpleName?.lowercase() in included

  fun isFocused(klass: KClass<*>): Boolean = klass.simpleName?.lowercase() in included
}
