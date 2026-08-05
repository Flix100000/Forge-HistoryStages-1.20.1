package net.bananemdnsa.historystages.data.graph;

/**
 * A partial node style. Every field may be null, meaning "inherit from the layer below".
 * Resolution order is built-in default → {@code graph.toml} → this.
 */
public class StageStyle {

    /** One of RECT, ROUNDED, CIRCLE, DIAMOND, HEXAGON — case-insensitive; null = inherit. */
    public String shape;
    /** Scale factor relative to the configured base size; null = inherit. */
    public Double size;
    /** Corner radius in pixels, only meaningful for ROUNDED; null = inherit. */
    public Integer cornerRadius;
    /** {@code #RRGGBB}; null = inherit. */
    public String border;
    public Integer borderWidth;
    /** {@code #RRGGBB}; null = inherit. */
    public String fill;
    /** 0.0-1.0; null = inherit. */
    public Double fillOpacity;
    /** NONE, ID or DISPLAY_NAME; null = inherit. */
    public String label;
    /** {@code #RRGGBB}; null = inherit. */
    public String labelColor;
    public Boolean checkmark;

    public boolean isEmpty() {
        return shape == null && size == null && cornerRadius == null && border == null
                && borderWidth == null && fill == null && fillOpacity == null
                && label == null && labelColor == null && checkmark == null;
    }

    /** A field-for-field duplicate, so an edit buffer cannot write back into loaded data. */
    public StageStyle copy() {
        StageStyle out = new StageStyle();
        out.shape = shape;
        out.size = size;
        out.cornerRadius = cornerRadius;
        out.border = border;
        out.borderWidth = borderWidth;
        out.fill = fill;
        out.fillOpacity = fillOpacity;
        out.label = label;
        out.labelColor = labelColor;
        out.checkmark = checkmark;
        return out;
    }

    /**
     * Flattens two partial styles into one, {@code upper} winning per field.
     *
     * <p>Used to fold a stage's per-state override onto its all-states override before either
     * reaches {@link ResolvedStyle#merge}. Doing it here rather than by calling merge twice keeps
     * the resolve path a single merge and keeps this step unit-testable — {@code ResolvedStyle}
     * needs no Minecraft classes either, but the caller that would run the second merge does.
     */
    public static StageStyle overlay(StageStyle lower, StageStyle upper) {
        StageStyle out = lower == null ? new StageStyle() : lower.copy();
        if (upper == null) return out;
        if (upper.shape != null) out.shape = upper.shape;
        if (upper.size != null) out.size = upper.size;
        if (upper.cornerRadius != null) out.cornerRadius = upper.cornerRadius;
        if (upper.border != null) out.border = upper.border;
        if (upper.borderWidth != null) out.borderWidth = upper.borderWidth;
        if (upper.fill != null) out.fill = upper.fill;
        if (upper.fillOpacity != null) out.fillOpacity = upper.fillOpacity;
        if (upper.label != null) out.label = upper.label;
        if (upper.labelColor != null) out.labelColor = upper.labelColor;
        if (upper.checkmark != null) out.checkmark = upper.checkmark;
        return out;
    }
}
