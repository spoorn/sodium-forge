package me.jellysquid.mods.sodium.mixin.chunk.serialization;

import me.jellysquid.mods.lithium.common.world.chunk.CompactingPackedIntegerArray;
import net.minecraft.util.BitArray;
import net.minecraft.util.palette.IPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Extends {@link BitArray} with a special compaction method defined in {@link CompactingPackedIntegerArray}.
 */
@Mixin(BitArray.class)
public class PackedIntegerArrayMixin implements CompactingPackedIntegerArray {
    @Shadow
    @Final
    private long[] data;

    @Shadow
    @Final
    private int size;

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private long mask;

    @Shadow
    @Final
    private int valuesPerLong;

    @Override
    public <T> void compact(IPalette<T> srcPalette, IPalette<T> dstPalette, short[] out) {
        if (this.size >= Short.MAX_VALUE) {
            throw new IllegalStateException("Array too large");
        }

        if (this.size != out.length) {
            throw new IllegalStateException("Array size mismatch");
        }

        short[] mappings = new short[(int) (this.mask + 1)];

        int idx = 0;

        for (long word : this.data) {
            long bits = word;

            for (int elementIdx = 0; elementIdx < this.valuesPerLong; ++elementIdx) {
                int value = (int) (bits & this.mask);
                int remappedId = mappings[value];

                if (remappedId == 0) {
                    remappedId = dstPalette.idFor(srcPalette.valueFor(value)) + 1;
                    mappings[value] = (short) remappedId;
                }

                out[idx] = (short) (remappedId - 1);
                bits >>= this.bits;

                ++idx;

                if (idx >= this.size) {
                    return;
                }
            }
        }
    }
}
