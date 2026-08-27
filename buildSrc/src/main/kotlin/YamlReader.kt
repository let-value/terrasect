// Self-contained minimal YAML reader for the runtime-test descriptors.
//
// The descriptors are intentionally simple: a top-level mapping of scalar keys plus one or more
// nested lists (sequences) whose items are either plain scalars or inline `key: value` mappings,
// with lists of scalars nested under a key. That is enough for declarative profiles/scenarios and
// avoids pulling a third-party YAML library into buildSrc (which would be a network dependency at
// configuration time). This reader supports exactly that subset and nothing else.
//
// It is intentionally strict: only the subset above is supported on purpose. Anything outside the
// subset fails loudly instead of silently producing the wrong structure.
//
// NOTE: YamlNode types are referenced with the `YamlNode.` qualifier so this file does not collide
// with kotlin.Sequence / kotlin.Scalar.

import java.io.File

/** A parsed node; a plain scalar maps to [YamlNode.Scalar]. */
sealed class YamlNode {
  data class Scalar(val value: String) : YamlNode()

  data class Sequence(val items: List<YamlNode>) : YamlNode()

  data class Mapping(val entries: Map<String, YamlNode>) : YamlNode()

  operator fun get(key: String): YamlNode? = (this as? Mapping)?.entries[key]

  fun requireString(): String =
    when (this) {
      is Scalar -> value
      else -> throw YamlException("expected a scalar string but found ${this::class.simpleName}")
    }
}

/** Thrown when a descriptor violates the subset this parser supports. Fails loudly. */
class YamlException(message: String) : Exception(message)

object YamlReader {

  fun parse(text: String): Map<String, YamlNode> {
    val lines = text.split("\n")
    val (node, _) = block(lines, 0, 0)
    return (node as? YamlNode.Mapping)?.entries
      ?: throw YamlException("top-level document must be a mapping")
  }

  fun parse(file: File): Map<String, YamlNode> = parse(file.readText())

  /**
   * Parse one block (mapping or sequence) whose entries share at least [indent]. Returns the node
   * and the index of the next line to process (the line that is outdented to <= [indent] or EOF).
   */
  private fun block(lines: List<String>, start: Int, indent: Int): Pair<YamlNode, Int> {
    var i = start
    while (i < lines.size && lines[i].trim().isEmpty()) i++
    if (i >= lines.size) return YamlNode.Mapping(emptyMap()) to i
    val thisIndent = lines[i].takeWhile { it == ' ' }.length
    if (thisIndent < indent) return YamlNode.Mapping(emptyMap()) to i
    return if (lines[i].trim().startsWith("-")) seq(lines, i, thisIndent)
    else map(lines, i, thisIndent)
  }

  /**
   * Parse a mapping at [indent]: each line is `key: value` or `key:` (value on the block below).
   */
  private fun map(lines: List<String>, start: Int, indent: Int): Pair<YamlNode, Int> {
    val entries = LinkedHashMap<String, YamlNode>()
    var i = start
    while (i < lines.size) {
      val raw = lines[i]
      if (raw.trim().isEmpty() || raw.trim().startsWith("#")) {
        i++
        continue
      }
      val thisIndent = raw.takeWhile { it == ' ' }.length
      if (thisIndent < indent) break
      if (thisIndent > indent) {
        throw YamlException("unexpected indentation at line ${i + 1}: '$raw'")
      }
      val trimmed = raw.trim()
      val m =
        Regex("""^([A-Za-z0-9_.-]+):\s*(.*)$""").find(trimmed)
          ?: throw YamlException("could not parse mapping key at line ${i + 1}: '$trimmed'")
      val key = m.groupValues[1]
      val rest = m.groupValues[2].trim()
      if (rest.isEmpty()) {
        val (node, next) = block(lines, i + 1, indent + 2)
        entries[key] = node
        i = next
      } else {
        entries[key] = YamlNode.Scalar(stripQu(rest))
        i++
      }
    }
    return YamlNode.Mapping(entries) to i
  }

  /** Parse a sequence at [indent]: each item is `- <scalar>` or `- key: value` (inline mapping). */
  private fun seq(lines: List<String>, start: Int, indent: Int): Pair<YamlNode, Int> {
    val items = ArrayList<YamlNode>()
    var i = start
    while (i < lines.size) {
      val raw = lines[i]
      if (raw.trim().isEmpty() || raw.trim().startsWith("#")) {
        i++
        continue
      }
      val thisIndent = raw.takeWhile { it == ' ' }.length
      if (thisIndent < indent) break
      val trimmed = raw.trim()
      if (!trimmed.startsWith("-")) break
      val afterDash = trimmed.removePrefix("-").trim()
      i++
      when {
        afterDash.isEmpty() -> {
          val (node, next) = block(lines, i, indent + 2)
          items.add(node)
          i = next
        }
        afterDash.contains(":") -> {
          val kv = afterDash.split(":", limit = 2)
          val firstKey = kv[0].trim()
          val firstVal = kv.getOrNull(1)?.trim()
          val sub = LinkedHashMap<String, YamlNode>()
          if (firstVal != null && firstVal.isNotEmpty()) {
            sub[firstKey] = YamlNode.Scalar(stripQu(firstVal))
          } else {
            val (node, next) = block(lines, i, indent + 2)
            sub[firstKey] = node
            i = next
          }
          // Consume the remaining sibling keys of this inline mapping, aligned under the first key.
          val sibIndent = indent + 2
          while (i < lines.size) {
            val sraw = lines[i]
            if (sraw.trim().isEmpty() || sraw.trim().startsWith("#")) {
              i++
              continue
            }
            val sIndent = sraw.takeWhile { it == ' ' }.length
            if (sIndent < sibIndent) break
            val sm = Regex("""^([A-Za-z0-9_.-]+):\s*(.*)$""").find(sraw.trim())!!
            val sk = sm.groupValues[1]
            val sr = sm.groupValues[2].trim()
            i++
            if (sr.isEmpty()) {
              val (node, nextIdx) = block(lines, i, sibIndent + 2)
              sub[sk] = node
              i = nextIdx
            } else {
              sub[sk] = YamlNode.Scalar(stripQu(sr))
            }
          }
          items.add(YamlNode.Mapping(sub))
        }
        else -> items.add(YamlNode.Scalar(stripQu(afterDash)))
      }
    }
    return YamlNode.Sequence(items) to i
  }

  private fun stripQu(s: String): String =
    s.removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'")
}

/** Convenience accessors over a parsed descriptor. */
fun YamlNode.asScalar(): String = (this as? YamlNode.Scalar)?.value ?: error("expected scalar")
