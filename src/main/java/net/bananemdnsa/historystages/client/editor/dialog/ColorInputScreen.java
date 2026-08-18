package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.client.editor.ConfigEditorScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Picks a {@code #RRGGBB} colour for a config row: a hex field, a preview, a saturation/value
 * area with a hue bar beside it, and the colours already in use elsewhere in {@code graph.toml}.
 *
 * <p>The hex field is still the value that leaves the dialog, but it is no longer the only
 * state. Hue, saturation and value are kept here, because two regions of the colour space are
 * degenerate: at value 0 every hue is black, and at saturation 0 every hue is the same grey.
 * Deriving the hue back out of the RGB each frame — which is what the old channel sliders did —
 * would throw the user's hue away the moment they dragged to the bottom or left edge, and
 * dragging back out would hand them a different colour than the one they left.
 */
public class ColorInputScreen extends AbstractInputScreen {

    private static final int SWATCH_H = 16;
    private static final int PALETTE_H = 14;
    private static final int BLOCK_GAP = 6;

    /** Width of the hue strip and the gap that separates it from the SV area. */
    private static final int HUE_W = 16;
    private static final int HUE_GAP = 8;

    private static final int SV_H_MAX = 120;
    /**
     * Floor for the SV area. Minecraft allows a GUI scale down to a 240px-tall screen, and
     * {@link #CHROME_H} eats most of that; below this the area stops being usable anyway.
     */
    private static final int SV_H_MIN = 60;
    /**
     * Everything in the dialog that is not the SV area — title, hex field, swatch, palette,
     * button row and the paddings between them — plus a few pixels of margin. Used only to
     * decide how much height is left over for the area.
     */
    private static final int CHROME_H = 180;

    /** Enough to show what a pack uses without the row wrapping. */
    private static final int PALETTE_MAX = 12;

    /** The seven stops of the hue wheel, red to red. Six gradients are drawn between them. */
    private static final int[] HUE_STOPS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    /** Which control a drag started on. Latched at mouse-down so a drag cannot change target. */
    private enum DragTarget {NONE, SV, HUE}

    private final String initial;
    private final Consumer<String> onDone;
    private final List<Integer> palette;

    /** Degrees 0..360, and 0..1 for the other two. */
    private float hue, sat, val;

    /**
     * The last string this screen wrote into the hex field. Typed input is recognised by
     * differing from it — without the guard the picker would re-derive from its own output
     * every frame, which is exactly the hue loss the separate state exists to avoid.
     */
    private String lastWritten;

    private DragTarget dragTarget = DragTarget.NONE;

    /**
     * Geometry of the extra content, captured while drawing so the input handlers can hit-test
     * against exactly what is on screen. The base class hands these coordinates to
     * {@link #renderExtraContent} only, and a click can never arrive before the first frame.
     */
    private int extraX, extraY, extraW;

    public ColorInputScreen(Screen parent, ConfigEditorScreen.ConfigEntry entry) {
        this(parent, Component.translatable(entry.labelKey), entry.value,
                picked -> entry.value = picked);
    }

    public ColorInputScreen(Screen parent, Component title, String initial,
                            Consumer<String> onDone) {
        super(parent, title);
        this.initial = initial == null ? "" : initial;
        this.onDone = onDone;
        this.palette = collectPalette();
        this.lastWritten = this.initial;
        setHsvFromRgb(GraphColors.parse(this.initial, 0));
    }

    /** Distinct valid colours currently set anywhere in graph.toml, in declaration order. */
    private static List<Integer> collectPalette() {
        Set<Integer> seen = new LinkedHashSet<>();
        for (String value : GraphConfigCodec.collect().values()) {
            if (GraphColors.isValid(value)) seen.add(GraphColors.parse(value, 0));
            if (seen.size() >= PALETTE_MAX) break;
        }
        return new ArrayList<>(seen);
    }

    @Override
    protected int dialogWidth() {
        // Wider than the default 280: the hue bar takes a fixed slice off the content width,
        // and the SV area needs what is left to stay comfortably wider than it is tall.
        return 300;
    }

    @Override
    protected List<InputField> fields() {
        return List.of(InputField.text("hex")
                .label(Component.translatable("editor.historystages.color.hex"))
                .maxLength(7)
                // Partial input has to be typeable, so the filter accepts any prefix; whether it
                // is a complete colour is the validator's job.
                .regex("#?[0-9a-fA-F]{0,6}")
                .validator(v -> GraphColors.isValid(v)
                        ? null
                        : Component.translatable("editor.historystages.color.invalid"))
                .initial(this.initial));
    }

    /**
     * Height of the SV area, sized to the window so the dialog never grows taller than the
     * screen. Safe to call from {@link #extraContentHeight}: {@code init()} runs after
     * {@code this.height} is set, and the value depends on nothing else, so the height reserved
     * during layout is the same one {@code renderContent} advances past.
     */
    private int svHeight() {
        return Math.max(SV_H_MIN, Math.min(SV_H_MAX, this.height - CHROME_H));
    }

    @Override
    protected int extraContentHeight() {
        return SWATCH_H + BLOCK_GAP + svHeight() + BLOCK_GAP + PALETTE_H;
    }

    // ============ Colour conversion ============

    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    /** @return 0xRRGGBB for the given hue in degrees and 0..1 saturation and value. */
    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hh = (h % 360.0f + 360.0f) % 360.0f / 60.0f;
        float x = c * (1 - Math.abs(hh % 2 - 1));
        float r, g, b;
        switch ((int) hh) {
            case 0 -> {r = c; g = x; b = 0;}
            case 1 -> {r = x; g = c; b = 0;}
            case 2 -> {r = 0; g = c; b = x;}
            case 3 -> {r = 0; g = x; b = c;}
            case 4 -> {r = x; g = 0; b = c;}
            default -> {r = c; g = 0; b = x;}
        }
        float m = v - c;
        return (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    /**
     * Re-derives the picker's state from an RGB colour, keeping whatever the RGB cannot say.
     * A grey carries no hue and black carries neither hue nor saturation, so those are left as
     * they were rather than collapsed to zero — otherwise typing {@code #000000} would discard
     * the colour the user was working towards.
     */
    private void setHsvFromRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        if (delta > 0.0f) {
            if (max == r) {
                hue = 60.0f * (((g - b) / delta % 6.0f + 6.0f) % 6.0f);
            } else if (max == g) {
                hue = 60.0f * ((b - r) / delta + 2.0f);
            } else {
                hue = 60.0f * ((r - g) / delta + 4.0f);
            }
        }
        if (max > 0.0f) sat = delta / max;
        val = max;
    }

    private int currentRgb() {
        return hsvToRgb(hue, sat, val);
    }

    /** Pushes the picker's state into the hex field, which is what actually gets saved. */
    private void writeField() {
        lastWritten = GraphColors.format(currentRgb());
        box(0).setValue(lastWritten);
    }

    /**
     * Pulls typed input back into the picker. Ignores the field while it holds what this screen
     * last wrote, and while it holds a half-typed colour — the area simply keeps its last state
     * rather than blanking out mid-keystroke.
     */
    private void syncFromField() {
        String value = box(0).getValue();
        if (value.equals(lastWritten)) return;
        lastWritten = value;
        if (GraphColors.isValid(value)) setHsvFromRgb(GraphColors.parse(value, 0));
    }

    // ============ Geometry ============

    private int svX() {
        return extraX;
    }

    private int svY() {
        return extraY + SWATCH_H + BLOCK_GAP;
    }

    private int svW() {
        return extraW - HUE_W - HUE_GAP;
    }

    private int hueX() {
        return extraX + svW() + HUE_GAP;
    }

    private int paletteY() {
        return svY() + svHeight() + BLOCK_GAP;
    }

    // ============ Rendering ============

    @Override
    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        this.extraX = x;
        this.extraY = y;
        this.extraW = w;

        syncFromField();

        int rgb = currentRgb();
        int svW = svW();
        int svH = svHeight();
        int svY = svY();

        // Preview
        g.fill(x - 1, y - 1, x + w + 1, y + SWATCH_H + 1, FIELD_BORDER);
        g.fill(x, y, x + w, y + SWATCH_H, 0xFF000000 | rgb);

        // SV area. fillGradient only interpolates vertically, so the horizontal white-to-hue
        // ramp is built one column at a time and each column fades to black on its own.
        g.fill(x - 1, svY - 1, x + svW + 1, svY + svH + 1, FIELD_BORDER);
        for (int i = 0; i < svW; i++) {
            float s = svW <= 1 ? 0.0f : (float) i / (svW - 1);
            g.fillGradient(x + i, svY, x + i + 1, svY + svH,
                    0xFF000000 | hsvToRgb(hue, s, 1.0f), 0xFF000000);
        }
        drawRing(g, x + Math.round(sat * (svW - 1)), svY + Math.round((1 - val) * (svH - 1)));

        // Hue strip, six gradients between the seven stops
        int hx = hueX();
        g.fill(hx - 1, svY - 1, hx + HUE_W + 1, svY + svH + 1, FIELD_BORDER);
        for (int i = 0; i < 6; i++) {
            g.fillGradient(hx, svY + i * svH / 6, hx + HUE_W, svY + (i + 1) * svH / 6,
                    HUE_STOPS[i], HUE_STOPS[i + 1]);
        }
        int handleY = svY + Math.round(hue / 360.0f * (svH - 1));
        g.fill(hx - 2, handleY - 2, hx + HUE_W + 2, handleY + 3, 0xFF000000);
        g.fill(hx - 2, handleY - 1, hx + HUE_W + 2, handleY + 2, 0xFFFFFFFF);

        // Colours already used in graph.toml
        int py = paletteY();
        for (int i = 0; i < palette.size(); i++) {
            int px = x + i * (PALETTE_H + 2);
            if (px + PALETTE_H > x + w) break;
            g.fill(px - 1, py - 1, px + PALETTE_H + 1, py + PALETTE_H + 1, FIELD_BORDER);
            g.fill(px, py, px + PALETTE_H, py + PALETTE_H, 0xFF000000 | palette.get(i));
        }
    }

    /** White ring inside a black one, so the cursor reads against any colour underneath it. */
    private static void drawRing(GuiGraphics g, int cx, int cy) {
        drawSquareOutline(g, cx, cy, 4, 0xFF000000);
        drawSquareOutline(g, cx, cy, 3, 0xFFFFFFFF);
    }

    /** Hollow square of half-width {@code r} centred on {@code cx, cy}. */
    private static void drawSquareOutline(GuiGraphics g, int cx, int cy, int r, int argb) {
        g.fill(cx - r, cy - r, cx + r + 1, cy - r + 1, argb);
        g.fill(cx - r, cy + r, cx + r + 1, cy + r + 1, argb);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r + 1, argb);
        g.fill(cx + r, cy - r, cx + r + 1, cy + r + 1, argb);
    }

    // ============ Input ============

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        int py = paletteY();
        if (my >= py && my < py + PALETTE_H) {
            for (int i = 0; i < palette.size(); i++) {
                int px = extraX + i * (PALETTE_H + 2);
                if (mx >= px && mx < px + PALETTE_H) {
                    setHsvFromRgb(palette.get(i));
                    writeField();
                    return true;
                }
            }
        }

        int svY = svY();
        int svH = svHeight();
        if (my < svY || my >= svY + svH) return false;

        if (mx >= svX() && mx < svX() + svW()) {
            dragTarget = DragTarget.SV;
        } else if (mx >= hueX() && mx < hueX() + HUE_W) {
            dragTarget = DragTarget.HUE;
        } else {
            return false;
        }
        applyDrag(mx, my);
        return true;
    }

    @Override
    protected boolean extraContentMouseDragged(double mx, double my, int button) {
        if (button != 0 || dragTarget == DragTarget.NONE) return false;
        applyDrag(mx, my);
        return true;
    }

    @Override
    protected boolean extraContentMouseReleased(double mx, double my, int button) {
        if (dragTarget == DragTarget.NONE) return false;
        dragTarget = DragTarget.NONE;
        return true;
    }

    /**
     * Writes the cursor position into whichever control the drag was started on, clamped to
     * that control's bounds. Deliberately does not re-test what is under the cursor: the SV
     * area and the hue strip sit side by side, and dragging past the area's right edge would
     * otherwise start changing the hue.
     */
    private void applyDrag(double mx, double my) {
        int svY = svY();
        int svH = svHeight();
        if (dragTarget == DragTarget.SV) {
            int svW = svW();
            sat = svW <= 1 ? 0.0f : clamp01((mx - svX()) / (svW - 1));
            val = svH <= 1 ? 1.0f : 1.0f - clamp01((my - svY) / (svH - 1));
        } else {
            hue = svH <= 1 ? 0.0f : clamp01((my - svY) / (svH - 1)) * 360.0f;
        }
        writeField();
    }

    @Override
    protected void onConfirm(InputValues values) {
        // Normalised on the way out, so "44cc99" and "#44CC99" cannot both end up in graph.toml.
        onDone.accept(GraphColors.format(GraphColors.parse(values.getString("hex"), 0)));
        this.minecraft.setScreen(parent);
    }
}
