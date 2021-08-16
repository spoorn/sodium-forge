package me.jellysquid.mods.sodium.mixin.entity.skip_fire_check;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private int remainingFireTicks;

    @Shadow
    protected abstract int getFireImmuneTicks();

    @Shadow
    public World level;

    //@Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;getAllInBox(Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/stream/Stream;"))
    //private Stream<BlockPos> skipFireTestIfResultDoesNotMatter(AxisAlignedBB box) {
    //    // Skip scanning the blocks around the entity touches by returning an empty stream when the result does not matter
    //    if (this.fire > 0 || this.fire == -this.getFireImmuneTicks()) {
    //        return Stream.empty();
    //    }
    //
    //    return world.getStatesInArea(box);
    //}
}
