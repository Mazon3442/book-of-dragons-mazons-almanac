package local.bodaddon.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import local.bodaddon.BodAddon;
import local.bodaddon.editor.EditorPayloads.SketchbookChoice;
import local.bodaddon.editor.EditorPayloads.SketchbookClose;
import local.bodaddon.editor.EditorPayloads.SketchbookPage;
import local.bodaddon.editor.VariantRow;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import static local.bodaddon.client.VariantsPanel.ROWS_PER_PAGE;
import static local.bodaddon.client.VariantsPanel.swatch;

final class SketchbookScreen extends Screen {
    private static final int BOOK_W = 256;
    private static final int BOOK_H = 171;
    private static final int SHEET_X = 129;
    private static final int SHEET_Y = 8;
    private static final int SHEET_W = 116;
    private static final int SHEET_H = 145;
    private static final int ROW_X = 9;
    private static final int ROW_Y = 27;
    private static final int ROW_W = 98;
    private static final int ROW_STEP = 21;
    private static final VariantsPanel.Skin PAPER = VariantsPanel.Skin.PAPER;
    private static final ResourceLocation BOOK = art("book_open");
    private static final ResourceLocation SHEET = art("page_blank");
    private static final int MAX_TURNING_PAGES = 4;
    private static final int SHEET_STAGGER_MS = 80;

    private final VariantsPanel variants = new VariantsPanel();
    private final List<FlyingPage> flying = new ArrayList<>(MAX_TURNING_PAGES);
    private final Deque<Integer> queuedTurns = new ArrayDeque<>();
    private SketchbookPage state;
    private VariantsPanel.OptionList options;
    private boolean draggingDragon;
    private int left, top;
    private int page;
    private int returnFocus = -1;

    SketchbookScreen(SketchbookPage state) {
        super(Component.translatable("bodaddon.sketchbook.title"));
        this.state = state;
    }

