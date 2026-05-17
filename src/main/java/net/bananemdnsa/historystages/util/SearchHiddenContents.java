package net.bananemdnsa.historystages.util;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.Optional;

public record SearchHiddenContents(String text) implements ComponentContents {
    public static final ComponentContents.Type<SearchHiddenContents> TYPE = new ComponentContents.Type<>(
            Codec.STRING.xmap(SearchHiddenContents::new, SearchHiddenContents::text).fieldOf("text"),
            "historystages:search_hidden"
    );

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> visitor, Style style) {
        return visitor.accept(style, this.text);
    }
}
