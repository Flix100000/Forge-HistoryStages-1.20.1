package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.StructureGenerationRule;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import org.jetbrains.annotations.Nullable;

/**
 * The structures tab: mod-linked entries like biomes, plus a generation rule per entry.
 *
 * <p>A rule caps how often a structure may still generate while the stage is locked. Entries
 * without one generate unrestricted, so the rules are stored as a sparse list rather than one
 * per entry — an absent rule is the normal case, not a missing value.
 */
public final class StructureCategoryTab extends ModLinkedCategoryTab {

    private final List<StructureGenerationRule> generationRules = new ArrayList<>();

    public StructureCategoryTab(LockCategory<String> category,
                                PickerFactory pickerFactory,
                                Runnable onChanged) {
        super(category, pickerFactory, onChanged,
                StageEntry::getStructureModLinked, StageEntry::setStructureModLinked);
    }

    @Override
    public void load(StageEntry stage) {
        super.load(stage);
        generationRules.clear();
        generationRules.addAll(stage.getStructureGenerationRules());
    }

    @Override
    public void store(StageEntry stage) {
        super.store(stage);
        stage.setStructureGenerationRules(new ArrayList<>(generationRules));
    }

    /** The rule for one entry, or null while it generates unrestricted. */
    @Nullable
    public StructureGenerationRule generationRuleFor(String structureId) {
        for (StructureGenerationRule rule : generationRules) {
            if (rule.id().equals(structureId)) return rule;
        }
        return null;
    }

    /** Sets or clears one entry's rule; a null rule puts the entry back to unrestricted. */
    public void applyGenerationRule(String structureId, @Nullable StructureGenerationRule rule) {
        generationRules.removeIf(r -> r.id().equals(structureId));
        if (rule != null) generationRules.add(rule);
    }

    @Override
    public void removeModSelectionByPrefix(String prefix) {
        super.removeModSelectionByPrefix(prefix);
        generationRules.removeIf(r -> r.id().startsWith(prefix));
    }
}
