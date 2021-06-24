package me.jellysquid.mods.sodium.common.util;

import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;

/**
 * Vanilla {@link net.minecraft.client.renderer.RenderTypeLookup} has a synchronized block around predicate function.
 * No need for that, we can instead cache the values and avoid computation every time.
 */
public class RenderTypeLookupUtil {

    private static final Object2BooleanMap<RenderTypeLookupKey> renderTypeLookupCache = new Object2BooleanArrayMap<>();

    public static boolean canRenderInLayer(BlockState state, RenderType layer) {
        RenderTypeLookupKey key = new RenderTypeLookupKey(state, layer);
        if (renderTypeLookupCache.containsKey(key) && !(state.getBlock() instanceof LeavesBlock)) {
            return renderTypeLookupCache.getBoolean(key);
        } else {
            boolean canRender = RenderTypeLookup.canRenderInLayer(state, layer);
            renderTypeLookupCache.put(key, canRender);
            return canRender;
        }
    }

    private static class RenderTypeLookupKey {
        private final BlockState state;
        private final RenderType layer;

        public RenderTypeLookupKey(BlockState state, RenderType layer) {
            this.state = state;
            this.layer = layer;
        }

        // This returns true if the Block of the BlockState is equal
        @Override
        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof RenderTypeLookupKey))
                return false;
            final RenderTypeLookupKey other = (RenderTypeLookupKey) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$layer = this.layer;
            final Object other$layer = other.layer;
            if (this$layer == null ? other$layer != null : !this$layer.equals(other$layer)) return false;
            final BlockState this$state = this.state;
            final BlockState other$state = other.state;
            if (this$state == null && other$state != null || this$state != null && other$state == null) return false;
            if (this$state == other$state) return true;
            return this$state.getBlock().equals(other$state.getBlock());
        }

        protected boolean canEqual(final Object other) {
            return other instanceof RenderTypeLookupKey;
        }

        @Override
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $state = this.state;
            result = result * PRIME + ($state == null ? 43 : $state.hashCode());
            final Object $layer = this.layer;
            result = result * PRIME + ($layer == null ? 43 : $layer.hashCode());
            return result;
        }
    }
}
