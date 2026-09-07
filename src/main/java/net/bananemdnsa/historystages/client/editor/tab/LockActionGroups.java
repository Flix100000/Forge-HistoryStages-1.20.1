package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

/**
 * How the actions popup arranges its checkboxes, and which of them a given category sees.
 *
 * <p>One layout table for every category rather than one table per vocabulary. A category
 * declares which actions it recognises — ten for items, seven for fluids, two for trades — and
 * this drops everything else, then drops any heading left with nothing under it. That is what
 * lets a new vocabulary appear without a fourth copy of the layout, and what keeps the trade
 * sides off the items tab and the item actions off the trades tab without either being named.
 *
 * <p>Split out of the screen and free of any Minecraft type on purpose. Which checkboxes a tab
 * offers is the kind of thing that goes wrong silently — a group that should have been dropped
 * shows actions the category cannot honour, and nothing crashes — so it belongs where a unit test
 * can ask it directly rather than where only a running client can.
 */
public final class LockActionGroups {

    /**
     * The layout: first element of each row is the group's name key, the rest are its actions in
     * the order they are drawn.
     *
     * <p>{@code trade} in the first row is the item action meaning "no trading with this at all,
     * anywhere". It is the broad rule; naming one merchant's offer is the trades tab's job and
     * carries no action at all, because there is no half of a single trade to narrow to.
     */
    private static final String[][] LAYOUT = {
            {"item",   "use",   "attack", "equip", "pickup", "trade"},
            {"block",  "place", "break",  "gui"},
            // "ingredient" exists only in the fluid vocabulary and is dropped again everywhere else.
            {"output", "loot",  "recipe", "ingredient", "icon"}
    };

    private LockActionGroups() {
    }

    /** The groups as the popup should draw them for a category with this action vocabulary. */
    public static List<String[]> forVocabulary(List<String> vocabulary) {
        List<String[]> groups = new ArrayList<>();
        for (String[] group : LAYOUT) {
            List<String> kept = new ArrayList<>();
            kept.add(group[0]);
            for (int i = 1; i < group.length; i++) {
                if (vocabulary.contains(group[i])) kept.add(group[i]);
            }
            // Only the heading left: the category recognises nothing in this group, so drawing it
            // would be a caption over an empty space.
            if (kept.size() > 1) groups.add(kept.toArray(new String[0]));
        }
        return groups;
    }

    /**
     * Every action the layout can draw, whatever the vocabulary.
     *
     * <p>For the guard that says an action nobody laid out is an action nobody can switch off.
     */
    public static List<String> laidOutActions() {
        List<String> actions = new ArrayList<>();
        for (String[] group : LAYOUT) {
            for (int i = 1; i < group.length; i++) {
                if (!actions.contains(group[i])) actions.add(group[i]);
            }
        }
        return actions;
    }
}
