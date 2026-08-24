package net.bananemdnsa.historystages.client.editor.dep;

import net.bananemdnsa.historystages.api.editor.RequirementEditor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * The editors addons have registered for their requirement types, client side only.
 *
 * <p>Kept apart from {@code RequirementTypes} on purpose. Registering a requirement is a
 * common-side concern — the server gates with it — while an editor is pure UI and must never be
 * reachable from server code. The two also close at different moments: requirements at common
 * setup, editors at client setup.
 */
public final class RequirementEditors {

    private static final Object LOCK = new Object();
    private static final Map<String, RequirementEditor> BY_REQUIREMENT = new LinkedHashMap<>();
    private static boolean frozen;

    private RequirementEditors() {}

    /** Legal only while {@link RegisterRequirementEditorsEvent} is being dispatched. */
    public static void register(RequirementEditor editor) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException("Editor for '" + editor.requirementId()
                        + "' registered after the window closed.");
            }
            if (BY_REQUIREMENT.containsKey(editor.requirementId())) {
                throw new IllegalArgumentException("Two editors registered for '"
                        + editor.requirementId() + "'.");
            }
            BY_REQUIREMENT.put(editor.requirementId(), editor);
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

    /** Null when the requirement has no editor, in which case it simply gets an empty tab. */
    @Nullable
    public static RequirementEditor byRequirement(String requirementId) {
        synchronized (LOCK) {
            return BY_REQUIREMENT.get(requirementId);
        }
    }

    /** Every registered editor, in registration order. */
    public static List<RequirementEditor> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_REQUIREMENT.values());
        }
    }

    /** Test-only: clears the registry and reopens it. Never call this from production code. */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_REQUIREMENT.clear();
            frozen = false;
        }
    }
}
