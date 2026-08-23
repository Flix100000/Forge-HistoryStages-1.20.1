package net.bananemdnsa.historystages.client.editor.tab;

import java.util.List;

import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an editor needs in order to show one extension point as a tab, without knowing what
 * that tab edits.
 *
 * <p>A tab used to be spread across seventeen places in a screen — an edit field, a picker, six
 * input-forwarding calls, a render call, a visibility check, a load and a store, plus a handful of
 * index branches. Gathering that behind one type is what lets something the editor has never heard
 * of appear as an ordinary tab.
 *
 * <p>One contract across the axes on purpose. A tab is the same idea whether it lists a lock
 * category's entries on a stage or a requirement's entries on one dependency group, and the only
 * real difference is what it reads from — which is this type parameter and nothing more. Two
 * contracts saying the same thing would be two things for an addon author to learn, and merging
 * them after the API is frozen would be a breaking change.
 *
 * <p>A tab does not render itself. Rows are drawn by the host screen, which owns the layout, the
 * scrolling and the hover animation; a tab supplies what to draw and what a click means.
 *
 * @param <C> what this tab loads from and stores into — a {@code StageEntry} for a lock category,
 *            one {@code DependencyGroup} for a requirement
 */
public interface EditorTab<C> {

    /** Lang key for the tab label. */
    String tabLangKey();

    /** Lang key for the tab tooltip. */
    String tooltipLangKey();

    /**
     * The rows this tab renders, live rather than a copy — the editor indexes into it and expects
     * {@link #removeAt} to be visible immediately, the way the old {@code edit*} fields behaved.
     */
    List<String> entries();

    void removeAt(int index);

    /**
     * An item id to draw as an icon at the left of this row, or null for none.
     *
     * <p>Deliberately an id and not an {@code ItemStack}: a tab says what to show, the host decides
     * how. Rows keep their fixed height either way, which is what keeps this from disturbing the
     * host's scroll arithmetic.
     *
     * <p><strong>Honoured by the dependency editor.</strong> The stage editor still draws its
     * item icons through a special case of its own and ignores this; moving it onto the hook is a
     * follow-up, and until then a lock-category tab that returns something here sees nothing.
     */
    @Nullable
    default String iconItemId(int index) {
        return null;
    }

    /**
     * Short text drawn right-aligned on this row — a badge such as "[NBT]" — or null for none.
     *
     * <p>Same caveat as {@link #iconItemId}: the dependency editor honours it, the stage editor
     * does not yet.
     */
    @Nullable
    default String badgeText(int index) {
        return null;
    }

    /** Pulls this tab's edit state out of the container being opened. */
    void load(C container);

    /**
     * Writes this tab's edit state into the container about to be saved.
     *
     * <p>How often this fires depends on the host. A stage is opened once and saved once; a
     * dependency group is one of several the maintainer switches between, so a host with more than
     * one container has to store before it leaves one and load after it enters the next.
     */
    void store(C container);

    /**
     * Rebuilds the picker. Called from the screen's {@code init()}, which Minecraft runs again on
     * every window resize — the picker is rebuilt each time, while this tab and its entries are
     * created once and must survive, or a resize would throw the player's edits away.
     */
    void rebuildPicker();

    /** The picker this tab opens on Add, or null before the first {@code init()}. */
    @Nullable
    PickerOverlay picker();

    void openPicker(int centerX, int centerY, int parentWidth);
}
