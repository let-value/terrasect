package com.terrasect.common.api;

/**
 * Utility class for packing and unpacking influence values.
 * 
 * <p>River influence is stored in the lower 32 bits as a float,
 * ridge influence is stored in the upper 32 bits as a float.
 * This avoids allocations when returning multiple values from hot paths.
 */
public final class Influence {
    
    private Influence() {}
    
    /**
     * Pack river and ridge influence into a single long.
     * 
     * @param river river influence (0.0 = no river, 1.0 = river)
     * @param ridge ridge/weirdness influence (0.0 to 1.0)
     * @return packed influence values
     */
    public static long pack(float river, float ridge) {
        return ((long) Float.floatToRawIntBits(ridge) << 32) | (Float.floatToRawIntBits(river) & 0xFFFFFFFFL);
    }
    
    /**
     * Unpack river influence from packed value.
     * 
     * @param packed the packed influence value
     * @return river influence (0.0 = no river, 1.0 = river)
     */
    public static float unpackRiver(long packed) {
        return Float.intBitsToFloat((int) packed);
    }
    
    /**
     * Unpack ridge/weirdness influence from packed value.
     * 
     * @param packed the packed influence value
     * @return ridge influence (0.0 to 1.0)
     */
    public static float unpackRidge(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }
}
