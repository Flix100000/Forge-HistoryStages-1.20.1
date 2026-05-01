package net.bananemdnsa.historystages.util;

import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * A ComponentContents that renders normally but is invisible to getString() —
 * preventing the text from being indexed by the creative mode search.
 */
public record SearchHiddenContents(String text) implements ComponentContents {

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
        // Return empty so getString() collects no text → search ignores this component
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> visitor, Style style) {
        // Return text with style so the rendering pipeline draws it normally
        return visitor.accept(style, this.text);
    }
}
