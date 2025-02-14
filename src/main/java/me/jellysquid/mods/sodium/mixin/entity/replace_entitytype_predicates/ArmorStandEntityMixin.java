package me.jellysquid.mods.sodium.mixin.entity.replace_entitytype_predicates;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ArmorStandEntity.class)
public class ArmorStandEntityMixin {
    @Shadow
    @Final
    private static Predicate<Entity> RIDABLE_MINECARTS;

    @Redirect(method = "pushEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> getMinecartsDirectly(World world, Entity excluded, AxisAlignedBB box, Predicate<? super Entity> predicate) {
        if (predicate == RIDABLE_MINECARTS) {
            //not using MinecartEntity.class and no predicate, because mods may add another minecart that is type ridable without being MinecartEntity
            return world.getEntitiesOfClass(AbstractMinecartEntity.class, box, (Entity e) -> e != excluded && ((AbstractMinecartEntity) e).getMinecartType() == AbstractMinecartEntity.Type.RIDEABLE);
        }
        return world.getEntities(excluded, box, predicate);
    }
}
