package local.bodaddon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import local.bodaddon.BodAddon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Hand-made page decoration for the sketchbook: paint dabs behind the colour names and small pencil doodles.
 * <p>
 * A dab texture is one dab shape cut into {@link #BANDS} brightness bands laid side by side (darkest first). Each band
 * is drawn tinted with a colour from the dragon's palette, so one shape can show any coat and keeps its painted shading.
 */
final class SketchbookDecor {
    static final int DAB_W = 18;
    static final int DAB_H = 11;
    private static final int BANDS = 5;
    private static final float DOODLE_ALPHA = 0.85F;
    private static final float DOODLE_DARKEN = 0.8F;
    private static final int SHEET_W = 116, SHEET_H = 145, ROW_X = 9;
    private static final int SHAPES = 4;
    private static final ResourceLocation[] DABS = new ResourceLocation[SHAPES];
    /** The nine pencil doodles at 72% of the size they were drawn, scattered behind the page content. */
    private static final Doodle[] DOODLES = {
        doodle(1, 27, 25), doodle(2, 23, 28), doodle(3, 22, 28), doodle(4, 32, 11), doodle(5, 30, 21),
        doodle(6, 28, 17), doodle(7, 24, 17), doodle(8, 24, 24), doodle(9, 17, 24)
    };
    /** Where doodles must not sit (x0, y0, x1, y1), in book pixels for the left page: lettering, dragon card, arrow. */
    private static final int[][] LEFT_ZONES = {{36, 12, 110, 49}, {15, 49, 124, 130}, {13, 138, 43, 154}};
    /** The same for a right-hand sheet, in sheet pixels: heading and the next-page arrow. */
    private static final int[][] RIGHT_ZONES = {{5, 10, 69, 26}, {86, 128, 113, 146}};
    /** How far a doodle may reach into a forbidden zone. */
    private static final int OVERLAP = 5;
    private static final int TRIES = 24;

    static {
        for (int i = 0; i < SHAPES; i++) DABS[i] = art("dab_" + i);
    }

    private static Doodle doodle(int n, int width, int height) {
        return new Doodle(art("doodle_" + n), width, height);
    }

    record Doodle(ResourceLocation texture, int width, int height) {}

    private static ResourceLocation art(String file) {
        return ResourceLocation.fromNamespaceAndPath(BodAddon.MOD_ID, "textures/gui/sketchbook/decor/" + file + ".png");
    }

    /** A paint dab in the given palette (any length, ARGB). Does nothing for an empty palette. */
    static void drawDab(GuiGraphics g, int[] palette, int x, int y, int shape) {
        if (palette.length == 0) return;
        int[] ramp = palette.clone();
        for (int i = 1; i < ramp.length; i++) { // palettes are a handful of colours: insertion sort, no allocation churn
            int value = ramp[i];
            int j = i - 1;
            for (; j >= 0 && luma(ramp[j]) > luma(value); j--) ramp[j + 1] = ramp[j];
            ramp[j + 1] = value;
        }
        withBlend(() -> {
            ResourceLocation texture = DABS[Math.floorMod(shape, SHAPES)];
            for (int band = 0; band < BANDS; band++) {
                int color = ramp[Math.round(band * (ramp.length - 1) / (float) (BANDS - 1))];
                g.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
                g.blit(texture, x, y, band * DAB_W, 0, DAB_W, DAB_H, DAB_W * BANDS, DAB_H);
            }
            g.setColor(1F, 1F, 1F, 1F);
        });
    }

    /** A pencil doodle, a shade darker than drawn so it stays clear when text or a paint dab sits over it. */
    static void drawDoodle(GuiGraphics g, Doodle doodle, int x, int y) {
        withBlend(() -> {
            g.setColor(DOODLE_DARKEN, DOODLE_DARKEN, DOODLE_DARKEN, DOODLE_ALPHA);
            g.blit(doodle.texture(), x, y, 0, 0, doodle.width(), doodle.height(), doodle.width(), doodle.height());
            g.setColor(1F, 1F, 1F, 1F);
        });
    }

    /**
     * Doodle for slot {@code slot} of spread {@code spread}. Each spread uses five consecutive doodles from the nine,
     * stepping on by five, so a spread never repeats one and all nine are used.
     */
    private static Doodle pick(int spread, int slot) {
        return DOODLES[Math.floorMod(spread * 5 + slot, DOODLES.length)];
    }

    /** Stable pseudo-random number in [0, bound) for a spread, slot and axis: the same page always looks the same. */
    private static int scatter(int spread, int slot, int axis, int bound) {
        int h = spread * 0x9E3779B1 + slot * 0x85EBCA6B + axis * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return Math.floorMod(h, Math.max(1, bound));
    }

    private static boolean clear(Doodle d, int x, int y, int[][] zones) {
        for (int[] z : zones) {
            if (x < z[2] - OVERLAP && x + d.width() > z[0] + OVERLAP && y < z[3] - OVERLAP && y + d.height() > z[1] + OVERLAP) return false;
        }
        return true;
    }

    /**
     * Scatters {@code count} doodles over a page. Each takes the first of a fixed run of pseudo-random spots that stays
     * off the forbidden zones (a little overlap is fine) and on the page; a doodle with no spot is skipped.
     * Call before the text so the doodles sit behind it.
     */
    private static void scatterDoodles(GuiGraphics g, int spread, int firstSlot, int count, int ox, int oy,
                                       int px, int py, int pw, int ph, int[][] zones) {
        for (int slot = firstSlot; slot < firstSlot + count; slot++) {
            Doodle doodle = pick(spread, slot);
            for (int attempt = 0; attempt < TRIES; attempt++) {
                int x = px + scatter(spread, slot, attempt * 2, pw - doodle.width() + 1);
                int y = py + scatter(spread, slot, attempt * 2 + 1, ph - doodle.height() + 1);
                if (clear(doodle, x, y, zones)) {
                    drawDoodle(g, doodle, ox + x, oy + y);
                    break;
                }
            }
        }
    }

    /** Right-hand sheet (origin at its top-left corner): one doodle, behind the rows. */
    static void drawPageDoodles(GuiGraphics g, int x, int y, int page) {
        scatterDoodles(g, page, 2, 1, x, y, 5, 6, SHEET_W - 10, SHEET_H - 12, RIGHT_ZONES);
    }

    /** Left portrait page (origin at the book's top-left corner): one doodle, mostly in the strip under the card. */
    static void drawPortraitDoodles(GuiGraphics g, int bookX, int bookY, int page) {
        scatterDoodles(g, page, 0, 1, bookX, bookY, 15, 12, 110, 138, LEFT_ZONES);
    }

    private static double luma(int argb) {
        return 0.299 * (argb >> 16 & 0xFF) + 0.587 * (argb >> 8 & 0xFF) + 0.114 * (argb & 0xFF);
    }

    private static void withBlend(Runnable draw) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        try {
            draw.run();
        } finally {
            if (!blend) RenderSystem.disableBlend();
        }
    }

    private SketchbookDecor() {}
}
