package local.bodaddon.client;

import local.bodaddon.editor.EditorState;
import local.bodaddon.editor.EditorPayloads.SketchbookPage;
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

    public static void showSketchbook(SketchbookPage page) {
        Minecraft mc = Minecraft.getInstance();
        if (page.opening()) {
            mc.setScreen(new SketchbookScreen(page));
        } else if (mc.screen instanceof SketchbookScreen open) {
            open.refresh(page);
        }
    }

    private EditorClient() {}
}
