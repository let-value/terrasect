package terrasect.lookup

import net.minecraft.core.Holder

internal fun <T : Any> tagsOf(holder: Holder<T>): Set<String> {
  val tags = HashSet<String>()
  holder.tags().forEach { tag -> tags.add(tag.location().toString()) }
  return tags
}
