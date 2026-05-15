package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable list of all installed mods that register content (items, blocks, or entities).
 */
public class SearchableModList extends AbstractSearchableList<SearchableModList.ModEntry> {

    public SearchableModList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableModList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search mods...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected int getPanelWidth() {
        return 220;
    }

    @Override
    protected List<ModEntry> loadEntries() {
        Set<String> contentMods = new HashSet<>();
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) contentMods.add(key.getNamespace());
        for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) contentMods.add(key.getNamespace());
        for (ResourceLocation key : BuiltInRegistries.ENTITY_TYPE.keySet()) contentMods.add(key.getNamespace());

        List<ModEntry> list = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods()) {
            String modId = mod.getModId();
            if (!contentMods.contains(modId)) continue;
            String displayName = mod.getDisplayName();
            list.add(new ModEntry(modId, displayName, displayName.toLowerCase()));
        }
        list.sort((a, b) -> a.modId.compareToIgnoreCase(b.modId));
        return list;
    }

    @Override
    protected String getIdForFilter(ModEntry entry) {
        return entry.modId;
    }

    @Override
    protected boolean matchesQuery(ModEntry entry, String lowerCaseQuery) {
        return entry.modId.contains(lowerCaseQuery) || entry.searchName.contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(ModEntry entry) {
        return entry.modId;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, ModEntry entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String text = entry.displayName + " §7(" + entry.modId + ")";
        drawRowText(g, font, text, x, y, w, hovered);
    }

    /**
     * Returns the human-readable mod name for a given mod id, or the id itself if not found.
     * Used by editor screens that want to display "Forge" instead of "forge".
     */
    public String getDisplayName(String modId) {
        for (ModEntry entry : allEntries) {
            if (entry.modId.equals(modId)) return entry.displayName;
        }
        return modId;
    }

    public record ModEntry(String modId, String displayName, String searchName) {}
}
