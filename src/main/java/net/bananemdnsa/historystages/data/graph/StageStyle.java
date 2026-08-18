package net.bananemdnsa.historystages.data.graph;

/**
 * A partial node style. Every field may be null, meaning "inherit from the layer below".
 * Resolution order is built-in default -&gt; {@code graph.toml} -&gt; this.
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
}
