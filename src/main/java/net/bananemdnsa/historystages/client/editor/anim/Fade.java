package net.bananemdnsa.historystages.client.editor.anim;

/**
 * ARGB helpers for animating colours, so fades and hover blends are written the same way
 * everywhere instead of being open-coded with shifts at each call site.
 */
public final class Fade {

    private Fade() {
    }

    /** Scales only the alpha channel of {@code argb} by {@code factor} (clamped to 0..1). */
    public static int alpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Ease.clamp01(factor));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Builds an ARGB colour from an opaque {@code rgb} and a 0..1 opacity. */
    public static int rgba(int rgb, float opacity) {
        int a = Math.round(0xFF * Ease.clamp01(opacity));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Blends two ARGB colours channel by channel. Used for hover transitions, where the
     * resting and hovered colours are both design decisions and the frames between them
     * should not be.
     */
    public static int mix(int from, int to, float t) {
        float f = Ease.clamp01(t);
        int a = blendChannel(from, to, f, 24);
        int r = blendChannel(from, to, f, 16);
        int g = blendChannel(from, to, f, 8);
        int b = blendChannel(from, to, f, 0);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(int from, int to, float t, int shift) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return Math.round(a + (b - a) * t) & 0xFF;
    }

    /** Opaque grey, for the many labels that only vary in brightness between states. */
    public static int grey(int level) {
        int v = Math.max(0, Math.min(0xFF, level));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }
}
