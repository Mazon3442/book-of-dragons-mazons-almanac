package local.bodaddon.client;

import local.bodaddon.stable.StablePayloads;
import local.bodaddon.stable.StableSignBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** One text box for the stable's name. Enter or Done saves it; Escape closes without changing it. */
public final class StableNameScreen extends Screen {
    private final BlockPos pos;
    private final String initial;
    private EditBox box;

    StableNameScreen(BlockPos pos, String name) {
        super(Component.translatable("bodaddon.stable.name.title"));
        this.pos = pos;
        this.initial = name;
    }

    @Override
    protected void init() {
        box = new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, Component.translatable("bodaddon.stable.name.title"));
        box.setMaxLength(StableSignBlockEntity.MAX_NAME);
        box.setValue(initial);
        box.setHint(Component.translatable("bodaddon.stable.name.hint"));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(width / 2 - 100, height / 2 + 20, 200, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new StablePayloads.Set(pos, box.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) {     // Enter, keypad Enter
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
