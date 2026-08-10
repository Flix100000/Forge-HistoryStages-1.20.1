package net.bananemdnsa.historystages.data.tooltip;

/**
 * One configurable line of the research scroll tooltip.
 *
 * <p>{@code text} empty means "use the built-in lang key", {@code style} empty means "use the
 * line's built-in colour". The packmaker therefore only overrides what they actually touched,
 * and everything else stays translated.
 *
 * <p>For the reserved option ids ({@code dep.icon_fulfilled} and friends) {@code text} carries
 * the value and the remaining fields are meaningless.
 */
public record ScrollTooltipLine(String id, boolean enabled, boolean spacerBefore,
                                String style, String text) {

    public ScrollTooltipLine {
        id = id == null ? "" : id;
        style = style == null ? "" : style;
        text = text == null ? "" : text;
    }

    public ScrollTooltipLine withEnabled(boolean value) {
        return new ScrollTooltipLine(id, value, spacerBefore, style, text);
    }

    public ScrollTooltipLine withSpacerBefore(boolean value) {
        return new ScrollTooltipLine(id, enabled, value, style, text);
    }

    public ScrollTooltipLine withStyle(String value) {
        return new ScrollTooltipLine(id, enabled, spacerBefore, value, text);
    }

    public ScrollTooltipLine withText(String value) {
        return new ScrollTooltipLine(id, enabled, spacerBefore, style, value);
    }
}
