package me.jellysquid.mods.sodium.client.util;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;

public interface MixinWorldRendererSodiumAccessor {
    SodiumWorldRenderer getRenderer();
}
