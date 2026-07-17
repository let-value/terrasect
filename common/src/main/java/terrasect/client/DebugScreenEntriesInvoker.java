package terrasect.client;

// spotless:off
//? if >=1.21.11 {
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.gen.Invoker;
//?}
import org.spongepowered.asm.mixin.Mixin;
// spotless:on

// DebugScreenEntries.register is private; Fabric API exposes it through a transitive access
// widener but nothing does on NeoForge, so this invoker is the loader-neutral door into the
// vanilla debug entry registry. The string target keeps it a soft skip on versions where the
// registry does not exist.
@Mixin(targets = "net.minecraft.client.gui.components.debug.DebugScreenEntries")
public interface DebugScreenEntriesInvoker {
  // spotless:off
  //? if >=1.21.11 {
  @Invoker("register")
  static Identifier terrasect$register(Identifier id, DebugScreenEntry entry) {
    throw new AssertionError();
  }
  //?}
  // spotless:on
}
