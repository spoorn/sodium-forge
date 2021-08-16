package me.jellysquid.mods.sodium.mixin.ai.poi.fast_portals;

import me.jellysquid.mods.lithium.common.world.interests.PointOfInterestDataExtended;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.TeleportationRepositioner;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.village.PointOfInterestType;
import net.minecraft.world.Teleporter;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(Teleporter.class)
public class PortalForcerMixin {
    @Shadow
    @Final
    protected ServerWorld level;

    /**
     * @author JellySquid
     * @reason Use optimized search for nearby points, avoid slow filtering, check for valid locations first
     */
    @Overwrite
    public Optional<TeleportationRepositioner.Result> findPortalAround(BlockPos centerPos, boolean shrink) {
        int searchRadius = shrink ? 16 : 128;

        PointOfInterestManager poiStorage = this.level.getPoiManager();
        poiStorage.ensureLoadedAndValid(this.level, centerPos, searchRadius);
        Optional<BlockPos> ret = ((PointOfInterestDataExtended) poiStorage).findNearestInSquare(centerPos, searchRadius,
                PointOfInterestType.NETHER_PORTAL, PointOfInterestManager.Status.ANY,
                (poi) -> this.level.getBlockState(poi.getPos()).hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
        );

        return ret.flatMap((pos) -> {
            BlockState state = this.level.getBlockState(pos);

            this.level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);

            return Optional.of(TeleportationRepositioner.getLargestRectangleAround(pos, state.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Direction.Axis.Y, 21, (searchPos) ->
                    this.level.getBlockState(searchPos) == state));
        });
    }
}
