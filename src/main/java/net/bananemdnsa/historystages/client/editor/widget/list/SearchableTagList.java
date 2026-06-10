package net.bananemdnsa.historystages.client.editor.widget.list;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all registered item tags.
 */
public class SearchableTagList extends AbstractSearchableList<String> {

    public SearchableTagList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableTagList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search tags...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> tags = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> tags.add(tagKey.location().toString()));
        tags.sort(String::compareToIgnoreCase);
        return tags;
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        return entry.toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        drawRowText(g, font, entry, x, y, w, hovered);
    }
}
