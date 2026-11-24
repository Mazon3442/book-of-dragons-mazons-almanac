package local.bodaddon.spatial.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import local.bodaddon.spatial.FlightPicking;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tastytrash.spatialGUI.render.WorldScreenRenderer;

@Pseudo
@Mixin(value = WorldScreenRenderer.class, remap = false)
public abstract class WorldScreenRendererMixin {
    // Spatial GUI 1.12 renamed these from capturePerspectiveState / resetPerspectiveState;
    // listing both names keeps 1.0 through 1.12 working.
    @Inject(method = {"captureCameraState", "capturePerspectiveState"}, at = @At("RETURN"))
    private void dragonSpatial$captureCamera(Matrix4f projection, VertexSorting sorting,
                                             PoseStack view, CallbackInfo ci) {
        FlightPicking.capture(projection, view == null ? new Matrix4f() : view.last().pose());
    }

    @Inject(method = {"resetCameraState", "resetPerspectiveState"}, at = @At("HEAD"))
    private void dragonSpatial$resetCamera(CallbackInfo ci) {
        FlightPicking.clear();
    }
}
