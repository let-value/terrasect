package com.terrasect.common.util;

public final class Packer {

    private Packer() {}

    public static long packPair(float first, float second) {
        return ((long) Float.floatToRawIntBits(second) << 32) | (Float.floatToRawIntBits(first) & 0xFFFFFFFFL);
    }

    public static float unpackPairFirst(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    public static float unpackPairSecond(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    public static long packIntFloat(int intVal, float floatVal) {
        return ((long) intVal << 32) | (Float.floatToRawIntBits(floatVal) & 0xFFFFFFFFL);
    }

    public static int unpackInt(long packed) {
        return (int) (packed >>> 32);
    }

    public static float unpackFloat(long packed) {
        return Float.intBitsToFloat((int) packed);
    }
}
