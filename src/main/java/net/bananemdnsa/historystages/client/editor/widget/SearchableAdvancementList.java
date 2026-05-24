package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all known advancements.
 * Populated from the client's advancement data on each show().
 */
public class SearchableAdvancementList extends AbstractSearchableList<String> {

    public SearchableAdvancementList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableAdvancementList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search advancements...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected int getPanelWidth() {
        return 300;
    }

    @Override
    protected List<String> loadEntries() {
        List<String> advs = new ArrayList<>();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            try {
                for (net.minecraft.advancements.Advancement adv : connection.getAdvancements().getAdvancements().getAllAdvancements()) {
                    if (adv.getId() != null) {
                        advs.add(adv.getId().toString());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        advs.sort(String::compareToIgnoreCase);
        return advs;
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
