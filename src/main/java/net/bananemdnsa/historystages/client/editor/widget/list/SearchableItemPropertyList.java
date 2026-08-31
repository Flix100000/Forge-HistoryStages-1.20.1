package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.client.editor.nbt.CriterionCard;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriterion;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What one item actually carries, offered for the taking.
 *
 * <p>Turns the usual order around. Adding a criterion by hand means naming a component and then
 * writing a value in its encoded form — two things a pack author has no way to know for a modded
 * item. Here the item is picked first and this list shows what it really has, values filled in,
 * so the whole question disappears.
 *
 * <p>Entries come from {@code NbtCriteriaCodec.load} over the criteria built from the stack, which
 * is the same path the matcher encodes with. Anything ticked here therefore matches the item it
 * was read from.
 */
public class SearchableItemPropertyList extends AbstractSearchableList<NbtCriterion> {

    private final List<NbtCriterion> candidates;

    public SearchableItemPropertyList(List<NbtCriterion> candidates,
                                      Consumer<String> onSelect,
                                      Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.nbt.from_item.placeholder").getString(),
                onSelect, alreadyAddedSupplier);
        this.candidates = candidates;
        setMultiSelect(true);
        reloadEntries();
    }

    @Override
    protected List<NbtCriterion> loadEntries() {
        // The field is still null while the superclass constructor runs; the explicit
        // reloadEntries() above fills the list once it is set.
        return candidates == null ? List.of() : candidates;
    }

    @Override
    protected int getPanelWidth() {
        return 280;
    }

    @Override
    protected String primaryTabLabel() {
        return Component.translatable("editor.historystages.nbt.from_item.tab").getString();
    }

    @Override
    protected String getIdForFilter(NbtCriterion entry) {
        return entry.identity();
    }

    @Override
    protected boolean matchesQuery(NbtCriterion entry, String lowerCaseQuery) {
        return entry.identity().toLowerCase().contains(lowerCaseQuery)
                || CriterionCard.titleOf(entry).toLowerCase().contains(lowerCaseQuery)
                || CriterionCard.previewOf(entry).toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(NbtCriterion entry) {
        return entry.identity();
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, NbtCriterion entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String title = CriterionCard.titleOf(entry);
        g.drawString(font, title, x + 3, y + 4, hovered ? 0xFFFFFF : 0xBBBBBB, false);

        String preview = CriterionCard.previewOf(entry);
        if (preview.isEmpty()) return;

        int previewX = x + 3 + font.width(title) + 8;
        int room = x + w - 6 - previewX;
        if (room <= 0) return;
        if (font.width(preview) > room) {
            preview = font.plainSubstrByWidth(preview, room - font.width("...")) + "...";
        }
        g.drawString(font, preview, x + w - 6 - font.width(preview), y + 4, 0x777777, false);
    }
}
