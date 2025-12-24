package com.terrasect.common.util;

import net.minecraft.world.level.levelgen.DensityFunction;

public final class MutablePointContext implements DensityFunction.FunctionContext {
    private int x;
    private int y;
    private int z;

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int blockX() {
        return x;
    }

    @Override
    public int blockY() {
        return y;
    }

    @Override
    public int blockZ() {
        return z;
    }
}
