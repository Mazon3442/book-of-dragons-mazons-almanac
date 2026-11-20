package local.bodaddon.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import local.bodaddon.BodAddon;
import local.bodaddon.editor.VariantRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Shared list, palette picker, and camera for the two variants editors. */
final class VariantsPanel {
    static final int ROWS_PER_PAGE = 5;
    private static final ResourceLocation READOUT = ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "readout");
    private static final ResourceLocation LETTERING = ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook");
    private static final ResourceLocation TITLE_LETTERING = ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "sketchbook_title");
    VariantRow[] shown = new VariantRow[0];
    int[] shownIndex = new int[0];
    int pickerRow = -1;
    private String pickerKey;
    private String drawsKey = "";
    private int pickerScroll;
    private float viewYaw = 50.0F;
    private float viewZoom = 1.0F;
    private float viewFocus = 0.5F;

    enum Skin {
        SLATE(0xFFEFE4CB, 0xFFA99F8C, 0xFF47474C, 0xFF35363A, 0xFFFA801A, 0xFFFFAE48, 0xFF1C1D20, 0xFF5D5C62),
        PAPER(0xFF3B291E, 0xFF593B29, 0xFFF2CD9F, 0xFFF5DBB1, 0xFF834723, 0xFFAB6639, 0xFFDAB385, 0xFF9B6944);

        final int ink, secondary, selected, hover, accent, accentHot, track, thumb;

        Skin(int ink, int secondary, int selected, int hover, int accent, int accentHot, int track, int thumb) {
            this.ink = ink;
            this.secondary = secondary;
            this.selected = selected;
            this.hover = hover;
            this.accent = accent;
            this.accentHot = accentHot;
            this.track = track;
            this.thumb = thumb;
        }

        Component text(Component label) {
            return this == PAPER ? label.copy().withStyle(style -> style.withFont(LETTERING)) : label;
        }

        /** Page headings: the larger, bolder cut of the same lettering. */
        Component title(Component label) {
            return this == PAPER ? label.copy().withStyle(style -> style.withFont(TITLE_LETTERING)) : label;
        }

        FormattedCharSequence fit(Font font, Component label, int width) {
            return Language.getInstance().getVisualOrder(font.substrByWidth(text(label), width));
        }
    }

    boolean lookChanged(Entity dragon) { return !LiveSlots.key(dragon).equals(drawsKey); }
    int pages() { return Math.max(1, (shown.length + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE); }
    void turn(double dx) { viewYaw += (float) dx * 1.5F; }
    void zoom(double by) { viewZoom = Mth.clamp(viewZoom * (1.0F + 0.12F * (float) by), 0.5F, 5.0F); }
    void resetView() { viewYaw = 50.0F; viewZoom = 1.0F; viewFocus = 0.5F; }

    /**
     * Picks which of the server's rows to offer: colour rows whose slot the renderer never reads are hidden (editing
     * them changes nothing), and what is left of a locus is numbered 1, 2, 3 in order. Pattern rows are all kept.
     */
    void rebuild(Entity dragon, VariantRow[] all) {
        Set<String> live = LiveSlots.compute(dragon);
        drawsKey = LiveSlots.key(dragon);
        List<Integer> keep = new ArrayList<>();
        Map<String, Integer> perLocus = new HashMap<>();
        for (int i = 0; i < all.length; i++) {
            VariantRow r = all[i];
            if (r.color() && live != null && !live.contains(r.locus() + ":" + r.slot())) continue;
            keep.add(i);
            perLocus.merge(r.locus(), 1, Integer::sum);
        }
        shown = new VariantRow[keep.size()];
        shownIndex = new int[keep.size()];
        Map<String, Integer> seen = new HashMap<>();
        pickerRow = -1;
        for (int k = 0; k < keep.size(); k++) {
            VariantRow r = all[keep.get(k)];
            int number = seen.merge(r.locus(), 1, Integer::sum);
            String label = perLocus.get(r.locus()) > 1 ? r.label() + " " + number : r.label();
            shown[k] = new VariantRow(label, r.locus(), r.slot(), r.color(), r.options(), r.ids(), r.current(), r.bondNeeded());
            shownIndex[k] = keep.get(k);
            if (pickerKey != null && pickerKey.equals(r.locus() + ":" + r.slot())) pickerRow = k;
        }
    }

    void openPicker(int row) {
        pickerRow = row;
        pickerKey = shown[row].locus() + ":" + shown[row].slot();
        pickerScroll = -1;      // the list centres on the current option the first time it is built
        View v = View.of(shown[row].locus());
        viewYaw = v.yaw;
        viewZoom = v.zoom;
        viewFocus = v.focus;
    }

    void closePicker() {
        pickerRow = -1;
        pickerKey = null;
    }

    /** Where the camera starts for each kind of edit: heads close up, wings wide, and so on. */
    private enum View {
        HEAD(20F, 2.4F, 0.78F), WINGS(40F, 1.5F, 0.6F), LEGS(50F, 1.8F, 0.25F), BACK(140F, 1.6F, 0.55F), BODY(60F, 1.4F, 0.5F);

        final float yaw;
        final float zoom;
        final float focus;

        View(float yaw, float zoom, float focus) {
            this.yaw = yaw;
            this.zoom = zoom;
            this.focus = focus;
        }

        static View of(String locus) {
            return switch (locus) {
                case "e", "z", "f", "s" -> HEAD;
                case "w", "x", "k", "a", "i", "v" -> WINGS;
                case "l" -> LEGS;
                case "d", "r", "h" -> BACK;
                default -> BODY;
            };
        }
    }

    /** Same render path as the vanilla inventory paper doll, with a free yaw, zoom and focus height instead of mouse-follow. */
    void drawDragon(GuiGraphics g, LivingEntity dragon, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
        try {
            renderDragon(g, dragon, x1, y1, x2, y2);
        } finally {
            g.disableScissor();
        }
    }

    void renderDragon(GuiGraphics g, LivingEntity dragon, int x1, int y1, int x2, int y2) {
        float size = Math.max(0.5F, Math.max(dragon.getBbWidth(), dragon.getBbHeight()));
        float scale = Math.max(3.0F, Math.min(x2 - x1, y2 - y1) * 0.42F / size) * viewZoom;
        Vector3f translate = new Vector3f(0.0F, dragon.getBbHeight() * viewFocus, 0.0F);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);

        float bodyRot = dragon.yBodyRot;
        float yRot = dragon.getYRot();
        float xRot = dragon.getXRot();
        float headRotO = dragon.yHeadRotO;
        float headRot = dragon.yHeadRot;
        dragon.yBodyRot = 180.0F + viewYaw;
        dragon.setYRot(180.0F + viewYaw);
        dragon.setXRot(0.0F);
        dragon.yHeadRot = dragon.getYRot();
        dragon.yHeadRotO = dragon.getYRot();
        try {
            InventoryScreen.renderEntityInInventory(g, (x1 + x2) / 2.0F, (y1 + y2) / 2.0F, scale, translate, pose, new Quaternionf(), dragon);
        } finally {
            dragon.yBodyRot = bodyRot;
            dragon.setYRot(yRot);
            dragon.setXRot(xRot);
            dragon.yHeadRotO = headRotO;
            dragon.yHeadRot = headRot;
        }
    }

    /** Scrollable list of every option for one row, with palette swatches for colours; picking sends at once. */
    final class OptionList extends AbstractWidget {
        private static final int ROW_H = 14;
        private static final int BAR_W = 4;
        private final VariantRow row;
        private final Font font;
        private final Skin skin;
        private final IntConsumer onPick;
        private final int visible;
        private int current;
        private int keyboardIndex;
        private boolean draggingBar;

        OptionList(Font font, Skin skin, int x, int y, int width, VariantRow row, IntConsumer onPick) {
            super(x, y, width, 112, Component.literal(row.label()));
            this.font = font;
            this.skin = skin;
            this.row = row;
            this.onPick = onPick;
            this.current = row.current();
            this.keyboardIndex = current;
            this.visible = height / ROW_H;
            if (pickerScroll < 0) pickerScroll = current - visible / 2;
            pickerScroll = Mth.clamp(pickerScroll, 0, maxScroll());
        }

        private int maxScroll() { return Math.max(0, row.options().length - visible); }

        private int listWidth() { return width - (row.options().length > visible ? BAR_W + 2 : 0); }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int n = row.options().length;
            if (skin == Skin.SLATE) g.blitSprite(READOUT, getX(), getY(), width, height);
            g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
            for (int i = 0; i < visible && pickerScroll + i < n; i++) {
                int index = pickerScroll + i;
                int ry = getY() + i * ROW_H;
                boolean selected = index == current;
                boolean hot = isFocused() && keyboardIndex == index
                    || mouseX >= getX() && mouseX < getX() + listWidth() && mouseY >= ry && mouseY < ry + ROW_H;
                if (selected) {
                    g.fill(getX() + 1, ry, getX() + listWidth(), ry + ROW_H - 1, skin.selected);
                    g.fill(getX() + 1, ry, getX() + 3, ry + ROW_H - 1, skin.accent);
                } else if (hot) {
                    g.fill(getX() + 1, ry, getX() + listWidth(), ry + ROW_H - 1, skin.hover);
                }
                int textX = getX() + 5;
                if (row.color()) {
                    drawSwatch(g, swatch(row.ids()[index]), textX, ry + 3, 3, 7);
                    textX += 22;
                }
                int need = row.bondNeeded()[index];
                int tagWidth = 0;
                if (need > 0) {
                    Component tag = skin.text(Component.translatable("bodaddon.sketchbook.locked", need));
                    tagWidth = font.width(tag) + 4;
                    g.drawString(font, tag, getX() + listWidth() - tagWidth + 2, ry + 3, skin.accent, false);
                }
                if (need > 0) {
                    drawLock(g, textX, ry + 2, skin.accent);
                } else {
                    FormattedCharSequence name = skin.fit(font, Component.literal(row.options()[index]), getX() + listWidth() - textX - 2);
                    g.drawString(font, name, textX, ry + 3, selected ? skin.ink : skin.secondary, false);
                }
            }
            g.disableScissor();
            if (isFocused() && skin == Skin.SLATE) {
                g.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, skin.accent);
            } else if (isFocused() && keyboardIndex >= pickerScroll && keyboardIndex < pickerScroll + visible) {
                int focusY = getY() + (keyboardIndex - pickerScroll + 1) * ROW_H - 1;
                g.fill(getX() + 1, focusY, getX() + listWidth(), focusY + 1, skin.accent);
            }
            if (n > visible) {
                int bx = getX() + width - BAR_W;
                g.fill(bx, getY() + 1, bx + BAR_W, getY() + height - 1, skin.track);
                int thumbH = Math.max(10, height * visible / n);
                int thumbY = getY() + (height - thumbH) * pickerScroll / maxScroll();
                g.fill(bx, thumbY, bx + BAR_W, thumbY + thumbH, draggingBar ? skin.accentHot : skin.thumb);
            }
        }

        @Override
        public void onClick(double mx, double my) {
            if (row.options().length > visible && mx >= getX() + width - BAR_W - 1) {
                draggingBar = true;
                dragBar(my);
                return;
            }
            int index = pickerScroll + (int) ((my - getY()) / ROW_H);
            if (index >= 0 && index < row.options().length && index != current && row.bondNeeded()[index] == 0) {
                current = index;
                keyboardIndex = index;
                onPick.accept(index);
            }
        }

        private void dragBar(double my) {
            double t = Mth.clamp((my - getY()) / height, 0.0, 1.0);
            pickerScroll = (int) Math.round(t * maxScroll());
        }

        @Override protected void onDrag(double mx, double my, double dx, double dy) { if (draggingBar) dragBar(my); }
        @Override public void onRelease(double mx, double my) { draggingBar = false; }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (!active || !isFocused() || row.options().length == 0) return false;
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN
                || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                keyboardIndex = key == GLFW.GLFW_KEY_HOME ? 0 : key == GLFW.GLFW_KEY_END ? row.options().length - 1
                    : Mth.clamp(keyboardIndex + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, row.options().length - 1);
                pickerScroll = Mth.clamp(pickerScroll, Math.max(0, keyboardIndex - visible + 1), keyboardIndex);
                pickerScroll = Mth.clamp(pickerScroll, 0, maxScroll());
                setMessage(Component.literal(row.label() + ": " + row.options()[keyboardIndex]));
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
                if (keyboardIndex != current && row.bondNeeded()[keyboardIndex] == 0) {
                    current = keyboardIndex;
                    onPick.accept(current);
                }
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my) || scrollY == 0) return false;
            pickerScroll = Mth.clamp(pickerScroll - (int) Math.signum(scrollY) * 2, 0, maxScroll());
            return true;
        }

        @Override public void playDownSound(SoundManager sounds) { }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    private static final ResourceLocation LOCK = ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "textures/gui/sketchbook/lock.png");

    /** A small padlock (7 by 9) standing in for the name of an option the dragon is not bonded enough to offer. */
    private static void drawLock(GuiGraphics g, int x, int y, int argb) {
        RenderSystem.enableBlend();
        g.setColor((argb >> 16 & 0xFF) / 255F, (argb >> 8 & 0xFF) / 255F, (argb & 0xFF) / 255F, 1F);
        g.blit(LOCK, x, y, 0, 0, 7, 9, 7, 9);
        g.setColor(1F, 1F, 1F, 1F);
    }

    // --- palette swatches ---------------------------------------------------------------------

    private static final Map<String, int[]> SWATCHES = new HashMap<>();
    private static final int[] NO_SWATCH = new int[0];

    /**
     * The shade ramp of a palette, read from its tiny strip texture (the id is already a texture path, otherwise assets/NS/textures/entity/genetics/ID_PATH.png),
     * so the list shows the real colours. Patterns and anything that cannot be read just get no swatch.
     */
    static int[] swatch(String id) {
        return SWATCHES.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return NO_SWATCH;
            ResourceLocation tex = rl.getPath().endsWith(".png") ? rl
                : ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), "textures/entity/genetics/" + rl.getPath() + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(tex).orElseThrow().open();
                 NativeImage img = NativeImage.read(in)) {
                int n = Math.min(img.getWidth(), 6);
                int[] out = new int[n];
                for (int i = 0; i < n; i++) {
                    int x = n == 1 ? 0 : i * (img.getWidth() - 1) / (n - 1);
                    int abgr = img.getPixelRGBA(x, 0);
                    out[i] = 0xFF000000 | ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
                }
                return out;
            } catch (Exception ex) {
                return NO_SWATCH;
            }
        });
    }

    static void drawSwatch(GuiGraphics g, int[] colors, int x, int y, int cell, int height) {
        if (colors.length == 0) return;
        g.fill(x - 1, y - 1, x + colors.length * cell + 1, y + height + 1, 0xFF101113);
        for (int i = 0; i < colors.length; i++) g.fill(x + i * cell, y, x + (i + 1) * cell, y + height, colors[i]);
    }
}
