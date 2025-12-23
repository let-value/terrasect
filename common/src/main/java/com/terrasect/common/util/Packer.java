package com.terrasect.common.util;

/**
 * Utility class for packing and unpacking pairs of values into a single long.
 * 
 * <p>Values are stored with first in the lower 32 bits, second in the upper 32 bits.
 * This avoids allocations when returning multiple values from hot paths.
 */
public final class Packer {
    
    private Packer() {}
    
    // ==================== Float + Float ====================
    
    /**
     * Pack two float values into a single long.
     * 
     * @param first first value (stored in lower 32 bits)
     * @param second second value (stored in upper 32 bits)
     * @return packed values
     */
    public static long packPair(float first, float second) {
        return ((long) Float.floatToRawIntBits(second) << 32) | (Float.floatToRawIntBits(first) & 0xFFFFFFFFL);
    }
    
    /**
     * Unpack first float value from packed pair.
     * 
     * @param packed the packed value
     * @return first value (from lower 32 bits)
     */
    public static float unpackPairFirst(long packed) {
        return Float.intBitsToFloat((int) packed);
    }
    
    /**
     * Unpack second float value from packed pair.
     * 
     * @param packed the packed value
     * @return second value (from upper 32 bits)
     */
    public static float unpackPairSecond(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }
    
    // ==================== Int + Float ====================
    
    /**
     * Pack an int and a float into a single long.
     * 
     * @param intVal int value (stored in upper 32 bits)
     * @param floatVal float value (stored in lower 32 bits)
     * @return packed values
     */
    public static long packIntFloat(int intVal, float floatVal) {
        return ((long) intVal << 32) | (Float.floatToRawIntBits(floatVal) & 0xFFFFFFFFL);
    }
    
    /**
     * Unpack int value from packed int+float.
     * 
     * @param packed the packed value
     * @return int value (from upper 32 bits)
     */
    public static int unpackInt(long packed) {
        return (int) (packed >>> 32);
    }
    
    /**
     * Unpack float value from packed int+float.
     * 
     * @param packed the packed value
     * @return float value (from lower 32 bits)
     */
    public static float unpackFloat(long packed) {
        return Float.intBitsToFloat((int) packed);
    }
}
