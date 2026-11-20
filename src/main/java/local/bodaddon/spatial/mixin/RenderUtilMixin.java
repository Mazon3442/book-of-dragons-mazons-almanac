package local.bodaddon.spatial.mixin;

import local.bodaddon.spatial.FlightPicking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tastytrash.spatialGUI.util.RenderUtil;

@Pseudo
@Mixin(value = RenderUtil.class, remap = false)
public abstract class RenderUtilMixin {
    @Inject(method = "buildCameraRayDirection", at = @At("HEAD"), cancellable = true)
    private static void dragonSpatial$useRenderedCamera(double x, double y,
                                                         CallbackInfoReturnable<float[]> cir) {
        if (FlightPicking.isActive()) {
            var window = Minecraft.getInstance().getWindow();
            cir.setReturnValue(FlightPicking.PROJECTION.ray(
                    x, y, window.getScreenWidth(), window.getScreenHeight()));
        }
    }

    // Spatial GUI's intersections assume a zero ray origin. Translate only the picking
    // geometry to preserve that assumption when the captured view includes camera shake.
    @ModifyVariable(method = "getQuadPlanePosition", at = @At("HEAD"), argsOnly = true)
    private static RenderUtil.QuadBasis dragonSpatial$shiftQuadOrigin(RenderUtil.QuadBasis basis) {
        if (!FlightPicking.isActive()) {
            return basis;
        }
        return new RenderUtil.QuadBasis(FlightPicking.PROJECTION.relativeToOrigin(basis.centerOffset()),
                basis.right(), basis.up(), basis.normal(), basis.halfWidth(), basis.halfHeight());
    }

    @ModifyVariable(method = "getCylinderUv", at = @At("HEAD"), argsOnly = true)
    private static RenderUtil.CylinderBasis dragonSpatial$shiftCylinderOrigin(RenderUtil.CylinderBasis basis) {
        if (!FlightPicking.isActive()) {
            return basis;
        }
        return new RenderUtil.CylinderBasis(FlightPicking.PROJECTION.relativeToOrigin(basis.axisPos()),
                basis.axisDir(), basis.refDir(), FlightPicking.PROJECTION.relativeToOrigin(basis.center()),
                basis.radius(), basis.arcRadians(), basis.halfHeight());
    }
}
