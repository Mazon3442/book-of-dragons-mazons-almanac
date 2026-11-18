package local.bodaddon.client;

import local.bodaddon.editor.EditorState;
import net.minecraft.client.Minecraft;

/** Client entry point for the editor; the only place the screen is referenced from shared code. */
public final class EditorClient {
    public static void show(EditorState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DragonEditorScreen open && open.entityId() == state.entityId()) {
            open.refresh(state);
        } else {
            mc.setScreen(new DragonEditorScreen(state));
        }
    }

    private EditorClient() {}
}
