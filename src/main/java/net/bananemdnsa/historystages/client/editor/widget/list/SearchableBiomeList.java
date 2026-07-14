package net.bananemdnsa.historystages.client.editor.widget.list;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all known biomes.
 *
 * <p>Data source: the client's biome registry (datapack-driven, only populated after joining a
 * world). When called outside a world the list will be empty.
 */
public class SearchableBiomeList extends AbstractSearchableList<String> {

    public SearchableBiomeList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableBiomeList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search biomes...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> biomes = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Registry<Biome> reg = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
            for (ResourceLocation key : reg.keySet()) {
                biomes.add(key.toString());
            }
        }
        biomes.sort(String::compareToIgnoreCase);
        return biomes;
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
