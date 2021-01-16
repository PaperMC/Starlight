package ca.spottedleaf.starlight.common.util;

import net.minecraft.world.HeightLimitView;

public final class WorldUtil {

    // min, max are inclusive
    // please note that the mappings for HeightLimitView are actual trash, so if you use them here you must
    // actually verify with a debugger that they are returning what they say they are

    public static int getMaxSection(final HeightLimitView world) {
        return world.getTopSectionLimit() - 1; // getTopSectionLimit() is exclusive
    }

    public static int getMinSection(final HeightLimitView world) {
        return world.method_32891();
    }

    public static int getMaxLightSection(final HeightLimitView world) {
        return getMaxSection(world) + 1;
    }

    public static int getMinLightSection(final HeightLimitView world) {
        return getMinSection(world) - 1;
    }



    public static int getTotalSections(final HeightLimitView world) {
        return getMaxSection(world) - getMinSection(world) + 1;
    }

    public static int getTotalLightSections(final HeightLimitView world) {
        return getMaxLightSection(world) - getMinLightSection(world) + 1;
    }

    public static int getMinBlockY(final HeightLimitView world) {
        return getMinSection(world) << 4;
    }

    public static int getMaxBlockY(final HeightLimitView world) {
        return (getMaxSection(world) << 4) | 15;
    }

    private WorldUtil() {
        throw new RuntimeException();
    }

}
