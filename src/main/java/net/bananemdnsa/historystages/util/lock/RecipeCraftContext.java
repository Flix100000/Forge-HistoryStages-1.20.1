package net.bananemdnsa.historystages.util.lock;

import java.util.UUID;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

/**
 * Who is crafting, for the duration of one recipe resolution.
 *
 * <p>{@code RecipeManager.getRecipeFor} has no player parameter — the manager is one instance per
 * level — so the only place that knows is the station the player is standing at. A station that
 * knows sets this around its single resolution call; {@code RecipeHandler} reads it, and stays the
 * only place that judges. That is deliberate: four stations formulating the same decision is the
 * kind of duplicate that has already drifted apart twice in this repo.
 *
 * <p>Set around the <em>call</em>, never around the method. A menu does more than resolve recipes,
 * and the window has to stay shut for the rest of it.
 *
 * <p><strong>No crafter set means global-only</strong>, which is what every recipe check did
 * before this class existed. Furnaces, hoppers, autocrafters and unknown mod stations therefore
 * do not change behaviour at all.
 *
 * <p>A {@link ThreadLocal} because an integrated server resolves recipes on its own thread while
 * the client resolves on the render thread, and neither may see the other's crafter.
 *
 * <p>Minecraft-free on purpose — it stores a {@link UUID}, not a player — so the window-closing
 * rules can be tested on a classpath with no game on it.
 */
public final class RecipeCraftContext {

    private static final ThreadLocal<UUID> CRAFTER = new ThreadLocal<>();

    private RecipeCraftContext() {}

    /**
     * Runs {@code resolution} with {@code crafter} recorded as the player crafting.
     *
     * <p>Restores whatever was recorded before rather than clearing outright. Nesting is not
     * expected, but a station resolving inside another station's window must not silently take it
     * over — and the restore costs nothing.
     *
     * @param crafter the crafting player, or null for a station that has no player to name
     */
    public static <T> T with(@Nullable UUID crafter, Supplier<T> resolution) {
        if (crafter == null) return resolution.get();

        UUID previous = CRAFTER.get();
        CRAFTER.set(crafter);
        try {
            return resolution.get();
        } finally {
            if (previous == null) {
                CRAFTER.remove();
            } else {
                CRAFTER.set(previous);
            }
        }
    }

    /** The crafter for the resolution in progress, or null when there is none. */
    @Nullable
    public static UUID crafter() {
        return CRAFTER.get();
    }
}
