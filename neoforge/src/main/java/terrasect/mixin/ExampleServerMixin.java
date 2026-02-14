package terrasect.mixin;

import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInfo;
import net.minecraft.server.TickTask;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import terrasect.Constants;

/** Example server mixin for NeoForge. Mixins should be written in Java, not Kotlin. */
@Mixin(MinecraftServer.class)
public abstract class ExampleServerMixin extends ReentrantBlockableEventLoop<TickTask>
    implements ServerInfo, ChunkIOErrorReporter, CommandSource, AutoCloseable {

  public ExampleServerMixin(String name) {
    super(name);
  }

  @Inject(method = "loadLevel", at = @At("TAIL"))
  private void terrasect$onLoadLevel(CallbackInfo ci) {
    System.out.println(
        "[" + Constants.MOD_NAME + "] Server level loaded! (modid: " + Constants.MOD_ID + ")");
  }
}
