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
 * Searchable overlay list of all vanilla custom stats.
 */
public class SearchableStatList extends AbstractSearchableList<String> {

    public SearchableStatList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableStatList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search stats...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected int getPanelWidth() {
        return 300;
    }

    @Override
    protected List<String> loadEntries() {
        List<String> stats = new ArrayList<>();
        BuiltInRegistries.CUSTOM_STAT.forEach(rl -> stats.add(rl.toString()));
        stats.sort(String::compareToIgnoreCase);
        return stats;
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
        drawRowMarqueeText(g, font, entry, x, y, w, h, hovered, rowIndex);
    }
}
