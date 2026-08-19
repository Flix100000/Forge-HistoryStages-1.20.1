package net.bananemdnsa.historystages.data.lock.category;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * The lock categories the mod knows about.
 *
 * <p>Iteration order is the editor's tab order, so anything that walks the registry to build UI
 * or reports comes out in the order players already know. Registration is closed in this phase —
 * a later phase opens it up so addons can add their own.
 */
public final class LockCategories {

    private static final Map<String, LockCategory<?>> BY_ID = new LinkedHashMap<>();

    static {
        for (LockCategory<?> category : BuiltInLockCategories.create()) {
            BY_ID.put(category.id(), category);
        }
    }

    private LockCategories() {}

    /** Every registered category, in editor tab order. */
    public static List<LockCategory<?>> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Every registered category id, in editor tab order. */
    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    /** Null when nothing is registered under this id. */
    @Nullable
    public static LockCategory<?> byId(String id) {
        return BY_ID.get(id);
    }
}
