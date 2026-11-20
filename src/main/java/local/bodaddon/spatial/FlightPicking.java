package local.bodaddon.spatial;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4fc;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.render.SpatialGUIRenderer;

public final class FlightPicking {
    public static final CameraProjection PROJECTION = new CameraProjection();
    private static ClientLevel capturedLevel;
    private static Screen capturedScreen;

    private FlightPicking() {
    }

    // Set when the installed Spatial GUI doesn't expose what we call; the fix then stays off.
    private static boolean unsupported;

    private static boolean isFlyingWithMenu() {
        if (unsupported) {
            return false;
        }
        try {
            return checkFlyingWithMenu();
        } catch (LinkageError e) {
            unsupported = true;
            return false;
        }
    }

    private static boolean checkFlyingWithMenu() {
        Minecraft mc = Minecraft.getInstance();
        SpatialGUIRenderer renderer = SpatialGUIClient.renderer();
        return mc.level != null && mc.player != null && mc.screen != null
                && mc.player.getVehicle() instanceof DragonBase dragon && dragon.isFlying()
                && SpatialGUIClient.isEnabled() && renderer != null && renderer.shouldCapture()
                && renderer.getHookedScreen() == mc.screen;
    }

    public static void capture(Matrix4fc projection, Matrix4fc view) {
        clear();
        if (isFlyingWithMenu()) {
            PROJECTION.capture(projection, view);
            Minecraft mc = Minecraft.getInstance();
            capturedLevel = mc.level;
            capturedScreen = mc.screen;
        }
    }

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return PROJECTION.isValid() && capturedLevel == mc.level && capturedScreen == mc.screen
                && isFlyingWithMenu();
    }

    public static void clear() {
        PROJECTION.clear();
        capturedLevel = null;
        capturedScreen = null;
    }
}
