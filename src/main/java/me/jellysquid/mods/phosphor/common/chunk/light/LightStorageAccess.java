package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.world.chunk.NibbleArray;

public interface LightStorageAccess {
    NibbleArray callGetLightSection(long sectionPos, boolean cached);

    /**
     * Returns the light value for a position that does not have an associated lightmap.
     * This is analogous to {@link net.minecraft.world.lighting.SectionLightStorage#getLight(long)}, but uses the cached light data.
     */
    int getLightWithoutLightmap(long blockPos);

    /**
     * Enables or disables light updates for the provided <code>chunkPos</code>.
     * Disabling light updates additionally disables source light and removes all data associated to the chunk.
     */
    void setLightUpdatesEnabled(long chunkPos, boolean enabled);

    void invokeSetColumnEnabled(long chunkPos, boolean enabled);
}
