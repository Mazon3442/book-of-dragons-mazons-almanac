package local.bodaddon;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import local.bodaddon.editor.DragonEditEvents;
import local.bodaddon.editor.EditorPayloads;
import local.bodaddon.item.DragonPoulticeItem;
import local.bodaddon.item.DragonNipItem;
import local.bodaddon.item.SagefruitItem;
import local.bodaddon.dragon.DragonFriendships;
import local.bodaddon.stable.StableEvents;
import local.bodaddon.worldgen.SageTreeFeature;
import local.bodaddon.client.ConfigClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(BodAddon.MOD_ID)
public class BodAddon {
    public static final String MOD_ID = "bodaddon";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    static {
        TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MOD_ID))
            .icon(() -> new ItemStack(ModItems.DRAGON_EDITOR.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.DRAGON_EDITOR.get());
                out.accept(ModItems.HICCUPS_SKETCHBOOK.get());
                out.accept(ModBlocks.FEEDING_TROUGH_ITEM.get());
                out.accept(ModItems.DRAGON_TREAT.get());
                out.accept(ModItems.DRAGON_NIP.get());
                out.accept(ModBlocks.DRAGON_NIP_GRASS_ITEM.get());
                out.accept(ModBlocks.DRAGON_ROOT_ITEM.get());
                out.accept(ModItems.EEL.get());
                out.accept(ModItems.EEL_BUCKET.get());
                out.accept(ModItems.EEL_SPAWN_EGG.get());
                out.accept(ModItems.DRAGON_REPELLENT.get());
                out.accept(ModBlocks.DRAGON_REPELLENT_BLOCK_ITEM.get());
                out.accept(ModItems.SAGEFRUIT.get());
                out.accept(ModBlocks.SAGE_SAPLING_ITEM.get());
                out.accept(ModBlocks.SAGE_LEAVES_ITEM.get());
                out.accept(ModItems.DRAGON_POULTICE.get());
                out.accept(ModItems.STABLE_PLATE.get());
            })
            .build());
    }

    public BodAddon(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BodAddonConfig.SPEC);
        modBus.addListener(BodAddonConfig::onConfigReloaded);
        NeoForge.EVENT_BUS.register(BodAddonConfig.class);
        NeoForge.EVENT_BUS.register(StableEvents.class);
        if (FMLEnvironment.dist == Dist.CLIENT) ConfigClient.register(container);
        ModItems.register(modBus);
        ModEffects.register(modBus);
        ModBlocks.register(modBus);
        SageTreeFeature.register(modBus);
        ModEntities.register(modBus);
        local.bodaddon.eel.EelFishingModifier.register(modBus);
        modBus.addListener(EditorPayloads::register);
        NeoForge.EVENT_BUS.register(DragonEditEvents.class);
        NeoForge.EVENT_BUS.register(DragonPoulticeItem.class);
        NeoForge.EVENT_BUS.register(DragonNipItem.class);
        NeoForge.EVENT_BUS.register(SagefruitItem.class);
        NeoForge.EVENT_BUS.register(DragonFriendships.class);
        NeoForge.EVENT_BUS.register(local.bodaddon.dragon.DragonSaddlebagFeeding.class);
        TABS.register(modBus);
    }
}
