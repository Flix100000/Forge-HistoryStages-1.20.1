package net.bananemdnsa.historystages.client.editor.tab;

import java.util.List;

import net.bananemdnsa.historystages.client.editor.widget.list.AbstractSearchableList;
import net.bananemdnsa.historystages.data.StageEntry;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the stage editor needs in order to show one lock category as a tab, without knowing
 * which category it is.
 *
 * <p>A tab has always been spread across seventeen places in the editor — an edit field, a
 * picker, six input-forwarding calls, a render call, a visibility check, a load and a store, plus
 * a handful of index branches. Gathering that behind one type is what lets a category the editor
 * has never heard of appear as an ordinary tab.
 *
 * <p>Loading and storing are not reimplemented here: they go through
 * {@link net.bananemdnsa.historystages.data.lock.category.LockCategory#read} and
 * {@code write}, so a tab and a lock check can never disagree about where a category's entries
 * live.
 */
public interface CategoryTab {

    String categoryId();

    String tabLangKey();

    String tooltipLangKey();

    /** Some categories make no sense per player — recipes and spawn locks are global-only today. */
    boolean availableForIndividualStages();

    /** Pulls this tab's edit state out of the stage being opened. */
    void load(StageEntry stage);

    /** Writes this tab's edit state into the stage about to be saved. */
    void store(StageEntry stage);

    /**
     * The rows this tab renders, live rather than a copy — the editor indexes into it and expects
     * {@link #removeAt} to be visible immediately, the way the old {@code edit*} fields behaved.
     */
    List<String> entries();

    void removeAt(int index);

    /**
     * Rebuilds the picker. Called from the screen's {@code init()}, which Minecraft runs again on
     * every window resize — the picker is rebuilt each time, while this tab and its entries are
     * created once and must survive, or a resize would throw the player's edits away.
     */
    void rebuildPicker();

    /** The picker this tab opens on Add, or null before the first {@code init()}. */
    @Nullable
    AbstractSearchableList<?> picker();

    void openPicker(int centerX, int centerY, int parentWidth);
}
