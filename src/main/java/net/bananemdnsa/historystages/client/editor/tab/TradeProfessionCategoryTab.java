package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.api.editor.AbstractCategoryTab;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import org.jetbrains.annotations.Nullable;

/**
 * The profession section of the trades tab: a list of ids, each optionally narrowed to some of
 * the merchant's five levels.
 *
 * <p>The narrowing is kept beside the rows rather than in them, the way the spawn lock keeps its
 * sources and its dimensions. Rows stay a list of plain ids, which is what the host draws and what
 * the picker adds to; the extras hang off the id and are edited from the right-click menu. A rich
 * tab would be the other option, but its extras are criteria and action lists — neither of which
 * a profession has — and it keys them by <em>position</em>, which would mean renumbering a map on
 * every removal to buy nothing.
 *
 * <p><strong>Keyed by id, so one profession appears at most once.</strong> The picker already
 * refuses to add an id twice, which is what makes that safe; if two rows ever shared an id they
 * would share their levels too, and removing either would take both narrowings with it.
 */
public final class TradeProfessionCategoryTab extends AbstractCategoryTab {

    private final LockCategory<TradeProfessionEntry> category;

    /** Profession id to the levels it gates. An absent key means every level, as it always has. */
    private final Map<String, List<String>> levels = new LinkedHashMap<>();

    public TradeProfessionCategoryTab(LockCategory<TradeProfessionEntry> category,
                                      PickerFactory pickerFactory,
                                      Runnable onChanged) {
        super(category, pickerFactory, onChanged);
        this.category = category;
    }

    /** The levels this profession is narrowed to, or null for every level. */
    @Nullable
    public List<String> levelsFor(String professionId) {
        return levels.get(professionId);
    }

    /** Narrows one profession, or widens it again when {@code gated} names every level or none. */
    public void setLevelsFor(String professionId, @Nullable List<String> gated) {
        boolean everyLevel = gated == null || gated.isEmpty()
                || gated.size() >= TradeProfessionEntry.ALL_LEVELS.size();
        if (everyLevel) {
            levels.remove(professionId);
        } else {
            // Kept ascending, so the stage file reads in the order a merchant climbs them whatever
            // order the switches were flipped in.
            List<String> ordered = new ArrayList<>();
            for (String level : TradeProfessionEntry.ALL_LEVELS) {
                if (gated.contains(level)) ordered.add(level);
            }
            levels.put(professionId, ordered);
        }
        markChanged();
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= entries().size()) return;
        String removed = entries().get(index);
        super.removeAt(index);
        // Only once the last row with this id is gone, so a future duplicate cannot strip the
        // narrowing off the row that stayed.
        if (!entries().contains(removed)) levels.remove(removed);
    }

    /** Short text on the row saying how far the narrowing goes, or null when it does not. */
    @Override
    @Nullable
    public String badgeText(int index) {
        if (index < 0 || index >= entries().size()) return null;
        List<String> gated = levels.get(entries().get(index));
        if (gated == null || gated.isEmpty()) return null;
        return "[" + String.join(",", gated) + "]";
    }

    @Override
    public void load(StageEntry stage) {
        entries().clear();
        levels.clear();
        for (TradeProfessionEntry entry : category.read(stage)) {
            entries().add(entry.getId());
            if (entry.hasLevels()) levels.put(entry.getId(), new ArrayList<>(entry.getLevels()));
        }
    }

    @Override
    public void store(StageEntry stage) {
        List<TradeProfessionEntry> rebuilt = new ArrayList<>(entries().size());
        for (String id : entries()) {
            rebuilt.add(new TradeProfessionEntry(id, levels.get(id)));
        }
        category.write(stage, rebuilt);
    }
}
