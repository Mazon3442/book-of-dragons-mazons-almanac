package local.bodaddon.client;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import local.bodaddon.BodAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Renders the two faces of a turning sheet, with one temporary texture for both. */
final class SketchbookPageTurn implements AutoCloseable {
    @FunctionalInterface
    interface Face {
        void draw(GuiGraphics graphics, int x, int y, int targetHeight);
    }

    private static final int COLUMNS = 24;
    private static final int ROWS = 4;
    private static boolean meshUnavailable;

    private final int width;
    private final int height;
    private TextureTarget target;
    private int textureScale;
    private boolean captured;

    SketchbookPageTurn(int width, int height) {
        this.width = width;
        this.height = height;
    }

    void invalidate() { captured = false; }

    void draw(GuiGraphics graphics, int spine, int top, float progress, Face front, Face back) {
        graphics.flush();
        if (ConfigClient.pageFlip() == ConfigClient.PageFlip.MESH && !meshUnavailable) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLState state = new GLState(stack);
                try {
                    if (!captured) capture(front, back);
                    state.restoreFramebuffer();
                    mesh(graphics.pose().last().pose(), spine, top, progress);
                    return;
                } finally {
                    state.restore();
                }
            } catch (RuntimeException | LinkageError err) {
                meshUnavailable = true;
                close();
                BodAddon.LOGGER.warn("Sketchbook page mesh failed, switching to the simple turn", err);
            }
        }
        simple(graphics, spine, top, progress, front, back);
    }

    void shadow(GuiGraphics graphics, int spine, int top, float progress) {
        float lift = Mth.sin(progress * Mth.PI);
        int edge = Math.max(1, Math.round(width * (1 - lift * 0.52F)));
        int direction = progress < 0.5F ? 1 : -1;
        int alpha = (int) (56 * lift);
        for (int i = 0; i < 6; i++) {
            int x = spine + direction * (edge - i * 4);
            graphics.fill(Math.min(x, x + direction * 4), top + 4, Math.max(x, x + direction * 4), top + height - 4,
                (alpha * (6 - i) / 6 << 24) | 0x452913);
        }
    }

    private void simple(GuiGraphics graphics, int spine, int top, float progress, Face front, Face back) {
        float projected = Mth.cos(progress * Mth.PI);
        if (Math.abs(projected) < 0.01F) return;
        graphics.pose().pushPose();
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            RenderSystem.disableCull();
            graphics.pose().translate(spine + Math.min(0, projected) * width, top, 0);
            graphics.pose().scale(Math.abs(projected), 1, 1);
            (projected > 0 ? front : back).draw(graphics, 0, 0, 0);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            if (cull) RenderSystem.enableCull();
        }
    }

    private void capture(Face front, Face back) {
        Minecraft mc = Minecraft.getInstance();
        int scale = Math.max(1, (int) mc.getWindow().getGuiScale());
        if (target == null || textureScale != scale) {
            close();
            target = new TextureTarget(2 * width * scale, height * scale, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            textureScale = scale;
        }
        target.bindWrite(true);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete sketchbook texture target");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            RenderSystem.depthMask(true);
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, stack.floats(0, 0, 0, 0));
            GL30.glClearBufferfv(GL11.GL_DEPTH, 0, stack.floats(1));
        }
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 2 * width, height, 0, 1000,
            ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        front.draw(graphics, 0, 0, target.height);
        back.draw(graphics, width, 0, target.height);
        graphics.flush();
        captured = true;
    }

    private void mesh(Matrix4f matrix, int spine, int top, float progress) {
        float rotation = progress * Mth.PI;
        float lift = Mth.sin(rotation);
        float[] xs = new float[COLUMNS + 1];
        float[] zs = new float[COLUMNS + 1];
        float[] angles = new float[COLUMNS];
        float strip = width / (float) COLUMNS;
        for (int column = 0; column < COLUMNS; column++) {
            float u = (column + 0.5F) / COLUMNS;
            float curl = Mth.clamp((u - (1 - progress) * 0.78F) / 0.55F, 0, 1);
            angles[column] = rotation + 0.8F * lift * curl * curl;
            xs[column + 1] = xs[column] + strip * Mth.cos(angles[column]);
            zs[column + 1] = zs[column] + strip * Mth.sin(angles[column]);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int column = 0; column < COLUMNS; column++) {
            boolean rear = Mth.cos(angles[column]) < 0;
            int brightness = (int) (255 - lift * (54 * (1 - Math.abs(Mth.cos(angles[column]))) + (rear ? 16 : 0)));
            for (int row = 0; row < ROWS; row++) {
                vertex(buffer, matrix, spine, top, xs, zs, column, row, lift, rear, brightness);
                vertex(buffer, matrix, spine, top, xs, zs, column, row + 1, lift, rear, brightness);
                vertex(buffer, matrix, spine, top, xs, zs, column + 1, row + 1, lift, rear, brightness);
                vertex(buffer, matrix, spine, top, xs, zs, column + 1, row, lift, rear, brightness);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private void vertex(BufferBuilder buffer, Matrix4f matrix, int spine, int top, float[] xs, float[] zs,
                        int column, int row, float lift, boolean rear, int brightness) {
        float u = column / (float) COLUMNS;
        float v = row / (float) ROWS;
        float bow = lift * u * u * (9 * (1 - v) * (1 - v) + 5 * Mth.sin(v * Mth.PI));
        float perspective = 420 / (420 - zs[column] - bow);
        float x = spine + xs[column] * perspective;
        float y = top + height / 2.0F + ((v - 0.5F) * height - bow * 0.25F) * perspective;
        float inset = 0.5F / target.width;
        float textureU = rear ? 0.5F + inset + (1 - u) * (0.5F - 2 * inset) : inset + u * (0.5F - 2 * inset);
        buffer.addVertex(matrix, x, y, 0).setUv(textureU, 1 - v).setColor(brightness, brightness, brightness, 255);
    }

    @Override
    public void close() {
        captured = false;
        if (target != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLState state = new GLState(stack);
                try {
                    target.destroyBuffers();
                } finally {
                    target = null;
                    state.restore();
                }
            }
        }
    }

    private static final class GLState {
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int texture = RenderSystem.getShaderTexture(0);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private final net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
        private final IntBuffer viewport;
        private final FloatBuffer clearColor;

        GLState(MemoryStack stack) {
            viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            clearColor = stack.mallocFloat(4);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        }

        void restoreFramebuffer() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            RenderSystem.setProjectionMatrix(projection, sorting);
        }

        void restore() {
            restoreFramebuffer();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.bindTexture(textureBinding);
            RenderSystem.setShader(() -> shader);
        }
    }
}
