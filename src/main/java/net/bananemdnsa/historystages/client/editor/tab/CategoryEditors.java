package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.CategoryEditor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * The editors addons have registered for their categories, client side only.
 *
 * <p>Kept apart from {@code LockCategories} on purpose. Registering a category is a common-side
 * concern — the server needs it to gate things — while an editor is pure UI and must never be
 * reachable from server code. The two also close at different moments: categories at common
 * setup, editors at client setup.
 */
public final class CategoryEditors {

    private static final Object LOCK = new Object();
    private static final Map<String, CategoryEditor> BY_CATEGORY = new LinkedHashMap<>();
    private static boolean frozen;

    private CategoryEditors() {}

    /** Legal only while {@link RegisterCategoryEditorsEvent} is being dispatched. */
    public static void register(CategoryEditor editor) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException("Editor for '" + editor.categoryId()
                        + "' registered after the window closed.");
            }
            if (BY_CATEGORY.containsKey(editor.categoryId())) {
                throw new IllegalArgumentException("Two editors registered for '"
                        + editor.categoryId() + "'.");
            }
            BY_CATEGORY.put(editor.categoryId(), editor);
        }
    }

    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    public static boolean isFrozen() {
        synchronized (LOCK) {
            return frozen;
        }
    }

    /** Null when the category has no editor, in which case it simply gets no tab. */
    @Nullable
    public static CategoryEditor byCategory(String categoryId) {
        synchronized (LOCK) {
            return BY_CATEGORY.get(categoryId);
        }
    }

    /** Every registered editor, in registration order. */
    public static List<CategoryEditor> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_CATEGORY.values());
        }
    }

    /** Test-only: clears the registry and reopens it. Never call this from production code. */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_CATEGORY.clear();
            frozen = false;
        }
    }
}
