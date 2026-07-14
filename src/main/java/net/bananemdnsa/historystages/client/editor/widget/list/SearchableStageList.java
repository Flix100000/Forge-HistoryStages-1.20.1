package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.client.editor.widget.SearchBar;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of all registered stages (global or individual).
 */
public class SearchableStageList extends AbstractSearchableList<SearchableStageList.StageListEntry> {

    private final boolean showIndividual;
    private String excludeStageId = null;

    public SearchableStageList(Consumer<String> onSelect, boolean showIndividual) {
        this(onSelect, showIndividual, null);
    }

    public SearchableStageList(Consumer<String> onSelect, boolean showIndividual,
                               Supplier<Collection<String>> alreadyAddedSupplier) {
        super("Search stages...", onSelect, alreadyAddedSupplier);
        this.showIndividual = showIndividual;
    }

    public void setExcludeStageId(String stageId) {
        this.excludeStageId = stageId;
    }

    @Override
    protected void configureFilters(SearchBar bar) {
        // Stages are user-defined — namespaces are not meaningful, so don't register
        // the "only vanilla / only modded" filters.
    }

    @Override
    protected List<StageListEntry> loadEntries() {
        List<StageListEntry> list = new ArrayList<>();
        Map<String, StageEntry> stages = showIndividual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            // Temporary stages re-lock on their own without cascading to dependents,
            // so they make poor dependency targets — hide them from the picker.
            if (entry.getValue().getMode() == StageMode.TEMPORARY) continue;
            list.add(new StageListEntry(entry.getKey(), entry.getValue().getDisplayName()));
        }
        list.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return list;
    }

    @Override
    protected String getIdForFilter(StageListEntry entry) {
        return entry.id;
    }

    @Override
    protected boolean matchesQuery(StageListEntry entry, String lowerCaseQuery) {
        return entry.id.toLowerCase().contains(lowerCaseQuery)
                || entry.displayName.toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(StageListEntry entry) {
        return entry.id;
    }

    @Override
    protected boolean isExcluded(StageListEntry entry) {
        return excludeStageId != null && entry.id.equals(excludeStageId);
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, StageListEntry entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String text = entry.displayName + " §7(" + entry.id + ")";
        drawRowMarqueeText(g, font, text, x, y, w, h, hovered, rowIndex);
    }

    public record StageListEntry(String id, String displayName) {}
}
