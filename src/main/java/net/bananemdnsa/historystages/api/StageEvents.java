package net.bananemdnsa.historystages.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.UUID;

/**
 * Public API: events fired when global or individual stages are unlocked/locked.
 *
 * <p>Other mods, KubeJS scripts, or CraftTweaker can subscribe to react to stage changes.
 * Each event has its own listener interface; register on the corresponding {@code EVENT}.</p>
 *
 * <p>Example (Java):</p>
 * <pre>
 * StageEvents.UNLOCKED.register((stageId, displayName) -&gt;
 *         System.out.println("Stage unlocked: " + stageId));
 * </pre>
 */
public final class StageEvents {
    private StageEvents() {
    }

    @FunctionalInterface
    public interface Global {
        void onChanged(String stageId, String displayName);
    }

    @FunctionalInterface
    public interface Individual {
        void onChanged(String stageId, String displayName, UUID playerUuid);
    }

    public static final Event<Global> UNLOCKED = EventFactory.createArrayBacked(Global.class,
            listeners -> (stageId, displayName) -> {
                for (Global listener : listeners) listener.onChanged(stageId, displayName);
            });

    public static final Event<Global> LOCKED = EventFactory.createArrayBacked(Global.class,
            listeners -> (stageId, displayName) -> {
                for (Global listener : listeners) listener.onChanged(stageId, displayName);
            });

    public static final Event<Individual> INDIVIDUAL_UNLOCKED = EventFactory.createArrayBacked(Individual.class,
            listeners -> (stageId, displayName, playerUuid) -> {
                for (Individual listener : listeners) listener.onChanged(stageId, displayName, playerUuid);
            });

    public static final Event<Individual> INDIVIDUAL_LOCKED = EventFactory.createArrayBacked(Individual.class,
            listeners -> (stageId, displayName, playerUuid) -> {
                for (Individual listener : listeners) listener.onChanged(stageId, displayName, playerUuid);
            });
}
