package net.bananemdnsa.historystages.client.editor.graph;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Lazily generates and caches anti-aliased shape/edge textures for the dependency graph.
 * Each texture is a white mask whose alpha is a supersampled coverage of the shape, so it
 * can be tinted via the shader colour and scaled smoothly with the zoom — replacing the
 * old row-by-row {@code g.fill} shapes that aliased badly when zoomed.
 */
public final class NodeTextures {

    private NodeTextures() {}

    /** Square resolution of the generated shape textures (high enough to downscale cleanly). */
    public static final int SIZE = 128;
    /**
     * Height of the line texture (width is SIZE).
     *
     * @deprecated The graph draws its edges as solid rotated fills now; a stretched, unfiltered
     *     texture broke up along diagonals and its soft edge rows swallowed thin lines. Kept only
     *     so an external caller does not break — nothing in this mod uses it.
     */
    @Deprecated
    public static final int LINE_H = 16;
    /** Supersample grid per pixel for anti-aliasing (SS x SS samples). */
    private static final int SS = 4;

    private static ResourceLocation circle;
    private static ResourceLocation diamond;
    private static ResourceLocation hexagon;
    private static ResourceLocation rect;
    private static ResourceLocation rounded;
    private static ResourceLocation arrow;
    private static ResourceLocation line;
    private static ResourceLocation check;

    public static ResourceLocation circle()  { ensure(); return circle; }
    public static ResourceLocation diamond() { ensure(); return diamond; }
    public static ResourceLocation hexagon() { ensure(); return hexagon; }
    public static ResourceLocation rect()    { ensure(); return rect; }
    public static ResourceLocation rounded() { ensure(); return rounded; }
    public static ResourceLocation arrow()   { ensure(); return arrow; }
    public static ResourceLocation line()    { ensure(); return line; }
    public static ResourceLocation check()   { ensure(); return check; }

    private static void ensure() {
        if (circle != null) return;
        circle  = register("depgraph_circle",  build(NodeTextures::insideCircle));
        diamond = register("depgraph_diamond", build(NodeTextures::insideDiamond));
        hexagon = register("depgraph_hexagon", build(NodeTextures::insideHexagon));
        rect    = register("depgraph_rect",    build(NodeTextures::insideRect));
        rounded = register("depgraph_rounded", build(NodeTextures::insideRounded));
        arrow   = register("depgraph_arrow",   build(NodeTextures::insideArrow));
        line    = register("depgraph_line",    buildLine(), true);
        check   = register("depgraph_check",   build(NodeTextures::insideCheck));
    }

    private interface Coverage { boolean inside(double fx, double fy); }

