package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.ClientStructureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all structures known to the server, with an internal
 * tab switcher between plain Structures and Structure Tags. The {@code onSelect}
 * callback receives either a plain structure ID (e.g. {@code minecraft:stronghold})
 * or a tag id prefixed with {@code #} (e.g. {@code #minecraft:village}).
 *
 * <p>Data source: {@link ClientStructureRegistry} (synced on login). Reloaded on every
 * {@link #show} and on every tab switch.
 */
public class SearchableStructureList extends AbstractSearchableList<String> {

    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PAD = 4;

    /** 0 = Structures, 1 = Tags. */
    private int activeTab = 0;

    private float tabIndicatorX = 0;
    private float tabIndicatorW = 0;
    private boolean tabIndicatorInit = false;

    private int marqueeHoveredRow = -1;
    private long marqueeHoverStart = 0;
    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    public SearchableStructureList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableStructureList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search structures...", onSelect, alreadyAddedSupplier);
    }

    @Override
    protected int getTopInsetHeight() {
        return TAB_HEIGHT + 4;
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
    protected void renderTopInset(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int tabY = panelY + PADDING;
        String[] labels = { "Structures", "Tags" };
        int[] tabXs = new int[2];
        int[] tabWs = new int[2];

        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            tabWs[i] = font.width(labels[i]) + TAB_PAD * 2;
            tabXs[i] = x;
            x += tabWs[i] + 2;
        }

        if (!tabIndicatorInit) {
            tabIndicatorX = tabXs[activeTab];
            tabIndicatorW = tabWs[activeTab];
            tabIndicatorInit = true;
        }

        float targetX = tabXs[activeTab];
        float targetW = tabWs[activeTab];
        tabIndicatorX += (targetX - tabIndicatorX) * 0.18f;
        tabIndicatorW += (targetW - tabIndicatorW) * 0.18f;
        if (Math.abs(tabIndicatorX - targetX) < 0.5f) tabIndicatorX = targetX;
        if (Math.abs(tabIndicatorW - targetW) < 0.5f) tabIndicatorW = targetW;

        for (int i = 0; i < 2; i++) {
            boolean active = (i == activeTab);
            boolean hovered = mouseX >= tabXs[i] && mouseX < tabXs[i] + tabWs[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            g.fill(tabXs[i], tabY, tabXs[i] + tabWs[i], tabY + TAB_HEIGHT, bg);
            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            g.drawString(font, labels[i], tabXs[i] + TAB_PAD, tabY + 3, textColor, false);
        }

        g.fill((int) tabIndicatorX, tabY + TAB_HEIGHT - 2,
                (int) (tabIndicatorX + tabIndicatorW), tabY + TAB_HEIGHT, 0xFFFFCC00);
        g.fill(panelX + PADDING, tabY + TAB_HEIGHT, panelX + panelW - PADDING, tabY + TAB_HEIGHT + 1, 0xFF555555);
    }

    @Override
    protected boolean onTopInsetClick(double mouseX, double mouseY) {
        int clickedTab = getTabAt(mouseX, mouseY);
        if (clickedTab < 0 || clickedTab == activeTab) return false;
        activeTab = clickedTab;
        searchBar.setPlaceholder(activeTab == 0 ? "Search structures..." : "Search structure tags...");
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        reloadEntries();
        return true;
    }

    private int getTabAt(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int tabY = panelY + PADDING;
        String[] labels = { "Structures", "Tags" };
        int x = panelX + PADDING;
        for (int i = 0; i < 2; i++) {
            int w = font.width(labels[i]) + TAB_PAD * 2;
            if (mouseX >= x && mouseX < x + w && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                return i;
            }
            x += w + 2;
        }
        return -1;
    }

    /**
     * Custom marquee variant — pre-delay we scissor and draw full text (no "..."),
     * matching the original SearchableStructureList behaviour exactly.
     */
    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String text = activeTab == 1 ? "#" + entry : entry;
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
        int searchY = panelY + PADDING + getTopInsetHeight();
        int listY = searchY + SearchBar.HEIGHT + PADDING;
        int listX = panelX + PADDING;
        int listW = panelW - PADDING * 2 - 8;
        boolean inListBounds = mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT;
        if (!inListBounds) marqueeHoveredRow = -1;
    }
}
