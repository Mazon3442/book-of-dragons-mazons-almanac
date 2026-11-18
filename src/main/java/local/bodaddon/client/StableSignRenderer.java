package local.bodaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import local.bodaddon.BodAddon;
import local.bodaddon.ModBlocks;
import local.bodaddon.stable.StableSignBlock;
import local.bodaddon.stable.StableSignBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Writes the dragon's name in the middle band of the board and the owner's name in the bottom band, on both broad faces (the flat
 * sign has only its front). The boards are modelled facing south with their faces at z = 7.33 and 8.67 (the flat one at z = 1.33);
 * the blockstate turns them, and so does this renderer.
 */
public final class StableSignRenderer implements BlockEntityRenderer<StableSignBlockEntity> {
    private static final int COLOR = 0xE3BE86;       // pale carved wood, like the crest
    private static final float NAME_SCALE = 0.0125F;  // blocks per font pixel: an 8 px line is 1.6 sixteenths tall
    private static final float OWNER_SCALE = 0.009F;
    private static final float ROOM = 12.0F;          // sixteenths of width between the corner brackets

    /** Where the text goes on each placement's board: its bottom edge (16ths, as modelled), and the front and back faces. */
    private record Spot(float bottom, float front, float back) {}

    private static final Spot WALL = new Spot(-2.0F, 8.67F, 7.33F);
    private static final Spot CEILING = new Spot(-4.0F, 8.67F, 7.33F);
    private static final Spot FLOOR = new Spot(9.33F, 8.67F, 7.33F);
    private static final Spot FLAT = new Spot(4.33F, 1.33F, Float.NaN);

    private final Font font;

    public StableSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(StableSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        String name = sign.name();
        String owner = sign.owner();
        if (name.isEmpty() && owner.isEmpty()) return;
        var state = sign.getBlockState();
        Spot spot = switch (state.getValue(StableSignBlock.MOUNT)) {
            case WALL -> WALL;
            case CEILING -> CEILING;
            case FLOOR -> FLOOR;
            case FLAT -> FLAT;
        };

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-StableSignBlock.turns(state) * 90));
        pose.translate(-0.5, 0, -0.5);
        for (int side = 0; side < 2; side++) {
            float z = side == 0 ? spot.front() : spot.back();
            if (Float.isNaN(z)) continue;
            float zz = z / 16.0F + (side == 0 ? 0.002F : -0.002F);
            line(name, spot.bottom() + 4.0F, zz, side, NAME_SCALE, pose, buffer, light);    // middle band, the board's centre
            line(owner, spot.bottom() + 1.5F, zz, side, OWNER_SCALE, pose, buffer, light);  // bottom band
        }
        pose.popPose();
    }

    private void line(String text, float y, float z, int side, float baseScale, PoseStack pose, MultiBufferSource buffer, int light) {
        if (text.isEmpty()) return;
        float width = font.width(text);
        float scale = Math.min(baseScale, (ROOM / 16.0F) / width);
        pose.pushPose();
        pose.translate(0.5, y / 16.0, z);
        if (side == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -width / 2.0F, -font.lineHeight / 2.0F, COLOR, false, pose.last().pose(), buffer,
            Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    @EventBusSubscriber(modid = BodAddon.MOD_ID, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlocks.STABLE_SIGN_ENTITY.get(), StableSignRenderer::new);
        }
    }
}
