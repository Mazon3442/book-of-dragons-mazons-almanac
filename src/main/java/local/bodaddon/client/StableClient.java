package local.bodaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client entry point for the Stable Sign name box; the only place the screen is referenced from shared code. */
public final class StableClient {
    public static void show(BlockPos pos, String name) {
        Minecraft.getInstance().setScreen(new StableNameScreen(pos, name));
    }

    private StableClient() {}
}
