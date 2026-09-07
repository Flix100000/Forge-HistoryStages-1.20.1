package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;
import net.bananemdnsa.historystages.api.editor.EditorTab;
import net.bananemdnsa.historystages.api.editor.widget.TradeRowGeometry;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.lock.TradePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The offer section of the trades tab: single trades, named one by one.
 *
 * <p>Rows are labels rather than ids, because a trade has no id — it is a merchant, a level and
 * two sides, and none of those alone says which one you meant. The entries themselves are kept
 * beside the rows, by position, the way the rich tabs keep their criteria: the label is what the
 * editor draws and searches, the entry is what gets saved.
 *
 * <p>By position and not by label, because two trades can read the same and still be different —
 * a farmer at level one has four recipes that all hand over an emerald, and telling them apart is
 * the whole point of listing offers instead of items.
 */
public final class TradeOfferCategoryTab extends AbstractCategoryTab {

    private final LockCategory<TradeOfferEntry> category;

    /** One per row, in the same order. Kept in step by every method that touches either. */
    private final List<TradeOfferEntry> offers = new ArrayList<>();

    public TradeOfferCategoryTab(LockCategory<TradeOfferEntry> category,
                                 PickerFactory pickerFactory,
                                 Runnable onChanged) {
        super(category, pickerFactory, onChanged);
        this.category = category;
    }

    /** Appends a trade, unless the same one is already listed. */
    public void addOffer(TradeOfferEntry offer) {
        for (TradeOfferEntry existing : offers) {
            if (existing.identity().equals(offer.identity())) return;
        }
        offers.add(offer);
        entries().add(labelOf(offer));
        markChanged();
    }

    /** What the picker's already-added check compares against: the trades already listed. */
    public List<String> addedIdentities() {
        List<String> identities = new ArrayList<>(offers.size());
        for (TradeOfferEntry offer : offers) identities.add(offer.identity());
        return identities;
    }

    /** The criterion on the row's trade, or null. Read by the NBT editor in the context menu. */
    @Nullable
    public JsonObject nbtAt(int index) {
        return index >= 0 && index < offers.size() ? offers.get(index).nbt() : null;
    }

    /** Narrows a listed trade to a stack criterion, or drops the one it had. */
    public void setNbtAt(int index, @Nullable JsonObject nbt) {
        if (index < 0 || index >= offers.size()) return;
        TradeOfferEntry old = offers.get(index);
        offers.set(index, new TradeOfferEntry(old.merchantKey(), old.level(), old.givesId(),
                old.takesAId(), old.takesBId(), nbt));
        markChanged();
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= entries().size()) return;
        entries().remove(index);
        if (index < offers.size()) offers.remove(index);
    }

    /** The row shows what the merchant hands over, which is what the trade is about. */
    @Override
    @Nullable
    public String iconItemId(int index) {
        return index >= 0 && index < offers.size() ? offers.get(index).givesId() : null;
    }

    /**
     * The prices, an arrow and the ware icon, in the reading order of the merchant window.
     *
     * <p>Takes the place of the leading item icon rather than sitting beside it: the ware icon is
     * the last thing in this zone, so the row would otherwise show it twice.
     *
     * <p>No stack counts. A stored entry has none — the lock deliberately forgets the numbers,
     * because vanilla recipes roll their price per villager. The space for a count stays reserved
     * all the same, so that this row and the picker's row line up on the same columns.
     */
    @Override
    @Nullable
    public EditorTab.LeadingArt leadingArt(int index) {
        if (index < 0 || index >= offers.size()) return null;
        TradeOfferEntry offer = offers.get(index);
        return new EditorTab.LeadingArt(TradeRowGeometry.WIDTH, (g, x, y, w, h) -> {
            drawStackAt(g, offer.takesAId(), x + TradeRowGeometry.priceIconX(0), y);
            drawStackAt(g, offer.takesBId(), x + TradeRowGeometry.priceIconX(1), y);
            g.drawString(Minecraft.getInstance().font, "→",
                    x + TradeRowGeometry.arrowX(), y + 2, 0xFF6A6A6A, false);
            drawStackAt(g, offer.givesId(), x + TradeRowGeometry.wareIconX(), y);
        });
    }

    /** The row reads as the ware's name; the ids stay in the stored entry. */
    @Override
    @Nullable
    public String displayText(int index, String entry) {
        if (index < 0 || index >= offers.size()) return null;
        return nameOf(offers.get(index).givesId());
    }

    /**
     * The criterion mark, then the merchant and its level, dimmed, at the right end of the row.
     *
     * <p>The merchant sits to the right because the row is about the trade; who offers it is the
     * answer to a second question. The wandering trader has no level and gets none printed.
     */
    @Override
    @Nullable
    public String badgeText(int index) {
        if (index < 0 || index >= offers.size()) return null;
        TradeOfferEntry offer = offers.get(index);
        String tag = "§8" + merchantName(offer.merchantKey())
                + (TradePreview.WANDERING_TRADER.equals(offer.merchantKey())
                        ? "" : " " + offer.level());
        return offer.hasNbt() ? "§6[NBT] " + tag : tag;
    }

    private static void drawStackAt(GuiGraphics g, @Nullable String itemId, int x, int y) {
        if (itemId == null) return;
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

    @Override
    public void load(StageEntry stage) {
        entries().clear();
        offers.clear();
        for (TradeOfferEntry offer : category.read(stage)) {
            offers.add(offer);
            entries().add(labelOf(offer));
        }
    }

    @Override
    public void store(StageEntry stage) {
        category.write(stage, new ArrayList<>(offers));
    }

    /**
     * What the row reads as.
     *
     * <p>Ids rather than translated names on purpose: this string is also what the editor's search
     * box matches against and what "copy id" puts on the clipboard, and both of those want the
     * thing you would type into a stage file.
     */
    private static String labelOf(TradeOfferEntry offer) {
        StringBuilder label = new StringBuilder(offer.givesId()).append(" \u2190 ");
        if (offer.takesAId() != null) label.append(offer.takesAId());
        if (offer.takesBId() != null) label.append(" + ").append(offer.takesBId());
        return label.append(" (").append(offer.merchantKey())
                .append(' ').append(offer.level()).append(')').toString();
    }
}
