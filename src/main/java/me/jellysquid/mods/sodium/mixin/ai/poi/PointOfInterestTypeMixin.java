package me.jellysquid.mods.sodium.mixin.ai.poi;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.jellysquid.mods.lithium.common.world.interests.types.PointOfInterestTypeHelper;
import net.minecraft.block.BlockState;
import net.minecraft.village.PointOfInterestType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * Replaces the backing map type with a faster collection type which uses reference equality.
 */
@Mixin(PointOfInterestType.class)
public class PointOfInterestTypeMixin {
    @Mutable
    @Shadow
    @Final
    private static Map<BlockState, PointOfInterestType> TYPE_BY_STATE;

    static {
        TYPE_BY_STATE = new Reference2ReferenceOpenHashMap<>(TYPE_BY_STATE);

        PointOfInterestTypeHelper.init(new ObjectArraySet<>(TYPE_BY_STATE.keySet()));
    }
}
