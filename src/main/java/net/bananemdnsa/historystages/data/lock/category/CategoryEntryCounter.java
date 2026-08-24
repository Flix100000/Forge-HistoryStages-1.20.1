package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import com.google.gson.JsonElement;
import net.bananemdnsa.historystages.data.StageEntry;

/**
 * How many things a stage gates, across every category.
 *
 * <p>The overview screen used to add this up by naming the categories, twice, and the two sums
 * disagreed — both forgot mod exceptions, and neither could know about a category registered by
 * another mod. Asking the registry instead means the number is right by construction, including
 * for categories this build has never heard of.
 */
public final class CategoryEntryCounter {

    private CategoryEntryCounter() {}

    /**
     * Counts every entry on the stage.
     *
     * <p>Entries belonging to an addon that is not installed are counted too, straight out of the
     * raw JSON. They are really there — the stage gates them the moment their mod comes back — so
     * reporting a smaller number would be a lie of the kind that makes someone delete data.
     */
    public static int totalEntries(StageEntry stage) {
        int total = 0;

        for (LockCategory<?> category : LockCategories.all()) {
            total += category.read(stage).size();
        }

        for (String addonId : stage.addonCategoryIds()) {
            if (LockCategories.byId(addonId) != null) continue; // already counted above
            JsonElement raw = stage.addonEntries(addonId);
            if (raw != null && raw.isJsonArray()) total += raw.getAsJsonArray().size();
        }

        return total;
    }
}
