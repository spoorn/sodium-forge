package me.jellysquid.mods.lithium.api.pathing;

import net.minecraft.block.BlockState;
import net.minecraft.pathfinding.PathNodeType;

/**
 * Provides the ability for mods to specify what {@link PathNodeType} their block uses for path-finding. This exists
 * because Lithium replaces a large amount of entity path-finding logic, which can cause other mods which mixin to
 * this code to fail or explode into other issues.
 */
public interface BlockPathingBehavior {
    /**
     * Controls how the given block state is seen in path-finding.
     * <p>
     * If you were mixing into the method {@link WalkNodeProcessor#getBlockPathTypeRaw(IBlockReader, BlockPos)},
     * you will want to implement this method with your logic instead.
     * <p>
     * The result of this method is cached in the block state and will only be called on block initialization.
     *
     * @param state The block state being examined
     * @return The path node type for the given block state
     */
    PathNodeType getPathNodeType(BlockState state);

    /**
     * Controls the behavior of the "neighbor" check for path finding. This is used when scanning the blocks next
     * to another path node for nearby obstacles (i.e. dangerous blocks the entity could possibly collide with, such as
     * fire or cactus.)
     * <p>
     * If you were mixing into the method {@link WalkNodeProcessor#getNodeTypeFromNeighbors}, you will want to implement
     * this method with your logic instead.
     * <p>
     * The result of this method is cached in the block state and will only be called on block initialization.
     *
     * @param state The block state being examined
     * @return The path node type for the given block state when this block is being searched as a
     * neighbor of another path node
     */
    PathNodeType getNeighborPathNodeType(BlockState state);
}
