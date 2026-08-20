package net.bananemdnsa.historystages.client.editor.trigger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * The authoring side of addon auto-triggers, client only.
 *
 * <p>Apart from the type registry for the same reason the category editors are apart from the
 * categories: the server needs the type to load and fire a trigger, and must never be dragged
 * into the editor's UI to do it.
 */
public final class TriggerEditors {

    private static final Object LOCK = new Object();
    private static final Map<String, TriggerEditor> BY_TYPE = new LinkedHashMap<>();
    private static boolean frozen;

    private TriggerEditors() {}

    /** Legal only while {@link RegisterTriggerEditorsEvent} is being dispatched. */
    public static void register(TriggerEditor editor) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException("Editor for trigger type '" + editor.type()
                        + "' registered after the window closed.");
            }
            if (BY_TYPE.containsKey(editor.type())) {
                throw new IllegalArgumentException("Two editors registered for trigger type '"
                        + editor.type() + "'.");
            }
            BY_TYPE.put(editor.type(), editor);
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

    /** Null when this trigger type cannot be authored here; it still loads, saves and fires. */
    @Nullable
    public static TriggerEditor byType(String type) {
        synchronized (LOCK) {
            return BY_TYPE.get(type);
        }
    }

    /** Every registered editor, in registration order. */
    public static List<TriggerEditor> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_TYPE.values());
        }
    }

    /** Test-only: clears the registry and reopens it. */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_TYPE.clear();
            frozen = false;
        }
    }
}
