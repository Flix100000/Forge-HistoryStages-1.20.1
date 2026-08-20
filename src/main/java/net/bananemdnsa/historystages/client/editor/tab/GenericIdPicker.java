package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.client.editor.widget.list.AbstractSearchableList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The picker an addon category gets for free: a searchable list over whatever ids the addon says
 * are available.
 *
 * <p>Every concrete picker in the editor answers the same five questions, and for a plain list of
 * ids four of those answers are the identity. So an addon that gates things identified by an id
 * has to supply only the ids themselves, and gets a tab that looks and behaves like a built-in.
 */
public final class GenericIdPicker extends AbstractSearchableList<String> {

    private final Supplier<Collection<String>> candidates;

    /**
     * @param searchPlaceholderLangKey lang key for the search box hint — a key rather than a
     *                                 string, so an addon's picker is translatable like the rest
     * @param candidates               what the maintainer may pick from, asked each time the
     *                                 picker opens so a changing world state is reflected
     */
    public GenericIdPicker(String searchPlaceholderLangKey,
                           Supplier<Collection<String>> candidates,
                           Consumer<String> onSelect,
                           Supplier<Collection<String>> alreadyAdded) {
        super(Component.translatable(searchPlaceholderLangKey).getString(), onSelect, alreadyAdded);
        this.candidates = candidates;
    }

    @Override
    protected List<String> loadEntries() {
        List<String> ids = new ArrayList<>(candidates.get());
        ids.sort(String::compareToIgnoreCase);
        return ids;
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
