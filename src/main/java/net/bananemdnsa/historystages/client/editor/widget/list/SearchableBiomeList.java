package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all known biomes, optionally with a second tab for biome tags.
 *
 * <p>Data source: the client's biome registry (datapack-driven, only populated after joining a
 * world). When called outside a world the list will be empty.
 *
 * <p>The tags tab is opt-in because not every caller can act on a tag: the auto-trigger editor
 * matches a biome ID exactly, so offering it {@code #minecraft:is_forest} would create a trigger
 * that never fires. The biome lock resolves tags and passes {@code true}.
 */
public class SearchableBiomeList extends AbstractSearchableList<String> {

    private final boolean includeTags;

    /** 0 = Biomes, 1 = Tags. Only meaningful when {@link #includeTags} is set. */
    private int activeTab = 0;

    public SearchableBiomeList(Consumer<String> onSelect) {
        this(onSelect, null, false);
    }

    public SearchableBiomeList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier,
                               boolean includeTags) {
        super(Component.translatable("editor.historystages.search.placeholder.biomes").getString(),
                onSelect, alreadyAddedSupplier);
        this.includeTags = includeTags;
    }

    @Override
    protected List<String> ownTabLabels() {
        if (!includeTags) return super.ownTabLabels();
        return List.of(
                Component.translatable("editor.historystages.search.tab.biomes").getString(),
                Component.translatable("editor.historystages.search.tab.biome_tags").getString());
    }

    @Override
    protected void onOwnTabChanged(int index) {
        activeTab = index;
        setPlaceholder(Component.translatable(index == 0
                ? "editor.historystages.search.placeholder.biomes"
                : "editor.historystages.search.placeholder.biome_tags").getString());
        reloadEntries();
    }

    @Override
    protected List<String> loadEntries() {
        List<String> entries = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Registry<Biome> reg = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
            if (activeTab == 0) {
                for (ResourceLocation key : reg.keySet()) {
                    entries.add(key.toString());
                }
            } else {
                reg.getTagNames().forEach(tag -> entries.add(tag.location().toString()));
            }
        }
        entries.sort(String::compareToIgnoreCase);
        return entries;
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected String getIdForAddedCheck(String entry) {
        // Tag rows are stored prefixed with "#" in the alreadyAdded supplier.
        return activeTab == 1 ? "#" + entry : entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        return entry.toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return activeTab == 1 ? "#" + entry : entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        drawRowText(g, font, activeTab == 1 ? "#" + entry : entry, x, y, w, hovered);
    }

    @Override
    protected void renderSelectedRow(GuiGraphics g, Font font, String value, String entry,
                                     int x, int y, int w, int h, boolean hovered, int rowIndex) {
        // The frozen value already carries the "#" if it was picked on the Tags tab —
        // activeTab has since moved on and can't be trusted to re-derive it.
        drawRowText(g, font, value, x, y, w, hovered);
    }
}
