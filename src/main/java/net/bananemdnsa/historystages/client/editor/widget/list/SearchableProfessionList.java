package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Searchable overlay list of every villager profession.
 *
 * <p>Read out of the registry rather than off a list of the fifteen vanilla ones, so a profession
 * added by another mod turns up here without a line of code changing. That is not a hypothetical
 * for this category: the packs that gate trades are usually the ones that added the trader.
 *
 * <p>The unemployed and nitwit professions stay in. They own no trades, so gating them changes
 * nothing — but leaving them out would look like a bug in the list rather than a deliberate
 * omission, and a maintainer would go looking for the one that was missing.
 */
public class SearchableProfessionList extends AbstractSearchableList<String> {

    public SearchableProfessionList(Consumer<String> onSelect,
                                    Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.professions")
                .getString(), onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> professions = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
            professions.add(id.toString());
        }
        professions.sort(String::compareToIgnoreCase);
        return professions;
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    /** Matches the id and the name, so "Bibliothekar" finds {@code minecraft:librarian}. */
    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        return entry.toLowerCase().contains(lowerCaseQuery)
                || displayName(entry).toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String name = displayName(entry);
        drawRowText(g, font, name.isEmpty() ? entry : name + " §8(" + entry + ")", x, y, w, hovered);
    }

    /**
     * The translated profession name, or an empty string when nothing translates it.
     *
     * <p>A modded profession that ships no lang entry would otherwise be listed as the raw key
     * Minecraft hands back for a missing translation, which reads worse than its own id.
     */
    private static String displayName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return "";
        String key = "entity." + location.getNamespace() + ".villager." + location.getPath();
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? "" : translated;
    }
}
