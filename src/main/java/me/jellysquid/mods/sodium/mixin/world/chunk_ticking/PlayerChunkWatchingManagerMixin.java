package me.jellysquid.mods.sodium.mixin.world.chunk_ticking;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import me.jellysquid.mods.lithium.common.world.PlayerChunkWatchingManagerIterable;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.chunk.PlayerGenerationTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerGenerationTracker.class)
public class PlayerChunkWatchingManagerMixin implements PlayerChunkWatchingManagerIterable {
    @Shadow
    @Final
    private Object2BooleanMap<ServerPlayerEntity> players;

    @Override
    public Iterable<ServerPlayerEntity> getPlayers() {
        return this.players.keySet();
    }
}
