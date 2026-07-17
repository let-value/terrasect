package terrasect.handler

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import kotlin.math.max
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import terrasect.compat.ResourceKeyCompat
import terrasect.config.TerrasectTomlWriter
import terrasect.definition.PresetRegistry
import terrasect.generation.DimensionContext
import terrasect.generation.LocatorResult
import terrasect.generation.Qualifier
import terrasect.sdf.estimateArea
import terrasect.sdf.estimateBounds

object CommandHandler {
  @JvmStatic
  fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
    dispatcher.register(
      requireGamemasters(Commands.literal("ts"))
        .then(
          Commands.literal("locate")
            .then(
              Commands.argument("selector", StringArgumentType.greedyString()).executes {
                locate(it)
              }
            )
        )
        .then(
          Commands.literal("query")
            .executes { query(it, null) }
            .then(
              Commands.argument("selector", StringArgumentType.greedyString()).executes {
                query(it, StringArgumentType.getString(it, "selector"))
              }
            )
        )
        .then(
          Commands.literal("print")
            .executes { print(it, null) }
            .then(
              Commands.argument("selector", StringArgumentType.greedyString()).executes {
                print(it, StringArgumentType.getString(it, "selector"))
              }
            )
        )
    )
  }

  private fun requireGamemasters(
    builder: LiteralArgumentBuilder<CommandSourceStack>
  ): LiteralArgumentBuilder<CommandSourceStack> {
    // spotless:off
    //? if >=1.21.11 {
    return builder.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
    //?} else {
    /*return builder.requires { it.hasPermission(2) }
    *///?}
    // spotless:on
  }

  private fun dimensionContext(source: CommandSourceStack): DimensionContext? {
    val dimensionId = ResourceKeyCompat.getKeyId(source.level.dimension())
    val context = DimensionContext.get(dimensionId)
    if (context == null) {
      source.sendFailure(Component.literal("Terrasect is not active in $dimensionId"))
    }
    return context
  }

  private fun locate(ctx: CommandContext<CommandSourceStack>): Int {
    val source = ctx.source
    val selector = StringArgumentType.getString(ctx, "selector")
    val context = dimensionContext(source) ?: return 0

    val position = source.position
    val result = context.query(selector, position.x.toInt(), position.z.toInt())
    if (result == null) {
      source.sendFailure(Component.literal("No region matched '$selector'"))
      return 0
    }

    val ambiguity = if (result.ambiguous) " (ambiguous)" else ""
    val distance = result.sdf(position.x.toInt(), position.z.toInt())
    source.sendSuccess(
      {
        Component.literal(
          "${chainOf(result)} center=(${result.centerX}, ${result.centerZ}) " +
            "distance=$distance$ambiguity"
        )
      },
      false,
    )
    return 1
  }

  private fun query(ctx: CommandContext<CommandSourceStack>, selector: String?): Int {
    val source = ctx.source
    val context = dimensionContext(source) ?: return 0

    val position = source.position
    val step = context.traverser.traverse(position.x.toInt(), position.z.toInt(), context.cache)
    val contextId = step.id

    val effective = selector ?: ".${step.region.name}"
    val result = context.locator.query(effective, contextId, context.cache)
    if (result == null) {
      source.sendFailure(Component.literal("No region matched '$effective'"))
      return 0
    }

    val ambiguity = if (result.ambiguous) " (ambiguous)" else ""
    val lines = mutableListOf<String>()
    val children = if (selector != null) result.region.strategy?.targets.orEmpty() else emptyList()
    lines += chainOf(result) + ambiguity + if (children.isNotEmpty()) " :" else ""

    val anchor = result.chain.last()
    for (child in children) {
      val resolved =
        context.locator.query(
          "#${anchor.address}.${anchor.name} > .${child.name}",
          contextId,
          context.cache,
        )
      lines +=
        if (resolved != null) "- #${resolved.chain.last().address}.${child.name}"
        else "- .${child.name}"
    }

    source.sendSuccess({ Component.literal(lines.joinToString("\n")) }, false)
    return 1
  }

  private fun print(ctx: CommandContext<CommandSourceStack>, selector: String?): Int {
    val source = ctx.source
    val context = dimensionContext(source) ?: return 0

    val position = source.position
    val step = context.traverser.traverse(position.x.toInt(), position.z.toInt(), context.cache)
    val effective = selector ?: ".${step.region.name}"
    val result = context.locator.query(effective, step.id, context.cache)
    if (result == null) {
      source.sendFailure(Component.literal("No region matched '$effective'"))
      return 0
    }

    val region = result.region
    val bounds = estimateBounds(result.sdf, result.centerX, result.centerZ)
    val areaStep = max(1, max(bounds.width, bounds.height) / 128)
    val area = estimateArea(result.sdf, bounds, areaStep)
    val distance = result.sdf(position.x.toInt(), position.z.toInt())

    val ambiguity = if (result.ambiguous) " (ambiguous)" else ""
    val lines = mutableListOf<String>()
    lines += chainOf(result) + ambiguity
    lines += "center=(${result.centerX}, ${result.centerZ}) distance=$distance"
    lines +=
      "bounds=(${bounds.minX}, ${bounds.minZ})..(${bounds.maxX}, ${bounds.maxZ}) " +
        "size=${bounds.width}x${bounds.height}"
    lines += "area~$area blocks (sampled every $areaStep)"
    region.strategy?.let {
      lines += "strategy=${it.javaClass.simpleName.removeSuffix("Strategy").lowercase()}"
    }
    if (region.children.isNotEmpty()) {
      lines += "children=" + region.children.joinToString(", ") { it.name }
    }

    val registry = PresetRegistry.resolve(context.presetId)
    if (registry != null && region.name in registry.drafts) {
      lines += ""
      lines += TerrasectTomlWriter.write(registry.resolveDraft(region.name)).trimEnd()
    }

    source.sendSuccess({ Component.literal(lines.joinToString("\n")) }, false)
    return 1
  }

  private fun chainOf(result: LocatorResult): String {
    return result.chain
      .ifEmpty { listOf(Qualifier(result.region.name, "")) }
      .joinToString(" > ") { "#${it.address}.${it.name}" }
  }
}
