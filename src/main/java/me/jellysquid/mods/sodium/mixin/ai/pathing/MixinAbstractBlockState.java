package me.jellysquid.mods.sodium.mixin.ai.pathing;

import me.jellysquid.mods.lithium.common.ai.pathing.BlockStatePathingCache;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.pathfinding.PathNodeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MixinAbstractBlockState implements BlockStatePathingCache {
    private PathNodeType pathNodeType = PathNodeType.OPEN;
    private PathNodeType pathNodeTypeNeighbor = PathNodeType.OPEN;

    @Inject(method = "initCache", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        // disable patch because it conflicts with forge baking
        //BlockState state = this.getSelf();
        //BlockPathingBehavior behavior = (BlockPathingBehavior) this.getBlock();

        //this.pathNodeType = Validate.notNull(behavior.getPathNodeType(state));
        //this.pathNodeTypeNeighbor = Validate.notNull(behavior.getNeighborPathNodeType(state));
    }

    @Override
    public PathNodeType getPathNodeType() {
        return this.pathNodeType;
    }

    @Override
    public PathNodeType getNeighborPathNodeType() {
        return this.pathNodeTypeNeighbor;
    }

    @Shadow
    protected abstract BlockState asState();

    @Shadow
    public abstract Block getBlock();
}