    private static ResourceLocation art(String file) {
        return ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "textures/gui/sketchbook/" + file + ".png");
    }

    private DragonBase dragon() {
        return minecraft != null && minecraft.level != null && minecraft.level.getEntity(state.entityId()) instanceof DragonBase dragon
            ? dragon : null;
    }

    @Override
    protected void init() {
        left = (width - BOOK_W) / 2;
        top = (height - BOOK_H) / 2;
        variants.rebuild(dragon(), state.rows());
        page = Math.min(page, variants.pages() - 1);
        options = null;
        int x = left + SHEET_X + ROW_X;
        int y = top + SHEET_Y + ROW_Y;
        if (variants.pickerRow >= 0) {
            VariantRow row = variants.shown[variants.pickerRow];
            options = addRenderableWidget(variants.new OptionList(font, PAPER, x, y, ROW_W, row, index -> choose(row, index)));
            addRenderableWidget(new InkButton(x + 67, y - 17, 31, 13, PAPER.text(Component.translatable("gui.back")), this::closePicker));
        } else {
            for (int i = 0; i < ROWS_PER_PAGE; i++) {
                int index = page * ROWS_PER_PAGE + i;
                if (index >= variants.shown.length) break;
                VariantRow row = variants.shown[index];
                var button = addWidget(new InkButton(x, y + i * ROW_STEP, ROW_W, ROW_STEP,
                    Component.translatable("bodaddon.sketchbook.row", row.label(), row.options()[row.current()]),
                    () -> openPicker(index)));
                if (index == returnFocus) setFocused(button);
            }
            if (variants.pages() > 1) {
                addPageCorner(left + 16, -1, "‹", "bodaddon.sketchbook.previous");
                addPageCorner(left + 226, 1, "›", "bodaddon.sketchbook.next");
            }
        }
    }

    private void addPageCorner(int x, int delta, String glyph, String narration) {
        InkButton button = addRenderableWidget(new InkButton(x, top + (delta < 0 ? 132 : 138), 18, 16,
            Component.translatable(narration), () -> requestTurn(delta)));
        button.visibleText = Component.literal(glyph);
        button.active = Mth.clamp(page + delta, 0, variants.pages() - 1) != page;
    }

    void refresh(SketchbookPage fresh) {
        if (!state.token().equals(fresh.token())) return;
        state = fresh;
        flying.forEach(leaf -> leaf.turn.invalidate());
        rebuildWidgets();
        if (options != null) setFocused(options);
    }

    @Override
    public void tick() {
        DragonBase target = dragon();
        if (target == null || !target.isAlive() || minecraft.player == null
            || !minecraft.player.canInteractWithEntity(target, 0.0)
            || !target.isTame() || !minecraft.player.getUUID().equals(target.ownerId())) {
            onClose();
        } else if (variants.lookChanged(target)) {
            rebuildWidgets();
            if (options != null) setFocused(options);
        }
    }

    private void choose(VariantRow row, int index) {
        ResourceLocation palette = ResourceLocation.tryParse(row.ids()[index]);
        if (palette != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new SketchbookChoice(state.token(), row.locus(), row.slot(), palette));
        }
    }

    private void openPicker(int index) {
        if (!flying.isEmpty()) return;
        returnFocus = index;
        variants.openPicker(index);
        rebuildWidgets();
        setFocused(options);
    }

    private void closePicker() {
        variants.closePicker();
        variants.resetView();
        rebuildWidgets();
        if (returnFocus >= 0 && returnFocus / ROWS_PER_PAGE == page) {
            int button = returnFocus % ROWS_PER_PAGE;
            if (button < children().size()) setFocused(children().get(button));
        }
        returnFocus = -1;
    }

    private void requestTurn(int delta) {
        if (options != null) return;
        int destination = page;
        for (int pending : queuedTurns) destination += pending;
        if (destination + delta < 0 || destination + delta >= variants.pages()) return;
        if (flying.isEmpty() && queuedTurns.isEmpty()) {
            startTurn(delta, Util.getMillis(), 450);
        } else if (flying.size() + queuedTurns.size() < MAX_TURNING_PAGES) {
            queuedTurns.addLast(delta);
            long now = Util.getMillis();
            if (!flying.isEmpty() && flying.getFirst().direction == delta) flying.forEach(leaf -> leaf.speedUp(now));
        }
    }

    private void startTurn(int delta, long now, long duration) {
        int destination = Mth.clamp(page + delta, 0, variants.pages() - 1);
        if (destination == page) return;
        if (ConfigClient.pageFlip() != ConfigClient.PageFlip.OFF) {
            flying.add(new FlyingPage(page, destination, delta, now, duration));
        }
        page = destination;
        rebuildWidgets();
    }

    private void advanceTurns(long now) {
        while (!flying.isEmpty() && flying.getFirst().fraction(now) >= 1) {
            flying.removeFirst().turn.close();
        }
        if (queuedTurns.isEmpty()) return;
        if (!flying.isEmpty()) {
            FlyingPage last = flying.getLast();
            if (last.direction != queuedTurns.getFirst() || now - last.launched < SHEET_STAGGER_MS) return;
        }
        startTurn(queuedTurns.removeFirst(), now, 300);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        long now = Util.getMillis();
        advanceTurns(now);
        blitBook(g, left, top, 0, 0, BOOK_W, BOOK_H);
        int x = left + SHEET_X;
        int y = top + SHEET_Y;
        int restingPage = !flying.isEmpty() && flying.getFirst().direction < 0 ? flying.getFirst().from : page;
        drawSheet(g, x, y, restingPage, mx, my, true);
        if (flying.stream().noneMatch(leaf -> leaf.angle(now) >= 0.56F)) {
            drawPortrait(g, left, top, 0, !flying.isEmpty() && flying.getFirst().direction > 0 ? flying.getFirst().from : page);
        }
        for (FlyingPage leaf : flying) leaf.turn.shadow(g, x, y, leaf.angle(now));
        // Draw raised sheets over the sheets already close to the page stacks.
        var raised = flying.stream().sorted(Comparator.comparingDouble(leaf -> Mth.sin(leaf.angle(now) * Mth.PI))).toList();
        for (FlyingPage leaf : raised) {
            int movingPage = leaf.direction > 0 ? leaf.from : leaf.to;
            leaf.turn.draw(g, x, y, leaf.angle(now),
                (graphics, px, py, targetHeight) -> drawSheet(graphics, px, py, movingPage, -1, -1, false),
                (graphics, px, py, targetHeight) -> drawFlyingPortrait(graphics, px, py, targetHeight, leaf.direction > 0 ? leaf.to : leaf.from));
        }
    }

    private void blitBook(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        try {
            g.blit(BOOK, x, y, u, v, w, h, BOOK_W, BOOK_H);
        } finally {
            if (!blend) RenderSystem.disableBlend();
        }
    }

    private void drawFlyingPortrait(GuiGraphics g, int x, int y, int targetHeight, int leftPage) {
        int portraitX = SHEET_X - SHEET_W;
        blitBook(g, x, y, portraitX, SHEET_Y, SHEET_W, SHEET_H);
        drawPortrait(g, x - portraitX, y - SHEET_Y, targetHeight, leftPage);
    }

    private void drawPortrait(GuiGraphics g, int bookX, int bookY, int targetHeight, int spread) {
        Component title = PAPER.title(getTitle());
        g.drawString(font, title, bookX + 70 - font.width(title) / 2, bookY + 16, PAPER.ink, false);
        var name = PAPER.fit(font, Component.literal(state.name()), 101);
        g.drawString(font, name, bookX + 70 - font.width(name) / 2, bookY + 37, PAPER.secondary, false);
        DragonBase target = dragon();
        if (target != null) drawPortraitDragon(g, target, bookX, bookY, targetHeight);
    }

    private void drawPortraitDragon(GuiGraphics g, DragonBase target, int bookX, int bookY, int targetHeight) {
        int x1 = bookX + 19;
        int y1 = bookY + 51;
        int x2 = bookX + 121;
        int y2 = bookY + 129;
        var matrix = g.pose().last().pose();
        double scale = minecraft.getWindow().getGuiScale();
        int framebufferHeight = targetHeight == 0 ? minecraft.getWindow().getHeight() : targetHeight;
        Vector3f lo = matrix.transformPosition(x1, y1, 0, new Vector3f());
        Vector3f hi = matrix.transformPosition(x2, y2, 0, new Vector3f());
        int clipX = Mth.floor(lo.x * scale);
        int clipY = framebufferHeight - Mth.ceil(hi.y * scale);
        int clipWidth = Mth.ceil(hi.x * scale) - clipX;
        int clipHeight = framebufferHeight - Mth.floor(lo.y * scale) - clipY;
        g.flush();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        var picked = dispatcher.crosshairPickEntity;
        boolean nameVisible = target.isCustomNameVisible();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var previous = stack.mallocInt(4);
            boolean scissored = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previous);
            RenderSystem.enableScissor(clipX, clipY, clipWidth, clipHeight);
            try {
                dispatcher.crosshairPickEntity = null;
                target.setCustomNameVisible(false);
                variants.renderDragon(g, target, x1, y1, x2, y2);
            } finally {
                dispatcher.crosshairPickEntity = picked;
                target.setCustomNameVisible(nameVisible);
                if (scissored) RenderSystem.enableScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
                else RenderSystem.disableScissor();
            }
        }
    }

    private void drawBlankSheet(GuiGraphics g, int x, int y) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        try {
            g.blit(SHEET, x, y, 0, 0, SHEET_W, SHEET_H, SHEET_W, SHEET_H);
        } finally {
            if (!blend) RenderSystem.disableBlend();
        }
    }

    private void drawSheet(GuiGraphics g, int x, int y, int sheetPage, int mx, int my, boolean live) {
        drawBlankSheet(g, x, y);
        boolean picking = live && options != null;
        Component heading = picking ? Component.literal(variants.shown[variants.pickerRow].label())
            : Component.translatable("bodaddon.sketchbook.heading");
        if (picking) {
            g.drawString(font, PAPER.fit(font, heading, 64), x + ROW_X, y + 11, PAPER.ink, false);
        } else {
            Component title = PAPER.title(heading);
            g.drawString(font, title, x + (SHEET_W - font.width(title)) / 2, y + 8, PAPER.ink, false);
        }
        if (picking) return;
        if (variants.shown.length == 0) {
            g.drawWordWrap(font, PAPER.text(Component.translatable("bodaddon.sketchbook.no_variants")), x + ROW_X, y + ROW_Y, ROW_W, PAPER.secondary);
        }
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int index = sheetPage * ROWS_PER_PAGE + i;
            if (index >= variants.shown.length) break;
            int rx = x + ROW_X;
            int ry = y + ROW_Y + i * ROW_STEP;
            boolean hot = live && flying.isEmpty() && ((mx >= rx && mx < rx + ROW_W && my >= ry && my < ry + ROW_STEP)
                || (i < children().size() && children().get(i).isFocused()));
            drawRow(g, variants.shown[index], rx, ry, hot);
        }
        if (variants.pages() > 1) {
            Component number = PAPER.text(Component.translatable("bodaddon.sketchbook.page", sheetPage + 1, variants.pages()));
            g.drawString(font, number, x + (SHEET_W - font.width(number)) / 2, y + 134, PAPER.secondary, false);
        }
    }

    private void drawRow(GuiGraphics g, VariantRow row, int x, int y, boolean hot) {
        int accent = hot ? PAPER.accent : PAPER.secondary;
        g.drawString(font, PAPER.fit(font, Component.literal(row.label()), ROW_W), x, y, PAPER.ink, false);
        int textX = x;
        if (row.color()) {
            SketchbookDecor.drawDab(g, swatch(row.ids()[row.current()]), x, y + 8, row.locus().hashCode() + row.slot());
            textX += SketchbookDecor.DAB_W + 3;
        }
        var value = PAPER.fit(font, Component.literal(row.options()[row.current()]), x + ROW_W - textX - 10);
        g.drawString(font, value, textX, y + 9, accent, false);
        g.drawString(font, "›", x + ROW_W - 5, y + 9, accent, false);
        if (hot) g.fill(x, y + 18, x + ROW_W, y + 19, PAPER.accent);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overDragon(mx, my)) {
            draggingDragon = true;
            return true;
        }
        boolean wasPicking = options != null;
        boolean clicked = super.mouseClicked(mx, my, button);
        if (!wasPicking && options != null) setFocused(options);
        return clicked;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingDragon && button == 0) {
            variants.turn(dx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingDragon = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean overDragon(double mx, double my) {
        return mx >= left + 17 && mx < left + 123 && my >= top + 49 && my < top + 131;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        if (overDragon(mx, my)) {
            variants.zoom(sy);
            return true;
        }
        if (options == null && mx >= left + SHEET_X && mx < left + SHEET_X + SHEET_W && my >= top && my < top + BOOK_H) {
            requestTurn(sy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (options != null && key == GLFW.GLFW_KEY_ESCAPE) {
            closePicker();
            return true;
        }
        if (options == null && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            requestTurn(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int width, int height) {
        flying.forEach(leaf -> leaf.turn.close());
        super.resize(mc, width, height);
    }

    @Override
    public void removed() {
        flying.forEach(leaf -> leaf.turn.close());
        flying.clear();
        if (minecraft.getConnection() != null && minecraft.getConnection().getConnection().isConnected()) {
            PacketDistributor.sendToServer(new SketchbookClose(state.token()));
        }
        super.removed();
    }

    private static final class FlyingPage {
        final SketchbookPageTurn turn = new SketchbookPageTurn(SHEET_W, SHEET_H);
        final int from, to, direction;
        final long launched;
        private long start, duration;

        FlyingPage(int from, int to, int direction, long now, long duration) {
            this.from = from;
            this.to = to;
            this.direction = direction;
            this.launched = now;
            this.start = now;
            this.duration = duration;
        }

        float fraction(long now) { return Mth.clamp((now - start) / (float) duration, 0, 1); }

        float angle(long now) {
            float t = fraction(now);
            float eased = t * t * (3 - 2 * t);
            return direction > 0 ? eased : 1 - eased;
        }

        void speedUp(long now) {
            float t = fraction(now);
            duration = Math.max(220, duration - 65);
            start = now - Math.round(t * duration);
        }
    }

    private final class InkButton extends AbstractButton {
        private final Runnable action;
        private Component visibleText;

        InkButton(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
            this.visibleText = label;
        }

        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            if (!active) return;
            int x = getX() + (width - font.width(visibleText)) / 2;
            int y = getY() + (height - 8) / 2;
            g.drawString(font, visibleText, x, y, isHoveredOrFocused() ? PAPER.accent : PAPER.ink, false);
            if (isFocused()) g.fill(x, y + 9, x + font.width(visibleText), y + 10, PAPER.accent);
        }
    }
}
