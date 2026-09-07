package net.bananemdnsa.historystages.client.editor.widget.list;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.api.editor.widget.TradeRowGeometry;
import net.bananemdnsa.historystages.client.ClientTradeGoods;
import net.bananemdnsa.historystages.data.lock.TradePreview;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Picks a trade.
 *
 * <p>A merchant's offers, listed the way the merchant window lists them: what it costs, an arrow,
 * what you get. That is the order a pack author already knows, and reading it the other way round
 * was the thing that made a list of two hundred and fifty offers hard to scan.
 *
 * <p>Grouped by merchant, because the scan already produces them that way — profession by
 * profession, and level one to five within each. The captions only make visible an order that was
 * always there. The merchant is therefore not repeated on every row; the level is, because inside
 * one merchant it is what tells two rows apart.
 *
 * <p>No item-registry tab beside it, on purpose. A trade that is not in this list cannot be named
 * by a lock either, so a tab full of items would offer choices that go nowhere. Gating an item for
 * trading wherever it turns up is a different rule and lives in the items tab, on the action
 * called trade.
 *
 * <p>The prices shown are samples. Several vanilla recipes roll their number per villager, so a
 * price here says which trade you are looking at rather than what it will cost — see
 * {@link TradePreview}.
 */
public class SearchableTradeList extends AbstractSearchableList<TradeRow> {

    /** Height of the strip above the list that keeps naming the merchant while rows scroll. */
    private static final int STICKY_H = 11;

    /** Merchant levels a villager can reach. The wandering trader has none and counts as one. */
    private static final int MAX_LEVEL = 5;

    /** Filter group for the level options: one at a time, or none for all of them. */
    private static final String LEVEL_GROUP = "trade_level";

