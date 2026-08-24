package net.bananemdnsa.historystages.api.editor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The screens addons have registered for their {@code CUSTOM_SCREEN} fields, client side only.
 *
 * <p>Kept apart from the field declarations for the reason every axis here keeps them apart: a
 * declaration is common-side, because the server reads and syncs the value, while a screen is pure
 * UI and must never be reachable from server code. That is also why {@code Setting} cannot simply
 * carry the factory — it would put a client type into the data layer.
 *
 * <p>Keyed by the declaring object itself, not by its key string. An addon holds its field as a
 * {@code static final} constant and registers against that same constant, so there is no name to
 * mistype and no pair of strings that can drift apart.
 */
public final class CustomFieldScreens {

    /**
     * Builds the screen that edits one value.
     *
     * @param parent       the screen to return to, whether the edit is confirmed or abandoned
     * @param currentValue what is stored today; never null, empty when unset
     * @param onDone       call with the new value to store it; not calling it means cancelled
     */
    @FunctionalInterface
    public interface Factory {
        Screen create(Screen parent, String currentValue, Consumer<String> onDone);
    }

    private static final Object LOCK = new Object();
    // Identity, not equality: two distinct fields could compare equal by key, and registering
    // against the constant is the whole point.
    private static final Map<Object, Factory> BY_FIELD = new IdentityHashMap<>();
    private static boolean frozen;

    private CustomFieldScreens() {}

    /** Legal only while {@link RegisterCustomFieldScreensEvent} is being dispatched. */
    public static void register(Object field, Factory factory) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "A custom field screen was registered after the window closed.");
            }
            if (BY_FIELD.containsKey(field)) {
                throw new IllegalArgumentException(
                        "Two screens registered for the same custom field.");
            }
            BY_FIELD.put(field, factory);
        }
    }

    /** Null when nothing was registered, in which case the field renders but cannot be edited. */
    @Nullable
    public static Factory forField(Object field) {
        synchronized (LOCK) {
            return BY_FIELD.get(field);
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

    /** Test-only: clears the registry and reopens it. Never call this from production code. */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_FIELD.clear();
            frozen = false;
        }
    }
}
