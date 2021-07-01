package me.jellysquid.mods.sodium.client.render.chunk.cull;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.IdTable;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.chunk.SetVisibility;

public interface ChunkCuller {
    IntArrayList computeVisible(ActiveRenderInfo camera, FrustumExtended frustum, int frame, boolean spectator);

    void onSectionStateChanged(int x, int y, int z, SetVisibility occlusionData);
    void onSectionLoaded(int x, int y, int z, int id);
    void onSectionUnloaded(int x, int y, int z);

    boolean isSectionVisible(int x, int y, int z);

    <T extends ChunkGraphicsState> boolean isInDirectView(IdTable<ChunkRenderContainer<T>> renders, ChunkRenderContainer<T> render, float camX, float camY, float camZ);
}
