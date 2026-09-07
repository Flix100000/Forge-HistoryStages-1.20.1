package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;

import java.util.List;

import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
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
     * False for a tab with nothing to add to, so the host leaves its Add button out.
     *
     * <p>Lived on {@code DependencyTab} first, for the XP requirement — a single value, where a
     * button that opens nothing is worse than no button at all. The lock axis then grew the same
     * shape in the merchant levels, which are five known switches rather than a list. The same
     * question asked twice under one name belongs on the contract both axes share; asked twice
     * under two names is how the two start meaning slightly different things.
     */
    default boolean hasAddButton() {
        return true;
    }

    /**
     * An item id to draw as an icon at the left of this row, or null for none.
     *
     * <p>Deliberately an id and not an {@code ItemStack}: a tab says what to show, the host decides
     * how. Rows keep their fixed height either way, which is what keeps this from disturbing the
     * host's scroll arithmetic.
     *
     * <p>Honoured by both editors. The stage editor keeps special cases of its own for the
     * categories that predate this hook — items, recipes, the entity tabs — and falls back to
     * asking the tab for everything else.
     */
    @Nullable
    default String iconItemId(int index) {
        return null;
    }

    /**
     * Short text drawn right-aligned on this row — a badge such as "[NBT]" — or null for none.
     *
     * <p>Honoured by both editors, after whatever badges the host declared for itself.
     */
    @Nullable
    default String badgeText(int index) {
        return null;
    }

    /**
     * A painted zone at the left of a row: how wide it is, and what goes in it.
     *
     * <p>More than {@link #iconItemId} can express, and the reason it exists: a trade row shows
     * two prices, an arrow and the ware before its name begins, and none of that is one item.
     * Rows keep their fixed height either way, so this disturbs nothing about the host's scroll
     * arithmetic.
     *
     * @param width how many pixels to reserve at the left; the row's text starts after them
     */
    record LeadingArt(int width, EditorRowList.RowPainter painter) {}

    /**
     * The zone to paint at the left of this row, or null to leave the matter to
     * {@link #iconItemId}.
     *
     * <p>Wins over {@code iconItemId} when both answer: a tab that paints the zone itself has
     * already put whatever icon it wanted in there, and drawing the host's on top of it would
     * show the same item twice.
     */
    @Nullable
    default LeadingArt leadingArt(int index) {
        return null;
    }

    /**
     * What this row should read as, or null to show the stored entry unchanged.
     *
     * <p>Separate from the stored entry on purpose. The entry is what "copy id" puts on the
     * clipboard and what somebody would type into a stage file; the display text is for a person
     * reading a list. For most categories those are the same string, which is why the default is
     * null and nothing but the trade tab answers.
     */
    @Nullable
    default String displayText(int index, String entry) {
        return null;
    }

    /**
     * How tall this tab's content is at the given width.
     *
     * <p>The single source of content height for both screens. Overriding it is what lets a tab
     * draw rows of another height, or something that is not rows at all, without the host having
     * to know.
     */
    default int contentHeight(int width) {
        return EditorRowList.heightFor(entries().size());
    }

    /**
     * Draws this tab's content.
     *
     * @return false to say "I drew nothing" — the host then draws {@link #entries()} as its
     *         standard rows, which is what every tab shipped before Phase 3b relies on
     */
    default boolean renderContent(TabRenderContext ctx) {
        return false;
    }

    /**
     * Handles a click inside the content area.
     *
     * <p>Six input methods and not one, because drawing without input is a picture. An embedded
     * number field would take its {@code +}/{@code −} clicks through this one and never see a
     * typed digit; a slider needs {@link #mouseDragged} and {@link #mouseReleased} as well.
     *
     * @return true if this tab consumed the click
     */
    default boolean mouseClicked(TabInputContext ctx, int button) {
        return false;
    }

    /**
     * Called when this tab becomes the visible one, and again when its container changes under it.
     *
     * <p>Exists for entrance animations. A tab that draws itself owns its row list, so resetting
     * the host's does not reach it — and the symptom is silent: the rows simply appear instead of
     * arriving, which reads as "the animation was never built" rather than "it was never started".
     */
    default void onShown() {
    }

    /**
     * Which of this tab's rows is under the cursor, or -1.
     *
     * <p>Only a tab that draws itself needs this. The host knows where its own rows are; it cannot
     * know where a tab put them, and without an answer such a tab could never offer a right-click
     * menu.
     */
    default int rowAt(TabInputContext ctx) {
        return -1;
    }

    /** @return true if this tab consumed the drag. Needed by anything draggable, such as a slider. */
    default boolean mouseDragged(TabInputContext ctx, int button) {
        return false;
    }

    /** @return true if this tab consumed the release. */
    default boolean mouseReleased(TabInputContext ctx, int button) {
        return false;
    }

    /** @return true if this tab consumed the scroll — for a tab with a scroll region of its own. */
    default boolean mouseScrolled(TabInputContext ctx, double scrollX, double scrollY) {
        return false;
    }

    /**
     * @return true if this tab consumed the key. A tab with a focused field must return true for
     *         {@code ESC}, or the host closes the editor instead of leaving the field
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** @return true if this tab consumed the character. Without it no embedded field can be typed into. */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
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

    /**
     * Whichever of this tab's overlays currently holds input, or null when none is up.
     *
     * <p>One method rather than a list: a tab may own as many overlays as it likes and only it
     * knows which is showing. The host renders and forwards to whatever comes back — which is what
     * lets a tab put up a dropdown of its own beside the Add picker.
     *
     * <p>Null before the first {@code init()}, because the picker is built there.
     */
    @Nullable
    PickerOverlay activeOverlay();

    void openPicker(int centerX, int centerY, int parentWidth);
}
