package terrasect.compat

import com.mojang.datafixers.util.Pair as MojangPair
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import net.minecraft.core.Holder
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Climate
import terrasect.handler.NoiseLogger

private val log = NoiseLogger.registry

object TerraBlenderCompat {
  internal fun expandBiomeParameters(
    parameters: Climate.ParameterList<Holder<Biome>>
  ): Climate.ParameterList<Holder<Biome>> {
    val treeCountMethod =
      parameters.javaClass.methods.firstOrNull {
        it.name == "getTreeCount" && it.parameterCount == 0
      } ?: return parameters
    val treeMethod =
      parameters.javaClass.methods.firstOrNull {
        it.name == "getTree" &&
          it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
      } ?: return parameters
    return try {
      val values = ArrayList<MojangPair<Climate.ParameterPoint, Holder<Biome>>>()
      val treeCount = treeCountMethod.invoke(parameters) as Int
      for (treeIndex in 0 until treeCount) {
        val tree = treeMethod.invoke(parameters, treeIndex) ?: continue
        collectTreeValues(tree, values)
      }
      if (values.isEmpty()) parameters else Climate.ParameterList(values)
    } catch (exception: Exception) {
      log.warn {
        "build: could not read positional biome provider parameters from ${parameters.javaClass.name}; " +
          "vanilla parameter entries will be used (${exception.javaClass.simpleName})"
      }
      parameters
    }
  }

  private fun collectTreeValues(
    tree: Any,
    values: MutableList<MojangPair<Climate.ParameterPoint, Holder<Biome>>>,
  ) {
    val rootField =
      findField(tree.javaClass) { !Modifier.isStatic(it.modifiers) && it.name == "root" }
        ?: error("missing biome provider tree root")
    rootField.makeAccessible()
    collectTreeNode(rootField.get(tree), values)
  }

  private fun collectTreeNode(
    node: Any,
    values: MutableList<MojangPair<Climate.ParameterPoint, Holder<Biome>>>,
  ) {
    val childField =
      findField(node.javaClass) {
        it.type.isArray && it.type.componentType.isAssignableFrom(node.javaClass)
      }
    if (childField != null) {
      childField.makeAccessible()
      val children = childField.get(node)
      for (index in 0 until ReflectArray.getLength(children)) {
        collectTreeNode(ReflectArray.get(children, index), values)
      }
      return
    }

    val parameterField =
      findField(node.javaClass) {
        it.type.isArray && !it.type.componentType.isAssignableFrom(node.javaClass)
      } ?: error("missing biome provider leaf parameters")
    val valueField =
      node.javaClass.declaredFields.firstOrNull {
        !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive && !it.type.isArray
      } ?: error("missing biome provider leaf value")
    parameterField.makeAccessible()
    valueField.makeAccessible()
    val parameters = parameterField.get(node)
    if (ReflectArray.getLength(parameters) < 7) {
      return
    }
    @Suppress("UNCHECKED_CAST") val holder = valueField.get(node) as? Holder<Biome> ?: return
    val point =
      Climate.ParameterPoint(
        ReflectArray.get(parameters, 0) as Climate.Parameter,
        ReflectArray.get(parameters, 1) as Climate.Parameter,
        ReflectArray.get(parameters, 2) as Climate.Parameter,
        ReflectArray.get(parameters, 3) as Climate.Parameter,
        ReflectArray.get(parameters, 4) as Climate.Parameter,
        ReflectArray.get(parameters, 5) as Climate.Parameter,
        (ReflectArray.get(parameters, 6) as Climate.Parameter).min(),
      )
    values += MojangPair(point, holder)
  }

  private fun findField(type: Class<*>, predicate: (Field) -> Boolean): Field? {
    var current: Class<*>? = type
    while (current != null) {
      current.declaredFields.firstOrNull(predicate)?.let {
        return it
      }
      current = current.superclass
    }
    return null
  }

  private fun Field.makeAccessible() {
    if (!trySetAccessible()) {
      error("cannot access biome provider field ${name}")
    }
  }
}
