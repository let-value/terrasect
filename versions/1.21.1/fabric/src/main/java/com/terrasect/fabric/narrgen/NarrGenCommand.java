package com.terrasect.fabric.narrgen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.terrasect.common.runtime.Config;
import com.terrasect.common.api.Context;
import com.terrasect.common.util.MathUtils;
import com.terrasect.common.runtime.World;
import com.terrasect.common.api.Region;
import com.terrasect.common.runtime.RegionField;
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
                    @Override public float getRiverInfluence(int x, int z) { return 0.0f; }
                    @Override public float getRidgeInfluence(int x, int z) { return 0.0f; }
                };

                BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
                
                for (int iy = 0; iy < size; iy++) {
                    for (int ix = 0; ix < size; ix++) {
                        int wx = x + ix * step;
                        int wz = z + iy * step;
                        
                        int color = 0;
                        
                        if (layer.equals("region")) {
                            long regionData = RegionField.getRegionData(wx, wz, seed, 512, 200.0f, 2048);
                            int regionId = RegionField.unpackRegionId(regionData);
                            int r = (int) (MathUtils.hash64(regionId, 1, 0, 0) & 0xFF);
                            int g = (int) (MathUtils.hash64(regionId, 2, 0, 0) & 0xFF);
                            int b = (int) (MathUtils.hash64(regionId, 3, 0, 0) & 0xFF);
                            color = (r << 16) | (g << 8) | b;
                        } else if (layer.equals("edge")) {
                            long regionData = RegionField.getRegionData(wx, wz, seed, 512, 200.0f, 2048);
                            float edge = RegionField.unpackEdge(regionData);
                            int val = (int) (MathUtils.clamp01(edge / Config.EDGE_SCALE) * 255);
                            color = (val << 16) | (val << 8) | val;
                        } else if (layer.equals("river")) {
                            float val = genContext.getRiverInfluence(wx, wz);
                            int v = (int) (val * 255);
                            color = (v << 16) | (v << 8) | v;
                        } else if (layer.equals("ridge")) {
                            float val = genContext.getRidgeInfluence(wx, wz);
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
