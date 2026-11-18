package local.bodaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import local.bodaddon.BodAddon;
import local.bodaddon.eel.EelEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class EelRenderer extends MobRenderer<EelEntity, EelModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "textures/entity/eel.png");

    public EelRenderer(EntityRendererProvider.Context context) {
        super(context, new EelModel(context.bakeLayer(EelClient.EEL_LAYER)), 0.2F);
    }

    @Override
    public ResourceLocation getTextureLocation(EelEntity eel) { return TEXTURE; }

    @Override
    protected void setupRotations(EelEntity eel, PoseStack pose, float ageInTicks, float bodyYaw, float partialTick, float scale) {
        super.setupRotations(eel, pose, ageInTicks, bodyYaw, partialTick, scale);
        if (!eel.isInWater()) {
            pose.translate(0.1F, 0.1F, -0.1F);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }
}
