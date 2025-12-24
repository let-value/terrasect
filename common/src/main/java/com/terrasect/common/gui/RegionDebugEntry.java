package com.terrasect.common.gui;

import org.jspecify.annotations.Nullable;

import com.terrasect.common.definition.Region;
import com.terrasect.common.generation.MinecraftContext;
import com.terrasect.common.generation.TraversalResult;
import com.terrasect.common.generation.World;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Debug screen entry that displays current region hierarchy and edge information.
 * 
 * <p>Shows on the F3 debug screen:
 * <ul>
 *   <li>[Terrasect] Regions: Overworld (e:50%,i:25%) > SPAWN (e:80%,i:10%) > Forest (e:95%,i:2%)</li>
 * </ul>
 */
public class RegionDebugEntry implements DebugScreenEntry {
    
    @Override
    public void display(DebugScreenDisplayer lines, @Nullable Level world, @Nullable LevelChunk clientChunk, @Nullable LevelChunk chunk) {
        Minecraft mc = Minecraft.getInstance();
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) return;
        
        MinecraftContext context = MinecraftContext.getAny();
        if (context == null) return;
        
        int blockX = cameraEntity.getBlockX();
        int blockZ = cameraEntity.getBlockZ();
        
        // Build region hierarchy with per-region edge/influence info
        StringBuilder sb = new StringBuilder("Regions: ");
        
        int depth = 1;
        String prevName = null;
        while (depth <= 5) {
            TraversalResult traversal = World.traverse(context, blockX, blockZ, depth);
            if (traversal == null) break;
            
            Region region = traversal.region;
            if (region == null) break;
            
            // Stop if we've reached a leaf (same region as previous depth)
            if (prevName != null && prevName.equals(region.name())) {
                break;
            }
            
            if (depth > 1) {
                sb.append(">");
            }
            sb.append(region.name());
            sb.append(String.format("(e:%.0f%%,i:%.0f%%)", 
                traversal.edgeDistance * 100, 
                traversal.edgeInfluence * 100));
            
            prevName = region.name();
            depth++;
        }
        
        if (depth > 1) {
            lines.addLine(sb.toString());
        }
    }
    
    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }
    
    @Override
    public DebugEntryCategory category() {
        return DebugEntryCategory.SCREEN_TEXT;
    }
}
