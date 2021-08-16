package me.jellysquid.mods.sodium.mixin.entity.inactive_navigations;

import me.jellysquid.mods.lithium.common.entity.EntityNavigationExtended;
import me.jellysquid.mods.lithium.common.world.ServerWorldExtended;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigator.class)
public abstract class EntityNavigationMixin implements EntityNavigationExtended {

    @Shadow
    @Final
    protected World level;

    @Shadow
    protected Path path;

    private boolean canListenForBlocks = false;

    @Shadow
    public abstract Path createPath(BlockPos target, int distance);

    @Redirect(method = "recomputePath()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/pathfinding/PathNavigator;createPath(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/pathfinding/Path;")
    )
    private Path updateListeningState(PathNavigator entityNavigation, BlockPos target, int distance) {
        Path pathTo = this.createPath(target, distance);
        if (this.canListenForBlocks && ((pathTo == null) != (this.path == null))) {
            if (pathTo == null) {
                ((ServerWorldExtended) this.level).setNavigationInactive(this);
            } else {
                ((ServerWorldExtended) this.level).setNavigationActive(this);
            }
        }
        return pathTo;
    }

    @Inject(method = "moveTo(Lnet/minecraft/pathfinding/Path;D)Z", at = @At(value = "RETURN"))
    private void updateListeningState2(Path path, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (this.canListenForBlocks) {
            if (this.path == null) {
                ((ServerWorldExtended) this.level).setNavigationInactive(this);
            } else {
                ((ServerWorldExtended) this.level).setNavigationActive(this);
            }
        }
    }

    @Inject(method = "stop", at = @At(value = "RETURN"))
    private void stopListening(CallbackInfo ci) {
        if (this.canListenForBlocks) {
            ((ServerWorldExtended) this.level).setNavigationInactive(this);
        }
    }

    @Override
    public void setRegisteredToWorld(boolean isRegistered) {
        //Drowneds are problematic. Their EntityNavigations do not register properly.
        //We make sure to not register them, when vanilla doesn't register them.
        this.canListenForBlocks = isRegistered;
    }
}
