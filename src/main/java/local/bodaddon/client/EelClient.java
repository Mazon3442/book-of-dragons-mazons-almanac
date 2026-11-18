package local.bodaddon.client;

import local.bodaddon.BodAddon;
import local.bodaddon.ModEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client only: gives the eel its model and renderer. */
@EventBusSubscriber(modid = BodAddon.MOD_ID, value = Dist.CLIENT)
public final class EelClient {
    public static final ModelLayerLocation EEL_LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "eel"), "main");

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EEL_LAYER, EelModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EEL.get(), EelRenderer::new);
    }

    private EelClient() {}
}
