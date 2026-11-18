package local.bodaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import local.bodaddon.BodAddon;
import local.bodaddon.ModBlocks;
import local.bodaddon.trough.FeedingTroughBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Lays the trough's contents flat in the basin: one spot per slot (9 along the length, 3 across), each stacked up to three
 * items high as the slot fills, with a different spin per slot so the pile looks loose.
 */
public final class TroughRenderer implements BlockEntityRenderer<FeedingTroughBlockEntity> {
    private static final int COLUMNS = 9;
    private static final int STAGGER_LEVELS = 11;
    private static final double STAGGER_STEP = 0.25;   // sixteenths of a block

    public TroughRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FeedingTroughBlockEntity trough, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (trough.getLevel() == null || trough.isEmpty()) return;
        Direction facing = trough.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        pose.pushPose();
        // the model is built along +x with the head half one block on; the blockstate turns it clockwise by this much
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-((facing.get2DDataValue() + 2) % 4) * 90));
        pose.translate(-0.5, 0, -0.5);
        for (int i = 0; i < trough.getContainerSize(); i++) {
            ItemStack stack = trough.getItem(i);
            if (stack.isEmpty()) continue;
            int layers = Math.max(1, Math.min(3, (stack.getCount() * 3 + stack.getMaxStackSize() - 1) / stack.getMaxStackSize()));
            for (int layer = 0; layer < layers; layer++) {
                pose.pushPose();
                // Flat items overlap their neighbours, and two overlapping items at one height flicker (z-fighting), so every
                // slot sits a hair above or below the ones near it: slot numbers within 10 of each other never share a height.
                double lift = (i % STAGGER_LEVELS) * STAGGER_STEP;
                pose.translate((4.0 + (i % COLUMNS) * 3.0) / 16.0, (7.2 + layer * 0.9 + lift) / 16.0, (5.5 + (i / COLUMNS) * 2.5) / 16.0);
                pose.mulPose(Axis.YP.rotationDegrees((i * 47 + layer * 29) % 360));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.scale(0.3F, 0.3F, 0.3F);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffer, trough.getLevel(), (int) trough.getBlockPos().asLong() + i * 3 + layer);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    @EventBusSubscriber(modid = BodAddon.MOD_ID, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlocks.TROUGH_ENTITY.get(), TroughRenderer::new);
        }
    }
}
