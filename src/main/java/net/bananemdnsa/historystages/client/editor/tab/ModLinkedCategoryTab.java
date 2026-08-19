package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.LockCategory;

/**
 * A tab whose entries can also be pulled in wholesale from a mod.
 *
 * <p>Besides the entries themselves, such a category remembers which of them arrived through the
 * "pick a mod, then tick its structures/biomes" flow. That second list is what makes re-editing a
 * mod's selection safe: unticked rows can be dropped without touching entries the maintainer
 * added by hand.
 *
 * <p>The satellite is not part of {@link LockCategory} — it is editor bookkeeping that happens to
 * be persisted — so its reader and writer are supplied at construction.
 */
public class ModLinkedCategoryTab extends StringListCategoryTab {

    private final List<String> modLinked = new ArrayList<>();
    private final Function<StageEntry, List<String>> modLinkedReader;
    private final BiConsumer<StageEntry, List<String>> modLinkedWriter;

    public ModLinkedCategoryTab(LockCategory<String> category,
                                boolean availableForIndividualStages,
                                PickerFactory pickerFactory,
                                Runnable onChanged,
                                Function<StageEntry, List<String>> modLinkedReader,
                                BiConsumer<StageEntry, List<String>> modLinkedWriter) {
        super(category, availableForIndividualStages, pickerFactory, onChanged);
        this.modLinkedReader = modLinkedReader;
        this.modLinkedWriter = modLinkedWriter;
    }

    @Override
    public void load(StageEntry stage) {
        super.load(stage);
        modLinked.clear();
        modLinked.addAll(modLinkedReader.apply(stage));
    }

    @Override
    public void store(StageEntry stage) {
        super.store(stage);
        modLinkedWriter.accept(stage, new ArrayList<>(modLinked));
    }

    /** Which entries came from a mod selection, live — the editor marks these rows differently. */
    public List<String> modLinkedEntries() {
        return modLinked;
    }

    /**
     * Applies a fresh selection for one mod: everything this tab previously took from that mod
     * goes, then the new picks come in. Entries the maintainer added by hand are left alone even
     * when they belong to the same mod, which is the whole reason the satellite list exists.
     *
     * @return whether anything actually changed
     */
    public boolean replaceModSelection(String modId, Collection<String> selectedIds) {
        String prefix = modId + ":";
        boolean changed = entries().removeIf(id -> id.startsWith(prefix) && modLinked.contains(id));
        changed |= modLinked.removeIf(id -> id.startsWith(prefix));

        for (String id : selectedIds) {
            if (!entries().contains(id)) entries().add(id);
            if (!modLinked.contains(id)) modLinked.add(id);
        }
        return changed || !selectedIds.isEmpty();
    }

    /** Drops everything this tab took from one mod, leaving hand-added entries in place. */
    public void removeModSelection(String modId) {
        removeModSelectionByPrefix(modId + ":");
    }

    /** Same, for callers that already hold the {@code "modid:"} prefix. */
    public void removeModSelectionByPrefix(String prefix) {
        entries().removeIf(id -> id.startsWith(prefix) && modLinked.contains(id));
        modLinked.removeIf(id -> id.startsWith(prefix));
    }
}
