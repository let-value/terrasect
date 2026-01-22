package terrasect;

import net.minecraft.world.level.biome.Climate;

public interface VanillaSamplerAccessor {
    Climate.TargetPoint terrasect$sampleVanilla(int x, int y, int z);
}
