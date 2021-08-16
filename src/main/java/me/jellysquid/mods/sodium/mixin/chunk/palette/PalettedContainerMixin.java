package me.jellysquid.mods.sodium.mixin.chunk.palette;

import me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.BitArray;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.palette.ArrayPalette;
import net.minecraft.util.palette.IPalette;
import net.minecraft.util.palette.IResizeCallback;
import net.minecraft.util.palette.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Function;

/**
 * Patches {@link PalettedContainer} to make use of {@link LithiumHashPalette}.
 */
@Mixin(value = PalettedContainer.class, priority = 999)
public abstract class PalettedContainerMixin<T> {
    @Shadow
    private IPalette<T> palette;

    @Shadow
    protected BitArray storage;

    @Shadow
    protected abstract void set(int int_1, T object_1);

    @Shadow
    private int bits;

    @Shadow
    @Final
    private Function<CompoundNBT, T> reader;

    @Shadow
    @Final
    private Function<T, CompoundNBT> writer;

    @Shadow
    @Final
    private ObjectIntIdentityMap<T> registry;

    @Shadow
    @Final
    private IPalette<T> globalPalette;

    @Shadow
    @Final
    private T defaultValue;

    @Shadow
    protected abstract T get(int int_1);

    /**
     * TODO: Replace this with something that doesn't overwrite.
     *
     * @reason Replace the hash palette from vanilla with our own and change the threshold for usage to only 3 bits,
     * as our implementation performs better at smaller key ranges.
     * @author JellySquid
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    @Overwrite
    private void setBits(int size) {
        if (size != this.bits) {
            this.bits = size;

            if (this.bits <= 2) {
                this.bits = 2;
                this.palette = new ArrayPalette<>(this.registry, this.bits, (PalettedContainer<T>) (Object) this, this.reader);
            } else if (this.bits <= 8) {
                this.palette = new LithiumHashPalette<>(this.registry, this.bits, (IResizeCallback<T>) this, this.reader, this.writer);
            } else {
                this.bits = MathHelper.ceillog2(this.registry.size());
                this.palette = this.globalPalette;
            }

            this.palette.idFor(this.defaultValue);
            this.storage = new BitArray(this.bits, 4096);
        }
    }

}
