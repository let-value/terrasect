package terrasect.config

import com.electronwill.nightconfig.core.UnmodifiableConfig
import terrasect.helpers.NoiseTransform

internal data class ExactOrRange(val exact: Boolean, val min: Long, val max: Long) {
  fun applyTo(exactCall: (Long) -> Unit, rangeCall: (Long, Long) -> Unit) {
    if (exact) exactCall(min) else rangeCall(min, max)
  }
}

internal class Table(private val config: UnmodifiableConfig, val path: String) {
  fun entries(): List<Pair<String, Any?>> =
    config.entrySet().map { it.key to it.getRawValue<Any?>() }

  fun raw(key: String): Any? = config.get(listOf(key))

  fun rejectUnknown(vararg allowed: String) {
    val allowedKeys = allowed.toHashSet()
    val unknown = config.entrySet().map { it.key }.filterNot(allowedKeys::contains).sorted()
    if (unknown.isNotEmpty()) fail("unknown properties: ${unknown.joinToString()}")
  }

  fun table(key: String): Table? {
    val value = raw(key) ?: return null
    return Table(asTable(key, value), "$path.$key")
  }

  fun requiredTable(key: String): Table = table(key) ?: fail(key, "table is required")

  fun asTable(key: String, value: Any?): UnmodifiableConfig =
    value as? UnmodifiableConfig ?: fail(key, "expected a table")

  fun asTableList(key: String, value: Any?): List<UnmodifiableConfig> {
    val list = value as? List<*> ?: fail(key, "expected an array of tables")
    return list.mapIndexed { index, item ->
      item as? UnmodifiableConfig ?: fail("$key[$index]", "expected a table")
    }
  }

  fun string(key: String, allowBlank: Boolean = false): String? {
    val value = raw(key) ?: return null
    return asString(key, value, allowBlank)
  }

  fun requiredString(key: String): String = string(key) ?: fail(key, "value is required")

  fun asString(key: String, value: Any?, allowBlank: Boolean = false): String {
    val string = value as? String ?: fail(key, "expected a string")
    if (!allowBlank && string.isBlank()) fail(key, "must not be blank")
    return string
  }

  fun boolean(key: String): Boolean? {
    val value = raw(key) ?: return null
    return asBoolean(key, value)
  }

  fun asBoolean(key: String, value: Any?): Boolean =
    value as? Boolean ?: fail(key, "expected a boolean")

  fun long(key: String): Long? {
    val value = raw(key) ?: return null
    return asLong(key, value)
  }

  fun requiredLong(key: String): Long = long(key) ?: fail(key, "value is required")

  fun int(key: String): Int? {
    val value = long(key) ?: return null
    if (value !in Int.MIN_VALUE..Int.MAX_VALUE) fail(key, "integer is out of range")
    return value.toInt()
  }

  fun float(key: String): Float? {
    val value = raw(key) ?: return null
    val number = asDouble(key, value)
    if (number !in -Float.MAX_VALUE..Float.MAX_VALUE) fail(key, "number is out of range")
    return number.toFloat()
  }

  fun requiredFloat(key: String): Float = float(key) ?: fail(key, "value is required")

  fun requiredDouble(key: String): Double {
    val value = raw(key) ?: fail(key, "value is required")
    return asDouble(key, value)
  }

  fun longRange(key: String): ExactOrRange? {
    val value = raw(key) ?: return null
    if (value is List<*>) {
      if (value.size != 2) fail(key, "expected an integer or a two-integer range")
      return ExactOrRange(false, asLong("$key[0]", value[0]), asLong("$key[1]", value[1]))
    }
    val exact = asLong(key, value)
    return ExactOrRange(true, exact, exact)
  }

  fun intPair(key: String): Pair<Int, Int>? {
    val value = raw(key) ?: return null
    val list = value as? List<*> ?: fail(key, "expected a two-integer range")
    if (list.size != 2) fail(key, "expected a two-integer range")
    val first = asLong("$key[0]", list[0])
    val second = asLong("$key[1]", list[1])
    if (first !in Int.MIN_VALUE..Int.MAX_VALUE || second !in Int.MIN_VALUE..Int.MAX_VALUE) {
      fail(key, "integer is out of range")
    }
    return first.toInt() to second.toInt()
  }

  fun stringArray(key: String): Array<String>? {
    val value = raw(key) ?: return null
    val list = value as? List<*> ?: fail(key, "expected a string array")
    return list.mapIndexed { index, item -> asString("$key[$index]", item) }.toTypedArray()
  }

  fun mapType(key: String): NoiseTransform.MapType {
    val name = requiredString(key).uppercase()
    return try {
      NoiseTransform.MapType.valueOf(name)
    } catch (_: IllegalArgumentException) {
      fail(key, "unknown map type '${name.lowercase()}'")
    }
  }

  fun fail(message: String): Nothing = throw TerrasectConfigException("$path: $message")

  fun fail(key: String, message: String): Nothing =
    throw TerrasectConfigException("$path.$key: $message")

  private fun asLong(key: String, value: Any?): Long =
    when (value) {
      is Byte -> value.toLong()
      is Short -> value.toLong()
      is Int -> value.toLong()
      is Long -> value
      else -> fail(key, "expected an integer")
    }

  private fun asDouble(key: String, value: Any?): Double {
    val number = value as? Number ?: fail(key, "expected a number")
    val result = number.toDouble()
    if (!result.isFinite()) fail(key, "expected a finite number")
    return result
  }
}
