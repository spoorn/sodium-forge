package me.jellysquid.mods.sodium.mixin.core.frustum;

import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClippingHelper.class)
public class MixinFrustum implements FrustumExtended {
    private float xF, yF, zF;

    private float nxX, nxY, nxZ, nxW;
    private float pxX, pxY, pxZ, pxW;
    private float nyX, nyY, nyZ, nyW;
    private float pyX, pyY, pyZ, pyW;
    private float nzX, nzY, nzZ, nzW;
    private float pzX, pzY, pzZ, pzW;

    @Inject(method = "prepare", at = @At("HEAD"))
    private void prePositionUpdate(double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        this.xF = (float) cameraX;
        this.yF = (float) cameraY;
        this.zF = (float) cameraZ;
    }

    @Inject(method = "getPlane", at = @At("HEAD"))
    private void transform(Matrix4f mat, int x, int y, int z, int index, CallbackInfo ci) {
        Vector4f vec = new Vector4f((float) x, (float) y, (float) z, 1.0F);
        vec.transform(mat);
        vec.normalize();

        switch (index) {
            case 0:
                this.nxX = vec.x();
                this.nxY = vec.y();
                this.nxZ = vec.z();
                this.nxW = vec.w();
                break;
            case 1:
                this.pxX = vec.x();
                this.pxY = vec.y();
                this.pxZ = vec.z();
                this.pxW = vec.w();
                break;
            case 2:
                this.nyX = vec.x();
                this.nyY = vec.y();
                this.nyZ = vec.z();
                this.nyW = vec.w();
                break;
            case 3:
                this.pyX = vec.x();
                this.pyY = vec.y();
                this.pyZ = vec.z();
                this.pyW = vec.w();
                break;
            case 4:
                this.nzX = vec.x();
                this.nzY = vec.y();
                this.nzZ = vec.z();
                this.nzW = vec.w();
                break;
            case 5:
                this.pzX = vec.x();
                this.pzY = vec.y();
                this.pzZ = vec.z();
                this.pzW = vec.w();
                break;
            default:
                throw new IllegalArgumentException("Invalid index");
        }
    }

    @Override
    public boolean fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.cubeInFrustum(minX - this.xF, minY - this.yF, minZ - this.zF,
                maxX - this.xF, maxY - this.yF, maxZ - this.zF);
    }

    /**
     * @author JellySquid
     * @reason Optimize away object allocations and for-loop
     */
    @Overwrite
    private boolean cubeInFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.nxX * (this.nxX < 0 ? minX : maxX) + this.nxY * (this.nxY < 0 ? minY : maxY) + this.nxZ * (this.nxZ < 0 ? minZ : maxZ) >= -this.nxW &&
                this.pxX * (this.pxX < 0 ? minX : maxX) + this.pxY * (this.pxY < 0 ? minY : maxY) + this.pxZ * (this.pxZ < 0 ? minZ : maxZ) >= -this.pxW &&
                this.nyX * (this.nyX < 0 ? minX : maxX) + this.nyY * (this.nyY < 0 ? minY : maxY) + this.nyZ * (this.nyZ < 0 ? minZ : maxZ) >= -this.nyW &&
                this.pyX * (this.pyX < 0 ? minX : maxX) + this.pyY * (this.pyY < 0 ? minY : maxY) + this.pyZ * (this.pyZ < 0 ? minZ : maxZ) >= -this.pyW &&
                this.nzX * (this.nzX < 0 ? minX : maxX) + this.nzY * (this.nzY < 0 ? minY : maxY) + this.nzZ * (this.nzZ < 0 ? minZ : maxZ) >= -this.nzW &&
                this.pzX * (this.pzX < 0 ? minX : maxX) + this.pzY * (this.pzY < 0 ? minY : maxY) + this.pzZ * (this.pzZ < 0 ? minZ : maxZ) >= -this.pzW;
    }
}
