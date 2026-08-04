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
 * Picks a {@code #RRGGBB} colour for a config row: a hex field, a preview, one slider per
 * channel, and the colours already in use elsewhere in {@code graph.toml}.
 *
 * <p>The hex field is the value; the sliders and the palette only write into it. Keeping one
 * source of truth is what stops the two halves disagreeing after a drag — and it means the
 * dialog's validation is the field's validation, with nothing to duplicate.
 */
public class ColorInputScreen extends AbstractInputScreen {

    private static final int SWATCH_H = 24;
    private static final int SLIDER_H = 10;
    private static final int SLIDER_GAP = 4;
    private static final int PALETTE_H = 14;
    private static final int BLOCK_GAP = 6;
    /** Width of the draggable handle; the track is shortened by it so the handle never leaves. */
    private static final int HANDLE_W = 6;
    /** Enough to show what a pack uses without the row wrapping. */
    private static final int PALETTE_MAX = 12;

    private static final int[] CHANNEL_TINT = {0xFFCC5555, 0xFF55CC55, 0xFF5577CC};

    private final String initial;
    private final Consumer<String> onDone;
    private final List<Integer> palette;

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

    @Override
    protected int extraContentHeight() {
        return SWATCH_H + BLOCK_GAP
                + SLIDER_H * 3 + SLIDER_GAP * 2 + BLOCK_GAP
                + PALETTE_H;
    }

    /** The colour the field currently spells out, or black while it is incomplete. */
    private int currentRgb() {
        return GraphColors.parse(box(0).getValue(), 0);
    }

    private void setRgb(int rgb) {
        box(0).setValue(GraphColors.format(rgb));
    }

    private int sliderY(int channel) {
        return extraY + SWATCH_H + BLOCK_GAP + channel * (SLIDER_H + SLIDER_GAP);
    }

    private int paletteY() {
        return extraY + SWATCH_H + BLOCK_GAP + SLIDER_H * 3 + SLIDER_GAP * 2 + BLOCK_GAP;
    }

    /** Shift of the channel within an 0xRRGGBB int: 0 = red, 1 = green, 2 = blue. */
    private static int shift(int channel) {
        return 16 - channel * 8;
    }

    @Override
    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        this.extraX = x;
        this.extraY = y;
        this.extraW = w;

        int rgb = currentRgb();

        // Preview
        g.fill(x - 1, y - 1, x + w + 1, y + SWATCH_H + 1, FIELD_BORDER);
        g.fill(x, y, x + w, y + SWATCH_H, 0xFF000000 | rgb);

        // One slider per channel, tinted so they are told apart without labels
        for (int c = 0; c < 3; c++) {
            int sy = sliderY(c);
            int value = (rgb >> shift(c)) & 0xFF;
            g.fill(x, sy + SLIDER_H / 2 - 1, x + w, sy + SLIDER_H / 2 + 1, FIELD_BORDER);
            int handleX = x + Math.round(value / 255.0f * (w - HANDLE_W));
            g.fill(handleX, sy, handleX + HANDLE_W, sy + SLIDER_H, CHANNEL_TINT[c]);
        }

        // Colours already used in graph.toml
        int py = paletteY();
        for (int i = 0; i < palette.size(); i++) {
            int px = x + i * (PALETTE_H + 2);
            if (px + PALETTE_H > x + w) break;
            g.fill(px - 1, py - 1, px + PALETTE_H + 1, py + PALETTE_H + 1, FIELD_BORDER);
            g.fill(px, py, px + PALETTE_H, py + PALETTE_H, 0xFF000000 | palette.get(i));
        }
    }

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        int py = paletteY();
        if (my >= py && my < py + PALETTE_H) {
            for (int i = 0; i < palette.size(); i++) {
                int px = extraX + i * (PALETTE_H + 2);
                if (mx >= px && mx < px + PALETTE_H) {
                    setRgb(palette.get(i));
                    return true;
                }
            }
        }
        return dragSlider(mx, my);
    }

    @Override
    protected boolean extraContentMouseDragged(double mx, double my, int button) {
        return button == 0 && dragSlider(mx, my);
    }

    /**
     * Maps a cursor x on a channel's track onto 0..255 and writes it back through the hex field.
     *
     * @return true when the cursor was on a track, so the click or drag is consumed
     */
    private boolean dragSlider(double mx, double my) {
        for (int c = 0; c < 3; c++) {
            int sy = sliderY(c);
            if (my < sy || my >= sy + SLIDER_H) continue;

            int track = extraW - HANDLE_W;
            if (track <= 0) return true;
            double pos = (mx - extraX - HANDLE_W / 2.0) / track;
            int value = (int) Math.round(Math.max(0.0, Math.min(1.0, pos)) * 255);

            int rgb = currentRgb();
            int cleared = rgb & ~(0xFF << shift(c));
            setRgb(cleared | (value << shift(c)));
            return true;
        }
        return false;
    }

    @Override
    protected void onConfirm(InputValues values) {
        // Normalised on the way out, so "44cc99" and "#44CC99" cannot both end up in graph.toml.
        onDone.accept(GraphColors.format(GraphColors.parse(values.getString("hex"), 0)));
        this.minecraft.setScreen(parent);
    }
}
