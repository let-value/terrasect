package com.terrasect.fabric.narrgen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.terrasect.common.runtime.Config;
import com.terrasect.common.api.Context;
import com.terrasect.common.util.Packer;
import com.terrasect.common.util.MathUtils;
import com.terrasect.common.runtime.World;
import com.terrasect.common.runtime.TraversalResult;
import com.terrasect.common.api.Region;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * VERSION OVERRIDE for 1.21.1: Uses hasPermission(int) instead of permissions().hasPermission()
 */
public class NarrGenCommand {

    private static final int OP_LEVEL_GAMEMASTER = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("narrgen")
            .requires(source -> source.hasPermission(OP_LEVEL_GAMEMASTER))
            .then(Commands.literal("dump")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                            .then(Commands.argument("step", IntegerArgumentType.integer(1))
                                .then(Commands.argument("layer", StringArgumentType.word())
                                    .executes(NarrGenCommand::dump)
                                )
                            )
                        )
                    )
                )
            )
        );
    }

    private static int dump(CommandContext<CommandSourceStack> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        int size = IntegerArgumentType.getInteger(context, "size");
        int step = IntegerArgumentType.getInteger(context, "step");
        String layer = StringArgumentType.getString(context, "layer");
        
        CommandSourceStack source = context.getSource();
        long seed = source.getLevel().getSeed();

        source.sendSystemMessage(Component.literal("Generating " + layer + " snapshot..."));

        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Access real climate sampler
                Context genContext = new Context() {
                    @Override public long getSeed() { return seed; }
                    @Override public long getInfluence(int x, int z) { return 0L; }
                };

                BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
                
                for (int iy = 0; iy < size; iy++) {
                    for (int ix = 0; ix < size; ix++) {
                        int wx = x + ix * step;
                        int wz = z + iy * step;
                        
                        int color = 0;
                        
                        if (layer.equals("region")) {
                            TraversalResult result = World.traverse(genContext, wx, wz);
                            if (result != null && result.region != null) {
                                int regionId = result.region.name().hashCode();
                                int r = (int) (MathUtils.hash64(regionId, 1, 0, 0) & 0xFF);
                                int g = (int) (MathUtils.hash64(regionId, 2, 0, 0) & 0xFF);
                                int b = (int) (MathUtils.hash64(regionId, 3, 0, 0) & 0xFF);
                                color = (r << 16) | (g << 8) | b;
                            }
                        } else if (layer.equals("edge")) {
                            TraversalResult result = World.traverse(genContext, wx, wz);
                            if (result != null) {
                                // edgeDistance is 0 at boundary, 1 at center - invert for visualization
                                int val = (int) ((1.0f - result.edgeDistance) * 255);
                                color = (val << 16) | (val << 8) | val;
                            }
                        } else if (layer.equals("river")) {
                            long influence = genContext.getInfluence(wx, wz);
                            float val = Packer.unpackPairFirst(influence);
                            int v = (int) (val * 255);
                            color = (v << 16) | (v << 8) | v;
                        } else if (layer.equals("ridge")) {
                            long influence = genContext.getInfluence(wx, wz);
                            float val = Packer.unpackPairSecond(influence);
                            int v = (int) (val * 255);
                            color = (v << 16) | (v << 8) | v;
                        } else if (layer.equals("archetype")) {
                            Region region = World.getRegion(genContext, wx, wz);
                            color = getRegionColor(region);
                        }
                        
                        img.setRGB(ix, iy, color);
                    }
                }
                
                File runDir = new File("config/narrgen");
                runDir.mkdirs();
                File outFile = new File(runDir, layer + "_" + x + "_" + z + ".png");
                ImageIO.write(img, "png", outFile);
                
                source.sendSystemMessage(Component.literal("Saved to " + outFile.getAbsolutePath()));
                
            } catch (IOException e) {
                source.sendSystemMessage(Component.literal("Error: " + e.getMessage()));
                e.printStackTrace();
            }
        });

        return 1;
    }

    private static int getRegionColor(Region region) {
        // Generate a deterministic color from the region name hash
        int hash = region.name().hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);
        return (r << 16) | (g << 8) | b;
    }
}