    /** Builds a SIZE x SIZE white image whose alpha is the supersampled coverage of {@code shape}. */
    private static NativeImage build(Coverage shape) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        double step = 1.0 / SS;
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                int hits = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double fx = ((px + (sx + 0.5) * step) / SIZE) * 2.0 - 1.0;
                        double fy = ((py + (sy + 0.5) * step) / SIZE) * 2.0 - 1.0;
                        if (shape.inside(fx, fy)) hits++;
                    }
                }
                int a = (int) Math.round(255.0 * hits / (SS * SS));
                img.setPixelRGBA(px, py, (a << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    /** Horizontal line mask: white, full alpha with a ~1px soft taper on the top/bottom rows. */
    private static NativeImage buildLine() {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, LINE_H, false);
        for (int py = 0; py < LINE_H; py++) {
            double cov = Math.min(1.0, Math.min(py + 0.5, LINE_H - (py + 0.5)));
            int a = (int) Math.round(255.0 * cov);
            for (int px = 0; px < SIZE; px++) {
                img.setPixelRGBA(px, py, (a << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    /**
     * Check glyph: two thick strokes unioned into one shape, so the bend and both ends come out
     * as a single continuous outline rather than as separate pieces butted together.
     *
     * <p>Drawn here rather than stroked at render time on purpose. Two rotated line blits meet at
     * a bare corner, and patching that corner with a round cap leaves a bead wider than the
     * stroke at badge sizes. As a coverage function the join is exact and the supersampling in
     * {@link #build} antialiases it like every other shape on the canvas.
     */
    private static boolean insideCheck(double x, double y) {
        final double halfWidth = 0.17;
        return distanceToSegment(x, y, -0.55, 0.02, -0.15, 0.42) <= halfWidth
                || distanceToSegment(x, y, -0.15, 0.42, 0.58, -0.42) <= halfWidth;
    }

    /** Shortest distance from (px,py) to the segment (ax,ay)-(bx,by). */
    private static double distanceToSegment(double px, double py,
                                            double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static ResourceLocation register(String name, NativeImage img) {
        return register(name, img, false);
    }

    /**
     * @param smooth linear filtering instead of nearest. Worth it for anything drawn at a size
     *               that is not a whole multiple of the source — the edge line in particular is
     *               stretched to arbitrary lengths and angles, and nearest sampling turns its
     *               soft border rows into a stair pattern.
     */
    private static ResourceLocation register(String name, NativeImage img, boolean smooth) {
        DynamicTexture tex = new DynamicTexture(img);
        if (smooth) {
            tex.setFilter(true, false);
        }
        ResourceLocation id = new ResourceLocation("historystages", "dyn/" + name);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return id;
    }

    /**
     * Draws a textured quad with <em>floating point</em> corners through the current pose.
     *
     * <p>{@link GuiGraphics#blit} and {@code fill} both take integers, so a line's thickness is
     * rounded to whole pixels before the pose rotates it — which is why a thin diagonal drawn that
     * way either snaps to two pixels or breaks apart entirely. Writing the vertices straight into
     * the buffer keeps sub-pixel sizes exact and lets the rasteriser and the texture filter do the
     * smoothing, which is how FTB Quests draws its dependency lines.
     */
    public static void quad(GuiGraphics g, ResourceLocation tex,
                            float x1, float y1, float x2, float y2, int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        if (a == 0f) a = 1f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float gg = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);

        // 1.20.1 builds the buffer in place: getBuilder()/begin(), vertex().uv().color().endVertex(),
        // end(). 1.21's begin()-returns-a-builder and addVertex()/setUv()/setColor() chain does not
        // exist here.
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(matrix, x1, y2, 0f).uv(0f, 1f).color(r, gg, b, a).endVertex();
        buf.vertex(matrix, x2, y2, 0f).uv(1f, 1f).color(r, gg, b, a).endVertex();
        buf.vertex(matrix, x2, y1, 0f).uv(1f, 0f).color(r, gg, b, a).endVertex();
        buf.vertex(matrix, x1, y1, 0f).uv(0f, 0f).color(r, gg, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /**
     * Blits the whole texture into a {@code w x h} box at (x,y), tinted by ARGB {@code color}.
     * Always restores the shader colour to white so later rendering is not tinted.
     */
    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                            int texW, int texH, int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        if (a == 0f) a = 1f; // colours passed without an alpha byte are fully opaque
        float r = ((color >> 16) & 0xFF) / 255f;
        float gg = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gg, b, a);
        g.blit(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // --- Coverage functions, normalised to [-1, 1] ---

    private static boolean insideCircle(double x, double y) { return x * x + y * y <= 1.0; }

    private static boolean insideDiamond(double x, double y) { return Math.abs(x) + Math.abs(y) <= 1.0; }

    /** Flat-top hexagon (full width at the middle band), matching the old fillHexagon. */
    private static boolean insideHexagon(double x, double y) {
        double h = 0.86602540378; // sin(60 deg) = flat-top half-height
        double ax = Math.abs(x), ay = Math.abs(y);
        return ay <= h && (h * ax + 0.5 * ay) <= h;
    }

    /** Triangle pointing toward +x: tip at (1,0), base spanning x = -1. */
    private static boolean insideArrow(double x, double y) {
        if (x < -1.0 || x > 1.0) return false;
        return Math.abs(y) <= 0.45 * (1.0 - x);
    }

    private static boolean insideRect(double x, double y) { return Math.abs(x) <= 1.0 && Math.abs(y) <= 1.0; }

    /**
     * Relative corner radius baked into the {@code rounded} texture, in the [-1, 1] normalised
     * space (side length 2) — i.e. 0.18 of the side. This is fixed at texture-generation time
     * and cannot track the config's {@code cornerRadius} per node; see {@link NodeShapes#rounded}.
     */
    private static final double ROUNDED_RADIUS = 0.36;

    /** Rounded rect: full coverage away from the corners, a circular arc test near each corner. */
    private static boolean insideRounded(double x, double y) {
        double ax = Math.abs(x), ay = Math.abs(y);
        if (ax <= 1.0 - ROUNDED_RADIUS || ay <= 1.0 - ROUNDED_RADIUS) return true;
        double dx = ax - (1.0 - ROUNDED_RADIUS);
        double dy = ay - (1.0 - ROUNDED_RADIUS);
        return dx * dx + dy * dy <= ROUNDED_RADIUS * ROUNDED_RADIUS;
    }
}
