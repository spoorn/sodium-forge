package me.jellysquid.mods.sodium.mixin.features.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.color.ColorARGB;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import me.jellysquid.mods.sodium.client.world.biome.ItemColorsExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    private final XoRoShiRoRandom random = new XoRoShiRoRandom();

    @Shadow
    @Final
    private ItemColors itemColors;

    /**
     * @reason Avoid allocations
     * @author JellySquid
     */
    @Overwrite(remap = false)
    public void renderModelLists(BakedModel model, ItemStack stack, int light, int overlay, PoseStack matrices, VertexConsumer vertices) {
        XoRoShiRoRandom random = this.random;

        for (Direction direction : DirectionUtil.ALL_DIRECTIONS) {
            List<BakedQuad> quads = model.getQuads(null, direction, random.setSeedAndReturn(42L));

            if (!quads.isEmpty()) {
                this.renderQuadList(matrices, vertices, quads, stack, light, overlay);
            }
        }

        List<BakedQuad> quads = model.getQuads(null, null, random.setSeedAndReturn(42L));

        if (!quads.isEmpty()) {
            this.renderQuadList(matrices, vertices, quads, stack, light, overlay);
        }
    }

    /**
     * @reason Use vertex building intrinsics
     * @author JellySquid
     */
    @Overwrite(remap = false)
    public void renderQuadList(PoseStack matrixStackIn, VertexConsumer bufferIn, List<BakedQuad> quadsIn, ItemStack itemStackIn, int combinedLightIn, int combinedOverlayIn) {
        PoseStack.Pose entry = matrixStackIn.last();

        ItemColor colorProvider = null;

        QuadVertexSink drain = VertexDrain.of(bufferIn)
                .createSink(VanillaVertexTypes.QUADS);
        drain.ensureCapacity(quadsIn.size() * 4);

        for (BakedQuad bakedQuad : quadsIn) {
            int color = 0xFFFFFFFF;

            if (!itemStackIn.isEmpty() && bakedQuad.isTinted()) {
                if (colorProvider == null) {
                    colorProvider = ((ItemColorsExtended) this.itemColors).getColorProvider(itemStackIn);
                }

                // Some mods mess with ItemRenderer and ItemColors.  Handle NPE here
                // See https://github.com/spoorn/sodium-forge/issues/100
                if (colorProvider == null) {
                    color = ColorARGB.toABGR(this.itemColors.getColor(itemStackIn, bakedQuad.getTintIndex()), 255);
                } else {
                    color = ColorARGB.toABGR((colorProvider.getColor(itemStackIn, bakedQuad.getTintIndex())), 255);
                }
            }

            ModelQuadView quad = ((ModelQuadView) bakedQuad);

            for (int i = 0; i < 4; i++) {
                // Combine the tint color with Item registered color to support Dynamic Overriden BakedModels
                // Fixes https://github.com/spoorn/sodium-forge/issues/103, https://github.com/spoorn/sodium-forge/issues/104
                int finalColor = color;
                try {
                    if (bakedQuad.isTinted()) {
                        finalColor = multABGRInts(quad.getColor(quad.getColorIndex()), color);
                    }
                } catch (Exception ex) {
                    // Sometimes fetching the Color runs into an IndexOutOfBoundsException, seems to conflict with JEI logic
                    // See https://github.com/spoorn/sodium-forge/issues/106
                    // TODO: This is not a true solution.  We should deep dive to see why color index is out of bounds
                }
                drain.writeQuad(entry, quad.getX(i), quad.getY(i), quad.getZ(i), finalColor, quad.getTexU(i), quad.getTexV(i),
                        combinedLightIn, combinedOverlayIn, ModelQuadUtil.getFacingNormal(bakedQuad.getDirection()));
            }

            SpriteUtil.markSpriteActive(quad.getSprite());
        }

        drain.flush();
    }

    private int multABGRInts(int colorA, int colorB) {
        int a = (int)((ColorABGR.unpackAlpha(colorA)/255.0f) * (ColorABGR.unpackAlpha(colorB)/255.0f) * 255.0f);
        int b = (int)((ColorABGR.unpackBlue(colorA)/255.0f) * (ColorABGR.unpackBlue(colorB)/255.0f) * 255.0f);
        int g = (int)((ColorABGR.unpackGreen(colorA)/255.0f) * (ColorABGR.unpackGreen(colorB)/255.0f) * 255.0f);
        int r = (int)((ColorABGR.unpackRed(colorA)/255.0f) * (ColorABGR.unpackRed(colorB)/255.0f) * 255.0f);
        return ColorABGR.pack(r, g, b, a);
    }
}
