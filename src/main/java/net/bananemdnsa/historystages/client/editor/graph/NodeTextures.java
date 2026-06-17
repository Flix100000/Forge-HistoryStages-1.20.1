package net.bananemdnsa.historystages.client.editor.graph;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

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
    /** Height of the line texture (width is SIZE). */
    public static final int LINE_H = 16;
    /** Supersample grid per pixel for anti-aliasing (SS x SS samples). */
    private static final int SS = 4;

    private static ResourceLocation circle;
    private static ResourceLocation diamond;
    private static ResourceLocation hexagon;
    private static ResourceLocation arrow;
    private static ResourceLocation line;

    public static ResourceLocation circle()  { ensure(); return circle; }
    public static ResourceLocation diamond() { ensure(); return diamond; }
    public static ResourceLocation hexagon() { ensure(); return hexagon; }
    public static ResourceLocation arrow()   { ensure(); return arrow; }
    public static ResourceLocation line()    { ensure(); return line; }

    private static void ensure() {
        if (circle != null) return;
        circle  = register("depgraph_circle",  build(NodeTextures::insideCircle));
        diamond = register("depgraph_diamond", build(NodeTextures::insideDiamond));
        hexagon = register("depgraph_hexagon", build(NodeTextures::insideHexagon));
        arrow   = register("depgraph_arrow",   build(NodeTextures::insideArrow));
        line    = register("depgraph_line",    buildLine());
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

    private static ResourceLocation register(String name, NativeImage img) {
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("historystages", "dyn/" + name);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return id;
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
}
