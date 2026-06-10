package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.mixin.ClientAdvancementsAccessor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        // The AdvancementTree only contains advancements that have a display block, so
        // recipe-grant advancements (minecraft:recipes/...) and other display-less ones
        // are missing from getTree().nodes(). We additionally pull the full progress map
        // via accessor mixin to enumerate every advancement the client has been told about.
        Set<String> seen = new HashSet<>();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            ClientAdvancements client = connection.getAdvancements();
            try {
                for (net.minecraft.advancements.AdvancementNode node : client.getTree().nodes()) {
                    seen.add(node.holder().id().toString());
                }
            } catch (Exception ignored) {
            }
            try {
                for (AdvancementHolder holder : ((ClientAdvancementsAccessor) (Object) client)
                        .historystages$getProgress().keySet()) {
                    seen.add(holder.id().toString());
                }
            } catch (Exception ignored) {
            }
        }
        List<String> advs = new ArrayList<>(seen);
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
