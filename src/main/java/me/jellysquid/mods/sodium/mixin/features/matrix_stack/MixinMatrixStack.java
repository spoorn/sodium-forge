package me.jellysquid.mods.sodium.mixin.features.matrix_stack;

import com.mojang.blaze3d.matrix.MatrixStack;
import me.jellysquid.mods.sodium.client.util.math.Matrix3fExtended;
import me.jellysquid.mods.sodium.client.util.math.Matrix4fExtended;
import me.jellysquid.mods.sodium.client.util.math.MatrixUtil;
import net.minecraft.util.math.vector.Quaternion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Deque;

@Mixin(MatrixStack.class)
public class MixinMatrixStack {
    @Shadow
    @Final
    private Deque<MatrixStack.Entry> poseStack;

    /**
     * @reason Use our faster specialized function
     * @author JellySquid
     */
    @Overwrite
    public void translate(double x, double y, double z) {
        MatrixStack.Entry entry = this.poseStack.getLast();

        Matrix4fExtended mat = MatrixUtil.getExtendedMatrix(entry.pose());
        mat.translate((float) x, (float) y, (float) z);
    }

    /**
     * @reason Use our faster specialized function
     * @author JellySquid
     */
    @Overwrite
    public void mulPose(Quaternion q) {
        MatrixStack.Entry entry = this.poseStack.getLast();

        Matrix4fExtended mat4 = MatrixUtil.getExtendedMatrix(entry.pose());
        mat4.rotate(q);

        Matrix3fExtended mat3 = MatrixUtil.getExtendedMatrix(entry.normal());
        mat3.rotate(q);
    }
}
