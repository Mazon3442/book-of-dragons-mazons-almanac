package local.bodaddon.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client only: the in-game config screen (Mods list, then Config) for the addon's settings. */
public final class ConfigClient {
    enum PageFlip { MESH, SIMPLE, OFF }

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<PageFlip> PAGE_FLIP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PAGE_FLIP = builder.translation("bodaddon.configuration.sketchbook.page_flip").defineEnum("pageFlip", PageFlip.MESH);
        SPEC = builder.build();
    }

    static PageFlip pageFlip() {
        return SPEC.isLoaded() ? PAGE_FLIP.get() : PageFlip.MESH;
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private ConfigClient() {}
}
