package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.EditorTab;

import net.bananemdnsa.historystages.data.StageEntry;

/**
 * An {@link EditorTab} over a stage: one lock category, shown in the stage editor.
 *
 * <p>Everything a tab has in common with the other axes lives on {@code EditorTab}. What is left
 * here is the two things only a category has.
 *
 * <p>Loading and storing are not reimplemented by the tabs: they go through
 * {@link net.bananemdnsa.historystages.api.lock.LockCategory#read} and
 * {@code write}, so a tab and a lock check can never disagree about where a category's entries
 * live.
 */
public interface CategoryTab extends EditorTab<StageEntry> {

    String categoryId();

    /**
     * Some categories make no sense per player — recipes and spawn locks are global-only today.
     *
     * <p>Deliberately not on {@code EditorTab}: the dependency axis answers the same question from
     * {@code Requirement.supportedScopes()}, so one shared method would have two different answers
     * under one name.
     */
    boolean availableForIndividualStages();
}
