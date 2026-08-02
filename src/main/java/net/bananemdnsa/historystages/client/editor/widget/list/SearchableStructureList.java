package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.client.ClientStructureRegistry;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all structures known to the server, with a tab switcher
 * between plain Structures and Structure Tags. The {@code onSelect} callback receives
 * either a plain structure ID (e.g. {@code minecraft:stronghold}) or a tag id prefixed
 * with {@code #} (e.g. {@code #minecraft:village}).
 *
 * <p>Data source: {@link ClientStructureRegistry} (synced on login). Reloaded on every
 * {@link #show} and on every tab switch.
 */
public class SearchableStructureList extends AbstractSearchableList<String> {

    private static final long MARQUEE_DELAY_MS = Timing.MARQUEE_DELAY_MS;
    private static final float MARQUEE_SPEED = Timing.MARQUEE_SPEED;

    /** 0 = Structures, 1 = Tags. Mirrors the base class's own-tab index. */
    private int activeTab = 0;

    private int marqueeHoveredRow = -1;
    private long marqueeHoverStart = 0;

    public SearchableStructureList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableStructureList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search structures...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> ownTabLabels() {
        return List.of(
                Component.translatable("editor.historystages.search.tab.structures").getString(),
                Component.translatable("editor.historystages.search.tab.structure_tags").getString());
    }

    @Override
    protected void onOwnTabChanged(int index) {
        activeTab = index;
        setPlaceholder(index == 0 ? "Search structures..." : "Search structure tags...");
        reloadEntries();
    }

    @Override
    protected List<String> loadEntries() {
        List<String> list = new ArrayList<>();
        if (activeTab == 0) {
            list.addAll(ClientStructureRegistry.get());
        } else {
            list.addAll(ClientStructureRegistry.getTags());
        }
        return list;
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
        drawEntry(g, font, activeTab == 1 ? "#" + entry : entry, x, y, w, h, hovered, rowIndex);
    }

    @Override
    protected void renderSelectedRow(GuiGraphics g, Font font, String value, String entry,
                                     int x, int y, int w, int h, boolean hovered, int rowIndex) {
        // The frozen value already carries the "#" if it was picked on the Tags tab —
        // activeTab has since moved on and can't be trusted to re-derive it.
        drawEntry(g, font, value, x, y, w, h, hovered, rowIndex);
    }

    /**
     * Custom marquee variant — pre-delay we scissor and draw full text (no "..."),
     * matching the original SearchableStructureList behaviour exactly.
     */
    private void drawEntry(GuiGraphics g, Font font, String text,
                           int x, int y, int w, int h, boolean hovered, int rowIndex) {
        int textColor = hovered ? 0xFFFFFF : 0xBBBBBB;
        int textX = x + 3;
        int textY = y + 4;
        int textAvailW = w - 6;
        int textW = font.width(text);

        if (textW <= textAvailW) {
            g.drawString(font, text, textX, textY, textColor, false);
            return;
        }

        if (hovered) {
            if (marqueeHoveredRow != rowIndex) {
                marqueeHoveredRow = rowIndex;
                marqueeHoverStart = System.currentTimeMillis();
            }
            long elapsed = System.currentTimeMillis() - marqueeHoverStart;
            if (elapsed > MARQUEE_DELAY_MS) {
                float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                int maxMarquee = textW - textAvailW + 10;
                float cycle = (float) maxMarquee * 2;
                float pos = scrollProg % cycle;
                int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                g.enableScissor(textX, y, textX + textAvailW, y + h);
                g.drawString(font, text, textX - scrollOff, textY, textColor, false);
                g.disableScissor();
            } else {
                g.enableScissor(textX, y, textX + textAvailW, y + h);
                g.drawString(font, text, textX, textY, textColor, false);
                g.disableScissor();
            }
        } else {
            g.drawString(font, font.plainSubstrByWidth(text, textAvailW - 6) + "...", textX, textY, textColor, false);
        }
    }

    @Override
    protected void afterRender(GuiGraphics g, Font font, int mouseX, int mouseY) {
        // Reset marquee hover when no row was hovered this frame.
        // (Detected via the mouseY range, since we don't have a hook into the base loop.)
        int listY = listTopY();
        int listX = panelX + PADDING;
        int listW = panelW - PADDING * 2 - 8;
        boolean inListBounds = mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT;
        if (!inListBounds) marqueeHoveredRow = -1;
    }
}
