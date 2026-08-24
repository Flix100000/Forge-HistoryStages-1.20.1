package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all known dimensions.
 */
public class SearchableDimensionList extends AbstractSearchableList<String> {

    public SearchableDimensionList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableDimensionList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.dimensions").getString(), onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> dims = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            Set<ResourceKey<Level>> levels = mc.getConnection().levels();
            for (ResourceKey<Level> level : levels) {
                dims.add(level.location().toString());
            }
        }
        // Always ensure vanilla dimensions are present
        if (!dims.contains("minecraft:overworld")) dims.add("minecraft:overworld");
        if (!dims.contains("minecraft:the_nether")) dims.add("minecraft:the_nether");
        if (!dims.contains("minecraft:the_end")) dims.add("minecraft:the_end");
        dims.sort(String::compareToIgnoreCase);
        return dims;
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