    public SearchableTradeList(Consumer<String> onSelect,
                               Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.trades").getString(),
                onSelect, alreadyAddedSupplier);
    }

    @Override
    protected String primaryTabLabel() {
        return Component.translatable("editor.historystages.search.tab.trades").getString();
    }

    @Override
    protected List<TradeRow> loadEntries() {
        List<TradeRow> rows = new ArrayList<>();
        for (TradePreview offer : ClientTradeGoods.offers()) rows.add(TradeRow.of(offer));
        return rows;
    }

    /**
     * The profession, which is what the namespace filters should judge this row by.
     *
     * <p>Not the trade's identity: that string begins with the profession anyway, but a trade
     * whose merchant is vanilla and whose goods come from a mod is a vanilla merchant's offer,
     * and answering with the merchant is the honest half.
     */
    @Override
    protected String getIdForFilter(TradeRow row) {
        return row.isHeader() ? "" : row.offer().professionId();
    }

    /**
     * The whole trade, as one exact string.
     *
     * <p>What the already-added check compares against, and why it is the whole trade rather than
     * the item handed over: a merchant may offer two trades for the same goods, and listing one
     * must not grey out the other.
     */
    @Override
    protected String getIdForAddedCheck(TradeRow row) {
        return row.isHeader() ? null : row.lockIdentity();
    }

    @Override
    protected String selectionValueOf(TradeRow row) {
        return row.lockIdentity();
    }

    /**
     * Matches everything the row shows — both halves and the merchant.
     *
     * <p>Librarian and paper are both ways somebody arrives at the same trade, and a search that
     * knew only one of them would send them back to scrolling.
     */
    @Override
    protected boolean matchesQuery(TradeRow row, String lowerCaseQuery) {
        if (row.isHeader()) return true;
        TradePreview offer = row.offer();
        if (offer.professionId().toLowerCase().contains(lowerCaseQuery)) return true;
        if (merchantName(offer.professionId()).toLowerCase().contains(lowerCaseQuery)) return true;
        for (String id : offer.itemIds()) {
            if (id.toLowerCase().contains(lowerCaseQuery)) return true;
            if (nameOf(id).toLowerCase().contains(lowerCaseQuery)) return true;
        }
        return false;
    }

    @Override
    protected void configureFilters(SearchBar bar) {
        super.configureFilters(bar);
        for (int level = 1; level <= MAX_LEVEL; level++) {
            bar.filters().addOption("trade_level_" + level,
                    Component.translatable("editor.historystages.search.filter.trade_level", level)
                            .getString(),
                    LEVEL_GROUP);
        }
    }

    /**
     * Narrows the list to one merchant level.
     *
     * <p>One at a time, like "only vanilla / only modded", and none active means all of them.
     * Wanting levels two and four but not three is not a question anybody asks of a merchant, and
     * five independent checkboxes would have to answer it anyway.
     */
    @Override
    protected boolean matchesExtraFilters(TradeRow row) {
        if (row.isHeader()) return true;
        for (int level = 1; level <= MAX_LEVEL; level++) {
            if (searchBar.filters().isActive("trade_level_" + level)) {
                return row.offer().level() == level;
            }
        }
        return true;
    }

    /**
     * Puts a caption above the first offer of each merchant.
     *
     * <p>Runs on what survived the filter rather than on the whole list, so a search that leaves
     * three librarian offers standing gets one caption and not fifteen. Nothing is sorted: the
     * scan hands the offers over grouped already, so a change of profession is the start of a
     * group.
     */
    @Override
    protected void afterFilter(List<TradeRow> filtered) {
        String current = null;
        for (int i = 0; i < filtered.size(); i++) {
            TradeRow row = filtered.get(i);
            if (row.isHeader()) continue;
            String profession = row.offer().professionId();
            if (!profession.equals(current)) {
                current = profession;
                filtered.add(i, TradeRow.header(merchantName(profession)));
                i++;
            }
        }
    }

    @Override
    protected boolean isHeaderRow(int index) {
        return index >= 0 && index < filteredEntries.size()
                && filteredEntries.get(index).isHeader();
    }

    @Override
    protected void renderHeaderRow(GuiGraphics g, Font font, TradeRow row,
                                   int x, int y, int w, int h) {
        g.drawString(font, row.caption(), x + 3, y + 4, 0xFFFFCC00, false);
        g.fill(x + 3, y + h - 1, x + w - 3, y + h, 0x40FFCC00);
    }

    @Override
    protected int stickyCaptionH() {
        return STICKY_H;
    }

    /**
     * Names the merchant whose offers are going past right now.
     *
     * <p>A merchant with five levels fills more than the ten rows on screen, so its caption
     * scrolls away and the rows below it stop saying whose they are. Skipped while that caption
     * is itself the top row, where repeating it would just be the same word twice.
     */
    @Override
    protected void renderStickyCaption(GuiGraphics g, Font font, int x, int y, int w,
                                       int firstVisibleIndex) {
        if (filteredEntries.isEmpty()) return;
        int start = Math.min(Math.max(firstVisibleIndex, 0), filteredEntries.size() - 1);
        if (filteredEntries.get(start).isHeader()) return;
        for (int i = start; i >= 0; i--) {
            TradeRow row = filteredEntries.get(i);
            if (row.isHeader()) {
                g.drawString(font, row.caption(), x + 3, y + 2, 0xFF8A8A8A, false);
                return;
            }
        }
    }

    /**
     * Price, arrow, ware — in the columns the trades tab uses, so a row looks the same after it
     * has been picked as it did in the picker.
     */
    @Override
    protected void renderRow(GuiGraphics g, Font font, TradeRow row,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        TradePreview offer = row.offer();
        g.drawString(font, String.valueOf(offer.level()), x + 3, y + 4,
                hovered ? 0xFFAAAAAA : 0xFF6A6A6A, false);

        int zone = x + 12;
        drawPrice(g, font, offer.costAId(), offer.costACount(), 0, zone, y, hovered);
        drawPrice(g, font, offer.costBId(), offer.costBCount(), 1, zone, y, hovered);
        g.drawString(font, "→", zone + TradeRowGeometry.arrowX(), y + 4, 0xFF777777, false);
        drawStack(g, offer.resultId(), zone + TradeRowGeometry.wareIconX(), y);

        int textX = zone + TradeRowGeometry.WIDTH;
        String name = (offer.resultCount() > 1 ? offer.resultCount() + "× " : "")
                + nameOf(offer.resultId());
        drawRowText(g, font, name, textX, y, x + w - textX - 3, hovered);
    }

    private void drawPrice(GuiGraphics g, Font font, String itemId, int count, int slot,
                           int zoneX, int y, boolean hovered) {
        if (itemId == null) return;
        drawStack(g, itemId, zoneX + TradeRowGeometry.priceIconX(slot), y);
        if (count > 1) {
            g.drawString(font, String.valueOf(count),
                    zoneX + TradeRowGeometry.priceCountX(slot), y + 4,
                    hovered ? 0xFFFFFFFF : 0xFFBBBBBB, false);
        }
    }

    private void drawStack(GuiGraphics g, String itemId, int x, int y) {
        ItemStack stack = stackOf(itemId);
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack stackOf(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static String nameOf(String itemId) {
        ItemStack stack = stackOf(itemId);
        return stack.isEmpty() ? itemId : stack.getHoverName().getString();
    }

    /** The translated merchant name, falling back to the path of its id. */
    private static String merchantName(String merchantKey) {
        ResourceLocation key = ResourceLocation.tryParse(merchantKey);
        if (key == null) return merchantKey;
        if (TradePreview.WANDERING_TRADER.equals(merchantKey)) {
            return Component.translatable("entity.minecraft.wandering_trader").getString();
        }
        String langKey = "entity." + key.getNamespace() + ".villager." + key.getPath();
        String translated = Component.translatable(langKey).getString();
        return translated.equals(langKey) ? key.getPath() : translated;
    }
}
