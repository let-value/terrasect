package terrasect.mixin.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.handler.CommandHandler;

// Registers directly into the vanilla dispatcher instead of the loader command events; the
// Commands(CommandSelection, CommandBuildContext) constructor is identical across the whole
// version matrix, so this single ungated injector covers every loader and version.
@Mixin(Commands.class)
public class CommandsMixin {
  @Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;

  @Inject(method = "<init>", at = @At("TAIL"))
  private void terrasect$registerCommands(
      Commands.CommandSelection commandSelection,
      CommandBuildContext commandBuildContext,
      CallbackInfo ci) {
    CommandHandler.register(this.dispatcher);
  }
}
