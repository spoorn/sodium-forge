package me.jellysquid.mods.sodium.common.util;

/**
 * Global class used to restrict number of translucent rebuilds at a time to help minimize lag.
 */
public class TranslucentPoolUtil {

    private static int translucentRebuilds = 0;

    public static int getTranslucentRebuilds() {
        return translucentRebuilds;
    }

    public static void incrementTranslucentRebuilds() {
        translucentRebuilds++;
    }

    public static void decrementTranslucentRebuilds() {
        if (translucentRebuilds > 0)
            translucentRebuilds--;
    }

    public static void resetTranslucentRebuilds() {
        translucentRebuilds = 0;
    }
}
