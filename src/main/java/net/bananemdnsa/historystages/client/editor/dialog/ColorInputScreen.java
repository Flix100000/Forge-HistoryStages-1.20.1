package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.client.editor.ConfigEditorScreen;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
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
 * area with a hue bar beside it, the colours already in use elsewhere in {@code graph.toml}, and
 * two fixed swatches — the colour the row held when the dialog opened, and the built-in default.
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

    /** Gap between a fixed swatch and its label, and between one labelled pair and the next. */
    private static final int FIXED_LABEL_GAP = 4;
    private static final int FIXED_PAIR_GAP = 12;

    /** Width of the hue strip and the gap that separates it from the SV area. */
    private static final int HUE_W = 16;
    private static final int HUE_GAP = 8;

    private static final int SV_H_MAX = 120;
    /**
     * Floor for the SV area. Minecraft allows a GUI scale down to a 240px-tall screen, and
     * {@link #CHROME_H} eats most of that; below this the area stops being usable anyway.
     */
    private static final int SV_H_MIN = 44;
    /**
     * Everything in the dialog that is not the SV area, used only to decide how much height is
     * left over for the area: title 20, padding 10, error balance 10, hex label 10 and field 20,
     * field gap 8, preview 16, palette 14, fixed swatches 14, three block gaps 18, error line 12,
     * padding 10, buttons 20, padding 10.
     */
    private static final int CHROME_H = 192;
    /** Kept clear above and below the dialog, so it never sits flush against the screen edge. */
    private static final int SCREEN_MARGIN = 4;

    /** Enough to show what a pack uses without the row wrapping. */
    private static final int PALETTE_MAX = 12;

    /** The seven stops of the hue wheel, red to red. Six gradients are drawn between them. */
    private static final int[] HUE_STOPS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    /** Which control a drag started on. Latched at mouse-down so a drag cannot change target. */
    private enum DragTarget {NONE, SV, HUE}

    private final String initial;
    private final String defaultValue;
    private final Consumer<String> onDone;
    private final List<Integer> palette;

    /**
     * The two fixed swatches as {@code {x, y, rgb}}, rebuilt every frame. Their widths depend on
     * translated label text, so laying them out twice — once to draw, once to hit-test — would
     * be two places to keep in step; the render pass records what it drew instead.
     */
    private final List<int[]> fixedRects = new ArrayList<>();

    /** Hover state per clickable swatch: one for each palette entry, plus the two fixed ones. */
    private final List<Anim> paletteHover = new ArrayList<>();
    private final Anim[] fixedHover = {new Anim(), new Anim()};

    /**
     * Cross-fade of the preview bar. {@code previewShown} is what was actually on screen last
     * frame, so a fade interrupted by another click continues from the colour the user can see
     * rather than snapping back to where the interrupted one started.
     */
    private int previewFrom;
    private int previewShown;
    private final Anim previewFade = new Anim(1.0f);

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
        this(parent, Component.translatable(entry.labelKey), entry.value, entry.defaultValue,
                picked -> entry.value = picked);
    }

    /**
     * @param defaultValue the colour a reset would restore, offered as a fixed swatch. May be
     *                     null or unparseable, in which case that swatch is simply not drawn.
     */
    public ColorInputScreen(Screen parent, Component title, String initial, String defaultValue,
                            Consumer<String> onDone) {
        super(parent, title);
        this.initial = initial == null ? "" : initial;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.onDone = onDone;
        this.palette = collectPalette();
        this.lastWritten = this.initial;
        setHsvFromRgb(GraphColors.parse(this.initial, 0));
        for (int i = 0; i < palette.size(); i++) paletteHover.add(new Anim());
        this.previewFrom = currentRgb();
        this.previewShown = this.previewFrom;
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
        return Math.max(SV_H_MIN,
                Math.min(SV_H_MAX, this.height - CHROME_H - SCREEN_MARGIN * 2));
    }

    @Override
    protected int extraContentHeight() {
        return SWATCH_H + BLOCK_GAP + svHeight() + BLOCK_GAP + PALETTE_H + BLOCK_GAP + PALETTE_H;
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
        if (!GraphColors.isValid(value)) return;
        setHsvFromRgb(GraphColors.parse(value, 0));
        beginPreviewFade();
    }

    /**
     * Starts the preview bar fading towards the new colour. Only for changes that jump — a
     * swatch click or typed input. A drag deliberately does not call this: the preview has to
     * track the cursor exactly, and restarting a fade every frame would just make it lag behind.
     */
    private void beginPreviewFade() {
        previewFrom = previewShown;
        previewFade.set(0.0f);
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

    private int fixedY() {
        return paletteY() + PALETTE_H + BLOCK_GAP;
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

        // Preview, easing towards the picked colour after a jump and pinned to it during a drag
        float fade = Ease.outCubic(previewFade.ramp(1.0f, Timing.REVEAL_MS));
        previewShown = Fade.mix(0xFF000000 | previewFrom, 0xFF000000 | rgb, fade) & 0xFFFFFF;
        g.fill(x - 1, y - 1, x + w + 1, y + SWATCH_H + 1, FIELD_BORDER);
        g.fill(x, y, x + w, y + SWATCH_H, 0xFF000000 | previewShown);

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
            drawSwatch(g, px, py, palette.get(i), paletteHover.get(i), mouseX, mouseY);
        }

        // The two fixed swatches: what the row held when this dialog opened, and what a reset
        // would restore. Unlike the palette above they do not depend on the rest of graph.toml,
        // so there is always a way back from an experiment.
        fixedRects.clear();
        int fx = x;
        fx = renderFixed(g, fx, "previous", this.initial, fixedHover[0], mouseX, mouseY);
        renderFixed(g, fx, "default", this.defaultValue, fixedHover[1], mouseX, mouseY);
    }

    /**
     * Draws one labelled fixed swatch and records its hit box.
     *
     * @return the x the next swatch starts at; unchanged when the colour was unusable and
     *         nothing was drawn, so a missing default leaves no gap.
     */
    private int renderFixed(GuiGraphics g, int x, String labelSuffix, String value,
                            Anim hover, int mouseX, int mouseY) {
        if (!GraphColors.isValid(value)) return x;

        int py = fixedY();
        int rgb = GraphColors.parse(value, 0);
        float h = drawSwatch(g, x, py, rgb, hover, mouseX, mouseY);
        fixedRects.add(new int[]{x, py, rgb});

        Component label = Component.translatable("editor.historystages.color." + labelSuffix);
        int textX = x + PALETTE_H + FIXED_LABEL_GAP;
        // The label brightens with its swatch, so the pair reads as one control.
        g.drawString(this.font, label, textX, py + 3,
                Fade.mix(0xFF000000 | LABEL_GREY, 0xFFFFFFFF, h), false);
        return textX + this.font.width(label) + FIXED_PAIR_GAP;
    }

    /**
     * Draws a clickable colour swatch with its hover state: the border warms towards the
     * editor's gold and thickens by a pixel.
     *
     * <p>Only the border grows. The filled square keeps its size, so the hit box the click
     * handler tests against is the one the swatch has always had — a control that moves under
     * the cursor as it is hovered is a control that can be missed.
     *
     * @return the eased hover progress, for callers that tie something else to the same state
     */
    private static float drawSwatch(GuiGraphics g, int x, int y, int rgb, Anim hover,
                                    int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + PALETTE_H
                && mouseY >= y && mouseY < y + PALETTE_H;
        float h = Ease.outCubic(hover.ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        int ring = 1 + Math.round(h);
        g.fill(x - ring, y - ring, x + PALETTE_H + ring, y + PALETTE_H + ring,
                Fade.mix(FIELD_BORDER, ACCENT_GOLD, h));
        g.fill(x, y, x + PALETTE_H, y + PALETTE_H, 0xFF000000 | rgb);
        return h;
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
                    beginPreviewFade();
                    return true;
                }
            }
        }

        for (int[] rect : fixedRects) {
            if (mx >= rect[0] && mx < rect[0] + PALETTE_H
                    && my >= rect[1] && my < rect[1] + PALETTE_H) {
                setHsvFromRgb(rect[2]);
                writeField();
                beginPreviewFade();
                return true;
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
        // Cancels any fade still running from a swatch click, so the preview snaps to the
        // cursor for the rest of the drag instead of trailing it.
        previewFade.set(1.0f);
        writeField();
    }

    @Override
    protected void onConfirm(InputValues values) {
        // Normalised on the way out, so "44cc99" and "#44CC99" cannot both end up in graph.toml.
        onDone.accept(GraphColors.format(GraphColors.parse(values.getString("hex"), 0)));
        this.minecraft.setScreen(parent);
    }
}
