package net.bananemdnsa.historystages.data.dependency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;

/**
 * Where a player's progress towards one dependency group is filed on the research scroll.
 *
 * <p>Three kinds of requirement remember something between checks: items thrown into the
 * pedestal, a consumed XP level, and — for a tag entry — both a count and the item that entry
 * settled on. All of it lives in the scroll's {@code DepositedDependencies} tag under a key built
 * here, and nowhere else — five places used to build that string by hand, which is how the
 * identity question below stayed invisible for so long.
 *
 * <p><strong>The key names the group, not its position.</strong> A group carries an id from the
 * moment a stage is loaded; deleting, reordering or duplicating groups in the editor leaves those
 * ids alone, so a player's deposits stay with the requirement they were made for. Groups written
 * before ids existed have none, and fall back to their position — which is exactly what their
 * progress is already filed under, so old scrolls keep working without a migration.
 */
public final class DependencyProgress {

    private DependencyProgress() {}

    /** The identity {@link #key} files this group's progress under. */
    public static String groupKey(DependencyGroup group, int index) {
        String id = group == null ? null : group.getId();
        return id == null || id.isEmpty() ? String.valueOf(index) : id;
    }

    /**
     * The NBT key one requirement's progress inside one group is stored under.
     *
     * <p>{@code suffix} identifies the requirement within the group: {@code "Item_<item id>"} for
     * a deposited item, {@code "XP"} for a consumed level, {@code "ItemTag_<#tag>"} and
     * {@code "ItemTagChoice_<#tag>"} for a tag entry. An addon requirement picks its own and
     * should include something of its own id, since the group is the only thing keeping two
     * requirements' keys apart otherwise.
     */
    public static String key(String groupKey, String suffix) {
        return "Group_" + groupKey + "_" + suffix;
    }

    /**
     * The suffix a deposited item is filed under.
     *
     * <p>An item id can never contain a {@code #}, which is the whole reason this stays apart
     * from {@link #itemTagSuffix}: both build a key out of the same shape of argument, and a tag
     * entry sharing a counter with an item entry would be silent.
     */
    public static String itemSuffix(String itemId) {
        return "Item_" + itemId;
    }

    /** The suffix a tag entry's deposited count is filed under. {@code tagId} carries its "#". */
    public static String itemTagSuffix(String tagId) {
        return "ItemTag_" + tagId;
    }

    /**
     * The suffix the item a tag entry settled on is filed under.
     *
     * <p>Written the moment the first matching item is actually booked, in the same write as the
     * counter beside it, and never before — a tag entry whose counter is zero is still open.
     * Nothing clears it either: there is no reset path for a scroll, so the choice lives and dies
     * with the scroll, exactly like the count.
     */
    public static String itemTagChoiceSuffix(String tagId) {
        return "ItemTagChoice_" + tagId;
    }

    /** The suffix a consumed XP level is filed under. */
    public static final String XP_SUFFIX = "XP";

    /**
     * Gives every group in the list an id, and reports the ones it had to take away.
     *
     * <p>Called on every stage as it loads. A group from a file written before ids existed gets
     * its position — not a migration but the opposite of one, since its progress on existing
     * scrolls is already filed under exactly that string. What changes is only what happens
     * next: the editor moves the id along with the group instead of leaving it behind with the
     * slot.
     *
     * <p>A duplicate id is taken away from the later group. Two groups filed under one id share
     * every deposit made into either of them — the exact failure this scheme exists to prevent,
     * and copying a group in a text editor is an easy way to get there.
     *
     * @return the ids that were found twice, in the order they were met, so the caller can say
     *         so; empty when there was nothing wrong
     */
    public static List<String> assignIds(List<DependencyGroup> groups) {
        if (groups == null || groups.isEmpty()) return List.of();

        List<String> duplicates = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < groups.size(); i++) {
            DependencyGroup group = groups.get(i);
            String id = group.getId();
            boolean named = id != null && !id.isEmpty();
            if (named && taken.add(id)) continue;
            if (named) duplicates.add(id);

            // The position first, because that is what an id-less group's existing progress is
            // already under. Only when another group has claimed that string does it need one
            // that belongs to nothing.
            String replacement = String.valueOf(i);
            if (taken.contains(replacement)) replacement = DependencyGroup.freshId(groups);
            group.setId(replacement);
            taken.add(replacement);
        }
        return duplicates;
    }
}
